package com.racehorse.medical;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Nationalized;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "veterinary_examination", schema = "dbo")
public class VeterinaryExamination {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id;
    @Column(name = "horse_id", nullable = false) Long horseId;
    @Column(name = "performed_by_user_id", nullable = false) Long performedByUserId;
    @Column(name = "exam_type", nullable = false, length = 20) String examType;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "examined_at", nullable = false, columnDefinition = "datetime2(3)") Instant examinedAt;
    @Nationalized @Column(name = "findings", columnDefinition = "nvarchar(max)") String findings;
    @Enumerated(EnumType.STRING) @Column(name = "health_status_set", length = 20) HealthStatus healthStatusSet;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(3)") Instant createdAt;

    protected VeterinaryExamination() {}
    VeterinaryExamination(long horseId, long userId, HealthStatus status, String notes, Instant now) {
        this.horseId = horseId;
        this.performedByUserId = userId;
        this.examType = "BASELINE";
        this.examinedAt = now;
        this.createdAt = now;
        this.findings = notes;
        this.healthStatusSet = status;
    }
}
