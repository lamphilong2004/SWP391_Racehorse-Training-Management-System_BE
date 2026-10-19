package com.racehorse.horse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record PedigreeRequest(
        @NotNull @Valid ParentReference sire,
        @NotNull @Valid ParentReference dam) {

    @AssertTrue(message = "Each parent can reference a horse id or a name, but not both.")
    public boolean isParentReferenceValid() {
        return valid(sire) && valid(dam);
    }

    private static boolean valid(ParentReference parent) {
        return parent == null || parent.horseId() == null || parent.name() == null;
    }
}
