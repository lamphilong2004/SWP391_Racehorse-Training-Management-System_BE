package com.racehorse.medical.restriction;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.medical.restriction.api.ExerciseRuleRequest;
import com.racehorse.medical.restriction.api.MedicalRestrictionCreateRequest;
import com.racehorse.medical.restriction.api.ReplaceExerciseRulesRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalRestrictionServiceTest {

    @Mock
    private JdbcMedicalRestrictionRepository repository;

    @Mock
    private CurrentActorProvider actorProvider;

    private MedicalRestrictionService service;

    @BeforeEach
    void setUp() {
        service = new MedicalRestrictionService(repository, Optional.of(actorProvider));
    }

    @Test
    void missingActorFailsClosed() {
        when(actorProvider.currentActor()).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(1, validRequest(RestrictionLevel.LOCKED)));

        assertEquals(ErrorCode.UNAUTHORIZED, exception.code());
        verify(repository, never()).insertRestriction(any(Long.class), any(), any(Long.class), any(), any(), any(), any());
    }

    @Test
    void nonVeterinarianCannotCreateRestriction() {
        authenticate(RoleCode.TRAINER);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(1, validRequest(RestrictionLevel.LOCKED)));

        assertEquals(ErrorCode.FORBIDDEN, exception.code());
        verify(repository, never()).insertRestriction(any(Long.class), any(), any(Long.class), any(), any(), any(), any());
    }

    @Test
    void veterinarianIdentityComesFromActorContext() {
        authenticate(RoleCode.VETERINARIAN);
        when(repository.horseExists(1)).thenReturn(true);
        when(repository.hasActiveRestriction(1)).thenReturn(false);
        when(repository.insertRestriction(eq(1L), eq(null), eq(44L), eq(RestrictionLevel.LIMITED),
                eq("Operational rest"), any(Instant.class), eq(null))).thenReturn(12L);
        when(repository.findRestriction(12)).thenReturn(Optional.of(record(12, 1, RestrictionLevel.LIMITED, null)));
        when(repository.findRules(12)).thenReturn(List.of());

        var result = service.create(1, validRequest(RestrictionLevel.LIMITED));

        assertEquals(12, result.id());
        verify(repository).insertRestriction(eq(1L), eq(null), eq(44L), eq(RestrictionLevel.LIMITED),
                eq("Operational rest"), any(Instant.class), eq(null));
    }

    @Test
    void futureEffectiveAtIsRejected() {
        authenticate(RoleCode.VETERINARIAN);
        when(repository.horseExists(1)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.create(1,
                new MedicalRestrictionCreateRequest(RestrictionLevel.LOCKED, "Rest",
                        OffsetDateTime.now(ZoneOffset.UTC).plusHours(1), null, null)));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.code());
        verify(repository, never()).insertRestriction(any(Long.class), any(), any(Long.class), any(), any(), any(), any());
    }

    @Test
    void activeRestrictionPrecheckReturnsConflict() {
        authenticate(RoleCode.VETERINARIAN);
        when(repository.horseExists(1)).thenReturn(true);
        when(repository.hasActiveRestriction(1)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(1, validRequest(RestrictionLevel.LOCKED)));

        assertEquals(ErrorCode.CONFLICT, exception.code());
        verify(repository, never()).insertRestriction(any(Long.class), any(), any(Long.class), any(), any(), any(), any());
    }

    @Test
    void databaseUniqueConstraintViolationMapsToConflict() {
        authenticate(RoleCode.VETERINARIAN);
        when(repository.horseExists(1)).thenReturn(true);
        when(repository.hasActiveRestriction(1)).thenReturn(false);
        when(repository.insertRestriction(eq(1L), eq(null), eq(44L), eq(RestrictionLevel.LOCKED),
                eq("Operational rest"), any(Instant.class), eq(null)))
                .thenThrow(new DuplicateKeyException("active restriction unique index"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(1, validRequest(RestrictionLevel.LOCKED)));

        assertEquals(ErrorCode.CONFLICT, exception.code());
    }

    @Test
    void unavailableHorseIsNotFound() {
        authenticate(RoleCode.VETERINARIAN);
        when(repository.horseExists(88)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.create(88, validRequest(RestrictionLevel.LOCKED)));

        assertEquals(ErrorCode.NOT_FOUND, exception.code());
    }

    @Test
    void nonPositiveHorseIdIsValidationError() {
        authenticate(RoleCode.VETERINARIAN);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.availability(0));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.code());
        verify(repository, never()).horseExists(0);
    }

    @Test
    void noActiveRestrictionDerivesAvailable() {
        authenticate(RoleCode.VETERINARIAN);
        when(repository.horseExists(1)).thenReturn(true);
        when(repository.findActiveRestriction(1)).thenReturn(Optional.empty());

        var result = service.availability(1);

        assertEquals(TrainingAvailability.AVAILABLE, result.availability());
        assertEquals(null, result.activeRestrictionId());
        assertTrue(result.exerciseRules().isEmpty());
    }

    @Test
    void activeRestrictionDerivesPersistedLevel() {
        authenticate(RoleCode.VETERINARIAN);
        when(repository.horseExists(1)).thenReturn(true);
        when(repository.findActiveRestriction(1)).thenReturn(Optional.of(record(12, 1, RestrictionLevel.LIMITED, null)));
        when(repository.findRules(12)).thenReturn(List.of());

        var result = service.availability(1);

        assertEquals(TrainingAvailability.LIMITED, result.availability());
        assertEquals(12L, result.activeRestrictionId());
    }

    @Test
    void unassignedTrainerCannotReadAvailability() {
        authenticate(RoleCode.TRAINER);
        when(repository.horseExists(1)).thenReturn(true);
        when(repository.trainerAssignedToHorse(eq(1L), eq(44L), any())).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.availability(1));

        assertEquals(ErrorCode.FORBIDDEN, exception.code());
        verify(repository, never()).findActiveRestriction(1);
    }

    @Test
    void assignedTrainerCanReadOperationalAvailability() {
        authenticate(RoleCode.TRAINER);
        when(repository.horseExists(1)).thenReturn(true);
        when(repository.trainerAssignedToHorse(eq(1L), eq(44L), any())).thenReturn(true);
        when(repository.findActiveRestriction(1)).thenReturn(Optional.empty());

        var result = service.availability(1);

        assertEquals(TrainingAvailability.AVAILABLE, result.availability());
    }

    @Test
    void assignedWorkRiderCanReadOperationalAvailability() {
        authenticate(RoleCode.WORK_RIDER);
        when(repository.horseExists(1)).thenReturn(true);
        when(repository.workRiderAssignedToHorse(1, 44)).thenReturn(true);
        when(repository.findActiveRestriction(1)).thenReturn(Optional.empty());

        var result = service.availability(1);

        assertEquals(TrainingAvailability.AVAILABLE, result.availability());
    }

    @Test
    void workRiderWithoutActionableSessionCannotReadAvailability() {
        authenticate(RoleCode.WORK_RIDER);
        when(repository.horseExists(1)).thenReturn(true);
        when(repository.workRiderAssignedToHorse(1, 44)).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.availability(1));

        assertEquals(ErrorCode.FORBIDDEN, exception.code());
        verify(repository, never()).findActiveRestriction(1);
    }


    @Test
    void groomCanReadOperationalAvailability() {
        authenticate(RoleCode.GROOM);
        when(repository.horseExists(1)).thenReturn(true);
        when(repository.findActiveRestriction(1)).thenReturn(Optional.empty());

        var result = service.availability(1);

        assertEquals(TrainingAvailability.AVAILABLE, result.availability());
    }

    @Test
    void liftStoresHistoryAndReturnsAvailable() {
        authenticate(RoleCode.VETERINARIAN);
        RestrictionRecord active = record(12, 1, RestrictionLevel.LOCKED, null);
        when(repository.findActiveRestrictionForUpdate(12)).thenReturn(Optional.of(active));
        when(repository.liftRestriction(eq(12L), any(Instant.class))).thenReturn(1);
        when(repository.horseExists(1)).thenReturn(true);
        when(repository.findActiveRestriction(1)).thenReturn(Optional.empty());

        var result = service.lift(12);

        assertTrue(result.activeRestrictionId() == null);
        assertEquals(TrainingAvailability.AVAILABLE, result.availability());
        verify(repository).liftRestriction(eq(12L), any(Instant.class));
    }

    @Test
    void alreadyLiftedRestrictionCannotBeLiftedAgain() {
        authenticate(RoleCode.VETERINARIAN);
        when(repository.findRestriction(12)).thenReturn(Optional.of(record(12, 1, RestrictionLevel.LOCKED,
                Instant.now().minusSeconds(1))));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.lift(12));

        assertEquals(ErrorCode.CONFLICT, exception.code());
        verify(repository, never()).liftRestriction(any(Long.class), any(Instant.class));
    }

    @Test
    void exerciseRuleTargetMustBeExactlyOne() {
        authenticate(RoleCode.VETERINARIAN);
        when(repository.findActiveRestrictionForUpdate(12))
            .thenReturn(Optional.of(record(12, 1, RestrictionLevel.LIMITED, null)));
        var invalidRule = new ExerciseRuleRequest(5L, ExerciseRuleType.ALLOW, "TROT");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.replaceExerciseRules(12, new ReplaceExerciseRulesRequest(List.of(invalidRule))));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.code());
        verify(repository, never()).replaceRules(any(Long.class), any());
    }

    @Test
    void exerciseRuleMustReferenceExistingExercise() {
        authenticate(RoleCode.VETERINARIAN);
        when(repository.findActiveRestrictionForUpdate(12))
            .thenReturn(Optional.of(record(12, 1, RestrictionLevel.LIMITED, null)));
        when(repository.exerciseExists(5)).thenReturn(false);
        var validRule = new ExerciseRuleRequest(5L, ExerciseRuleType.ALLOW, null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.replaceExerciseRules(12, new ReplaceExerciseRulesRequest(List.of(validRule))));

        assertEquals(ErrorCode.NOT_FOUND, exception.code());
        verify(repository, never()).replaceRules(any(Long.class), any());
    }

    @Test
    void liftedRestrictionCannotHaveRulesReplaced() {
        authenticate(RoleCode.VETERINARIAN);
        when(repository.findRestriction(12)).thenReturn(Optional.of(record(12, 1, RestrictionLevel.LIMITED,
                Instant.now().minusSeconds(1))));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.replaceExerciseRules(12, new ReplaceExerciseRulesRequest(List.of())));

        assertEquals(ErrorCode.CONFLICT, exception.code());
        verify(repository, never()).replaceRules(any(Long.class), any());
    }

    @Test
    void exerciseGateUsesStoredRuleValuesAndRestrictionLevel() {
        when(repository.horseExists(1)).thenReturn(true);
        when(repository.exerciseExists(5)).thenReturn(true);
        when(repository.findExerciseType(5)).thenReturn(Optional.of("TROT"));
        when(repository.findActiveRestriction(1)).thenReturn(Optional.of(record(12, 1,
                RestrictionLevel.LIMITED, null)));
        when(repository.findRules(12)).thenReturn(List.of(
                new ExerciseRule(5L, ExerciseRuleType.ALLOW, null),
                new ExerciseRule(5L, ExerciseRuleType.PROHIBIT, null)));

        assertFalse(service.exerciseAllowed(1, 5));
    }

    private void authenticate(RoleCode role) {
        when(actorProvider.currentActor()).thenReturn(Optional.of(new ActorContext(44, List.of(role))));
    }

    private MedicalRestrictionCreateRequest validRequest(RestrictionLevel level) {
        return new MedicalRestrictionCreateRequest(level, " Operational rest ",
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), null, null);
    }

    private RestrictionRecord record(long id, long horseId, RestrictionLevel level, Instant liftedAt) {
        return new RestrictionRecord(id, horseId, null, 44, level, "Operational rest",
                Instant.now().minusSeconds(60), liftedAt, null);
    }
}