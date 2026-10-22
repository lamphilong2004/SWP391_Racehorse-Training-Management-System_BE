package com.racehorse.exercise;

import com.racehorse.common.error.BusinessException;
import com.racehorse.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExerciseService {

    private final ExerciseRepository repository;

    public ExerciseService(ExerciseRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ExerciseResponse> list(String query, String exerciseType) {
        if (query != null && query.length() > 100) {
            throw validation("q must not exceed 100 characters.");
        }
        if (exerciseType != null && exerciseType.length() > 50) {
            throw validation("exerciseType must not exceed 50 characters.");
        }
        return repository.findAll(query, exerciseType);
    }

    @Transactional(readOnly = true)
    public ExerciseResponse get(long id) {
        requirePositiveId(id);
        return repository.findById(id).orElseThrow(() -> notFound("Exercise was not found."));
    }

    @Transactional
    public ExerciseResponse create(ExerciseInput input) {
        validate(input);
        return repository.insert(input);
    }

    @Transactional
    public ExerciseResponse update(long id, ExerciseInput input) {
        requirePositiveId(id);
        validate(input);
        if (repository.update(id, input) == 0) {
            throw notFound("Exercise was not found.");
        }
        return repository.findById(id).orElseThrow(() -> notFound("Exercise was not found."));
    }

    private void validate(ExerciseInput input) {
        if (input == null || input.name() == null || input.name().isBlank() || input.name().length() > 150
                || lengthExceeds(input.exerciseType(), 50) || lengthExceeds(input.description(), 2000)
                || lengthExceeds(input.targetMetric(), 100)) {
            throw validation("Exercise name is required and exercise fields must fit the approved contract limits.");
        }
    }

    private boolean lengthExceeds(String value, int max) {
        return value != null && value.length() > max;
    }

    private void requirePositiveId(long id) {
        if (id <= 0) {
            throw validation("id must be a positive integer.");
        }
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }
}
