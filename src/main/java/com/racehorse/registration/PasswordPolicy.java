package com.racehorse.registration;

import com.racehorse.common.api.ErrorDetail;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * DEC-008 §6 R2 server-side password rules: 8–64 characters, at least 1 letter and 1 digit, at most 72 bytes in
 * UTF-8 (BCrypt input limit), not equal to the email, and the confirmation must match. Messages never echo the value.
 */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 64;
    static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static List<ErrorDetail> check(String password, String confirmPassword, String email) {
        List<ErrorDetail> errors = new ArrayList<>();
        if (password == null) {
            errors.add(new ErrorDetail("password", "must not be blank"));
            return errors;
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            errors.add(new ErrorDetail("password", "must be 8–64 characters"));
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            errors.add(new ErrorDetail("password", "must be at most 72 bytes"));
        }
        if (password.codePoints().noneMatch(Character::isLetter)) {
            errors.add(new ErrorDetail("password", "must contain at least one letter"));
        }
        if (password.codePoints().noneMatch(Character::isDigit)) {
            errors.add(new ErrorDetail("password", "must contain at least one digit"));
        }
        if (email != null && password.trim().equalsIgnoreCase(email.trim())) {
            errors.add(new ErrorDetail("password", "must not be the same as the email"));
        }
        if (!password.equals(confirmPassword)) {
            errors.add(new ErrorDetail("confirmPassword", "must match the password"));
        }
        return errors;
    }
}
