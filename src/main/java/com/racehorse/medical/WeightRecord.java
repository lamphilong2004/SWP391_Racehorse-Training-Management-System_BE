package com.racehorse.medical;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "weight_record", schema = "dbo")
public class WeightRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "horse_id", nullable = false) Long horseId;
    @Column(name = "recorded_by_user_id", nullable = false) Long recordedByUserId;
    @Column(name = "weight", nullable = false, precision = 6, scale = 2) BigDecimal weight;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "measured_at", nullable = false, columnDefinition = "datetime2(3)") Instant measuredAt;
    @Column(name = "context", length = 50) @org.hibernate.annotations.Nationalized String context;
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, columnDefinition = "datetime2(3)") Instant createdAt;

    protected WeightRecord() {}
    WeightRecord(long horseId, long userId, BigDecimal weight, Instant measuredAt, Instant createdAt, String context) {
        this.horseId = horseId;
        this.recordedByUserId = userId;
        this.weight = weight;
        this.measuredAt = measuredAt;
        this.createdAt = createdAt;
        this.context = context;
    }
}
