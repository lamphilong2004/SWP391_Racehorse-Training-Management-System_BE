package com.racehorse.core035;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
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
    List<TreatmentTaskEntity> findByTreatmentPlanIdOrderByTaskDateAscIdAsc(Long planId);
    @Query("select t from TreatmentTaskEntity t where t.id in :ids and t.treatmentPlanId is not null order by t.taskDate, t.id")
    List<TreatmentTaskEntity> assigned(@Param("ids") Collection<Long> ids);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TreatmentTaskEntity t where t.id = :id")
    Optional<TreatmentTaskEntity> locked(@Param("id") Long id);
}
