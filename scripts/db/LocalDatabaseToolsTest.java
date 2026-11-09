import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

/** No SQL connection or mutations. Exercises JDBC multi-result batch completion. */
class LocalDatabaseToolsTest {
    public static void main(String[] args) throws Exception {
        if(!LocalDatabase.json("a\"b\\c/\n").equals("\"a\\\"b\\\\c\\/\\n\""))throw new AssertionError("JSON escaping");
        Map<String,Object> row=new LinkedHashMap<>();row.put("name","horse");row.put("nullable",false);row.put("ordinal",12);
        if(!LocalDatabase.json(List.of(row)).equals("[{\"name\":\"horse\",\"nullable\":false,\"ordinal\":12}]"))throw new AssertionError("Metadata types/order");
        var plan=LocalDatabase.migrationPlan();
        if(plan.size()!=6||!plan.get(2).get("version").equals("V20261021_01")||!plan.get(2).get("order").equals(3))throw new AssertionError("Groom migration omitted or unordered");
        if(!plan.get(3).get("version").equals("V20261027_01")||!plan.get(3).get("order").equals(4))throw new AssertionError("Strength migration omitted or unordered");
        if(!plan.get(4).get("version").equals("V20261029_01")||!plan.get(4).get("order").equals(5))throw new AssertionError("Care history migration omitted or unordered");
        if(!plan.get(5).get("version").equals("V20261109_01")||!plan.get(5).get("order").equals(6))throw new AssertionError("In-ride incident migration omitted or unordered");
        int guards=0;
        String[][] rejected={
            {"master","racehorse_local_member_new","master","PC","PC"},
            {"HSF302","racehorse_local_member_new","HSF302","PC","PC"},
            {"racehorse_local_member_new","RACEHORSE_LOCAL_MEMBER_NEW","racehorse_local_member_new","PC","PC"},
            {"racehorse_old];DROP DATABASE other;--","racehorse_local_member_new","racehorse_old];DROP DATABASE other;--","PC","PC"},
            {"racehorse_old","racehorse_local_member_new","racehorse_typo","PC","PC"},
            {"racehorse_old","racehorse_local_member_new","racehorse_old","OTHER","PC"}
        };
        for(String[] input:rejected){try{LocalDatabase.validateRetirement(input[0],input[1],input[2],input[3],input[4]);throw new AssertionError("Unsafe retirement accepted");}catch(IllegalStateException expected){guards++;}}
        LocalDatabase.validateRetirement("Racehorse_training","racehorse_local_member_new","Racehorse_training","pc","PC");
        if(guards!=6)throw new AssertionError("Retirement guard cases missing");
        if(args.length==2&&args[0].equals("--package"))LocalDatabase.verifyPackage(java.nio.file.Path.of(args[1]));
        int[] phase={0},closed={0};
        ResultSet rs=(ResultSet)Proxy.newProxyInstance(LocalDatabaseToolsTest.class.getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->{
            if(m.getName().equals("next"))return false;
            if(m.getName().equals("close")){closed[0]++;return null;}
            throw new UnsupportedOperationException(m.getName());
        });
        Statement s=(Statement)Proxy.newProxyInstance(LocalDatabaseToolsTest.class.getClassLoader(),new Class[]{Statement.class},(p,m,a)->switch(m.getName()) {
            case "execute" -> false;
            case "getUpdateCount" -> phase[0]==0?7:-1;
            case "getMoreResults" -> {phase[0]++;yield phase[0]==1;}
            case "getResultSet" -> rs;
            default -> throw new UnsupportedOperationException(m.getName());
        });
        LocalDatabase.executeBatch(s,"approved multi-statement batch");
        if(phase[0]!=2||closed[0]!=1)throw new AssertionError("Batch not fully consumed before next operation");
        System.out.println("LOCAL_TOOL_UNIT_PASS; migration order, package hashes, JDBC result completion and 7 retirement guard cases; zero database connections");
        if(args.length==2&&args[0].equals("--live-readonly")) {
            try(Connection c=LocalDatabase.connect(LocalDatabase.database());Statement batch=c.createStatement()) {
                batch.setQueryTimeout(20);
                LocalDatabase.executeBatch(batch,"BEGIN TRANSACTION; SELECT 1 AS probe; COMMIT TRANSACTION;");
                LocalDatabase.verify(c,java.nio.file.Path.of(args[1]));
            }
            System.out.println("LIVE_JDBC_MULTIPLE_RESULTS_AND_COMMIT_PASS; SELECT only; no schema/data mutation");
        }
    }
}
