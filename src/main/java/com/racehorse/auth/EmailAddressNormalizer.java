package com.racehorse.auth;

import java.text.Normalizer;
import java.util.Locale;

final class EmailAddressNormalizer {

    private EmailAddressNormalizer() {
    }

    static String normalize(String email) {
        return Normalizer.normalize(email.trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }
}
