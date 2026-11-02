package com.racehorse.stablecare;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import static com.racehorse.stablecare.StableCareApi.*;
import static org.junit.jupiter.api.Assertions.*;

class StableCareValidationTest {
    static ValidatorFactory factory;
    static Validator validator;
    final LocalDate date = LocalDate.of(2026, 11, 2);

    @BeforeAll static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void cleanup() { factory.close(); }

    @Test void rejectsNullCareItemBeforeServiceExecution() {
        var request = new CarePlanRequest(1, 2, date, date, null, Arrays.asList((CareItem) null));
        assertTrue(validator.validate(request).stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("items[0]")));
    }
    @Test void rejectsNullNutritionItemBeforeServiceExecution() {
        var request = new NutritionRequest(1, 2, date, date, null, Arrays.asList((NutritionItem) null));
        assertTrue(validator.validate(request).stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("items[0]")));
    }
    @Test void preservesValidCareAndNutritionPayloads() {
        assertTrue(validator.validate(new CarePlanRequest(1, 2, date, date, null,
                List.of(new CareItem("CLEANING", "08:00", "Observe and clean")))).isEmpty());
        assertTrue(validator.validate(new NutritionRequest(1, 2, date, date, null,
                List.of(new NutritionItem("07:00", "HAY", new BigDecimal("0.001"), "kg")))).isEmpty());
    }
    @Test void stillRejectsMissingItemsAndInvalidAmounts() {
        assertFalse(validator.validate(new CarePlanRequest(1, 2, date, date, null, List.of())).isEmpty());
        assertFalse(validator.validate(new NutritionRequest(1, 2, date, date, null,
                List.of(new NutritionItem("07:00", "HAY", new BigDecimal("-1"), "kg")))).isEmpty());
    }
}
