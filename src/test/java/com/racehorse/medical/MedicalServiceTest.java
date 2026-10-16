package com.racehorse.medical;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MedicalServiceTest {
    private MedicalAccess access;
    private MedicalHorseRepository horses;
    private VeterinaryExaminationRepository examinations;
    private WeightRecordRepository weights;
    private MedicalService service;
    private ValidatorFactory validation;

    @BeforeEach void setup() {
        access = mock(MedicalAccess.class);
        horses = mock(MedicalHorseRepository.class);
        examinations = mock(VeterinaryExaminationRepository.class);
        weights = mock(WeightRecordRepository.class);
        validation = Validation.buildDefaultValidatorFactory();
        service = new MedicalService(access, horses, examinations, weights, validation.getValidator());
        lenient().when(access.requireIdentity(anyBoolean())).thenReturn(
                new MedicalIdentityProvider.Identity(42, Set.of("VETERINARIAN")));
    }
    @AfterEach void close() { validation.close(); }

    @ParameterizedTest @EnumSource(HealthStatus.class)
    void baselinePersistsActorWeightAndStatus(HealthStatus status) {
        when(horses.lockLifecycle(7)).thenReturn("DRAFT");
        when(examinations.saveAndFlush(any())).thenAnswer(call -> {
            VeterinaryExamination exam = call.getArgument(0);
            assertEquals(42L, exam.performedByUserId);
            assertEquals("BASELINE", exam.examType);
            assertEquals("Normal findings", exam.findings);
            exam.id = 11L;
            return exam;
        });
        when(weights.saveAndFlush(any())).thenAnswer(call -> {
            WeightRecord weight = call.getArgument(0);
            assertEquals(42L, weight.recordedByUserId);
            assertEquals(new BigDecimal("450.25"), weight.weight);
            assertEquals("BASELINE", weight.context);
            return weight;
        });
        var response = service.recordBaseline(7, new BaselineExaminationRequest(
                new BigDecimal("450.25"), status, "Normal findings"));
        assertEquals(11, response.id());
        assertEquals(status, response.healthStatus());
        var order = inOrder(horses, examinations, weights);
        order.verify(horses).lockLifecycle(7);
        order.verify(examinations).existsByHorseIdAndExamType(7, "BASELINE");
        order.verify(examinations).saveAndFlush(any());
        order.verify(weights).saveAndFlush(any());
        order.verify(horses).updateHealth(7, status, response.recordedAt());
    }

    @Test void duplicateBaselineIsConflictWithoutWrites() {
        when(horses.lockLifecycle(7)).thenReturn("DRAFT");
        when(examinations.existsByHorseIdAndExamType(7, "BASELINE")).thenReturn(true);
        assertCode(ErrorCode.CONFLICT, () -> service.recordBaseline(7, baseline()));
        verify(examinations, never()).saveAndFlush(any());
        verifyNoInteractions(weights);
        verify(horses, never()).updateHealth(anyLong(), any(), any());
    }

    @Test void missingHorseDoesNotCreateRecords() {
        assertCode(ErrorCode.NOT_FOUND, () -> service.recordBaseline(7, baseline()));
        verifyNoInteractions(examinations, weights);
    }

    @Test void existingActiveHorseWithoutBaselineMayReceiveInitialRecord() {
        when(horses.lockLifecycle(7)).thenReturn("ACTIVE");
        when(examinations.saveAndFlush(any())).thenAnswer(call -> {
            VeterinaryExamination exam = call.getArgument(0);
            exam.id = 11L;
            return exam;
        });
        when(weights.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(11, service.recordBaseline(7, baseline()).id());
    }

    @Test void failedWeightInsertNeverUpdatesHealth() {
        when(horses.lockLifecycle(7)).thenReturn("DRAFT");
        when(examinations.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(weights.saveAndFlush(any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> service.recordBaseline(7, baseline()));
        verify(horses, never()).updateHealth(anyLong(), any(), any());
    }

    @Test void weightUsesAuthenticatedUserAndUtcTimestampWithoutChangingHealth() {
        when(horses.lockLifecycle(7)).thenReturn("ACTIVE");
        Instant measured = Instant.parse("2025-01-01T00:00:00Z");
        when(weights.saveAndFlush(any())).thenAnswer(call -> {
            WeightRecord weight = call.getArgument(0);
            assertEquals(42L, weight.recordedByUserId);
            assertEquals(measured, weight.measuredAt);
            weight.id = 12L;
            return weight;
        });
        var response = service.recordWeight(7, new WeightRecordRequest(new BigDecimal("450.25"), measured));
        assertEquals(measured, response.recordedAt());
        verify(horses, never()).updateHealth(anyLong(), any(), any());
        verifyNoInteractions(examinations);
    }

    @Test void omittedWeightTimestampDefaultsToNow() {
        when(horses.lockLifecycle(7)).thenReturn("DRAFT");
        when(weights.saveAndFlush(any())).thenAnswer(call -> {
            WeightRecord weight = call.getArgument(0);
            weight.id = 12L;
            assertEquals(weight.createdAt, weight.measuredAt);
            return weight;
        });
        assertNotNull(service.recordWeight(7, new WeightRecordRequest(new BigDecimal("450"), null)).recordedAt());
    }

    @Test void invalidWeightsAndFutureDatesFailBeforeDatabaseAccess() {
        for (String value : List.of("0", "-1", "10000", "450.123"))
            assertCode(ErrorCode.VALIDATION_ERROR, () ->
                    service.recordWeight(7, new WeightRecordRequest(new BigDecimal(value), null)));
        assertCode(ErrorCode.VALIDATION_ERROR, () ->
                service.recordWeight(7, new WeightRecordRequest(new BigDecimal("450"), Instant.now().plusSeconds(3600))));
        assertCode(ErrorCode.VALIDATION_ERROR, () ->
                service.recordBaseline(7, new BaselineExaminationRequest(new BigDecimal("450"), null, null)));
        verifyNoInteractions(horses, weights, examinations);
    }

    @Test void paginationReturnsContractProjection() {
        when(horses.exists(7)).thenReturn(true);
        var record = new WeightRecord(7, 42, new BigDecimal("450"), Instant.EPOCH, Instant.EPOCH, null);
        record.id = 12L;
        when(weights.findByHorseIdOrderByMeasuredAtDescIdDesc(7, PageRequest.of(1, 20)))
                .thenReturn(new PageImpl<>(List.of(record), PageRequest.of(1, 20), 21));
        var result = service.listWeights(7, 1, 20);
        assertEquals(21, result.totalElements());
        assertEquals(1, result.page());
        assertEquals(12, result.items().getFirst().id());
    }

    @Test void outOfScopeTrainerCannotQueryWeights() {
        var identity = new MedicalIdentityProvider.Identity(42, Set.of("TRAINER"));
        when(access.requireIdentity(false)).thenReturn(identity);
        when(access.needsTrainerScope(identity)).thenReturn(true);
        assertCode(ErrorCode.FORBIDDEN, () -> service.listWeights(7, 0, 20));
        verifyNoInteractions(weights);
        verify(horses, never()).exists(anyLong());
    }

    @Test void assignedTrainerCanRead() {
        var identity = new MedicalIdentityProvider.Identity(42, Set.of("TRAINER"));
        when(access.requireIdentity(false)).thenReturn(identity);
        when(access.needsTrainerScope(identity)).thenReturn(true);
        when(horses.isAssignedTrainer(eq(7L), eq(42L), any())).thenReturn(true);
        when(horses.exists(7)).thenReturn(true);
        when(weights.findByHorseIdOrderByMeasuredAtDescIdDesc(7, PageRequest.of(0, 20)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));
        assertTrue(service.listWeights(7, 0, 20).items().isEmpty());
    }

    @Test void invalidPaginationAndIdsNeverReachRepository() {
        for (int[] values : List.of(new int[]{-1, 20}, new int[]{0, 0}, new int[]{0, 101}))
            assertCode(ErrorCode.VALIDATION_ERROR, () -> service.listWeights(7, values[0], values[1]));
        assertCode(ErrorCode.VALIDATION_ERROR, () -> service.recordBaseline(0, baseline()));
        verifyNoInteractions(horses, examinations, weights);
    }

    private BaselineExaminationRequest baseline() {
        return new BaselineExaminationRequest(new BigDecimal("450"), HealthStatus.FIT, null);
    }
    private void assertCode(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BusinessException.class, action).code());
    }
}
