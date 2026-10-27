-- Leader-approved 2026-10-27: Club Manager declares racing strengths on a horse; templates carry a strength focus
-- used only for labelled recommendations. Additive tables, no backfill, no change to existing columns.
-- Dry-run on a backup or local copy first; never run implicitly at application startup.
SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;
    IF OBJECT_ID(N'dbo.horse_strength', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.horse_strength (
            horse_id            BIGINT       NOT NULL,
            strength_code       VARCHAR(30)  NOT NULL,
            declared_by_user_id BIGINT       NOT NULL,
            declared_at         DATETIME2(3) NOT NULL CONSTRAINT df_horse_strength__declared_at DEFAULT SYSUTCDATETIME(),
            CONSTRAINT pk_horse_strength PRIMARY KEY (horse_id, strength_code),
            CONSTRAINT fk_horse_strength__horse FOREIGN KEY (horse_id) REFERENCES dbo.horse(id),
            CONSTRAINT fk_horse_strength__declared_by FOREIGN KEY (declared_by_user_id) REFERENCES dbo.app_user(id),
            CONSTRAINT ck_horse_strength__code CHECK (strength_code IN (
                'SPRINT','MILE','MIDDLE_DISTANCE','STAYING',
                'TURF','DIRT','SYNTHETIC',
                'FRONT_RUNNER','STALKER','CLOSER',
                'SPEED','ENDURANCE','STRENGTH','BALANCE'))
        );
    END;
    IF OBJECT_ID(N'dbo.template_version_strength_focus', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.template_version_strength_focus (
            template_version_id BIGINT      NOT NULL,
            strength_code       VARCHAR(30) NOT NULL,
            CONSTRAINT pk_template_version_strength_focus PRIMARY KEY (template_version_id, strength_code),
            CONSTRAINT fk_template_strength_focus__version FOREIGN KEY (template_version_id) REFERENCES dbo.template_version(id),
            CONSTRAINT ck_template_strength_focus__code CHECK (strength_code IN (
                'SPRINT','MILE','MIDDLE_DISTANCE','STAYING',
                'TURF','DIRT','SYNTHETIC',
                'FRONT_RUNNER','STALKER','CLOSER',
                'SPEED','ENDURANCE','STRENGTH','BALANCE'))
        );
    END;
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
