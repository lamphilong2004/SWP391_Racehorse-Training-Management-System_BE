package com.racehorse.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * DEMO ONLY (profile "demo"). On startup, when app.demo.reset-on-start=true, resets the business tables and loads the
 * deterministic demo dataset, then sets every demo account's BCrypt hash from the DEMO_PASSWORD environment variable.
 * No password or hash is committed (DEC-008). Never enable this profile against a real database.
 */
@Component
@Profile("demo")
public class DemoDataInitializer implements ApplicationRunner {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final boolean resetOnStart;
    private final String demoPassword;

    public DemoDataInitializer(DataSource dataSource, JdbcTemplate jdbc, PasswordEncoder encoder,
                               @Value("${app.demo.reset-on-start:false}") boolean resetOnStart,
                               @Value("${DEMO_PASSWORD:}") String demoPassword) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.resetOnStart = resetOnStart;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (resetOnStart) reset();
        if (demoPassword == null || demoPassword.length() < 8)
            throw new IllegalStateException("Profile 'demo' needs DEMO_PASSWORD (at least 8 characters) in the environment.");
        String hash = encoder.encode(demoPassword);
        jdbc.update("UPDATE dbo.app_user SET password_hash=? WHERE email LIKE '%@demo.racehorse.local'", hash);
    }

    public void reset() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("demo/demo-reset.sql"), new ClassPathResource("demo/demo-seed.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);
        if (demoPassword != null && demoPassword.length() >= 8)
            jdbc.update("UPDATE dbo.app_user SET password_hash=? WHERE email LIKE '%@demo.racehorse.local'", encoder.encode(demoPassword));
    }
}
