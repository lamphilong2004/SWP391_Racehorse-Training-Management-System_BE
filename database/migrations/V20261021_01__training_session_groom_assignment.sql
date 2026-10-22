-- Leader-authorized CORE-026 technical assignment. No backfill or business-row rewrite.
-- Dry-run on racehorse_integration_test first; never run implicitly at application startup.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.training_session_groom_assignment', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.training_session_groom_assignment (
            training_session_id BIGINT NOT NULL CONSTRAINT pk_training_session_groom_assignment PRIMARY KEY,
            groom_user_id BIGINT NOT NULL,
            assigned_by_user_id BIGINT NOT NULL,
            assigned_at DATETIME2(3) NOT NULL CONSTRAINT df_session_groom_assigned_at DEFAULT SYSUTCDATETIME(),
            CONSTRAINT fk_session_groom_session FOREIGN KEY(training_session_id) REFERENCES dbo.training_session(id),
            CONSTRAINT fk_session_groom_user FOREIGN KEY(groom_user_id) REFERENCES dbo.app_user(id),
            CONSTRAINT fk_session_groom_assigner FOREIGN KEY(assigned_by_user_id) REFERENCES dbo.app_user(id)
        );
        CREATE INDEX ix_session_groom_user ON dbo.training_session_groom_assignment(groom_user_id,training_session_id);
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
