import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** JDBC fixtures only. Proves a failed backup, identity, VERIFYONLY or workload gate never reaches DROP. */
class LocalDatabaseRetirementTest {
    static class Fixture {
        final String failure;
        final List<String> events=new ArrayList<>();
        int identities,workloads;
        boolean dropped;
        Fixture(String failure){this.failure=failure;}
        // Keys are column labels or 1-based column indexes, as JDBC callers use both.
        ResultSet rows(Map<?,?> row) {
            int[] pos={0};
            return (ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->switch(m.getName()) {
                case "next" -> ++pos[0]==1;
                case "getString" -> Objects.toString(row.get(a[0]),null);
                case "getInt" -> ((Number)row.get(a[0])).intValue();
                case "getLong" -> ((Number)row.get(a[0])).longValue();
                case "getBoolean" -> (Boolean)row.get(a[0]);
                case "getObject" -> row.get(a[0]);
                case "close" -> null;
                default -> throw new AssertionError(m.getName());
            });
        }
        PreparedStatement statement(String sql) {
            return (PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->switch(m.getName()) {
                case "setString","setQueryTimeout","close" -> null;
                case "executeQuery" -> {
                    if(sql.contains("database_guid")){identities++;events.add("identity");yield rows(Map.of(1,failure.equals("identity")&&identities==2?"changed-guid":"old-guid"));}
                    if(sql.contains("dm_exec_sessions")){workloads++;events.add("idle");yield rows(Map.of(1,failure.equals("busy")||failure.equals("late-busy")&&workloads==2?1L:0L));}
                    if(sql.startsWith("RESTORE HEADERONLY")){
                        events.add("header");yield rows(Map.of("DatabaseName",failure.equals("header")?"other":"racehorse_old","BackupType",1,"HasBackupChecksums",true,"IsDamaged",false));
                    }
                    if(sql.startsWith("SELECT DB_ID")){Map<Object,Object> row=new HashMap<>();row.put(1,null);yield rows(row);}
                    throw new AssertionError(sql);
                }
                case "execute" -> {
                    String command=a==null||a.length==0?sql:(String)a[0];
                    if(command.startsWith("BACKUP DATABASE")){
                        if(!command.contains("COPY_ONLY,CHECKSUM,NOFORMAT,NOINIT"))throw new AssertionError("Safe backup options missing");
                        events.add("backup");if(failure.equals("backup"))throw new SQLException("Fixture backup failure");
                    } else if(command.startsWith("RESTORE VERIFYONLY")){
                        events.add("verify");if(failure.equals("verify"))throw new SQLException("Fixture VERIFYONLY failure");
                    } else if(command.equals("DROP DATABASE [racehorse_old]")){
                        events.add("drop");dropped=true;
                    } else throw new AssertionError("Unexpected SQL mutation: "+command);
                    yield false;
                }
                case "getUpdateCount" -> -1;
                default -> throw new AssertionError(m.getName());
            });
        }
        Connection connection() {
            return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->switch(m.getName()) {
                case "prepareStatement" -> statement((String)a[0]);
                case "createStatement" -> statement("");
                case "close" -> null;
                default -> throw new AssertionError(m.getName());
            });
        }
    }
    public static void main(String[] args) throws Exception {
        for(String failure:List.of("busy","backup","verify","header","identity","late-busy")){
            Fixture f=new Fixture(failure);
            try{LocalDatabase.retireOldDatabase(f.connection(),"racehorse_old",Path.of("fixture-only.bak"));throw new AssertionError("Failure not rejected: "+failure);}
            catch(SQLException|IllegalStateException expected){if(f.dropped)throw new AssertionError("DROP after failed gate: "+failure);}
        }
        Fixture ok=new Fixture("");LocalDatabase.retireOldDatabase(ok.connection(),"racehorse_old",Path.of("fixture-only.bak"));
        if(!ok.events.equals(List.of("identity","idle","backup","verify","header","identity","idle","drop")))throw new AssertionError(ok.events);
        System.out.println("RETIREMENT_FIXTURE_PASS: 6 fail-closed cases and ordered success; no SQL connection or file written");
    }
}
