package com.racehorse.staging.network;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.sql.Driver;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Deployment fixtures only: no real tailnet, Render, SQL login or business data. */
class RenderTailscaleTest {
    @TempDir Path temp;
    static Map<String, String> env() {
        var env = new HashMap<String, String>();
        for (String key : List.of("TS_AUTHKEY", "DB_USERNAME", "DB_PASSWORD", "JWT_SIGNING_KEY", "JWT_ACCESS_TOKEN_TTL_SECONDS", "CORS_ALLOWED_ORIGINS", "STAGING_DOCS_USERNAME", "STAGING_DOCS_PASSWORD", "TRUSTSTORE_PASSWORD")) env.put(key, "fixture-not-a-real-secret");
        env.put("SPRING_PROFILES_ACTIVE", "staging");
        env.put("DB_URL", RenderTailscaleLauncher.JDBC_URL);
        return env;
    }

    @Test void rejectsEveryMissingSecretBeforeStartingProcesses() {
        assertDoesNotThrow(() -> RenderTailscaleLauncher.validate(env()));
        for (String key : env().keySet()) {
            var values = env(); values.remove(key);
            assertThrows(IllegalArgumentException.class, () -> RenderTailscaleLauncher.validate(values));
        }
    }

    @Test void rejectsWrongHostnameTlsBypassPublicBridgeAndOtherProfiles() {
        for (String url : List.of(RenderTailscaleLauncher.JDBC_URL.replace("encrypt=true", "encrypt=false"),
                RenderTailscaleLauncher.JDBC_URL.replace("trustServerCertificate=false", "trustServerCertificate=true"),
                RenderTailscaleLauncher.JDBC_URL.replace("PHILONG-100604", "wrong.test"),
                RenderTailscaleLauncher.JDBC_URL.replace("127.0.0.1:11433", "0.0.0.0:1433"),
                RenderTailscaleLauncher.JDBC_URL + ";trustServerCertificate=true")) {
            var values = env(); values.put("DB_URL", url);
            assertThrows(IllegalArgumentException.class, () -> RenderTailscaleLauncher.validate(values));
        }
        for (String profile : List.of("dev", "test", "demo", "staging,dev")) {
            var values = env(); values.put("SPRING_PROFILES_ACTIVE", profile);
            assertThrows(IllegalArgumentException.class, () -> RenderTailscaleLauncher.validate(values));
        }
    }

    @Test void startupCommandsUseUserspaceLoopbackAndAuthFileWithoutRawKey() {
        var daemon = RenderTailscaleLauncher.daemonCommand("/tmp/fixture.sock");
        assertTrue(daemon.contains("--tun=userspace-networking"));
        assertTrue(daemon.contains("--state=mem:"));
        assertTrue(daemon.contains("--socks5-server=127.0.0.1:1055"));
        var command = RenderTailscaleLauncher.upCommand("/tmp/fixture.sock", Path.of("/tmp/auth-key"), 60);
        assertTrue(command.contains("--advertise-tags=tag:racehorse-be"));
        assertTrue(command.stream().anyMatch(value -> value.startsWith("--auth-key=file:")));
        assertTrue(command.contains("--timeout=60s"));
        assertFalse(command.toString().contains(env().get("TS_AUTHKEY")));
        var process = RenderTailscaleLauncher.process(command, temp);
        assertEquals(Set.of("PATH", "HOME", "GOMEMLIMIT"), process.environment().keySet());
        assertEquals(ProcessBuilder.Redirect.DISCARD, process.redirectOutput());
        assertEquals(ProcessBuilder.Redirect.DISCARD, process.redirectError());
        assertFalse(daemon.toString().contains("funnel"));
    }

    @Test void boundsStartupTimeoutAndRedactsProviderErrors() {
        assertEquals(60, RenderTailscaleLauncher.startupTimeout(env()));
        for (String value : List.of("0", "121", "garbage", "-1")) {
            var values = env(); values.put("NETWORK_STARTUP_TIMEOUT_SECONDS", value);
            assertThrows(IllegalArgumentException.class, () -> RenderTailscaleLauncher.startupTimeout(values));
        }
        assertEquals("NETWORK_OR_TRUSTSTORE_UNAVAILABLE", RenderTailscaleLauncher.safeFailure(new IOException("password=private key=private")));
        assertEquals("NETWORK_OR_TRUSTSTORE_UNAVAILABLE", RenderTailscaleLauncher.safeFailure(new IllegalArgumentException("private")));
        assertEquals("MISSING_TS_AUTHKEY", RenderTailscaleLauncher.safeFailure(new IllegalArgumentException("MISSING_TS_AUTHKEY")));
    }

    @Test void loadsOnlyPublicPkcs12AndRefusesWrongPasswordEmptyInvalidOrPrivateKeyStore() throws Exception {
        String password = "test-only-keytool-password";
        Path privateStore = temp.resolve("fixture-private.p12");
        keytool("-genkeypair", "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=fixture.test", "-ext", "SAN=dns:fixture.test", "-keystore", privateStore.toString(), "-storetype", "PKCS12", "-storepass", password, "-keypass", password, "-noprompt");
        Path cert = temp.resolve("fixture.cer");
        keytool("-exportcert", "-alias", "fixture", "-keystore", privateStore.toString(), "-storepass", password, "-file", cert.toString());
        Path publicStore = temp.resolve("fixture-public.p12");
        keytool("-importcert", "-alias", "fixture", "-file", cert.toString(), "-keystore", publicStore.toString(), "-storetype", "PKCS12", "-storepass", password, "-noprompt");
        Path encoded = encoded(publicStore);
        assertArrayEquals(Files.readAllBytes(publicStore), RenderTailscaleLauncher.readTruststore(encoded, password));
        assertThrows(Exception.class, () -> RenderTailscaleLauncher.readTruststore(encoded, "wrong-test-password"));
        assertThrows(IOException.class, () -> RenderTailscaleLauncher.readTruststore(encoded(privateStore), password));
        KeyStore empty = KeyStore.getInstance("PKCS12"); empty.load(null, password.toCharArray());
        Path emptyStore = temp.resolve("empty.p12");
        try (var out = Files.newOutputStream(emptyStore)) { empty.store(out, password.toCharArray()); }
        assertThrows(IOException.class, () -> RenderTailscaleLauncher.readTruststore(encoded(emptyStore), password));
        Files.writeString(encoded, "invalid-base64");
        assertThrows(Exception.class, () -> RenderTailscaleLauncher.readTruststore(encoded, password));
        assertThrows(IOException.class, () -> RenderTailscaleLauncher.readTruststore(temp.resolve("missing"), password));
    }

    @Test void actualMicrosoftDriverExposesHostnameAndDedicatedTruststoreProperties() throws Exception {
        Driver driver = (Driver) Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver").getDeclaredConstructor().newInstance();
        var names = Arrays.stream(driver.getPropertyInfo(RenderTailscaleLauncher.JDBC_URL, new Properties())).map(value -> value.name.toLowerCase(Locale.ROOT)).toList();
        for (String name : List.of("hostnameincertificate", "encrypt", "trustservercertificate", "truststore", "truststorepassword", "truststoretype")) assertTrue(names.contains(name), name);
        // Driver property support is not a SQL Server TLS handshake PASS.
    }

    private Path encoded(Path store) throws IOException {
        Path path = temp.resolve(store.getFileName() + ".b64");
        Files.writeString(path, Base64.getEncoder().encodeToString(Files.readAllBytes(store)));
        return path;
    }

    private void keytool(String... arguments) throws Exception {
        String suffix = System.getProperty("os.name").startsWith("Windows") ? ".exe" : "";
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool" + suffix).toString());
        command.addAll(List.of(arguments));
        var process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try { assertTrue(process.waitFor(20, TimeUnit.SECONDS)); assertEquals(0, process.exitValue()); }
        finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
