import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Local-only operational JDBC adapter. Never edits an existing database during install. */
class LocalDatabase {
    static final String SCHEMA = "416eb579e59f926e01c08bdfb25af6db5bc9376e87ec93eb7b6aa35d512a9141";
    static final String MIGRATION = "18a5d3eba5aad92967c6a6d2f6646410a98828d69e3c5be8fa3c61a7ef418604";
    static String env(String key) { String v=System.getenv(key); require(v!=null&&!v.isBlank(), "ENV_REQUIRED_"+key); return v; }
    static void require(boolean ok,String code) { if(!ok) throw new IllegalStateException(code); }
    static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static String database() { String d=env("RH_DATABASE"); require(d.matches("racehorse_local_[a-z0-9_]{1,70}"),"NEW_LOCAL_DATABASE_NAME_REQUIRED"); return d; }
    static String machine() { return env("COMPUTERNAME"); }
    static Connection connect(String db) throws Exception {
        String host=env("RH_SQL_HOST"),port=env("RH_SQL_PORT"),cert=env("RH_SQL_CERT_HOST");
        require(host.equalsIgnoreCase("localhost")||host.equals("127.0.0.1")||host.equalsIgnoreCase(machine()),"LOCAL_HOST_REQUIRED");
        require(port.matches("[0-9]{1,5}")&&Integer.parseInt(port)>0&&Integer.parseInt(port)<=65535,"INVALID_PORT");
        require(cert.matches("[A-Za-z0-9.-]+"),"CERTIFICATE_HOST_REQUIRED");
        Properties p=new Properties(); p.setProperty("encrypt","true"); p.setProperty("trustServerCertificate","false");
        p.setProperty("trustStore",env("RH_SQL_TRUSTSTORE")); p.setProperty("trustStorePassword",env("RH_SQL_TRUSTSTORE_PASSWORD"));
        p.setProperty("hostNameInCertificate",cert); p.setProperty("loginTimeout","5"); p.setProperty("applicationName","Racehorse-Local-Installer");
        String user=System.getenv("RH_SQL_USERNAME");
        if(user==null||user.isBlank()) p.setProperty("integratedSecurity","true");
        else {p.setProperty("user",user);p.setProperty("password",env("RH_SQL_PASSWORD"));}
        Connection c=DriverManager.getConnection("jdbc:sqlserver://"+host+":"+port+";databaseName="+db,p);
        try(Statement s=c.createStatement()) {
            s.setQueryTimeout(15);
            try(ResultSet r=s.executeQuery("SELECT DB_NAME(),CONVERT(nvarchar(128),SERVERPROPERTY('ComputerNamePhysicalNetBIOS')),CONVERT(int,SERVERPROPERTY('ProductMajorVersion'))")) {
                require(r.next()&&db.equals(r.getString(1))&&machine().equalsIgnoreCase(r.getString(2)),"LOCAL_SQL_IDENTITY_MISMATCH");
                require(r.getInt(3)>=16,"SQL_SERVER_2022_OR_NEWER_REQUIRED");
            }
            try(ResultSet r=s.executeQuery("SELECT encrypt_option,net_transport FROM sys.dm_exec_connections WHERE session_id=@@SPID")) {
                require(r.next()&&"TRUE".equalsIgnoreCase(r.getString(1))&&"TCP".equalsIgnoreCase(r.getString(2)),"ENCRYPTED_TCP_REQUIRED");
            }
        } catch(Exception e) {c.close();throw e;}
        return c;
    }
    static String snapshot(Connection c,Path root) throws Exception {
        StringBuilder b=new StringBuilder("{");
        String[] sections=Files.readString(root.resolve("database/verification/snapshot.sql")).split("(?m)^-- SECTION ");
        require(sections.length==9,"EXACT_METADATA_SECTIONS_REQUIRED");
        for(int i=1;i<sections.length;i++) {
            int newline=sections[i].indexOf('\n');String key=sections[i].substring(0,newline).strip();
            require(key.matches("[a-z_]+"),"INVALID_METADATA_SECTION");
            if(i>1)b.append(',');b.append('"').append(key).append("\":");
            List<Map<String,Object>> rows=new ArrayList<>();
            try(Statement s=c.createStatement()) {s.setQueryTimeout(20);try(ResultSet r=s.executeQuery(sections[i].substring(newline+1))) {
                ResultSetMetaData md=r.getMetaData();
                while(r.next()) {Map<String,Object> row=new LinkedHashMap<>();for(int j=1;j<=md.getColumnCount();j++){Object v=r.getObject(j);if(v!=null)row.put(md.getColumnLabel(j),v);}rows.add(row);}
            }}
            String[] order=key.equals("tables")?new String[]{"schema","name"}:key.equals("columns")?new String[]{"schema","table","ordinal"}:new String[]{"schema","table","name","ordinal"};
            rows.sort((a,z)->{for(String field:order){Object x=a.get(field),y=z.get(field);int cmp=x instanceof Number&&y instanceof Number?Long.compare(((Number)x).longValue(),((Number)y).longValue()):Objects.toString(x,"").compareTo(Objects.toString(y,""));if(cmp!=0)return cmp;}return 0;});
            b.append(key.equals("settings")?json(rows.getFirst()):json(rows));
        }
        return b.append('}').toString();
    }
    static String json(Object value) {
        if(value instanceof Map<?,?> m){List<String> p=new ArrayList<>();m.forEach((k,v)->p.add(json(k.toString())+":"+json(v)));return "{"+String.join(",",p)+"}";}
        if(value instanceof List<?> l)return "["+String.join(",",l.stream().map(LocalDatabase::json).toList())+"]";
        if(value instanceof Number||value instanceof Boolean)return value.toString();
        StringBuilder s=new StringBuilder("\"");for(char ch:value.toString().toCharArray()){switch(ch){case '"'->s.append("\\\"");case '\\'->s.append("\\\\");case '/'->s.append("\\/");case '\n'->s.append("\\n");case '\r'->s.append("\\r");case '\t'->s.append("\\t");default->{if(ch<32)s.append(String.format("\\u%04x",(int)ch));else s.append(ch);}}}return s.append('"').toString();
    }
    static void executeBatch(Statement s,String sql) throws Exception {
        boolean result=s.execute(sql);
        while(true) {
            if(result) {try(ResultSet rows=s.getResultSet()){while(rows.next()) { /* drain without logging data */ }}}
            else if(s.getUpdateCount()==-1) break;
            result=s.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
        }
    }
    static void verify(Connection c,Path root) throws Exception {
        String actual=snapshot(c,root),expected=Files.readString(root.resolve("database/verification/expected-schema.json")).strip();
        require(actual.equals(expected),"SCHEMA_DRIFT_DETECTED");
    }
    public static void main(String[] args) {
        try { run(args); }
        catch(Exception e) {
            String code=e instanceof IllegalStateException?e.getMessage():e.getClass().getSimpleName();
            System.err.println("LOCAL_DATABASE_FAILED "+code);
            if(e instanceof SQLException se)System.err.println("SQL_STATE="+se.getSQLState()+" SQL_ERROR="+se.getErrorCode());
            System.exit(2);
        }
    }
    static void run(String[] args) throws Exception {
        require(args.length>=2,"OPERATION_AND_REPOSITORY_REQUIRED");
        String op=args[0],db=database(); Path root=Path.of(args[1]).toAbsolutePath();
        require(hash(Files.readAllBytes(root.resolve("database/schema.sql"))).equals(SCHEMA),"CANONICAL_SCHEMA_CHECKSUM_MISMATCH");
        require(hash(Files.readAllBytes(root.resolve("database/migrations/V20261016_01__create_registration_request.sql"))).equals(MIGRATION),"MIGRATION_CHECKSUM_MISMATCH");
        if(op.equals("install")) {
            // A master connection checks local identity and strict TLS BEFORE any CREATE.
            try(Connection c=connect("master");PreparedStatement q=c.prepareStatement("SELECT DB_ID(?)")) {
                q.setString(1,db);try(ResultSet r=q.executeQuery()){r.next();require(r.getObject(1)==null,"DATABASE_ALREADY_EXISTS_NO_OVERWRITE");}
                try(Statement s=c.createStatement()){s.setQueryTimeout(60);s.execute("CREATE DATABASE ["+db+"] COLLATE SQL_Latin1_General_CP1_CI_AS");}
            }
            try(Connection c=connect(db);Statement s=c.createStatement()) {
                s.setQueryTimeout(60);
                executeBatch(s,Files.readString(root.resolve("database/schema.sql"),StandardCharsets.UTF_8));
                executeBatch(s,Files.readString(root.resolve("database/migrations/V20261016_01__create_registration_request.sql"),StandardCharsets.UTF_8));
            }
            try(Connection fresh=connect(db)){verify(fresh,root);}
            System.out.println("INSTALL_AND_STRUCTURAL_VERIFY_PASS"); return;
        }
        try(Connection c=connect(db)) {
            switch(op) {
                case "identity" -> {
                    try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT collation_name,compatibility_level,CONVERT(nvarchar(128),SERVERPROPERTY('ProductVersion')) FROM sys.databases WHERE name=DB_NAME()")) {
                        require(r.next(),"DATABASE_METADATA_REQUIRED");
                        Map<String,Object> identity=new LinkedHashMap<>();identity.put("database",db);identity.put("machine",machine());
                        identity.put("sqlVersion",r.getString(3));identity.put("driverVersion",c.getMetaData().getDriverVersion());
                        identity.put("collation",r.getString(1));identity.put("compatibilityLevel",r.getInt(2));identity.put("strictTlsEncryptedTcp",true);
                        System.out.println(json(identity));
                    }
                }
                case "snapshot" -> System.out.println(snapshot(c,root));
                case "verify" -> {verify(c,root);System.out.println("STRICT_TLS_SCHEMA_VERIFY_PASS");}
                case "seed" -> {
                    verify(c,root);
                    try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT COUNT_BIG(*) FROM dbo.app_user")){r.next();require(r.getLong(1)==0,"SEED_REFUSES_EXISTING_USERS");}
                    require(args.length==3,"RENDERED_APPROVED_SEED_REQUIRED");
                    try(Statement s=c.createStatement()){s.setQueryTimeout(20);executeBatch(s,Files.readString(Path.of(args[2]),StandardCharsets.UTF_8));}
                    System.out.println("OPTIONAL_AUTH_SEED_PASS");
                }
                case "account" -> {
                    try(PreparedStatement q=c.prepareStatement("SELECT id,account_status,last_login_at,(SELECT COUNT_BIG(*) FROM dbo.audit_log WHERE actor_user_id=u.id AND action='USER_LOGGED_OUT'),(SELECT COUNT_BIG(*) FROM dbo.stable_box) FROM dbo.app_user u WHERE email=?")) {
                        q.setString(1,env("RH_ACCOUNT_EMAIL"));try(ResultSet r=q.executeQuery()) {
                            require(r.next()&&"ACTIVE".equals(r.getString(2)),"ACTIVE_ACCOUNT_REQUIRED");Timestamp at=r.getTimestamp(3);
                            System.out.printf("{\"id\":%d,\"active\":\"ACTIVE\",\"lastLogin\":%s,\"logoutAudits\":%d,\"boxCount\":%d}%n",r.getLong(1),at==null?"null":Long.toString(at.getTime()),r.getLong(4),r.getLong(5));
                            require(!r.next(),"ACCOUNT_NOT_UNIQUE");
                        }
                    }
                }
                default -> throw new IllegalStateException("UNKNOWN_OPERATION");
            }
        }
    }
}
