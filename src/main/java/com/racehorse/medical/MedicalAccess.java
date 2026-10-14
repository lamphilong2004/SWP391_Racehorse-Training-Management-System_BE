package com.racehorse.medical;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.util.Set;

@Component
public class MedicalAccess {
    private static final Set<String> READ_ROLES =
            Set.of("CLUB_MANAGER", "HEAD_TRAINER", "TRAINER", "VETERINARIAN", "GROOM");
    private final ObjectProvider<MedicalIdentityProvider> providers;

    public MedicalAccess(ObjectProvider<MedicalIdentityProvider> providers) {
        this.providers = providers;
    }

    public MedicalIdentityProvider.Identity requireIdentity(boolean write) {
        var provider = providers.getIfAvailable();
        var identity = provider == null ? null : provider.currentIdentity();
        if (identity == null)
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
        boolean allowed = write ? identity.roles().contains("VETERINARIAN")
                : identity.roles().stream().anyMatch(READ_ROLES::contains);
        if (!allowed) throw new BusinessException(ErrorCode.FORBIDDEN, "This operation is not permitted.");
        return identity;
    }

    public boolean needsTrainerScope(MedicalIdentityProvider.Identity identity) {
        return identity.roles().contains("TRAINER") && identity.roles().stream()
                .noneMatch(role -> READ_ROLES.contains(role) && !role.equals("TRAINER"));
    }
}
