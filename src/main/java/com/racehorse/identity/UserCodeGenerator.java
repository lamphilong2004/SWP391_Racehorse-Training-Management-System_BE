package com.racehorse.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Next app_user.user_code in the U-nnn series (DEC-008 §6 R3; also needed by MF-17 user creation, CORE-049).
 * Concurrent callers may compute the same code; uq_app_user__user_code rejects the second insert and the caller
 * reports a conflict instead of writing a duplicate.
 */
@Component
public class UserCodeGenerator {

    private final JdbcTemplate jdbc;

    public UserCodeGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String next() {
        List<String> codes = jdbc.queryForList("SELECT user_code FROM app_user WHERE user_code LIKE 'U-%'", String.class);
        int max = 0;
        for (String code : codes) {
            try {
                max = Math.max(max, Integer.parseInt(code.substring(2)));
            } catch (NumberFormatException ignored) {
                // codes outside the numeric U-nnn series are skipped
            }
        }
        return String.format("U-%03d", max + 1);
    }
}
