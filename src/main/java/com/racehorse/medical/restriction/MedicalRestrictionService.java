package com.racehorse.medical.restriction;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.medical.restriction.api.AvailabilityBatchRequest;
import com.racehorse.medical.restriction.api.AvailabilityBatchResponse;
import com.racehorse.medical.restriction.api.ExerciseRuleRequest;
import com.racehorse.medical.restriction.api.ExerciseRuleResponse;
import com.racehorse.medical.restriction.api.MedicalRestrictionCreateRequest;
import com.racehorse.medical.restriction.api.MedicalRestrictionResponse;
import com.racehorse.medical.restriction.api.ReplaceExerciseRulesRequest;
import com.racehorse.medical.restriction.api.RestrictionSummaryResponse;
import com.racehorse.medical.restriction.api.RestrictionsPageResponse;
import com.racehorse.medical.restriction.api.TrainingAvailabilityResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class MedicalRestrictionService {

    private final JdbcMedicalRestrictionRepository repository;
    private final Optional<CurrentActorProvider> actorProvider;
    private final RestrictionExercisePolicy exercisePolicy = new RestrictionExercisePolicy();

    public MedicalRestrictionService(JdbcMedicalRestrictionRepository repository,
                                     Optional<CurrentActorProvider> actorProvider) {
        this.repository = repository;
        this.actorProvider = actorProvider;
    }

    @Transactional
    public MedicalRestrictionResponse create(long horseId, MedicalRestrictionCreateRequest request) {
        ActorContext actor = requireRole(RoleCode.VETERINARIAN);
        validateCreateRequest(request);
        requireHorse(horseId);
        if (request.medicalCaseId() != null
                && !repository.medicalCaseBelongsToHorse(request.medicalCaseId(), horseId)) {
            throw notFound("Medical case was not found for this horse.");
        }

        Instant effectiveAt = request.effectiveAt().toInstant();
        if (effectiveAt.isAfter(Instant.now())) {
            throw validation("effectiveAt must not be in the future.");
        }
        if (repository.hasActiveRestriction(horseId)) {
            throw conflict("The horse already has an active medical restriction.");
        }

        long restrictionId;
        try {
            restrictionId = repository.insertRestriction(horseId, request.medicalCaseId(), actor.userId(),
                    request.level(), request.reason().trim(), effectiveAt, request.reviewDate());
        } catch (DuplicateKeyException ex) {
            throw conflict("The horse already has an active medical restriction.");
        }
        return restrictionResponse(requireRestriction(restrictionId));
    }

    @Transactional
    public MedicalRestrictionResponse replaceExerciseRules(long restrictionId, ReplaceExerciseRulesRequest request) {
        requireRole(RoleCode.VETERINARIAN);
        RestrictionRecord restriction = requireActiveRestrictionForUpdate(restrictionId);
        if (request == null || request.rules() == null) {
            throw validation("rules is required.");
        }

        List<ExerciseRule> rules = new ArrayList<>(request.rules().size());
        for (ExerciseRuleRequest rule : request.rules()) {
            if (rule == null || rule.ruleType() == null) {
                throw validation("Each rule requires a supported ruleType.");
            }
            boolean hasExerciseId = rule.exerciseId() != null;
            boolean hasTypeNote = rule.exerciseTypeNote() != null && !rule.exerciseTypeNote().isBlank();
            if (hasExerciseId == hasTypeNote) {
                throw validation("Each rule must specify exactly one of exerciseId and exerciseTypeNote.");
            }
            if (rule.exerciseId() != null && rule.exerciseId() <= 0) {
                throw validation("exerciseId must be a positive integer.");
            }
            if (rule.exerciseTypeNote() != null
                    && (rule.exerciseTypeNote().isBlank() || rule.exerciseTypeNote().length() > 200)) {
                throw validation("exerciseTypeNote must contain 1 to 200 characters.");
            }
            if (hasExerciseId && !repository.exerciseExists(rule.exerciseId())) {
                throw notFound("Exercise definition was not found.");
            }
            rules.add(new ExerciseRule(rule.exerciseId(), rule.ruleType(),
                    hasTypeNote ? rule.exerciseTypeNote().trim() : null));
        }

        repository.replaceRules(restrictionId, rules);
        return restrictionResponse(requireRestriction(restrictionId));
    }

    @Transactional
    public TrainingAvailabilityResponse lift(long restrictionId) {
        ActorContext actor = requireRole(RoleCode.VETERINARIAN);
        RestrictionRecord restriction = requireActiveRestrictionForUpdate(restrictionId);
        Instant liftedAt = Instant.now();
        if (liftedAt.isBefore(restriction.effectiveAt())) {
            liftedAt = restriction.effectiveAt();
        }
        if (repository.liftRestriction(restrictionId, liftedAt) != 1) {
            throw conflict("The medical restriction has already been lifted.");
        }
        return availabilityForActor(actor, restriction.horseId());
    }

    @Transactional(readOnly = true)
    public TrainingAvailabilityResponse availability(long horseId) {
        ActorContext actor = requireActor();
        return availabilityForActor(actor, horseId);
    }

    @Transactional(readOnly = true)
    public AvailabilityBatchResponse availabilityBatch(AvailabilityBatchRequest request) {
        ActorContext actor = requireActor();
        if (request == null || request.horseIds() == null || request.horseIds().isEmpty()
                || request.horseIds().size() > 100 || request.horseIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw validation("horseIds must contain between 1 and 100 positive horse ids.");
        }
        List<TrainingAvailabilityResponse> items = request.horseIds().stream()
                .map(horseId -> availabilityForActor(actor, horseId))
                .toList();
        return new AvailabilityBatchResponse(items);
    }

    @Transactional(readOnly = true)
    public RestrictionsPageResponse list(int page, int size) {
        ActorContext actor = requireActor();
        if (page < 0 || size < 1 || size > 100) {
            throw validation("page must be non-negative and size must be between 1 and 100.");
        }

        Long trainerUserId = null;
        if (!hasAnyRole(actor, RoleCode.VETERINARIAN, RoleCode.CLUB_MANAGER, RoleCode.HEAD_TRAINER)) {
            if (!actor.roles().contains(RoleCode.TRAINER)) {
                throw forbidden("The authenticated role cannot read medical restrictions.");
            }
            trainerUserId = actor.userId();
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<RestrictionRecord> restrictions = repository.findRestrictions(page, size, trainerUserId, today);
        boolean clinicalProjection = trainerUserId == null;
        List<?> items = restrictions.stream()
                .map(restriction -> clinicalProjection
                        ? restrictionResponse(restriction)
                        : summaryResponse(restriction))
                .toList();
        return new RestrictionsPageResponse(items, page, size,
                repository.countRestrictions(trainerUserId, today));
    }

    @Transactional(readOnly = true)
    public boolean exerciseAllowed(long horseId, long exerciseId) {
        requireHorse(horseId);
        if (!repository.exerciseExists(exerciseId)) {
            throw notFound("Exercise definition was not found.");
        }
        String exerciseType = repository.findExerciseType(exerciseId).orElse(null);
        Optional<RestrictionRecord> activeRestriction = repository.findActiveRestriction(horseId);
        if (activeRestriction.isEmpty()) {
            return true;
        }
        RestrictionRecord restriction = activeRestriction.get();
        return exercisePolicy.allows(restriction.level(), exerciseId, exerciseType,
                repository.findRules(restriction.id()));
    }

    private TrainingAvailabilityResponse availabilityForActor(ActorContext actor, long horseId) {
        requireHorse(horseId);
        authorizeAvailabilityRead(actor, horseId);
        Optional<RestrictionRecord> activeRestriction = repository.findActiveRestriction(horseId);
        if (activeRestriction.isEmpty()) {
            return new TrainingAvailabilityResponse(horseId, TrainingAvailability.AVAILABLE, null, null, List.of());
        }
        RestrictionRecord restriction = activeRestriction.get();
        return new TrainingAvailabilityResponse(horseId, TrainingAvailability.valueOf(restriction.level().name()),
                restriction.reason(), restriction.id(), ruleResponses(restriction.id()));
    }

    private void authorizeAvailabilityRead(ActorContext actor, long horseId) {
        if (hasAnyRole(actor, RoleCode.CLUB_MANAGER, RoleCode.HEAD_TRAINER, RoleCode.VETERINARIAN,
                RoleCode.GROOM)) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (actor.roles().contains(RoleCode.TRAINER)
                && repository.trainerAssignedToHorse(horseId, actor.userId(), today)) {
            return;
        }
        if (actor.roles().contains(RoleCode.WORK_RIDER)
                && repository.workRiderAssignedToHorse(horseId, actor.userId())) {
            return;
        }
        throw forbidden("The horse is outside the authenticated user's read scope.");
    }

    private void validateCreateRequest(MedicalRestrictionCreateRequest request) {
        if (request == null || request.level() == null || request.effectiveAt() == null
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 1000
                || request.medicalCaseId() != null && request.medicalCaseId() <= 0) {
            throw validation("Restriction level, reason, and effectiveAt are required and must be valid.");
        }
    }

    private void requireHorse(long horseId) {
        if (horseId <= 0) {
            throw validation("horseId must be a positive integer.");
        }
        if (!repository.horseExists(horseId)) {
            throw notFound("Horse was not found.");
        }
    }

    private RestrictionRecord requireRestriction(long restrictionId) {
        if (restrictionId <= 0) {
            throw validation("restrictionId must be a positive integer.");
        }
        return repository.findRestriction(restrictionId)
                .orElseThrow(() -> notFound("Medical restriction was not found."));
    }

    private RestrictionRecord requireActiveRestrictionForUpdate(long restrictionId) {
        if (restrictionId <= 0) {
            throw validation("restrictionId must be a positive integer.");
        }
        return repository.findActiveRestrictionForUpdate(restrictionId)
                .orElseThrow(() -> repository.findRestriction(restrictionId).isPresent()
                        ? conflict("The medical restriction is no longer active.")
                        : notFound("Medical restriction was not found."));
    }

    private ActorContext requireRole(RoleCode requiredRole) {
        ActorContext actor = requireActor();
        if (!actor.roles().contains(requiredRole)) {
            throw forbidden("The authenticated role cannot perform this operation.");
        }
        return actor;
    }

    private ActorContext requireActor() {
        ActorContext actor = actorProvider.flatMap(CurrentActorProvider::currentActor)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED,
                        "A valid authenticated actor is required."));
        if (actor.userId() <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "A valid authenticated actor is required.");
        }
        return actor;
    }

    private boolean hasAnyRole(ActorContext actor, RoleCode... roles) {
        for (RoleCode role : roles) {
            if (actor.roles().contains(role)) {
                return true;
            }
        }
        return false;
    }

    private MedicalRestrictionResponse restrictionResponse(RestrictionRecord restriction) {
        return new MedicalRestrictionResponse(restriction.id(), restriction.horseId(), restriction.level(),
                restriction.reason(), restriction.effectiveAt(), restriction.liftedAt(), restriction.reviewDate(),
                restriction.medicalCaseId(), ruleResponses(restriction.id()));
    }

    private RestrictionSummaryResponse summaryResponse(RestrictionRecord restriction) {
        return new RestrictionSummaryResponse(restriction.id(), restriction.horseId(), restriction.level(),
                restriction.reason(), restriction.effectiveAt(), restriction.liftedAt(), restriction.reviewDate(),
                ruleResponses(restriction.id()));
    }

    private List<ExerciseRuleResponse> ruleResponses(long restrictionId) {
        return repository.findRules(restrictionId).stream()
                .map(rule -> new ExerciseRuleResponse(rule.exerciseId(), rule.ruleType(), rule.exerciseTypeNote()))
                .toList();
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}