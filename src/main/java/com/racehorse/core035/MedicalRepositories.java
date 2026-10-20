package com.racehorse.core035;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.*;

interface MedicalCases extends JpaRepository<MedicalCaseEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from MedicalCaseEntity c where c.id = :id")
    Optional<MedicalCaseEntity> locked(@Param("id") Long id);
}
interface Injuries extends JpaRepository<InjuryEntity, Long> {
    List<InjuryEntity> findByMedicalCaseIdOrderByIdDesc(Long caseId);
}
interface TreatmentPlans extends JpaRepository<TreatmentPlanEntity, Long> {
    Optional<TreatmentPlanEntity> findByMedicalCaseId(Long caseId);
}
interface TreatmentTasks extends JpaRepository<TreatmentTaskEntity, Long> {
    Page<TreatmentTaskEntity> findByTreatmentPlanIdOrderByTaskDateAscIdAsc(Long planId, Pageable pageable);
}
