package com.racehorse.restriction;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Effective heart-rate thresholds (BR-D11): Veterinarian per-horse override (horse_threshold_override, critical only)
 * else the global default from configuration (physical-schema appendix: application property).
 * WARNING band = effective critical − margin (proposal P-07). The numbers in application.yml are DEMO DATA.
 */
@Component
public class MonitoringThresholds {
    public record Effective(BigDecimal warning, BigDecimal critical, boolean overridden) {}

    private final JdbcTemplate jdbc;
    private final BigDecimal globalCritical;
    private final BigDecimal warningMargin;

    public MonitoringThresholds(JdbcTemplate jdbc,
                                @Value("${racehorse.monitoring.hr-critical-default:180}") BigDecimal globalCritical,
                                @Value("${racehorse.monitoring.hr-warning-margin:10}") BigDecimal warningMargin) {
        this.jdbc = jdbc;
        this.globalCritical = globalCritical;
        this.warningMargin = warningMargin;
    }

    public BigDecimal globalCritical() { return globalCritical; }

    public Optional<BigDecimal> override(long horseId) {
        return jdbc.queryForList("SELECT critical_value FROM dbo.horse_threshold_override WHERE horse_id=? AND metric='HEART_RATE' "
                        + "AND effective_to IS NULL ORDER BY effective_from DESC, id DESC", BigDecimal.class, horseId)
                .stream().findFirst();
    }

    public Effective effective(long horseId) {
        var o = override(horseId);
        BigDecimal critical = o.orElse(globalCritical);
        return new Effective(critical.subtract(warningMargin), critical, o.isPresent());
    }
}
