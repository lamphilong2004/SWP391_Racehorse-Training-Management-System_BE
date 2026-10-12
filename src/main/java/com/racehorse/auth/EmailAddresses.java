package com.racehorse.auth;

import java.text.Normalizer;
import java.util.Locale;

/** Email normalisation (DEC-008 §6 R1) and log masking (R11). */
public final class EmailAddresses {

    private EmailAddresses() {
    }

    /** Trim, Unicode NFC, lower-case. */
    public static String normalize(String email) {
        if (email == null) {
            return "";
        }
        return Normalizer.normalize(email.trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    /** {@code jane.doe@club.vn} → {@code j***@club.vn}. Use this in every log line that mentions an email. */
    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        String value = email.trim();
        int at = value.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return value.charAt(0) + "***" + value.substring(at);
    }
}
