package com.racehorse.horse.owner;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.horse.owner.OwnerHorseDtos.HealthSummary;
import com.racehorse.horse.owner.OwnerHorseDtos.HorseProfile;
import com.racehorse.horse.owner.OwnerHorseDtos.TrainingSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OwnerHorseService {

    private final CurrentActorProvider currentActorProvider;
    private final OwnerHorseRepository repository;

    public OwnerHorseService(CurrentActorProvider currentActorProvider, OwnerHorseRepository repository) {
        this.currentActorProvider = currentActorProvider;
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<OwnerHorseDtos.HorseCard> listOwnedHorses() {
        ActorContext actor = requireOwner();
        return repository.findOwnedHorseCards(actor.userId());
    }

    @Transactional(readOnly = true)
    public HorseProfile getOwnedProfile(long horseId) {
        ActorContext actor = requireOwner();
        return repository.findOwnedProfile(actor.userId(), horseId)
                .orElseThrow(() -> missingOrForbidden(horseId));
    }

    @Transactional(readOnly = true)
    public HealthSummary getOwnedHealthSummary(long horseId) {
        ActorContext actor = requireOwner();
        OwnerHorseRepository.HealthSnapshot snapshot = repository
                .findOwnedHealthSnapshot(actor.userId(), horseId)
                .orElseThrow(() -> missingOrForbidden(horseId));

        return new HealthSummary(
                snapshot.healthStatus(),
                snapshot.trainingAvailability(),
                snapshot.restrictionReviewDate(),
                repository.findLatestOpenCaseRecoveryStatus(actor.userId(), horseId).orElse(null),
                repository.findLatestExaminedAt(actor.userId(), horseId).orElse(null),
                repository.findRecentWeights(actor.userId(), horseId));
    }

    @Transactional(readOnly = true)
    public TrainingSummary getOwnedTrainingSummary(long horseId) {
        ActorContext actor = requireOwner();
        return repository.findOwnedTrainingSummary(actor.userId(), horseId)
                .orElseThrow(() -> missingOrForbidden(horseId));
    }

    private ActorContext requireOwner() {
        ActorContext actor = currentActorProvider.currentActor()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required."));
        if (!actor.roles().contains(RoleCode.HORSE_OWNER)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "The authenticated role cannot access owner data.");
        }
        if (!repository.isActiveHorseOwner(actor.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "An active Horse Owner account is required.");
        }
        return actor;
    }

    private BusinessException missingOrForbidden(long horseId) {
        if (!repository.horseExists(horseId)) {
            return new BusinessException(ErrorCode.NOT_FOUND, "The requested horse was not found.");
        }
        return new BusinessException(ErrorCode.FORBIDDEN, "The horse is outside the authenticated owner's scope.");
    }
}
