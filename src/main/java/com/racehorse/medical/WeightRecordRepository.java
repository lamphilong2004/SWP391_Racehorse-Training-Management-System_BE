package com.racehorse.medical;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightRecordRepository extends JpaRepository<WeightRecord, Long> {
    Page<WeightRecord> findByHorseIdOrderByMeasuredAtDescIdDesc(long horseId, Pageable pageable);
}
