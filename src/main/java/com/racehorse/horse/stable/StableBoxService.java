package com.racehorse.horse.stable;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StableBoxService {

    private final StableBoxReadRepository repository;

    public StableBoxService(StableBoxReadRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<StableBoxResponse> list(BoxStatus status) {
        return repository.findAll(status);
    }
}
