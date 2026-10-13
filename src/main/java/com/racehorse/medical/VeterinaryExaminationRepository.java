package com.racehorse.medical;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VeterinaryExaminationRepository extends JpaRepository<VeterinaryExamination, Long> {
    boolean existsByHorseIdAndExamType(long horseId, String examType);
}
