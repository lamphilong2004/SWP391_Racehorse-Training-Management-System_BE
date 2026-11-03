package com.racehorse.common.jdbc;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeneratedIdentityTest {

    @Test
    void readsNamedIdentityColumn() {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        keyHolder.getKeyList().add(Map.of("ID", 41L));

        assertEquals(41L, GeneratedIdentity.requireId(keyHolder));
    }

    @Test
    void readsMicrosoftJdbcGeneratedKeysLabel() {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        keyHolder.getKeyList().add(Map.of("GENERATED_KEYS", 42L));

        assertEquals(42L, GeneratedIdentity.requireId(keyHolder));
    }

    @Test
    void rejectsMissingOrAmbiguousIdentity() {
        GeneratedKeyHolder missing = new GeneratedKeyHolder();
        GeneratedKeyHolder ambiguous = new GeneratedKeyHolder();
        ambiguous.getKeyList().add(Map.of("id", 1L, "GENERATED_KEYS", 2L));

        assertThrows(IllegalStateException.class, () -> GeneratedIdentity.requireId(missing));
        assertThrows(IllegalStateException.class, () -> GeneratedIdentity.requireId(ambiguous));
    }

    @Test
    void rejectsMultipleGeneratedRows() {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        keyHolder.getKeyList().add(Map.of("GENERATED_KEYS", 41L));
        keyHolder.getKeyList().add(Map.of("GENERATED_KEYS", 42L));

        assertThrows(IllegalStateException.class, () -> GeneratedIdentity.requireId(keyHolder));
    }
}
