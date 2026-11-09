import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Local-only operational JDBC adapter. Never edits an existing database during install. */
class LocalDatabase {
    static final String SCHEMA = "416eb579e59f926e01c08bdfb25af6db5bc9376e87ec93eb7b6aa35d512a9141";
    static final String CORE007_MIGRATION = "431c6d0b0f84309b2820e2697e26dd52ab6072fdd664d0eef9d850abcc98b34c";
    static final String CORE045_MIGRATION = "decb437690c9e8e4249caba168c6095f1c21bb627bfd575c4b14f6fa2f736488";
    record Migration(String version, String path, String sha256) {}
    static final List<Migration> MIGRATIONS = List.of(
        new Migration("V20261016_01", "database/migrations/V20261016_01__create_registration_request.sql", CORE007_MIGRATION),
        new Migration("V20261020_01", "database/migrations/V20261020_01__add_care_task_assigned_groom_user_id.sql", CORE045_MIGRATION),
        new Migration("V20261021_01", "database/migrations/V20261021_01__training_session_groom_assignment.sql", "79c4098f5635e521b2f8e9ca81a21df36e5c5cdaa618094dccc9ac851ab00222"),
        new Migration("V20261027_01", "database/migrations/V20261027_01__horse_strength_and_template_focus.sql", "f6693efa86935400257660043e6dad36ef5f8f22222bc51764f7fc27b0a92681"),
        new Migration("V20261029_01", "database/migrations/V20261029_01__care_task_change_history.sql", "eeb23c420f390f34bf795fd65bde6a55d81afd9e4611f3ac81483b8930a1bc30"),
        new Migration("V20261109_01", "database/migrations/V20261109_01__incident_in_ride_types.sql", "564085d17383e32bbc1bd9b30f7a3825b3bd191f48877df7f173af2e7df406c4"));
    static final String EXPECTED_SCHEMA = "database/verification/expected-schema-v20261109.json";
    static final String EXPECTED_SCHEMA_HASH = "6f261a9bb504fa2188abc6b1edd0e0c3c0bdb881aa50d74ac117511f29db43b4";
    static List<Map<String,Object>> migrationPlan() {
        List<Map<String,Object>> plan=new ArrayList<>();
        for(int i=0;i<MIGRATIONS.size();i++) {
            Migration migration=MIGRATIONS.get(i);
            Map<String,Object> step=new LinkedHashMap<>();step.put("version",migration.version());
            step.put("order",i+1);step.put("path",migration.path());step.put("sha256",migration.sha256());plan.add(step);
        }
        return plan;
    }
    static void verifyPackage(Path root) throws Exception {
        require(hash(Files.readAllBytes(root.resolve("database/schema.sql"))).equals(SCHEMA),"CANONICAL_SCHEMA_CHECKSUM_MISMATCH");
        for(Migration migration:MIGRATIONS)
            require(hash(Files.readAllBytes(root.resolve(migration.path()))).equals(migration.sha256()),"MIGRATION_CHECKSUM_MISMATCH_"+migration.version());
        require(hash(Files.readAllBytes(root.resolve(EXPECTED_SCHEMA))).equals(EXPECTED_SCHEMA_HASH),"EXPECTED_SCHEMA_CHECKSUM_MISMATCH");
    }
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
            try(ResultSet r=s.executeQuery("SELECT encrypt_option,net_transport FROM sys.dm_exec_connections WHERE session_id=@@SPID OPTION (MAXDOP 1, RECOMPILE)")) {
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
        finishBatch(s,s.execute(sql));
    }
    static void finishBatch(Statement s,boolean result) throws Exception {
        while(true) {
            if(result) {try(ResultSet rows=s.getResultSet()){while(rows.next()) { /* drain without logging data */ }}}
            else if(s.getUpdateCount()==-1) break;
            result=s.getMoreResults(Statement.CLOSE_CURRENT_RESULT);
        }
    }
    static void executeMigration(Statement statement, Path root, Migration migration) throws Exception {
        if (migration.version().equals("V20261020_01")) {
            // SQL Server compiles the approved CREATE INDEX before its earlier ALTER ADD.
            // Pre-add the same approved nullable column in a separate batch, within the caller transaction.
            executeBatch(statement,"IF COL_LENGTH(N'dbo.care_task',N'assigned_groom_user_id') IS NULL "
                    +"ALTER TABLE dbo.care_task ADD assigned_groom_user_id BIGINT NULL;");
        }
        executeBatch(statement,Files.readString(root.resolve(migration.path()),StandardCharsets.UTF_8));
    }
    static void verify(Connection c,Path root) throws Exception {
        String actual=snapshot(c,root),expected=Files.readString(root.resolve(EXPECTED_SCHEMA)).strip();
        require(actual.equals(expected),"SCHEMA_DRIFT_DETECTED");
    }
    static void validateRetirement(String oldDb,String newDb,String confirmation,String expectedMachine,String actualMachine) {
        require(oldDb.matches("(?i)racehorse_[a-z0-9_]{1,100}"),"OLD_RACEHORSE_DATABASE_NAME_REQUIRED");
        require(!oldDb.equalsIgnoreCase(newDb),"OLD_AND_NEW_DATABASE_MUST_DIFFER");
        require(oldDb.equals(confirmation),"EXACT_OLD_DATABASE_CONFIRMATION_REQUIRED");
        require(actualMachine.equalsIgnoreCase(expectedMachine),"EXPLICIT_LOCAL_MACHINE_CONFIRMATION_REQUIRED");
    }
    static void requireIdle(Connection c,String db) throws Exception {
        // No KILL, SINGLE_USER or ROLLBACK IMMEDIATE. Busy databases remain untouched.
        try(PreparedStatement q=c.prepareStatement("SELECT COUNT_BIG(*) FROM sys.dm_exec_sessions WHERE is_user_process=1 AND (database_id=DB_ID(?) OR session_id IN (SELECT session_id FROM sys.dm_exec_requests WHERE database_id=DB_ID(?)) OR session_id IN (SELECT st.session_id FROM sys.dm_tran_session_transactions st JOIN sys.dm_tran_database_transactions dt ON dt.transaction_id=st.transaction_id WHERE dt.database_id=DB_ID(?)))")) {
            q.setQueryTimeout(15);for(int i=1;i<=3;i++)q.setString(i,db);
            try(ResultSet r=q.executeQuery()){require(r.next()&&r.getLong(1)==0,"OLD_DATABASE_HAS_CONNECTIONS_OR_TRANSACTIONS_STOP_MANUALLY");}
        }
    }
    static String databaseGuid(Connection c,String db) throws Exception {
        try(PreparedStatement q=c.prepareStatement("SELECT CONVERT(varchar(36),r.database_guid) FROM sys.databases d JOIN sys.database_recovery_status r ON r.database_id=d.database_id WHERE d.name=? AND d.state_desc='ONLINE'")) {
            q.setQueryTimeout(15);q.setString(1,db);
            try(ResultSet r=q.executeQuery()){require(r.next()&&r.getString(1)!=null,"OLD_DATABASE_ONLINE_IDENTITY_REQUIRED");return r.getString(1);}
        }
    }
    static void retire(Path root,String newDb) throws Exception {
        String oldDb=env("RH_OLD_DATABASE");
        validateRetirement(oldDb,newDb,env("RH_RETIRE_CONFIRMATION"),env("RH_RETIRE_MACHINE"),machine());
        Path backup=Path.of(env("RH_RETIRE_BACKUP")).toAbsolutePath();
        require(Path.of(env("RH_RETIRE_BACKUP")).isAbsolute()&&!backup.toString().startsWith("\\\\")&&backup.getFileName().toString().endsWith(".bak")
                &&Files.isDirectory(backup.getParent())&&!Files.exists(backup),"NEW_ABSOLUTE_BACKUP_FILE_REQUIRED");
        try(Connection fresh=connect(newDb)){verify(fresh,root);}
        // Prove this is a Racehorse database, not merely a similarly named database.
        try(Connection old=connect(oldDb);Statement s=old.createStatement()) {
            s.setQueryTimeout(15);
            try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo') AND name IN ('horse','app_user','medical_case','training_session')")) {
                require(r.next()&&r.getInt(1)==4,"OLD_RACEHORSE_TABLE_IDENTITY_REQUIRED");
            }
        }
        try(Connection master=connect("master")){retireOldDatabase(master,oldDb,backup);}
        System.out.println("OLD_LOCAL_DATABASE_RETIRED_AFTER_COPY_ONLY_BACKUP_VERIFYONLY; new database unchanged");
    }
    static void retireOldDatabase(Connection master,String oldDb,Path backup) throws Exception {
            String guid=databaseGuid(master,oldDb);requireIdle(master,oldDb);
            try(PreparedStatement q=master.prepareStatement("BACKUP DATABASE ["+oldDb+"] TO DISK=? WITH COPY_ONLY,CHECKSUM,NOFORMAT,NOINIT")) {
                q.setQueryTimeout(180);q.setString(1,backup.toString());finishBatch(q,q.execute());
            }
            try(PreparedStatement q=master.prepareStatement("RESTORE VERIFYONLY FROM DISK=? WITH CHECKSUM")) {
                q.setQueryTimeout(180);q.setString(1,backup.toString());finishBatch(q,q.execute());
            }
            try(PreparedStatement q=master.prepareStatement("RESTORE HEADERONLY FROM DISK=?")) {
                q.setQueryTimeout(30);q.setString(1,backup.toString());
                try(ResultSet r=q.executeQuery()) {
                    require(r.next()&&oldDb.equals(r.getString("DatabaseName"))&&r.getInt("BackupType")==1
                            &&r.getBoolean("HasBackupChecksums")&&!r.getBoolean("IsDamaged")&&!r.next(),"SINGLE_VERIFIED_FULL_BACKUP_REQUIRED");
                }
            }
            require(guid.equals(databaseGuid(master,oldDb)),"OLD_DATABASE_CHANGED_DURING_BACKUP");
            requireIdle(master,oldDb);
            try(Statement s=master.createStatement()){s.setQueryTimeout(15);s.execute("DROP DATABASE ["+oldDb+"]");}
            try(PreparedStatement q=master.prepareStatement("SELECT DB_ID(?)")) {
                q.setString(1,oldDb);try(ResultSet r=q.executeQuery()){require(r.next()&&r.getObject(1)==null,"OLD_DATABASE_DROP_NOT_CONFIRMED");}
            }
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
        String op=args[0]; Path root=Path.of(args[1]).toAbsolutePath();
        verifyPackage(root);
        if(op.equals("plan")){System.out.println(json(migrationPlan()));return;}
        String db=database();
        if(op.equals("retire")){retire(root,db);return;}
        if(op.equals("install")) {
            // A master connection checks local identity and strict TLS BEFORE any CREATE.
            try(Connection c=connect("master");PreparedStatement q=c.prepareStatement("SELECT DB_ID(?)")) {
                q.setString(1,db);try(ResultSet r=q.executeQuery()){r.next();require(r.getObject(1)==null,"DATABASE_ALREADY_EXISTS_NO_OVERWRITE");}
                try(Statement s=c.createStatement()){s.setQueryTimeout(60);s.execute("CREATE DATABASE ["+db+"] COLLATE SQL_Latin1_General_CP1_CI_AS");}
            }
            try(Connection c=connect(db)) {
                c.setAutoCommit(false);
                try(Statement s=c.createStatement()) {
                    s.setQueryTimeout(60);
                    executeBatch(s,Files.readString(root.resolve("database/schema.sql"),StandardCharsets.UTF_8));
                    for(Migration migration:MIGRATIONS)executeMigration(s,root,migration);
                    verify(c,root);
                    c.commit();
                } catch(Exception failure) {c.rollback();throw failure;}
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
                case "reference" -> {
                    verify(c,root);
                    require(args.length==3,"RENDERED_REFERENCE_SEED_REQUIRED");
                    try(Statement s=c.createStatement()){s.setQueryTimeout(20);executeBatch(s,Files.readString(Path.of(args[2]),StandardCharsets.UTF_8));}
                    System.out.println("LOCAL_REFERENCE_SEED_PASS");
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
