/*
 * PROPOSED — NOT APPROVED FOR APPLICATION
 * CORE-007 review artifact only. Do not run, add to a migration runner, or apply to any database.
 *
 * Source availability:
 * - The project master context says registration_request is a technical table outside the canonical
 *   51 business tables (RACEHORSE_PROJECT_MASTER_CONTEXT.md:31-32, 687-689), but contains no DDL.
 * - The standalone DEC-008 decision source and its cited §2.3 / §6 Amendment A were not found in the
 *   available workspace. DUNG-2.md:168-171 cites DEC-008 §6 but does not reproduce the pending-login
 *   or registration-table DDL.
 * - The shared contract is present at docs/api/shared.openapi.yaml. It references DEC-008, but is
 *   contract text, not an approved physical schema.
 * - Historical database/technical/registration_request.sql exists at
 *   ce3ac73cba5f28e4614efd02f887ef946980eb59 on the demo branch only, not on origin/dev. It is used
 *   solely for comparison and is NOT approved production schema.
 * - No CORE-007 -> CORE-047 dependency is proposed.
 *
 * Proposed column-by-column mapping (every proposed physical detail remains subject to approval):
 *
 * | Proposed column | SQL Server type / nullability | Key, default, check, index | Contract requirement | Source / decision | Approval gap |
 * |---|---|---|---|---|---|
 * | id | BIGINT NOT NULL | IDENTITY(1,1); PK pk_registration_request | RegistrationRequest.id is int64 and required | shared.openapi.yaml:1056-1065; canonical SQL convention: database/schema.sql header | UNRESOLVED — LEADER DECISION REQUIRED: technical-table key/identity is not specified by available approved DDL |
 * | full_name | NVARCHAR(150) NOT NULL | none | fullName, maxLength 150; required in response | shared.openapi.yaml RegistrationRequest:1061-1065; registration request contract at :159-164 | UNRESOLVED — LEADER DECISION REQUIRED: physical column name/type/nullability not specified |
 * | email | NVARCHAR(254) NOT NULL | none | email format, maxLength 254; login/registration identity | shared.openapi.yaml:159-167 and RegistrationRequest:1061-1065 | UNRESOLVED — LEADER DECISION REQUIRED: physical storage and collation not specified |
 * | email_normalized | NVARCHAR(254) NOT NULL | filtered unique index ux_registration_request__pending_email; lookup index ix_registration_request__email_normalized | Duplicate-email and rate-limit rules use normalized email; normalization is trim + NFC + lower-case | shared.openapi.yaml:20-22, 166-168 | UNRESOLVED — LEADER DECISION REQUIRED: whether a stored computed/application-maintained value is approved, its collation, and index design |
 * | password_hash | VARCHAR(255) NULL | check ck_registration_request__hash_only_while_pending | BCrypt immediately; write-only; pending login compares submitted password; clear after approve/reject | shared.openapi.yaml:23-28, 74-78, 162-165, 548-553 | UNRESOLVED — LEADER DECISION REQUIRED: physical type/length/nullability/constraint need approval; contract does not specify hash column width |
 * | status | VARCHAR(20) NOT NULL | check ck_registration_request__status; included in filtered and status indexes | enum PENDING_APPROVAL, APPROVED, REJECTED | shared.openapi.yaml:875-877 and registration operations :162, :548-553, :586-589 | UNRESOLVED — LEADER DECISION REQUIRED: physical encoding and constraint are not approved DDL |
 * | requested_at | DATETIME2(3) NOT NULL | included in ix_registration_request__email_normalized and ix_registration_request__status_requested_at | requestedAt is required date-time | shared.openapi.yaml:1061-1067; canonical timestamp convention in database/schema.sql header | UNRESOLVED — LEADER DECISION REQUIRED: precision, UTC convention and physical name are not specified for this technical table |
 * | reviewed_by_user_id | BIGINT NULL | FK fk_registration_request__reviewed_by_user_id -> dbo.app_user(id), NO ACTION | reviewedBy is nullable UserRef; review is Club Manager only | shared.openapi.yaml:24, :517-519, :1067-1070 | UNRESOLVED — LEADER DECISION REQUIRED: physical FK/nullability mapping is not approved |
 * | reviewed_at | DATETIME2(3) NULL | review-state check | reviewedAt is nullable date-time | shared.openapi.yaml:1066-1068; canonical timestamp convention in database/schema.sql header | UNRESOLVED — LEADER DECISION REQUIRED: physical type and state invariant are not approved |
 * | decision_reason | NVARCHAR(500) NULL | review-state check | reason maxLength 500; Club Managers only, never shown to applicant | shared.openapi.yaml:586-589, :1077-1080, :1071 | UNRESOLVED — LEADER DECISION REQUIRED: physical column/visibility invariant is not approved DDL |
 * | approved_role_id | BIGINT NULL | FK fk_registration_request__approved_role_id -> dbo.role(id), NO ACTION; review-state check | Club Manager assigns exactly one RoleCode on approval; approvedRoleCode may be null | shared.openapi.yaml:548-553, :1068-1070 | UNRESOLVED — LEADER DECISION REQUIRED: ID-based FK mapping and nullability are not specified |
 * | created_user_id | BIGINT NULL | FK fk_registration_request__created_user_id -> dbo.app_user(id), NO ACTION; review-state check | approval creates an ACTIVE app_user; response exposes nullable createdUserId | shared.openapi.yaml:548-553, :1070 | UNRESOLVED — LEADER DECISION REQUIRED: physical FK and lifecycle invariant are not approved |
 * | created_at | DATETIME2(3) NOT NULL | default SYSUTCDATETIME() | no corresponding contract field | canonical schema convention only; no technical-table source found | UNRESOLVED — LEADER DECISION REQUIRED: whether an unexposed creation timestamp/default belongs on this table |
 * | updated_at | DATETIME2(3) NULL | none | no corresponding contract field | no available registration-specific source | UNRESOLVED — LEADER DECISION REQUIRED: field is not required by the contract or available decision text |
 *
 * Constraint / index mapping:
 * - PK pk_registration_request(id): response requires an int64 id; the PK/identity mechanics are not
 *   prescribed by the contract. UNRESOLVED — LEADER DECISION REQUIRED.
 * - ck_registration_request__status: reflects the contract's three RegistrationStatus values
 *   (shared.openapi.yaml:875-877); physical CHECK constraint is not prescribed.
 *   UNRESOLVED — LEADER DECISION REQUIRED.
 * - ck_registration_request__hash_only_while_pending: intended to enforce the contract's pending
 *   password-hash retention and clearing after decision (shared.openapi.yaml:23, :552-553).
 *   UNRESOLVED — LEADER DECISION REQUIRED: exact physical invariant and transitional transaction state.
 * - ck_registration_request__review_state: proposed workflow cross-field invariant, not stated
 *   completely by the OpenAPI contract. UNRESOLVED — LEADER DECISION REQUIRED.
 * - Three foreign keys: link reviewer/assigned role/created account to existing canonical entities.
 *   Canonical schema says FKs use NO ACTION, but does not approve these technical-table FKs.
 *   UNRESOLVED — LEADER DECISION REQUIRED.
 * - ux_registration_request__pending_email: proposed uniqueness guard; the contract makes duplicate
 *   requests indistinguishable but does not expressly require this index.
 *   UNRESOLVED — LEADER DECISION REQUIRED.
 * - ix_registration_request__email_normalized and ix_registration_request__status_requested_at:
 *   performance indexes inferred from login/history/review lookup needs, not mandated by the contract.
 *   UNRESOLVED — LEADER DECISION REQUIRED.
 *
 * Pending-registration login rule and security implications:
 * 1. Apply the contract normalization exactly: trim, Unicode NFC normalization, then lower-case
 *    (shared.openapi.yaml:20-22). Database collation and Unicode equivalence still require approval.
 * 2. Look up app_user first. If one exists, evaluate only that account; a wrong password or INACTIVE
 *    account must not fall through to registration_request (shared.openapi.yaml:74-78).
 * 3. Only if no app_user exists, look up a PENDING_APPROVAL registration for the normalized email.
 *    Compare the submitted plaintext only in application code with BCrypt.matches; never compare a hash
 *    in SQL. Matching pending password -> exact 403 message, no JWT. Wrong password, REJECTED, and
 *    unknown email -> same neutral 401 with dummy BCrypt verification for unsuccessful paths.
 * 4. Never return/log password_hash, raw password, or indicate hash presence. A missing approved
 *    physical adapter must not be represented as a successful lookup or allow token issuance.
 * 5. Approval can create app_user concurrently; transaction/isolation and race handling are
 *    UNRESOLVED — LEADER DECISION REQUIRED.
 *
 * Historical DDL comparison:
 * - The CREATE TABLE columns, SQL types, nullability, PK/default/CHECK constraints, three foreign keys,
 *   and three indexes below match historical demo DDL at
 *   ce3ac73cba5f28e4614efd02f887ef946980eb59:database/technical/registration_request.sql.
 * - No structural difference is proposed. The header/column comments here are expanded to make source
 *   and approval gaps explicit; this does not change the SQL.
 * - The historical file's DEC-008 references, owner note, and suggested sqlcmd invocation are not
 *   accepted as approval or instructions to apply it. It is not on origin/dev.
 *
 * Safe migration boundary:
 * - Proposal only. It creates one new dbo.registration_request table and its constraints/indexes;
 *   it does not update/delete existing rows or alter existing tables. The foreign keys only reference
 *   dbo.app_user and dbo.role. A name collision makes CREATE TABLE fail; it does not overwrite data.
 * - It has NOT been applied to any database and must not be run until the Leader approves physical DDL.
 */

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
    reviewed_at            DATETIME2(3)   NULL,
    decision_reason        NVARCHAR(500)  NULL,
    approved_role_id       BIGINT         NULL,
    created_user_id        BIGINT         NULL,
    created_at             DATETIME2(3)   NOT NULL CONSTRAINT df_registration_request__created_at DEFAULT SYSUTCDATETIME(),
    updated_at             DATETIME2(3)   NULL,
    CONSTRAINT pk_registration_request PRIMARY KEY (id),
    CONSTRAINT ck_registration_request__status CHECK (status IN ('PENDING_APPROVAL','APPROVED','REJECTED')),
    CONSTRAINT ck_registration_request__hash_only_while_pending CHECK (
           (status = 'PENDING_APPROVAL' AND password_hash IS NOT NULL)
        OR (status <> 'PENDING_APPROVAL' AND password_hash IS NULL)),
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

CREATE UNIQUE INDEX ux_registration_request__pending_email
    ON dbo.registration_request (email_normalized) WHERE status = 'PENDING_APPROVAL';
CREATE INDEX ix_registration_request__email_normalized
    ON dbo.registration_request (email_normalized, requested_at DESC);
CREATE INDEX ix_registration_request__status_requested_at
    ON dbo.registration_request (status, requested_at DESC);
