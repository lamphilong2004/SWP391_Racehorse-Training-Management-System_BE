package com.racehorse.medical.threshold;

import com.racehorse.auth.ActorContext;
import com.racehorse.auth.CurrentActorProvider;
import com.racehorse.auth.RoleCode;
import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import com.racehorse.medical.threshold.ThresholdRepository.ThresholdRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.racehorse.medical.threshold.ThresholdApi.*;

@Service
public class ThresholdService {
    private final ThresholdRepository repository;
    private final CurrentActorProvider actors;
    private final BigDecimal configuredGlobalHeartRate;

    public ThresholdService(ThresholdRepository repository, CurrentActorProvider actors,
                            @Value("${app.medical.thresholds.heart-rate-critical:#{null}}") BigDecimal configuredGlobalHeartRate) {
        this.repository = repository;
        this.actors = actors;
        this.configuredGlobalHeartRate = configuredGlobalHeartRate;
    }

    @Transactional(readOnly = true)
    public List<Threshold> global() {
        requireReadActor();
        return configuredGlobalHeartRate == null ? List.of()
                : List.of(new Threshold("HEART_RATE", configuredGlobalHeartRate, "GLOBAL", null, null, null));
    }

    @Transactional(readOnly = true)
    public List<Threshold> effectiveForHorse(long horseId) {
        ActorContext actor = requireReadActor();
        requireHorse(horseId);
        if (actor.roles().contains(RoleCode.TRAINER)
                && !repository.trainerAssignedToHorse(horseId, actor.userId(), Instant.now())) {
            throw forbidden("The horse is outside the authenticated Trainer's assignment scope.");
        }
        return repository.findEffectiveOverride(horseId, Instant.now())
                .map(row -> List.of(toResponse(row)))
                .orElseGet(this::globalThresholdList);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Threshold setOverride(long horseId, OverrideRequest request) {
        ActorContext actor = requireRole(RoleCode.VETERINARIAN);
        requireHorse(horseId);
        validateRequest(request);
        Instant from = request.effectiveFrom().toInstant().truncatedTo(ChronoUnit.MILLIS);

        List<ThresholdRecord> overlapping = repository.findIntervalsOverlappingFrom(horseId, from);
        for (ThresholdRecord interval : overlapping) {
            if (interval.to() != null) throw conflict("The requested threshold interval overlaps an existing override.");
            if (interval.from().isAfter(from)) throw conflict("The requested threshold starts before the current future override.");
            if (repository.endInterval(interval.id(), from) != 1) {
                throw conflict("The current threshold override changed concurrently.");
            }
        }
        try {
            long id = repository.insertOverride(horseId, actor.userId(), request.thresholdValue(),
                    request.reason() == null ? null : request.reason().trim(), from);
            return repository.findById(id).map(this::toResponse)
                    .orElseThrow(() -> new IllegalStateException("Inserted threshold override could not be read back."));
        } catch (DataIntegrityViolationException ex) {
            throw conflict("An overlapping or open threshold override already exists.");
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Threshold endOverride(long horseId, EndRequest request) {
        requireRole(RoleCode.VETERINARIAN);
        requireHorse(horseId);
        if (request == null || request.effectiveTo() == null) throw validation("effectiveTo is required.");
        Instant to = request.effectiveTo().toInstant().truncatedTo(ChronoUnit.MILLIS);
        ThresholdRecord open = repository.findOpenOverride(horseId)
                .orElseThrow(() -> notFound("No open threshold override exists for this horse."));
        if (to.isBefore(open.from())) throw validation("effectiveTo must be greater than or equal to effectiveFrom.");
        if (repository.endInterval(open.id(), to) != 1) throw conflict("The threshold override changed concurrently.");
        return repository.findById(open.id()).map(this::toResponse)
                .orElseThrow(() -> new IllegalStateException("Ended threshold override could not be read back."));
    }

    private List<Threshold> globalThresholdList() {
        return configuredGlobalHeartRate == null ? List.of()
                : List.of(new Threshold("HEART_RATE", configuredGlobalHeartRate, "GLOBAL", null, null, null));
    }

    private Threshold toResponse(ThresholdRecord record) {
        return new Threshold(record.metric(), record.value(), "HORSE_OVERRIDE", record.horseId(),
                OffsetDateTime.ofInstant(record.from(), java.time.ZoneOffset.UTC),
                record.to() == null ? null : OffsetDateTime.ofInstant(record.to(), java.time.ZoneOffset.UTC));
    }

    private ActorContext requireReadActor() {
        ActorContext actor = actors.currentActor().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required."));
        if (actor.roles().stream().noneMatch(r -> r == RoleCode.HEAD_TRAINER || r == RoleCode.TRAINER || r == RoleCode.VETERINARIAN)) {
            throw forbidden("The authenticated role cannot read medical thresholds.");
        }
        return actor;
    }

    private ActorContext requireRole(RoleCode role) {
        ActorContext actor = actors.currentActor().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication is required."));
        if (!actor.roles().contains(role)) throw forbidden("Only a Veterinarian may change medical thresholds.");
        return actor;
    }

    private void requireHorse(long horseId) {
        if (horseId <= 0) throw validation("horseId must be a positive integer.");
        if (!repository.horseExists(horseId)) throw notFound("Horse was not found.");
    }

    private void validateRequest(OverrideRequest request) {
        if (request == null || request.metricType() == null || !"HEART_RATE".equals(request.metricType())
                || request.thresholdValue() == null || request.thresholdValue().compareTo(BigDecimal.ZERO) <= 0
                || request.thresholdValue().compareTo(new BigDecimal("99999.99")) > 0
                || request.thresholdValue().scale() > 2 || request.effectiveFrom() == null
                || request.reason() != null && request.reason().length() > 1000) {
            throw validation("metricType must be HEART_RATE and thresholdValue, reason and effectiveFrom must be valid.");
        }
    }

    private static BusinessException validation(String message) { return new BusinessException(ErrorCode.VALIDATION_ERROR, message); }
    private static BusinessException forbidden(String message) { return new BusinessException(ErrorCode.FORBIDDEN, message); }
    private static BusinessException notFound(String message) { return new BusinessException(ErrorCode.NOT_FOUND, message); }
    private static BusinessException conflict(String message) { return new BusinessException(ErrorCode.CONFLICT, message); }
}
