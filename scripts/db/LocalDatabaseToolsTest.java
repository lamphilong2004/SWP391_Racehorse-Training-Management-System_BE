import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

/** No SQL connection or mutations. Exercises JDBC multi-result batch completion. */
class LocalDatabaseToolsTest {
    public static void main(String[] args) throws Exception {
        if(!LocalDatabase.json("a\"b\\c/\n").equals("\"a\\\"b\\\\c\\/\\n\""))throw new AssertionError("JSON escaping");
        Map<String,Object> row=new LinkedHashMap<>();row.put("name","horse");row.put("nullable",false);row.put("ordinal",12);
        if(!LocalDatabase.json(List.of(row)).equals("[{\"name\":\"horse\",\"nullable\":false,\"ordinal\":12}]"))throw new AssertionError("Metadata types/order");
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
        System.out.println("LOCAL_TOOL_UNIT_PASS 3 assertions; zero database connections");
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
