package com.racehorse.horse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * CORE-004 identity input. Lifecycle and clinical fields are server-owned. {@code strengths} holds declared
 * {@link HorseStrength} codes; null on update keeps the current declaration, an empty list clears it.
 */
public record HorseIdentityRequest(
        @NotBlank @Size(max=20) String horseCode,
        @NotBlank @Size(max=150) String name,
        @Size(max=100) String breed,
        @Size(max=20) String sex,
        LocalDate foaledDate,
        @Size(max=100) String countryOfOrigin,
        @Size(max=50) String registrationNo,
        @Size(max=14) List<@Size(max=30) String> strengths) {
    public HorseIdentityRequest(String horseCode, String name, String breed, String sex, LocalDate foaledDate,
                                String countryOfOrigin, String registrationNo) {
        this(horseCode, name, breed, sex, foaledDate, countryOfOrigin, registrationNo, null);
    }
    HorseIdentityRequest normalized() {
        return new HorseIdentityRequest(trim(horseCode),trim(name),trim(breed),trim(sex),foaledDate,trim(countryOfOrigin),trim(registrationNo),strengths);
    }
    private static String trim(String value) { return value==null||value.isBlank()?null:value.strip(); }
}
