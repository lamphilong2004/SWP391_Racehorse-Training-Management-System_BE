package com.racehorse.staging.network;

import com.racehorse.RacehorseApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Render-only entrypoint. No Spring/SQL connection starts before the private TCP route is ready. */
public final class RenderTailscaleLauncher implements AutoCloseable {
    public static final String JDBC_URL = "jdbc:sqlserver://127.0.0.1:11433;databaseName=racehorse_demo;encrypt=true;trustServerCertificate=false;hostNameInCertificate=PHILONG-100604;loginTimeout=10";
    private static final InetSocketAddress SOCKS = new InetSocketAddress("127.0.0.1", 1055);
    private static final InetSocketAddress SQL = new InetSocketAddress("100.83.34.4", 1433);
    private final AtomicBoolean closed = new AtomicBoolean();
    private Path directory;
    private Process daemon;
    private LoopbackTcpBridge bridge;
    private ConfigurableApplicationContext application;
    private String phase = "CONFIG";

    public static void main(String[] args) {
        var runtime = new RenderTailscaleLauncher();
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::close, "staging-network-cleanup"));
        try {
            if (args.length != 0) throw new IllegalArgumentException("STAGING_ARGUMENTS_NOT_ALLOWED");
            runtime.start(System.getenv());
            runtime.phase = "SPRING";
            runtime.application = SpringApplication.run(RacehorseApplication.class, args);
        } catch (Exception failure) {
            // Do not print exception text, process output, env, JDBC URL or secret-bearing causes.
            System.err.println("STAGING_NETWORK_STARTUP_FAILED: " + safeFailure(failure) + "; phase=" + runtime.phase);
            runtime.close();
            System.exit(70);
        }
    }

    static void validate(Map<String, String> env) {
        for (String name : List.of("TS_AUTHKEY", "DB_USERNAME", "DB_PASSWORD", "JWT_SIGNING_KEY",
                "JWT_ACCESS_TOKEN_TTL_SECONDS", "CORS_ALLOWED_ORIGINS", "STAGING_DOCS_USERNAME", "STAGING_DOCS_PASSWORD", "TRUSTSTORE_PASSWORD")) {
            if (env.getOrDefault(name, "").isBlank()) throw new IllegalArgumentException("MISSING_" + name);
        }
        if (!"staging".equals(env.get("SPRING_PROFILES_ACTIVE"))) throw new IllegalArgumentException("STAGING_PROFILE_REQUIRED");
        if (!JDBC_URL.equals(env.get("DB_URL"))) throw new IllegalArgumentException("STRICT_BRIDGE_DB_URL_REQUIRED");
    }

    private void start(Map<String, String> env) throws Exception {
        validate(env);
        long timeout = startupTimeout(env);
        phase = "TRUSTSTORE";
        byte[] trust = readTruststore(Path.of(env.getOrDefault("TRUSTSTORE_BASE64_FILE", "/etc/secrets/racehorse-truststore.p12.b64")), env.get("TRUSTSTORE_PASSWORD"));
        directory = Files.createTempDirectory("racehorse-staging-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path trustFile = privateFile("truststore.p12", trust);
        Arrays.fill(trust, (byte) 0);
        // Driver-specific properties, not global cacerts or the JVM-wide trust roots.
        System.setProperty("spring.datasource.hikari.data-source-properties.trustStore", trustFile.toString());
        System.setProperty("spring.datasource.hikari.data-source-properties.trustStoreType", "PKCS12");
        System.setProperty("spring.datasource.hikari.data-source-properties.trustStorePassword", env.get("TRUSTSTORE_PASSWORD"));
        Path auth = privateFile("auth-key", env.get("TS_AUTHKEY").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String socket = directory.resolve("tailscaled.sock").toString();
        System.out.println("NETWORK_CONFIG_VALIDATED");
        phase = "DAEMON";
        daemon = process(daemonCommand(socket), directory).start();
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(timeout).toNanos();
            while (!Files.exists(Path.of(socket))) {
                if (!daemon.isAlive() || System.nanoTime() >= deadline) throw new IOException("daemon unavailable");
                Thread.sleep(100);
            }
            phase = "AUTH";
            run(upCommand(socket, auth, timeout), timeout);
            System.out.println("NETWORK_TAILSCALE_AUTHENTICATED");
        } finally { Files.deleteIfExists(auth); }
        phase = "ROUTE";
        bridge = new LoopbackTcpBridge(11433, SOCKS, SQL);
        long deadline = System.nanoTime() + Duration.ofSeconds(timeout).toNanos();
        while (true) {
            if (!daemon.isAlive()) throw new IOException("daemon stopped");
            try (Socket probe = bridge.connect()) { break; }
            catch (IOException unavailable) {
                if (System.nanoTime() >= deadline) throw new IOException("private SQL route unavailable");
                Thread.sleep(500);
            }
        }
        System.out.println("NETWORK_TCP_ROUTE_READY: loopback only; JDBC TLS and schema validation still required");
        Thread.ofVirtual().name("network-supervisor").start(() -> {
            try {
                while (!closed.get()) {
                    if (!daemon.isAlive() || !bridge.isRunning()) {
                        System.err.println("NETWORK_DEPENDENCY_EXITED");
                        System.exit(70);
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
        });
    }

    static long startupTimeout(Map<String, String> env) {
        try {
            long value = Long.parseLong(env.getOrDefault("NETWORK_STARTUP_TIMEOUT_SECONDS", "60"));
            if (value < 5 || value > 120) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException("INVALID_NETWORK_TIMEOUT"); }
    }

    static List<String> daemonCommand(String socket) {
        return List.of("/usr/local/bin/tailscaled", "--tun=userspace-networking", "--state=mem:", "--socket=" + socket,
                "--socks5-server=127.0.0.1:1055");
    }

    static List<String> upCommand(String socket, Path auth, long timeout) {
        return List.of("/usr/local/bin/tailscale", "--socket=" + socket, "up", "--auth-key=file:" + auth,
                "--advertise-tags=tag:racehorse-be", "--hostname=racehorse-render-staging", "--accept-dns=false",
                "--accept-routes=false", "--ssh=false", "--timeout=" + timeout + "s");
    }

    static ProcessBuilder process(List<String> command, Path directory) {
        var builder = new ProcessBuilder(command).directory(directory.toFile());
        // No inherited application credentials, JWT, Java secret options or auth key in child environments.
        builder.environment().clear();
        builder.environment().put("PATH", "/usr/local/bin:/usr/bin:/bin");
        builder.environment().put("HOME", directory.toString());
        builder.environment().put("GOMEMLIMIT", "96MiB");
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        return builder;
    }

    private void run(List<String> command, long seconds) throws Exception {
        Process child = process(command, directory).start();
        try {
            if (!child.waitFor(seconds, TimeUnit.SECONDS) || child.exitValue() != 0) throw new IOException("Tailscale authentication unavailable");
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS); }
        }
    }

    static byte[] readTruststore(Path file, String password) throws Exception {
        if (!Files.isRegularFile(file) || Files.size(file) > 131072) throw new IOException("truststore unavailable");
        byte[] bytes = Base64.getDecoder().decode(Files.readString(file).replaceAll("\\s", ""));
        KeyStore store = KeyStore.getInstance("PKCS12");
        char[] chars = password.toCharArray();
        try (var input = new ByteArrayInputStream(bytes)) { store.load(input, chars); }
        finally { Arrays.fill(chars, '\0'); }
        if (store.size() == 0) throw new IOException("empty truststore");
        for (var aliases = store.aliases(); aliases.hasMoreElements();) {
            String alias = aliases.nextElement();
            if (store.isKeyEntry(alias)) throw new IOException("private key in truststore");
            if (store.getCertificate(alias) instanceof java.security.cert.X509Certificate cert) cert.checkValidity();
        }
        return bytes;
    }

    private Path privateFile(String name, byte[] bytes) throws IOException {
        Path file = directory.resolve(name);
        return Files.write(Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))), bytes);
    }

    static String safeFailure(Exception failure) {
        // Only our fixed validation codes can reach logs; provider/socket/SQL exceptions cannot.
        String value = failure.getMessage();
        return failure instanceof IllegalArgumentException && value != null && value.matches("(?:MISSING_[A-Z_]+|STAGING_PROFILE_REQUIRED|STAGING_ARGUMENTS_NOT_ALLOWED|STRICT_BRIDGE_DB_URL_REQUIRED|INVALID_NETWORK_TIMEOUT)")
                ? value : "NETWORK_OR_TRUSTSTORE_UNAVAILABLE";
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        // Finish Spring's graceful shutdown before removing the route used by in-flight requests.
        if (application != null) application.close();
        if (bridge != null) bridge.close();
        if (daemon != null) {
            daemon.destroy();
            try { if (!daemon.waitFor(5, TimeUnit.SECONDS)) daemon.destroyForcibly(); }
            catch (InterruptedException stop) { Thread.currentThread().interrupt(); daemon.destroyForcibly(); }
        }
        if (directory != null) {
            // Delete only files created inside this instance's private temporary directory.
            try (var files = Files.list(directory)) {
                for (Path file : files.toList()) Files.deleteIfExists(file);
            } catch (IOException ignored) { }
            try { Files.deleteIfExists(directory); } catch (IOException ignored) { }
        }
    }
}
