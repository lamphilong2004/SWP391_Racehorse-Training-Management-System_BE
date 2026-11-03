package com.racehorse.core035;

import jakarta.persistence.*;
import java.time.*;

enum CaseStatus { OPEN, CLOSED }
enum ExamType { EXAMINATION, RE_EXAMINATION }
enum ClinicalSeverity { MILD, MODERATE, SEVERE }
enum RecoveryStatus { NOT_RECOVERED, IMPROVING, RECOVERED }
enum HealthStatus { FIT, MONITORING, INJURED, QUARANTINED }

@Entity @Table(name = "medical_case", schema = "dbo")
class MedicalCaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "horse_id", nullable = false) Long horseId;
    @Column(name = "opened_by_user_id", nullable = false) Long openedByUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) CaseStatus status;
    @Column(name = "opened_at", nullable = false) LocalDateTime openedAt;
    @Column(name = "closed_at") LocalDateTime closedAt;
    @Column(name = "condition_summary", length = 2000, columnDefinition = "nvarchar(2000)") String conditionSummary;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @Column(name = "updated_at") LocalDateTime updatedAt;
    protected MedicalCaseEntity() {}
    MedicalCaseEntity(long horseId, long actorId, String reason, LocalDateTime now) {
        this.horseId = horseId; this.openedByUserId = actorId; this.conditionSummary = reason;
        this.status = CaseStatus.OPEN; this.openedAt = now; this.createdAt = now;
    }
}

@Entity @Table(name = "injury", schema = "dbo")
class InjuryEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "medical_case_id", nullable = false) Long medicalCaseId;
    @Column(name = "veterinary_examination_id") Long veterinaryExaminationId;
    @Column(name = "body_region", nullable = false, length = 100, columnDefinition = "nvarchar(100)") String bodyRegion;
    @Column(name = "position_3d", length = 100) String position3d;
    @Column(length = 20, columnDefinition = "nvarchar(20)") String side;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) ClinicalSeverity severity;
    @Enumerated(EnumType.STRING) @Column(name = "recovery_status", nullable = false, length = 20) RecoveryStatus recoveryStatus;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @Column(name = "updated_at") LocalDateTime updatedAt;
    protected InjuryEntity() {}
}

@Entity @Table(name = "treatment_plan", schema = "dbo")
class TreatmentPlanEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "medical_case_id", nullable = false, unique = true) Long medicalCaseId;
    @Column(name = "created_by_user_id", nullable = false) Long createdByUserId;
    @Column(name = "treatment_medication_instructions", nullable = false, columnDefinition = "nvarchar(max)") String treatmentMedicationInstructions;
    @Column(name = "rest_period", length = 100, columnDefinition = "nvarchar(100)") String restPeriod;
    @Column(name = "care_instructions", length = 2000, columnDefinition = "nvarchar(2000)") String careInstructions;
    @Column(name = "re_examination_date") LocalDate reExaminationDate;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @Column(name = "updated_at") LocalDateTime updatedAt;
    protected TreatmentPlanEntity() {}
}

@Entity @Table(name = "care_task", schema = "dbo")
class TreatmentTaskEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "horse_id", nullable = false) Long horseId;
    @Column(name = "care_plan_item_id") Long carePlanItemId;
    @Column(name = "nutrition_plan_item_id") Long nutritionPlanItemId;
    @Column(name = "treatment_plan_id") Long treatmentPlanId;
    @Column(name = "completed_by_user_id") Long completedByUserId;
    @Column(name = "task_date", nullable = false) LocalDate taskDate;
    @Column(name = "scheduled_slot", length = 50, columnDefinition = "nvarchar(50)") String scheduledSlot;
    @Column(name = "instruction_snapshot", length = 2000, columnDefinition = "nvarchar(2000)") String instructionSnapshot;
    @Column(name = "completed_at") LocalDateTime completedAt;
    @Column(name = "created_at", nullable = false) LocalDateTime createdAt;
    @Column(name = "updated_at") LocalDateTime updatedAt;
    protected TreatmentTaskEntity() {}
}
