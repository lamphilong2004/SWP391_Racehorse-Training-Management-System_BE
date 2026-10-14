package com.racehorse.medical;

import com.racehorse.common.api.ErrorDetail;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import jakarta.validation.Validator;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;


@Service
public class MedicalService {
    private final MedicalAccess access;
    private final MedicalHorseRepository horses;
    private final VeterinaryExaminationRepository examinations;
    private final WeightRecordRepository weights;
    private final Validator validator;
    // DEMO INTEGRATION: Flow 1 automatic activation re-check after the baseline (DEC-009 §1).
    private final org.springframework.beans.factory.ObjectProvider<com.racehorse.integration.ActivationHook> activation;

    /** Original CORE-014 signature (kept for its unit tests); no activation hook. */
    public MedicalService(MedicalAccess access, MedicalHorseRepository horses,
                          VeterinaryExaminationRepository examinations, WeightRecordRepository weights,
                          Validator validator) {
        this(access, horses, examinations, weights, validator,
                new org.springframework.beans.factory.support.StaticListableBeanFactory()
                        .getBeanProvider(com.racehorse.integration.ActivationHook.class));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MedicalService(MedicalAccess access, MedicalHorseRepository horses,
                          VeterinaryExaminationRepository examinations, WeightRecordRepository weights,
                          Validator validator,
                          org.springframework.beans.factory.ObjectProvider<com.racehorse.integration.ActivationHook> activation) {
        this.access = access;
        this.horses = horses;
        this.examinations = examinations;
        this.weights = weights;
        this.validator = validator;
        this.activation = activation;
    }

    @Transactional
    public BaselineExaminationResponse recordBaseline(long horseId, BaselineExaminationRequest request) {
        var identity = access.requireIdentity(true);
        validateId(horseId);
        validate(request);
        String lifecycle = horses.lockLifecycle(horseId);
        if (lifecycle == null) throw notFound();
        if (examinations.existsByHorseIdAndExamType(horseId, "BASELINE"))
            throw new BusinessException(ErrorCode.CONFLICT, "A baseline examination already exists for this horse.");

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        var examination = examinations.saveAndFlush(new VeterinaryExamination(horseId, identity.userId(),
                request.healthStatus(), request.clinicalNotes(), now));
        weights.saveAndFlush(new WeightRecord(horseId, identity.userId(), request.weightKg(), now, now, "BASELINE"));
        horses.updateHealth(horseId, request.healthStatus(), now);
        activation.ifAvailable(hook -> hook.evaluate(horseId));
        return new BaselineExaminationResponse(examination.id, horseId, request.weightKg(), request.healthStatus(), now);
    }

    @Transactional
    public WeightRecordResponse recordWeight(long horseId, WeightRecordRequest request) {
        var identity = access.requireIdentity(true);
        validateId(horseId);
        validate(request);
        if (request.recordedAt() != null && request.recordedAt().isBefore(Instant.parse("0001-01-01T00:00:00Z")))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Recorded time is outside the supported database range.");
        if (horses.lockLifecycle(horseId) == null) throw notFound();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant measured = request.recordedAt() == null ? now : request.recordedAt().truncatedTo(ChronoUnit.MILLIS);
        return WeightRecordResponse.from(weights.saveAndFlush(
                new WeightRecord(horseId, identity.userId(), request.weightKg(), measured, now, null)));
    }

    @Transactional(readOnly = true)
    public WeightRecordsPage listWeights(long horseId, int page, int size) {
        var identity = access.requireIdentity(false);
        validateId(horseId);
        if (page < 0 || size < 1 || size > 100)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Page must be non-negative and size between 1 and 100.");
        if (access.needsTrainerScope(identity) &&
                !horses.isAssignedTrainer(horseId, identity.userId(), LocalDate.now(ZoneOffset.UTC)))
            throw new BusinessException(ErrorCode.FORBIDDEN, "The horse is outside your assigned scope.");
        if (!horses.exists(horseId)) throw notFound();
        var result = weights.findByHorseIdOrderByMeasuredAtDescIdDesc(horseId, PageRequest.of(page, size));
        return new WeightRecordsPage(result.getContent().stream().map(WeightRecordResponse::from).toList(),
                page, size, result.getTotalElements());
    }

    private void validateId(long id) {
        if (id < 1) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Horse id must be positive.");
    }

    private <T> void validate(T request) {
        if (request == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request body is required.");
        var violations = validator.validate(request);
        if (!violations.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Request validation failed.",
                violations.stream().map(v -> new ErrorDetail(v.getPropertyPath().toString(), v.getMessage())).toList());
    }

    private BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND, "Horse not found.");
    }
}
