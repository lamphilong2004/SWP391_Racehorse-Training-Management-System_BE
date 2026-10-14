package com.racehorse.audit;

import org.springframework.data.jpa.repository.JpaRepository;

/** Writer-side repository. The audit query API (SCR-ADMIN-004) belongs to CORE-051. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
