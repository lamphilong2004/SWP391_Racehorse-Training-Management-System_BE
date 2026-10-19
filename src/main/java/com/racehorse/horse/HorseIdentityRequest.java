package com.racehorse.horse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** CORE-004 identity input. Lifecycle and clinical fields are server-owned. */
public record HorseIdentityRequest(
        @NotBlank @Size(max=20) String horseCode,
        @NotBlank @Size(max=150) String name,
        @Size(max=100) String breed,
        @Size(max=20) String sex,
        LocalDate foaledDate,
        @Size(max=100) String countryOfOrigin,
        @Size(max=50) String registrationNo) {
    HorseIdentityRequest normalized() {
        return new HorseIdentityRequest(trim(horseCode),trim(name),trim(breed),trim(sex),foaledDate,trim(countryOfOrigin),trim(registrationNo));
    }
    private static String trim(String value) { return value==null||value.isBlank()?null:value.strip(); }
}
