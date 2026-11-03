package com.racehorse.horse;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ParentReference(
        @Positive Long horseId,
        @Size(max = 150) String name) {
}
