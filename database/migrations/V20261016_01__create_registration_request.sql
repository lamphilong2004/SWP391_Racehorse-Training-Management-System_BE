/*
 * Version: V20261016_01
 * Leader approval recorded in docs/decisions/CORE-007-leader-decisions.md on 2026-10-16.
 * Applies only to an empty CORE-007 isolated test database after database/schema.sql.
 * This migration creates one technical table; it does not alter existing tables or data.
 */
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.registration_request', N'U') IS NOT NULL
        THROW 51000, 'dbo.registration_request already exists; migration was not applied.', 1;

    CREATE TABLE dbo.registration_request (
        id                    BIGINT IDENTITY(1,1) NOT NULL,
        full_name             NVARCHAR(150)  NOT NULL,
        email                 NVARCHAR(254)  NOT NULL,
        email_normalized      NVARCHAR(254) COLLATE Latin1_General_100_BIN2 NOT NULL,
        password_hash         VARCHAR(255)   NULL,
        status                VARCHAR(20)    NOT NULL,
        requested_at          DATETIME2(3)   NOT NULL,
        reviewed_by_user_id   BIGINT         NULL,
        reviewed_at           DATETIME2(3)   NULL,
        decision_reason       NVARCHAR(500)  NULL,
        approved_role_id      BIGINT         NULL,
        created_user_id       BIGINT         NULL,
        created_at            DATETIME2(3)   NOT NULL
            CONSTRAINT df_registration_request__created_at DEFAULT SYSUTCDATETIME(),
        updated_at            DATETIME2(3)   NULL,
        CONSTRAINT pk_registration_request PRIMARY KEY (id),
        CONSTRAINT ck_registration_request__status CHECK (
            status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
        CONSTRAINT ck_registration_request__hash_only_while_pending CHECK (
               (status = 'PENDING_APPROVAL' AND password_hash IS NOT NULL)
            OR (status <> 'PENDING_APPROVAL' AND password_hash IS NULL)),
        CONSTRAINT ck_registration_request__review_state CHECK (
               (status = 'PENDING_APPROVAL'
                    AND reviewed_at IS NULL
                    AND reviewed_by_user_id IS NULL
                    AND approved_role_id IS NULL
                    AND created_user_id IS NULL
                    AND decision_reason IS NULL)
            OR (status = 'APPROVED'
                    AND reviewed_at IS NOT NULL
                    AND reviewed_by_user_id IS NOT NULL
                    AND approved_role_id IS NOT NULL
                    AND created_user_id IS NOT NULL)
            OR (status = 'REJECTED'
                    AND reviewed_at IS NOT NULL
                    AND reviewed_by_user_id IS NOT NULL
                    AND approved_role_id IS NULL
                    AND created_user_id IS NULL))
    );

    ALTER TABLE dbo.registration_request
        ADD CONSTRAINT fk_registration_request__reviewed_by_user_id
        FOREIGN KEY (reviewed_by_user_id) REFERENCES dbo.app_user (id)
        ON DELETE NO ACTION ON UPDATE NO ACTION;
    ALTER TABLE dbo.registration_request
        ADD CONSTRAINT fk_registration_request__approved_role_id
        FOREIGN KEY (approved_role_id) REFERENCES dbo.role (id)
        ON DELETE NO ACTION ON UPDATE NO ACTION;
    ALTER TABLE dbo.registration_request
        ADD CONSTRAINT fk_registration_request__created_user_id
        FOREIGN KEY (created_user_id) REFERENCES dbo.app_user (id)
        ON DELETE NO ACTION ON UPDATE NO ACTION;

    CREATE UNIQUE INDEX ux_registration_request__pending_email
        ON dbo.registration_request (email_normalized)
        WHERE status = 'PENDING_APPROVAL';
    CREATE INDEX ix_registration_request__email_normalized
        ON dbo.registration_request (email_normalized, requested_at DESC);
    CREATE INDEX ix_registration_request__status_requested_at
        ON dbo.registration_request (status, requested_at DESC);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
