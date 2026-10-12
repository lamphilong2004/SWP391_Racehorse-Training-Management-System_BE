/* =====================================================================
   Technical support table — registration_request (DEC-008 §2.3, §6 Amendment A)
   NOT one of the 51 business entities and NOT part of database/schema.sql (which stays byte-identical).
   Same category as physical-schema.md §9 (technical support tables). Owner: CORE-047.

   Run AFTER database/schema.sql on the same database (needs dbo.app_user and dbo.role):
     sqlcmd -S localhost -d racehorse_dev -E -C -b -i database/technical/registration_request.sql
   Conventions follow physical-schema.md §2 (snake_case, BIGINT IDENTITY, DATETIME2(3) UTC, named constraints,
   ON DELETE NO ACTION). Rows are never hard-deleted (DEC-008 §2.1 item 4).
   ===================================================================== */

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

CREATE TABLE dbo.registration_request (
    id                    BIGINT IDENTITY(1,1) NOT NULL,
    full_name             NVARCHAR(150)  NOT NULL,
    email                 NVARCHAR(254)  NOT NULL,
    email_normalized      NVARCHAR(254)  NOT NULL,
    password_hash         VARCHAR(255)   NULL,
    status                VARCHAR(20)    NOT NULL,
    requested_at          DATETIME2(3)   NOT NULL,
    reviewed_by_user_id   BIGINT         NULL,
    reviewed_at           DATETIME2(3)   NULL,
    decision_reason       NVARCHAR(500)  NULL,
    approved_role_id      BIGINT         NULL,
    created_user_id       BIGINT         NULL,
    created_at            DATETIME2(3)   NOT NULL CONSTRAINT df_registration_request__created_at DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2(3)   NULL,
    CONSTRAINT pk_registration_request PRIMARY KEY (id),
    CONSTRAINT ck_registration_request__status CHECK (status IN ('PENDING_APPROVAL','APPROVED','REJECTED')),
    -- R2 / R4 / R12: a hash exists only while the request is PENDING_APPROVAL, and every PENDING request has one.
    CONSTRAINT ck_registration_request__hash_only_while_pending CHECK (
           (status = 'PENDING_APPROVAL' AND password_hash IS NOT NULL)
        OR (status <> 'PENDING_APPROVAL' AND password_hash IS NULL)),
    -- R3 / R4: a decided request records who decided and when; a pending one has no decision data.
    CONSTRAINT ck_registration_request__review_state CHECK (
           (status = 'PENDING_APPROVAL' AND reviewed_at IS NULL AND reviewed_by_user_id IS NULL
                AND approved_role_id IS NULL AND created_user_id IS NULL AND decision_reason IS NULL)
        OR (status = 'APPROVED' AND reviewed_at IS NOT NULL AND reviewed_by_user_id IS NOT NULL
                AND approved_role_id IS NOT NULL AND created_user_id IS NOT NULL)
        OR (status = 'REJECTED' AND reviewed_at IS NOT NULL AND reviewed_by_user_id IS NOT NULL
                AND approved_role_id IS NULL AND created_user_id IS NULL))
);

ALTER TABLE dbo.registration_request ADD CONSTRAINT fk_registration_request__reviewed_by_user_id
    FOREIGN KEY (reviewed_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.registration_request ADD CONSTRAINT fk_registration_request__approved_role_id
    FOREIGN KEY (approved_role_id) REFERENCES dbo.role (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.registration_request ADD CONSTRAINT fk_registration_request__created_user_id
    FOREIGN KEY (created_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- R1 DB guard: at most one PENDING_APPROVAL request per normalised email.
CREATE UNIQUE INDEX ux_registration_request__pending_email
    ON dbo.registration_request (email_normalized) WHERE status = 'PENDING_APPROVAL';
-- R1 history lookups (latest decision, rejection count) and the Club Manager review list.
CREATE INDEX ix_registration_request__email_normalized ON dbo.registration_request (email_normalized, requested_at DESC);
CREATE INDEX ix_registration_request__status_requested_at ON dbo.registration_request (status, requested_at DESC);
