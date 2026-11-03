package com.racehorse.horse.baseline;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.horse.activation.HorseActivationService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Service
public class HorseBaselineService {

    private static final Set<RoleCode> WEIGHT_READ_ROLES = Set.of(
            RoleCode.CLUB_MANAGER,
            RoleCode.HEAD_TRAINER,
            RoleCode.TRAINER,
            RoleCode.VETERINARIAN,
            RoleCode.GROOM);
    private static final Set<RoleCode> UNRESTRICTED_HORSE_READ_ROLES = Set.of(
            RoleCode.CLUB_MANAGER,
            RoleCode.HEAD_TRAINER,
            RoleCode.VETERINARIAN,
            RoleCode.GROOM);

    private final HorseBaselineRepository repository;
    private final CurrentActorProvider actors;
    private final HorseActivationService activationService;

    public HorseBaselineService(HorseBaselineRepository repository, CurrentActorProvider actors,
                                HorseActivationService activationService) {
        this.repository = repository;
        this.actors = actors;
        this.activationService = activationService;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public BaselineExamination recordBaseline(long horseId, BaselineExaminationRequest request) {
        ActorContext actor = requireActiveActor();
        requireVeterinarian(actor);
        requireHorse(horseId);
        if (repository.hasBaseline(horseId)) {
            throw conflict("A veterinarian baseline already exists for this horse.");
        }

        LocalDateTime recordedAt = utcNow();
        try {
            long examinationId = repository.insertBaseline(
                    horseId, actor.userId(), recordedAt, request.healthStatus(), request.clinicalNotes());
            repository.insertBaselineWeight(horseId, actor.userId(), request.weightKg(), recordedAt);
            if (repository.updateHealthStatus(horseId, request.healthStatus(), recordedAt) != 1) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "Horse not found.");
            }
            activationService.reevaluate(horseId);
            return new BaselineExamination(
                    examinationId, horseId, request.weightKg(), request.healthStatus(),
                    recordedAt.toInstant(ZoneOffset.UTC));
        } catch (DataIntegrityViolationException error) {
            if (isUniqueViolation(error)) {
                throw conflict("A veterinarian baseline already exists for this horse.");
            }
            throw error;
        }
    }

    @Transactional
    public WeightRecord recordWeight(long horseId, WeightRecordRequest request) {
        ActorContext actor = requireActiveActor();
        requireVeterinarian(actor);
        requireHorse(horseId);

        Instant requestedAt = request.recordedAt() == null ? Instant.now() : request.recordedAt();
        if (requestedAt.isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "recordedAt cannot be in the future.");
        }
        LocalDateTime recordedAt = LocalDateTime.ofInstant(
                requestedAt.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
        long weightId = repository.insertWeight(horseId, actor.userId(), request.weightKg(), recordedAt);
        return new WeightRecord(weightId, horseId, request.weightKg(), recordedAt.toInstant(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true)
    public WeightRecordsPage listWeights(long horseId, int page, int size) {
        ActorContext actor = requireActiveActor();
        if (actor.roles().stream().noneMatch(WEIGHT_READ_ROLES::contains)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "The authenticated role cannot read horse weights.");
        }
        requireHorse(horseId);
        Long trainerUserId = actor.roles().contains(RoleCode.TRAINER)
                && actor.roles().stream().noneMatch(UNRESTRICTED_HORSE_READ_ROLES::contains)
                ? actor.userId()
                : null;
        if (trainerUserId != null && !repository.trainerHasHorse(trainerUserId, horseId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This horse is outside your assigned programs.");
        }
        HorseBaselineRepository.WeightPage result = repository.findWeights(horseId, page, size);
        return new WeightRecordsPage(result.items(), page, size, result.totalElements());
    }

    private ActorContext requireActiveActor() {
        ActorContext actor = actors.currentActor()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required."));
        if (!repository.userIsActive(actor.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "An active account is required.");
        }
        return actor;
    }

    private static void requireVeterinarian(ActorContext actor) {
        if (!actor.roles().contains(RoleCode.VETERINARIAN)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only a Veterinarian may write baseline or weight data.");
        }
    }

    private void requireHorse(long horseId) {
        if (!repository.horseExists(horseId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Horse not found.");
        }
    }

    private static LocalDateTime utcNow() {
        return LocalDateTime.ofInstant(Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }

    private static boolean isUniqueViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && (sql.getErrorCode() == 2601 || sql.getErrorCode() == 2627
                    || "23505".equals(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }
}
