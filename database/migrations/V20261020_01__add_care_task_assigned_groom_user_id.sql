/*
 * CORE-045 / approved CORE-006 G1 schema addition.
 * Existing care tasks remain unassigned; no backfill or data change is performed.
 */
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF COL_LENGTH(N'dbo.care_task', N'assigned_groom_user_id') IS NULL
        ALTER TABLE dbo.care_task ADD assigned_groom_user_id BIGINT NULL;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.foreign_keys
        WHERE parent_object_id = OBJECT_ID(N'dbo.care_task')
          AND name = N'fk_care_task__assigned_groom_user_id'
    )
        ALTER TABLE dbo.care_task
            ADD CONSTRAINT fk_care_task__assigned_groom_user_id
            FOREIGN KEY (assigned_groom_user_id) REFERENCES dbo.app_user (id)
            ON DELETE NO ACTION ON UPDATE NO ACTION;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE object_id = OBJECT_ID(N'dbo.care_task')
          AND name = N'ix_care_task__assigned_groom_user_id'
    )
        CREATE INDEX ix_care_task__assigned_groom_user_id
            ON dbo.care_task (assigned_groom_user_id)
            WHERE assigned_groom_user_id IS NOT NULL;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
