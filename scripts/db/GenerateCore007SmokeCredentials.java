package com.racehorse.tools;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

public final class GenerateCore007SmokeCredentials {

    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final List<SeedAccount> ACCOUNTS = List.of(
            new SeedAccount("CLUB_MANAGER", "core007.club.manager@example.test"),
            new SeedAccount("HEAD_TRAINER", "core007.head.trainer@example.test"),
            new SeedAccount("TRAINER", "core007.trainer@example.test"),
            new SeedAccount("WORK_RIDER", "core007.work.rider@example.test"),
            new SeedAccount("VETERINARIAN", "core007.veterinarian@example.test"),
            new SeedAccount("GROOM", "core007.groom@example.test"),
            new SeedAccount("HORSE_OWNER", "core007.horse.owner@example.test"));

    private GenerateCore007SmokeCredentials() {
    }

    public static void main(String[] args) {
        SecureRandom random = new SecureRandom();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        for (SeedAccount account : ACCOUNTS) {
            String password = randomPassword(random);
            String encodedPassword = Base64.getEncoder().encodeToString(
                    password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println(account.role() + "|" + account.email() + "|" + encodedPassword + "|"
                    + encoder.encode(password));
        }
    }

    private static String randomPassword(SecureRandom random) {
        StringBuilder password = new StringBuilder(24);
        for (int index = 0; index < 24; index++) {
            password.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    private record SeedAccount(String role, String email) {
    }
}
