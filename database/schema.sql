/* =====================================================================
   Racehorse Training & Management System — physical schema (DB-01)
   Target        : Microsoft SQL Server (2016 SP1 or later; filtered indexes, DATETIME2)
   Source model  : design/erd (ERD-02, validated by ERD-03) — 51 business entities
   Scope         : 51 business tables only. No seed / demo data.
                   Technical support tables (password reset, refresh tokens,
                   global thresholds) are NOT created here — see physical-schema.md §Appendix.

   Conventions (see physical-schema.md §2):
   - snake_case, singular table names, schema dbo; USER is reserved -> app_user
   - PK: id BIGINT IDENTITY(1,1); business codes are UNIQUE alternate keys
   - FK column: <referenced>_id, or <role>_user_id for app_user references
   - Timestamps: DATETIME2(3) holding UTC (suffix _at); calendar dates: DATE
   - Money: DECIMAL(19,4) + CHAR(3) ISO-4217 currency
   - Fixed vocabularies: VARCHAR + CHECK; role is a reference table
   - Every FK: ON DELETE NO ACTION ON UPDATE NO ACTION (no cascade anywhere)
   - Constraint names: pk_<t>, uq_<t>__<cols>, ck_<t>__<rule>, df_<t>__<col>,
     fk_<t>__<col>, ix_<t>__<cols>, ux_<t>__<cols> (unique index)

   Run on an empty database. The script targets the current database and
   contains no GO separators (runs as one batch in sqlcmd, SSMS or Spring ScriptUtils).
   ===================================================================== */

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

/* =====================================================================
   PART 1 — TABLES (PK, UNIQUE, CHECK, DEFAULT)
   ===================================================================== */

/* ---------- D01 Identity & Access + Ownership ---------- */

CREATE TABLE dbo.role (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    role_code       VARCHAR(30)    NOT NULL,
    display_name    NVARCHAR(100)  NOT NULL,
    created_at      DATETIME2(3)   NOT NULL CONSTRAINT df_role__created_at DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2(3)   NULL,
    CONSTRAINT pk_role PRIMARY KEY (id),
    CONSTRAINT uq_role__role_code UNIQUE (role_code),
    CONSTRAINT ck_role__role_code CHECK (role_code IN ('CLUB_MANAGER','HEAD_TRAINER','TRAINER','WORK_RIDER','VETERINARIAN','GROOM','HORSE_OWNER'))
);

CREATE TABLE dbo.app_user (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    user_code           VARCHAR(20)    NOT NULL,
    full_name           NVARCHAR(150)  NOT NULL,
    email               NVARCHAR(254)  NOT NULL,
    password_hash       VARCHAR(255)   NULL,
    account_status      VARCHAR(20)    NOT NULL,
    last_login_at       DATETIME2(3)   NULL,
    experience_years    SMALLINT       NULL,
    experience_level    NVARCHAR(50)   NULL,
    created_at          DATETIME2(3)   NOT NULL CONSTRAINT df_app_user__created_at DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(3)   NULL,
    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT uq_app_user__user_code UNIQUE (user_code),
    CONSTRAINT uq_app_user__email UNIQUE (email),
    CONSTRAINT ck_app_user__account_status CHECK (account_status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_app_user__experience_years CHECK (experience_years IS NULL OR experience_years >= 0)
);

CREATE TABLE dbo.user_role (
    id                    BIGINT IDENTITY(1,1) NOT NULL,
    user_id               BIGINT        NOT NULL,
    role_id               BIGINT        NOT NULL,
    assigned_by_user_id   BIGINT        NULL,
    assigned_at           DATETIME2(3)  NOT NULL,
    created_at            DATETIME2(3)  NOT NULL CONSTRAINT df_user_role__created_at DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2(3)  NULL,
    CONSTRAINT pk_user_role PRIMARY KEY (id),
    CONSTRAINT uq_user_role__user_id_role_id UNIQUE (user_id, role_id)
);

CREATE TABLE dbo.skill (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    name        NVARCHAR(100) NOT NULL,
    kind        VARCHAR(30)   NOT NULL,
    created_at  DATETIME2(3)  NOT NULL CONSTRAINT df_skill__created_at DEFAULT SYSUTCDATETIME(),
    updated_at  DATETIME2(3)  NULL,
    CONSTRAINT pk_skill PRIMARY KEY (id),
    CONSTRAINT uq_skill__kind_name UNIQUE (kind, name),
    CONSTRAINT ck_skill__kind CHECK (kind IN ('TRAINER_SPECIALIZATION','RIDER_SKILL'))
);

CREATE TABLE dbo.user_skill (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    user_id     BIGINT        NOT NULL,
    skill_id    BIGINT        NOT NULL,
    created_at  DATETIME2(3)  NOT NULL CONSTRAINT df_user_skill__created_at DEFAULT SYSUTCDATETIME(),
    updated_at  DATETIME2(3)  NULL,
    CONSTRAINT pk_user_skill PRIMARY KEY (id),
    CONSTRAINT uq_user_skill__user_id_skill_id UNIQUE (user_id, skill_id)
);

/* ---------- D02 Horse Profile + Stable ---------- */

CREATE TABLE dbo.horse (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    horse_code          VARCHAR(20)    NOT NULL,
    name                NVARCHAR(150)  NOT NULL,
    breed               NVARCHAR(100)  NULL,
    sex                 NVARCHAR(20)   NULL,
    foaled_date         DATE           NULL,
    country_of_origin   NVARCHAR(100)  NULL,
    registration_no     VARCHAR(50)    NULL,
    lifecycle           VARCHAR(20)    NOT NULL,
    health_status       VARCHAR(20)    NULL,
    sire_horse_id       BIGINT         NULL,
    sire_name           NVARCHAR(150)  NULL,
    dam_horse_id        BIGINT         NULL,
    dam_name            NVARCHAR(150)  NULL,
    activated_at        DATETIME2(3)   NULL,
    created_at          DATETIME2(3)   NOT NULL CONSTRAINT df_horse__created_at DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(3)   NULL,
    CONSTRAINT pk_horse PRIMARY KEY (id),
    CONSTRAINT uq_horse__horse_code UNIQUE (horse_code),
    CONSTRAINT ck_horse__lifecycle CHECK (lifecycle IN ('DRAFT','ACTIVE','INACTIVE')),
    CONSTRAINT ck_horse__health_status CHECK (health_status IS NULL OR health_status IN ('FIT','MONITORING','INJURED','QUARANTINED')),
    CONSTRAINT ck_horse__sire_ref_xor_name CHECK (NOT (sire_horse_id IS NOT NULL AND sire_name IS NOT NULL)),
    CONSTRAINT ck_horse__dam_ref_xor_name CHECK (NOT (dam_horse_id IS NOT NULL AND dam_name IS NOT NULL)),
    CONSTRAINT ck_horse__sire_not_self CHECK (sire_horse_id IS NULL OR sire_horse_id <> id),
    CONSTRAINT ck_horse__dam_not_self CHECK (dam_horse_id IS NULL OR dam_horse_id <> id),
    CONSTRAINT ck_horse__sire_not_dam CHECK (sire_horse_id IS NULL OR dam_horse_id IS NULL OR sire_horse_id <> dam_horse_id)
);

CREATE TABLE dbo.horse_ownership (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    horse_id            BIGINT        NOT NULL,
    owner_user_id       BIGINT        NOT NULL,
    share_percentage    DECIMAL(5,2)  NOT NULL,
    effective_from      DATE          NOT NULL,
    effective_to        DATE          NULL,
    is_managing_owner   BIT           NOT NULL CONSTRAINT df_horse_ownership__is_managing_owner DEFAULT 0,
    created_at          DATETIME2(3)  NOT NULL CONSTRAINT df_horse_ownership__created_at DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(3)  NULL,
    CONSTRAINT pk_horse_ownership PRIMARY KEY (id),
    CONSTRAINT ck_horse_ownership__share_percentage CHECK (share_percentage > 0 AND share_percentage <= 100),
    CONSTRAINT ck_horse_ownership__period CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE dbo.weight_record (
    id                    BIGINT IDENTITY(1,1) NOT NULL,
    horse_id              BIGINT        NOT NULL,
    recorded_by_user_id   BIGINT        NOT NULL,
    weight                DECIMAL(6,2)  NOT NULL,
    measured_at           DATETIME2(3)  NOT NULL,
    context               NVARCHAR(50)  NULL,
    created_at            DATETIME2(3)  NOT NULL CONSTRAINT df_weight_record__created_at DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2(3)  NULL,
    CONSTRAINT pk_weight_record PRIMARY KEY (id),
    CONSTRAINT ck_weight_record__weight CHECK (weight > 0)
);

CREATE TABLE dbo.horse_photo (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    horse_id        BIGINT         NOT NULL,
    file_reference  NVARCHAR(500)  NOT NULL,
    uploaded_at     DATETIME2(3)   NOT NULL,
    created_at      DATETIME2(3)   NOT NULL CONSTRAINT df_horse_photo__created_at DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2(3)   NULL,
    CONSTRAINT pk_horse_photo PRIMARY KEY (id)
);

CREATE TABLE dbo.stable_box (
    id          BIGINT IDENTITY(1,1) NOT NULL,
    box_code    VARCHAR(20)    NOT NULL,
    yard        NVARCHAR(100)  NULL,
    created_at  DATETIME2(3)   NOT NULL CONSTRAINT df_stable_box__created_at DEFAULT SYSUTCDATETIME(),
    updated_at  DATETIME2(3)   NULL,
    CONSTRAINT pk_stable_box PRIMARY KEY (id),
    CONSTRAINT uq_stable_box__box_code UNIQUE (box_code)
);

CREATE TABLE dbo.horse_stable_assignment (
    id                    BIGINT IDENTITY(1,1) NOT NULL,
    horse_id              BIGINT         NOT NULL,
    stable_box_id         BIGINT         NOT NULL,
    assigned_by_user_id   BIGINT         NOT NULL,
    effective_from        DATE           NOT NULL,
    effective_to          DATE           NULL,
    reason                NVARCHAR(500)  NULL,
    created_at            DATETIME2(3)   NOT NULL CONSTRAINT df_horse_stable_assignment__created_at DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2(3)   NULL,
    CONSTRAINT pk_horse_stable_assignment PRIMARY KEY (id),
    CONSTRAINT ck_horse_stable_assignment__period CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

/* ---------- D03 Stable Care + Nutrition ---------- */

CREATE TABLE dbo.care_plan (
    id                    BIGINT IDENTITY(1,1) NOT NULL,
    horse_id              BIGINT          NOT NULL,
    managed_by_user_id    BIGINT          NOT NULL,
    effective_from        DATE            NOT NULL,
    effective_to          DATE            NULL,
    notes                 NVARCHAR(2000)  NULL,
    created_at            DATETIME2(3)    NOT NULL CONSTRAINT df_care_plan__created_at DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2(3)    NULL,
    CONSTRAINT pk_care_plan PRIMARY KEY (id),
    CONSTRAINT ck_care_plan__period CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE dbo.care_plan_item (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    care_plan_id        BIGINT          NOT NULL,
    task_type           VARCHAR(30)     NOT NULL,
    time_slot           NVARCHAR(50)    NULL,
    frequency           NVARCHAR(100)   NULL,
    responsible_role    VARCHAR(30)     NULL,
    instructions        NVARCHAR(2000)  NULL,
    created_at          DATETIME2(3)    NOT NULL CONSTRAINT df_care_plan_item__created_at DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(3)    NULL,
    CONSTRAINT pk_care_plan_item PRIMARY KEY (id),
    CONSTRAINT ck_care_plan_item__task_type CHECK (task_type IN ('FEEDING','CLEANING','BATHING','LEG_SOAKING','OTHER')),
    CONSTRAINT ck_care_plan_item__responsible_role CHECK (responsible_role IS NULL OR responsible_role IN ('CLUB_MANAGER','HEAD_TRAINER','TRAINER','WORK_RIDER','VETERINARIAN','GROOM','HORSE_OWNER'))
);

CREATE TABLE dbo.nutrition_plan (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    horse_id                BIGINT          NOT NULL,
    maintained_by_user_id   BIGINT          NOT NULL,
    feeding_notes           NVARCHAR(2000)  NULL,
    effective_from          DATE            NOT NULL,
    created_at              DATETIME2(3)    NOT NULL CONSTRAINT df_nutrition_plan__created_at DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(3)    NULL,
    CONSTRAINT pk_nutrition_plan PRIMARY KEY (id)
);

CREATE TABLE dbo.nutrition_plan_item (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    nutrition_plan_id   BIGINT          NOT NULL,
    meal                NVARCHAR(50)    NOT NULL,
    component           VARCHAR(30)     NOT NULL,
    quantity            DECIMAL(10,3)   NOT NULL,
    unit                NVARCHAR(20)    NOT NULL,
    created_at          DATETIME2(3)    NOT NULL CONSTRAINT df_nutrition_plan_item__created_at DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(3)    NULL,
    CONSTRAINT pk_nutrition_plan_item PRIMARY KEY (id),
    CONSTRAINT ck_nutrition_plan_item__component CHECK (component IN ('HAY','GRAIN','VITAMINS_SUPPLEMENTS')),
    CONSTRAINT ck_nutrition_plan_item__quantity CHECK (quantity > 0)
);

CREATE TABLE dbo.care_task (
    id                        BIGINT IDENTITY(1,1) NOT NULL,
    horse_id                  BIGINT          NOT NULL,
    care_plan_item_id         BIGINT          NULL,
    nutrition_plan_item_id    BIGINT          NULL,
    treatment_plan_id         BIGINT          NULL,
    completed_by_user_id      BIGINT          NULL,
    task_date                 DATE            NOT NULL,
    scheduled_slot            NVARCHAR(50)    NULL,
    instruction_snapshot      NVARCHAR(2000)  NULL,
    completed_at              DATETIME2(3)    NULL,
    created_at                DATETIME2(3)    NOT NULL CONSTRAINT df_care_task__created_at DEFAULT SYSUTCDATETIME(),
    updated_at                DATETIME2(3)    NULL,
    CONSTRAINT pk_care_task PRIMARY KEY (id),
    CONSTRAINT ck_care_task__source_xor CHECK (
          (CASE WHEN care_plan_item_id      IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN nutrition_plan_item_id IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN treatment_plan_id      IS NOT NULL THEN 1 ELSE 0 END) = 1),
    CONSTRAINT ck_care_task__completion_pair CHECK (
           (completed_at IS NULL     AND completed_by_user_id IS NULL)
        OR (completed_at IS NOT NULL AND completed_by_user_id IS NOT NULL))
);

/* ---------- D04 Training Templates + Horse Programs ---------- */

CREATE TABLE dbo.training_program_template (
    id                            BIGINT IDENTITY(1,1) NOT NULL,
    template_code                 VARCHAR(20)   NOT NULL,
    status                        VARCHAR(20)   NOT NULL,
    duplicated_from_template_id   BIGINT        NULL,
    created_at                    DATETIME2(3)  NOT NULL CONSTRAINT df_training_program_template__created_at DEFAULT SYSUTCDATETIME(),
    updated_at                    DATETIME2(3)  NULL,
    CONSTRAINT pk_training_program_template PRIMARY KEY (id),
    CONSTRAINT uq_training_program_template__template_code UNIQUE (template_code),
    CONSTRAINT ck_training_program_template__status CHECK (status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT ck_training_program_template__not_self CHECK (duplicated_from_template_id IS NULL OR duplicated_from_template_id <> id)
);

/* Immutable snapshot: no updated_at. Edit Template -> new row with version_no + 1 (DEC-005 §7). */
CREATE TABLE dbo.template_version (
    id                              BIGINT IDENTITY(1,1) NOT NULL,
    training_program_template_id    BIGINT          NOT NULL,
    created_by_user_id              BIGINT          NULL,
    version_no                      INT             NOT NULL,
    name                            NVARCHAR(150)   NOT NULL,
    goals                           NVARCHAR(2000)  NULL,
    suitable_horse_profile          NVARCHAR(1000)  NULL,
    recommended_distance_use_case   NVARCHAR(500)   NULL,
    recommended_duration            NVARCHAR(100)   NULL,
    workload_intensity_guidance     NVARCHAR(1000)  NULL,
    preferred_surfaces              NVARCHAR(500)   NULL,
    recovery_guidance               NVARCHAR(1000)  NULL,
    created_at                      DATETIME2(3)    NOT NULL CONSTRAINT df_template_version__created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_template_version PRIMARY KEY (id),
    CONSTRAINT uq_template_version__template_id_version_no UNIQUE (training_program_template_id, version_no),
    CONSTRAINT ck_template_version__version_no CHECK (version_no >= 1)
);

/* Immutable with its version: no updated_at. */
CREATE TABLE dbo.template_version_phase (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    template_version_id     BIGINT          NOT NULL,
    phase_order             SMALLINT        NOT NULL,
    name                    NVARCHAR(150)   NOT NULL,
    objective               NVARCHAR(1000)  NULL,
    recommended_duration    NVARCHAR(100)   NULL,
    intensity_guidance      NVARCHAR(500)   NULL,
    created_at              DATETIME2(3)    NOT NULL CONSTRAINT df_template_version_phase__created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_template_version_phase PRIMARY KEY (id),
    CONSTRAINT uq_template_version_phase__version_id_phase_order UNIQUE (template_version_id, phase_order),
    CONSTRAINT ck_template_version_phase__phase_order CHECK (phase_order >= 1)
);

/* Immutable with its version: no updated_at. */
CREATE TABLE dbo.template_required_skill (
    id                    BIGINT IDENTITY(1,1) NOT NULL,
    template_version_id   BIGINT        NOT NULL,
    skill_id              BIGINT        NOT NULL,
    created_at            DATETIME2(3)  NOT NULL CONSTRAINT df_template_required_skill__created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_template_required_skill PRIMARY KEY (id),
    CONSTRAINT uq_template_required_skill__version_id_skill_id UNIQUE (template_version_id, skill_id)
);

CREATE TABLE dbo.horse_training_program (
    id                    BIGINT IDENTITY(1,1) NOT NULL,
    horse_id              BIGINT          NOT NULL,
    template_version_id   BIGINT          NOT NULL,
    target_race_id        BIGINT          NULL,
    created_by_user_id    BIGINT          NOT NULL,
    name                  NVARCHAR(150)   NOT NULL,
    goals                 NVARCHAR(2000)  NULL,
    target_metrics        NVARCHAR(1000)  NULL,
    period_start          DATE            NOT NULL,
    period_end            DATE            NOT NULL,
    status                VARCHAR(20)     NOT NULL,
    notes                 NVARCHAR(2000)  NULL,
    activated_at          DATETIME2(3)    NOT NULL,
    created_at            DATETIME2(3)    NOT NULL CONSTRAINT df_horse_training_program__created_at DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2(3)    NULL,
    CONSTRAINT pk_horse_training_program PRIMARY KEY (id),
    CONSTRAINT ck_horse_training_program__status CHECK (status IN ('ACTIVE','PAUSED','COMPLETED')),
    CONSTRAINT ck_horse_training_program__period CHECK (period_end >= period_start)
);

CREATE TABLE dbo.horse_program_phase (
    id                          BIGINT IDENTITY(1,1) NOT NULL,
    horse_training_program_id   BIGINT          NOT NULL,
    template_version_phase_id   BIGINT          NULL,
    phase_order                 SMALLINT        NOT NULL,
    name                        NVARCHAR(150)   NOT NULL,
    objective                   NVARCHAR(1000)  NULL,
    start_date                  DATE            NOT NULL,
    end_date                    DATE            NOT NULL,
    intensity_min               NVARCHAR(30)    NULL,
    intensity_max               NVARCHAR(30)    NULL,
    workload                    NVARCHAR(500)   NULL,
    is_customized               BIT             NOT NULL CONSTRAINT df_horse_program_phase__is_customized DEFAULT 0,
    created_at                  DATETIME2(3)    NOT NULL CONSTRAINT df_horse_program_phase__created_at DEFAULT SYSUTCDATETIME(),
    updated_at                  DATETIME2(3)    NULL,
    CONSTRAINT pk_horse_program_phase PRIMARY KEY (id),
    CONSTRAINT uq_horse_program_phase__program_id_phase_order UNIQUE (horse_training_program_id, phase_order),
    CONSTRAINT ck_horse_program_phase__phase_order CHECK (phase_order >= 1),
    CONSTRAINT ck_horse_program_phase__period CHECK (end_date >= start_date)
);

CREATE TABLE dbo.trainer_assignment (
    id                          BIGINT IDENTITY(1,1) NOT NULL,
    horse_training_program_id   BIGINT         NOT NULL,
    trainer_user_id             BIGINT         NOT NULL,
    assigned_by_user_id         BIGINT         NOT NULL,
    effective_from              DATE           NOT NULL,
    effective_to                DATE           NULL,
    reason                      NVARCHAR(500)  NULL,
    created_at                  DATETIME2(3)   NOT NULL CONSTRAINT df_trainer_assignment__created_at DEFAULT SYSUTCDATETIME(),
    updated_at                  DATETIME2(3)   NULL,
    CONSTRAINT pk_trainer_assignment PRIMARY KEY (id),
    CONSTRAINT ck_trainer_assignment__period CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE TABLE dbo.program_adjustment_request (
    id                          BIGINT IDENTITY(1,1) NOT NULL,
    horse_training_program_id   BIGINT          NOT NULL,
    horse_program_phase_id      BIGINT          NULL,
    requested_by_user_id        BIGINT          NOT NULL,
    reviewed_by_user_id         BIGINT          NULL,
    request_type                VARCHAR(40)     NOT NULL,
    current_value               NVARCHAR(1000)  NULL,
    requested_value             NVARCHAR(1000)  NOT NULL,
    reason                      NVARCHAR(2000)  NOT NULL,
    evidence_notes              NVARCHAR(2000)  NULL,
    status                      VARCHAR(20)     NOT NULL,
    requested_at                DATETIME2(3)    NOT NULL,
    reviewed_at                 DATETIME2(3)    NULL,
    decision_reason             NVARCHAR(2000)  NULL,
    created_at                  DATETIME2(3)    NOT NULL CONSTRAINT df_program_adjustment_request__created_at DEFAULT SYSUTCDATETIME(),
    updated_at                  DATETIME2(3)    NULL,
    CONSTRAINT pk_program_adjustment_request PRIMARY KEY (id),
    CONSTRAINT ck_program_adjustment_request__request_type CHECK (request_type IN (
        'SOURCE_TEMPLATE','PRIMARY_GOAL','TARGET_RACE','PHASE_STRUCTURE','PHASE_OBJECTIVE',
        'PHASE_WORKLOAD_INTENSITY','PHASE_TIMELINE','TOTAL_DURATION','OTHER_STRUCTURAL')),
    CONSTRAINT ck_program_adjustment_request__status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT ck_program_adjustment_request__review_state CHECK (
           (status = 'PENDING' AND reviewed_at IS NULL AND reviewed_by_user_id IS NULL)
        OR (status IN ('APPROVED','REJECTED') AND reviewed_at IS NOT NULL AND reviewed_by_user_id IS NOT NULL))
);

/* ---------- D05 Training Sessions + Realtime Monitoring ---------- */

CREATE TABLE dbo.exercise_definition (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    name            NVARCHAR(150)   NOT NULL,
    exercise_type   NVARCHAR(50)    NULL,
    description     NVARCHAR(2000)  NULL,
    target_metric   NVARCHAR(100)   NULL,
    created_at      DATETIME2(3)    NOT NULL CONSTRAINT df_exercise_definition__created_at DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2(3)    NULL,
    CONSTRAINT pk_exercise_definition PRIMARY KEY (id)
);

CREATE TABLE dbo.training_session (
    id                        BIGINT IDENTITY(1,1) NOT NULL,
    session_code              VARCHAR(20)     NOT NULL,
    horse_program_phase_id    BIGINT          NOT NULL,
    created_by_user_id        BIGINT          NOT NULL,
    work_rider_user_id        BIGINT          NOT NULL,
    scheduled_start           DATETIME2(3)    NOT NULL,
    scheduled_end             DATETIME2(3)    NULL,
    surface                   NVARCHAR(50)    NULL,
    focus                     NVARCHAR(200)   NULL,
    intensity                 NVARCHAR(30)    NULL,
    status                    VARCHAR(20)     NOT NULL,
    actual_start              DATETIME2(3)    NULL,
    actual_end                DATETIME2(3)    NULL,
    interruption_reason       NVARCHAR(1000)  NULL,
    created_at                DATETIME2(3)    NOT NULL CONSTRAINT df_training_session__created_at DEFAULT SYSUTCDATETIME(),
    updated_at                DATETIME2(3)    NULL,
    CONSTRAINT pk_training_session PRIMARY KEY (id),
    CONSTRAINT uq_training_session__session_code UNIQUE (session_code),
    CONSTRAINT ck_training_session__status CHECK (status IN ('SCHEDULED','IN_PROGRESS','PAUSED','COMPLETED','INTERRUPTED')),
    CONSTRAINT ck_training_session__scheduled_period CHECK (scheduled_end IS NULL OR scheduled_end >= scheduled_start),
    CONSTRAINT ck_training_session__actual_period CHECK (actual_end IS NULL OR (actual_start IS NOT NULL AND actual_end >= actual_start)),
    CONSTRAINT ck_training_session__interruption_reason CHECK (status <> 'INTERRUPTED' OR interruption_reason IS NOT NULL)
);

CREATE TABLE dbo.session_exercise (
    id                        BIGINT IDENTITY(1,1) NOT NULL,
    training_session_id       BIGINT          NOT NULL,
    exercise_definition_id    BIGINT          NOT NULL,
    exercise_order            SMALLINT        NOT NULL,
    distance                  DECIMAL(9,2)    NULL,
    duration                  DECIMAL(7,2)    NULL,
    repetitions               SMALLINT        NULL,
    rest_interval             DECIMAL(7,2)    NULL,
    intensity                 NVARCHAR(30)    NULL,
    surface                   NVARCHAR(50)    NULL,
    suitability_category      CHAR(1)         NULL,
    acknowledgement_note      NVARCHAR(1000)  NULL,
    created_at                DATETIME2(3)    NOT NULL CONSTRAINT df_session_exercise__created_at DEFAULT SYSUTCDATETIME(),
    updated_at                DATETIME2(3)    NULL,
    CONSTRAINT pk_session_exercise PRIMARY KEY (id),
    CONSTRAINT uq_session_exercise__session_id_exercise_order UNIQUE (training_session_id, exercise_order),
    CONSTRAINT ck_session_exercise__exercise_order CHECK (exercise_order >= 1),
    CONSTRAINT ck_session_exercise__non_negative CHECK (
            (distance IS NULL OR distance >= 0) AND (duration IS NULL OR duration >= 0)
        AND (repetitions IS NULL OR repetitions >= 0) AND (rest_interval IS NULL OR rest_interval >= 0)),
    CONSTRAINT ck_session_exercise__suitability_category CHECK (suitability_category IS NULL OR suitability_category IN ('A','B','C','D')),
    CONSTRAINT ck_session_exercise__no_ack_for_category_a CHECK (suitability_category IS NULL OR suitability_category <> 'A' OR acknowledgement_note IS NULL)
);

CREATE TABLE dbo.pre_session_check (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    training_session_id     BIGINT          NOT NULL,
    performed_by_user_id    BIGINT          NOT NULL,
    performed_at            DATETIME2(3)    NOT NULL,
    observations            NVARCHAR(4000)  NULL,
    overall_result          VARCHAR(20)     NOT NULL,
    created_at              DATETIME2(3)    NOT NULL CONSTRAINT df_pre_session_check__created_at DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(3)    NULL,
    CONSTRAINT pk_pre_session_check PRIMARY KEY (id),
    CONSTRAINT uq_pre_session_check__training_session_id UNIQUE (training_session_id),
    CONSTRAINT ck_pre_session_check__overall_result CHECK (overall_result IN ('ALL_NORMAL','ABNORMAL'))
);

/* Append-only telemetry: no updated_at. */
CREATE TABLE dbo.training_metric_sample (
    id                    BIGINT IDENTITY(1,1) NOT NULL,
    training_session_id   BIGINT        NOT NULL,
    recorded_at           DATETIME2(3)  NOT NULL,
    heart_rate            SMALLINT      NULL,
    speed                 DECIMAL(6,2)  NULL,
    distance              DECIMAL(9,2)  NULL,
    source                VARCHAR(20)   NOT NULL,
    created_at            DATETIME2(3)  NOT NULL CONSTRAINT df_training_metric_sample__created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_training_metric_sample PRIMARY KEY (id),
    CONSTRAINT ck_training_metric_sample__source CHECK (source IN ('MANUAL','SIMULATOR')),
    CONSTRAINT ck_training_metric_sample__has_value CHECK (heart_rate IS NOT NULL OR speed IS NOT NULL OR distance IS NOT NULL),
    CONSTRAINT ck_training_metric_sample__non_negative CHECK (
        (heart_rate IS NULL OR heart_rate >= 0) AND (speed IS NULL OR speed >= 0) AND (distance IS NULL OR distance >= 0))
);

CREATE TABLE dbo.monitoring_alert (
    id                        BIGINT IDENTITY(1,1) NOT NULL,
    training_session_id       BIGINT        NOT NULL,
    acknowledged_by_user_id   BIGINT        NULL,
    metric                    VARCHAR(20)   NOT NULL,
    metric_value              DECIMAL(7,2)  NOT NULL,
    threshold_applied         DECIMAL(7,2)  NOT NULL,
    severity                  VARCHAR(20)   NOT NULL,
    raised_at                 DATETIME2(3)  NOT NULL,
    acknowledged_at           DATETIME2(3)  NULL,
    action_taken              VARCHAR(30)   NULL,
    escalated_to_vet_at       DATETIME2(3)  NULL,
    created_at                DATETIME2(3)  NOT NULL CONSTRAINT df_monitoring_alert__created_at DEFAULT SYSUTCDATETIME(),
    updated_at                DATETIME2(3)  NULL,
    CONSTRAINT pk_monitoring_alert PRIMARY KEY (id),
    CONSTRAINT ck_monitoring_alert__metric CHECK (metric IN ('HEART_RATE')),
    CONSTRAINT ck_monitoring_alert__severity CHECK (severity IN ('CRITICAL')),
    CONSTRAINT ck_monitoring_alert__action_taken CHECK (action_taken IS NULL OR action_taken IN ('ACKNOWLEDGE','REDUCE_INTENSITY','PAUSE','STOP')),
    CONSTRAINT ck_monitoring_alert__ack_pair CHECK (
           (acknowledged_at IS NULL     AND acknowledged_by_user_id IS NULL)
        OR (acknowledged_at IS NOT NULL AND acknowledged_by_user_id IS NOT NULL)),
    CONSTRAINT ck_monitoring_alert__action_after_ack CHECK (action_taken IS NULL OR acknowledged_at IS NOT NULL)
);

CREATE TABLE dbo.session_result (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    training_session_id     BIGINT          NOT NULL,
    evaluated_by_user_id    BIGINT          NOT NULL,
    evaluated_summary       NVARCHAR(2000)  NULL,
    evaluation_label        NVARCHAR(50)    NULL,
    evaluation_notes        NVARCHAR(2000)  NULL,
    recommendation          NVARCHAR(2000)  NULL,
    evaluated_at            DATETIME2(3)    NOT NULL,
    created_at              DATETIME2(3)    NOT NULL CONSTRAINT df_session_result__created_at DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(3)    NULL,
    CONSTRAINT pk_session_result PRIMARY KEY (id),
    CONSTRAINT uq_session_result__training_session_id UNIQUE (training_session_id)
);

CREATE TABLE dbo.rider_feedback (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    training_session_id     BIGINT          NOT NULL,
    submitted_by_user_id    BIGINT          NOT NULL,
    submitted_at            DATETIME2(3)    NOT NULL,
    responses               NVARCHAR(4000)  NOT NULL,
    created_at              DATETIME2(3)    NOT NULL CONSTRAINT df_rider_feedback__created_at DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(3)    NULL,
    CONSTRAINT pk_rider_feedback PRIMARY KEY (id),
    CONSTRAINT uq_rider_feedback__training_session_id UNIQUE (training_session_id)
);

/* ---------- D06 Medical & Health ---------- */

CREATE TABLE dbo.medical_case (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    horse_id            BIGINT          NOT NULL,
    opened_by_user_id   BIGINT          NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    opened_at           DATETIME2(3)    NOT NULL,
    closed_at           DATETIME2(3)    NULL,
    condition_summary   NVARCHAR(2000)  NULL,
    created_at          DATETIME2(3)    NOT NULL CONSTRAINT df_medical_case__created_at DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(3)    NULL,
    CONSTRAINT pk_medical_case PRIMARY KEY (id),
    CONSTRAINT ck_medical_case__status CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT ck_medical_case__closed_state CHECK (
           (status = 'OPEN'   AND closed_at IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL AND closed_at >= opened_at))
);

CREATE TABLE dbo.veterinary_examination (
    id                              BIGINT IDENTITY(1,1) NOT NULL,
    horse_id                        BIGINT          NOT NULL,
    medical_case_id                 BIGINT          NULL,
    performed_by_user_id            BIGINT          NOT NULL,
    changed_medical_restriction_id  BIGINT          NULL,
    exam_type                       VARCHAR(20)     NOT NULL,
    examined_at                     DATETIME2(3)    NOT NULL,
    findings                        NVARCHAR(MAX)   NULL,
    diagnosis                       NVARCHAR(1000)  NULL,
    clinical_severity               VARCHAR(20)     NULL,
    recovery_outcome                VARCHAR(20)     NULL,
    body_condition                  NVARCHAR(500)   NULL,
    health_status_set               VARCHAR(20)     NULL,
    created_at                      DATETIME2(3)    NOT NULL CONSTRAINT df_veterinary_examination__created_at DEFAULT SYSUTCDATETIME(),
    updated_at                      DATETIME2(3)    NULL,
    CONSTRAINT pk_veterinary_examination PRIMARY KEY (id),
    CONSTRAINT ck_veterinary_examination__exam_type CHECK (exam_type IN ('BASELINE','EXAMINATION','RE_EXAMINATION')),
    CONSTRAINT ck_veterinary_examination__case_unless_baseline CHECK (
           (exam_type = 'BASELINE'  AND medical_case_id IS NULL)
        OR (exam_type <> 'BASELINE' AND medical_case_id IS NOT NULL)),
    CONSTRAINT ck_veterinary_examination__clinical_severity CHECK (clinical_severity IS NULL OR clinical_severity IN ('MILD','MODERATE','SEVERE')),
    CONSTRAINT ck_veterinary_examination__recovery_outcome CHECK (
        recovery_outcome IS NULL OR (exam_type = 'RE_EXAMINATION' AND recovery_outcome IN ('NOT_RECOVERED','IMPROVING','RECOVERED'))),
    CONSTRAINT ck_veterinary_examination__health_status_set CHECK (health_status_set IS NULL OR health_status_set IN ('FIT','MONITORING','INJURED','QUARANTINED')),
    CONSTRAINT ck_veterinary_examination__restriction_change_on_reexam CHECK (changed_medical_restriction_id IS NULL OR exam_type = 'RE_EXAMINATION')
);

CREATE TABLE dbo.injury (
    id                          BIGINT IDENTITY(1,1) NOT NULL,
    medical_case_id             BIGINT         NOT NULL,
    veterinary_examination_id   BIGINT         NULL,
    body_region                 NVARCHAR(100)  NOT NULL,
    position_3d                 VARCHAR(100)   NULL,
    side                        NVARCHAR(20)   NULL,
    severity                    VARCHAR(20)    NOT NULL,
    recovery_status             VARCHAR(20)    NOT NULL,
    created_at                  DATETIME2(3)   NOT NULL CONSTRAINT df_injury__created_at DEFAULT SYSUTCDATETIME(),
    updated_at                  DATETIME2(3)   NULL,
    CONSTRAINT pk_injury PRIMARY KEY (id),
    CONSTRAINT ck_injury__severity CHECK (severity IN ('MILD','MODERATE','SEVERE')),
    CONSTRAINT ck_injury__recovery_status CHECK (recovery_status IN ('NOT_RECOVERED','IMPROVING','RECOVERED'))
);

CREATE TABLE dbo.treatment_plan (
    id                                  BIGINT IDENTITY(1,1) NOT NULL,
    medical_case_id                     BIGINT          NOT NULL,
    created_by_user_id                  BIGINT          NOT NULL,
    treatment_medication_instructions   NVARCHAR(MAX)   NOT NULL,
    rest_period                         NVARCHAR(100)   NULL,
    care_instructions                   NVARCHAR(2000)  NULL,
    re_examination_date                 DATE            NULL,
    created_at                          DATETIME2(3)    NOT NULL CONSTRAINT df_treatment_plan__created_at DEFAULT SYSUTCDATETIME(),
    updated_at                          DATETIME2(3)    NULL,
    CONSTRAINT pk_treatment_plan PRIMARY KEY (id),
    CONSTRAINT uq_treatment_plan__medical_case_id UNIQUE (medical_case_id)
);

CREATE TABLE dbo.medical_restriction (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    horse_id            BIGINT          NOT NULL,
    medical_case_id     BIGINT          NULL,
    set_by_user_id      BIGINT          NOT NULL,
    restriction_level   VARCHAR(20)     NOT NULL,
    reason              NVARCHAR(1000)  NOT NULL,
    effective_from      DATETIME2(3)    NOT NULL,
    lifted_at           DATETIME2(3)    NULL,
    review_date         DATE            NULL,
    created_at          DATETIME2(3)    NOT NULL CONSTRAINT df_medical_restriction__created_at DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(3)    NULL,
    CONSTRAINT pk_medical_restriction PRIMARY KEY (id),
    CONSTRAINT ck_medical_restriction__restriction_level CHECK (restriction_level IN ('LIMITED','LOCKED')),
    CONSTRAINT ck_medical_restriction__period CHECK (lifted_at IS NULL OR lifted_at >= effective_from)
);

CREATE TABLE dbo.restriction_exercise_rule (
    id                        BIGINT IDENTITY(1,1) NOT NULL,
    medical_restriction_id    BIGINT         NOT NULL,
    exercise_definition_id    BIGINT         NULL,
    rule_type                 VARCHAR(20)    NOT NULL,
    exercise_type_note        NVARCHAR(200)  NULL,
    created_at                DATETIME2(3)   NOT NULL CONSTRAINT df_restriction_exercise_rule__created_at DEFAULT SYSUTCDATETIME(),
    updated_at                DATETIME2(3)   NULL,
    CONSTRAINT pk_restriction_exercise_rule PRIMARY KEY (id),
    CONSTRAINT ck_restriction_exercise_rule__rule_type CHECK (rule_type IN ('ALLOWED','PROHIBITED')),
    CONSTRAINT ck_restriction_exercise_rule__target CHECK (exercise_definition_id IS NOT NULL OR exercise_type_note IS NOT NULL)
);

CREATE TABLE dbo.horse_threshold_override (
    id                BIGINT IDENTITY(1,1) NOT NULL,
    horse_id          BIGINT          NOT NULL,
    set_by_user_id    BIGINT          NOT NULL,
    metric            VARCHAR(20)     NOT NULL,
    critical_value    DECIMAL(7,2)    NOT NULL,
    reason            NVARCHAR(1000)  NULL,
    effective_from    DATETIME2(3)    NOT NULL,
    effective_to      DATETIME2(3)    NULL,
    created_at        DATETIME2(3)    NOT NULL CONSTRAINT df_horse_threshold_override__created_at DEFAULT SYSUTCDATETIME(),
    updated_at        DATETIME2(3)    NULL,
    CONSTRAINT pk_horse_threshold_override PRIMARY KEY (id),
    CONSTRAINT ck_horse_threshold_override__metric CHECK (metric IN ('HEART_RATE')),
    CONSTRAINT ck_horse_threshold_override__critical_value CHECK (critical_value > 0),
    CONSTRAINT ck_horse_threshold_override__period CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

/* ---------- D07 Incident + Preventive Healthcare ---------- */

CREATE TABLE dbo.incident (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    incident_code           VARCHAR(20)     NOT NULL,
    horse_id                BIGINT          NOT NULL,
    reported_by_user_id     BIGINT          NOT NULL,
    reviewed_by_user_id     BIGINT          NULL,
    medical_case_id         BIGINT          NULL,
    pre_session_check_id    BIGINT          NULL,
    training_session_id     BIGINT          NULL,
    care_task_id            BIGINT          NULL,
    observed_at             DATETIME2(3)    NOT NULL,
    reported_at             DATETIME2(3)    NOT NULL,
    incident_type           VARCHAR(30)     NOT NULL,
    observed_severity       VARCHAR(20)     NOT NULL,
    description             NVARCHAR(2000)  NULL,
    status                  VARCHAR(20)     NOT NULL,
    created_at              DATETIME2(3)    NOT NULL CONSTRAINT df_incident__created_at DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(3)    NULL,
    CONSTRAINT pk_incident PRIMARY KEY (id),
    CONSTRAINT uq_incident__incident_code UNIQUE (incident_code),
    CONSTRAINT ck_incident__incident_type CHECK (incident_type IN ('OFF_FEED','COLIC_SIGNS','FEVER','HOOF_OR_LEG_PROBLEM','OTHER')),
    CONSTRAINT ck_incident__observed_severity CHECK (observed_severity IN ('LOW','MEDIUM','HIGH')),
    CONSTRAINT ck_incident__status CHECK (status IN ('REPORTED','UNDER_REVIEW','RESOLVED')),
    CONSTRAINT ck_incident__single_source_context CHECK (
          (CASE WHEN pre_session_check_id IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN training_session_id  IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN care_task_id         IS NOT NULL THEN 1 ELSE 0 END) <= 1)
);

CREATE TABLE dbo.incident_photo (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    incident_id     BIGINT         NOT NULL,
    file_reference  NVARCHAR(500)  NOT NULL,
    uploaded_at     DATETIME2(3)   NOT NULL,
    created_at      DATETIME2(3)   NOT NULL CONSTRAINT df_incident_photo__created_at DEFAULT SYSUTCDATETIME(),
    updated_at      DATETIME2(3)   NULL,
    CONSTRAINT pk_incident_photo PRIMARY KEY (id)
);

CREATE TABLE dbo.preventive_task (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    horse_id                BIGINT          NOT NULL,
    responsible_user_id     BIGINT          NULL,
    completed_by_user_id    BIGINT          NULL,
    medical_case_id         BIGINT          NULL,
    task_type               VARCHAR(20)     NOT NULL,
    due_date                DATE            NOT NULL,
    external_provider       NVARCHAR(150)   NULL,
    notes                   NVARCHAR(2000)  NULL,
    completed_at            DATETIME2(3)    NULL,
    next_due_entered        DATE            NULL,
    created_at              DATETIME2(3)    NOT NULL CONSTRAINT df_preventive_task__created_at DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(3)    NULL,
    CONSTRAINT pk_preventive_task PRIMARY KEY (id),
    CONSTRAINT ck_preventive_task__task_type CHECK (task_type IN ('VACCINATION','DEWORMING','FARRIER','RE_EXAMINATION')),
    CONSTRAINT ck_preventive_task__completion_pair CHECK (
           (completed_at IS NULL     AND completed_by_user_id IS NULL)
        OR (completed_at IS NOT NULL AND completed_by_user_id IS NOT NULL)),
    CONSTRAINT ck_preventive_task__case_only_for_reexam CHECK (medical_case_id IS NULL OR task_type = 'RE_EXAMINATION')
);

/* ---------- D08 Race (MF-14 OPTIONAL) + Finance (MF-16 OPTIONAL) ---------- */

CREATE TABLE dbo.race (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    race_code               VARCHAR(20)     NOT NULL,
    created_by_user_id      BIGINT          NOT NULL,
    name                    NVARCHAR(200)   NOT NULL,
    race_date               DATE            NOT NULL,
    distance                INT             NULL,
    venue                   NVARCHAR(200)   NULL,
    entry_conditions_note   NVARCHAR(2000)  NULL,
    created_at              DATETIME2(3)    NOT NULL CONSTRAINT df_race__created_at DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(3)    NULL,
    CONSTRAINT pk_race PRIMARY KEY (id),
    CONSTRAINT uq_race__race_code UNIQUE (race_code),
    CONSTRAINT ck_race__distance CHECK (distance IS NULL OR distance > 0)
);

CREATE TABLE dbo.race_registration (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    race_id                 BIGINT          NOT NULL,
    horse_id                BIGINT          NOT NULL,
    registered_by_user_id   BIGINT          NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    registered_at           DATETIME2(3)    NOT NULL,
    withdrawn_at            DATETIME2(3)    NULL,
    eligibility_snapshot    NVARCHAR(1000)  NULL,
    created_at              DATETIME2(3)    NOT NULL CONSTRAINT df_race_registration__created_at DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(3)    NULL,
    CONSTRAINT pk_race_registration PRIMARY KEY (id),
    CONSTRAINT ck_race_registration__status CHECK (status IN ('REGISTERED','WITHDRAWN','RESULT_RECORDED')),
    CONSTRAINT ck_race_registration__withdrawn_state CHECK (
           (status = 'WITHDRAWN'  AND withdrawn_at IS NOT NULL)
        OR (status <> 'WITHDRAWN' AND withdrawn_at IS NULL))
);

CREATE TABLE dbo.race_result (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    race_registration_id    BIGINT          NOT NULL,
    recorded_by_user_id     BIGINT          NOT NULL,
    result_summary          NVARCHAR(1000)  NULL,
    placing                 SMALLINT        NULL,
    finishing_time          VARCHAR(20)     NULL,
    prize_amount            DECIMAL(19,4)   NULL,
    prize_currency_code     CHAR(3)         NULL,
    notes                   NVARCHAR(2000)  NULL,
    recorded_at             DATETIME2(3)    NOT NULL,
    created_at              DATETIME2(3)    NOT NULL CONSTRAINT df_race_result__created_at DEFAULT SYSUTCDATETIME(),
    updated_at              DATETIME2(3)    NULL,
    CONSTRAINT pk_race_result PRIMARY KEY (id),
    CONSTRAINT uq_race_result__race_registration_id UNIQUE (race_registration_id),
    CONSTRAINT ck_race_result__placing CHECK (placing IS NULL OR placing >= 1),
    CONSTRAINT ck_race_result__prize_amount CHECK (prize_amount IS NULL OR prize_amount >= 0),
    CONSTRAINT ck_race_result__prize_pair CHECK (
           (prize_amount IS NULL     AND prize_currency_code IS NULL)
        OR (prize_amount IS NOT NULL AND prize_currency_code IS NOT NULL)),
    CONSTRAINT ck_race_result__prize_currency_code CHECK (prize_currency_code IS NULL OR prize_currency_code COLLATE Latin1_General_BIN LIKE '[A-Z][A-Z][A-Z]')
);

CREATE TABLE dbo.cost_record (
    id                    BIGINT IDENTITY(1,1) NOT NULL,
    horse_id              BIGINT          NULL,
    created_by_user_id    BIGINT          NOT NULL,
    cost_date             DATE            NOT NULL,
    category              VARCHAR(20)     NOT NULL,
    amount                DECIMAL(19,4)   NOT NULL,
    currency_code         CHAR(3)         NOT NULL,
    note                  NVARCHAR(1000)  NULL,
    created_at            DATETIME2(3)    NOT NULL CONSTRAINT df_cost_record__created_at DEFAULT SYSUTCDATETIME(),
    updated_at            DATETIME2(3)    NULL,
    CONSTRAINT pk_cost_record PRIMARY KEY (id),
    CONSTRAINT ck_cost_record__category CHECK (category IN ('HUSBANDRY','MEDICAL','OPERATING')),
    CONSTRAINT ck_cost_record__amount CHECK (amount >= 0),
    CONSTRAINT ck_cost_record__currency_code CHECK (currency_code COLLATE Latin1_General_BIN LIKE '[A-Z][A-Z][A-Z]')
);

/* ---------- D09 Inventory (MF-13 OPTIONAL) ---------- */

CREATE TABLE dbo.inventory_item (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    name                NVARCHAR(150)   NOT NULL,
    category            VARCHAR(20)     NOT NULL,
    unit                NVARCHAR(20)    NOT NULL,
    threshold           DECIMAL(12,3)   NULL,
    current_quantity    DECIMAL(12,3)   NOT NULL CONSTRAINT df_inventory_item__current_quantity DEFAULT 0,
    is_active           BIT             NOT NULL CONSTRAINT df_inventory_item__is_active DEFAULT 1,
    created_at          DATETIME2(3)    NOT NULL CONSTRAINT df_inventory_item__created_at DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(3)    NULL,
    CONSTRAINT pk_inventory_item PRIMARY KEY (id),
    CONSTRAINT ck_inventory_item__category CHECK (category IN ('FEED','MEDICINE','MEDICAL_SUPPLY','TOOL')),
    CONSTRAINT ck_inventory_item__threshold CHECK (threshold IS NULL OR threshold >= 0),
    CONSTRAINT ck_inventory_item__current_quantity CHECK (current_quantity >= 0)
);

/* Append-only history: no updated_at. */
CREATE TABLE dbo.stock_movement (
    id                    BIGINT IDENTITY(1,1) NOT NULL,
    inventory_item_id     BIGINT         NOT NULL,
    recorded_by_user_id   BIGINT         NOT NULL,
    change_quantity       DECIMAL(12,3)  NOT NULL,
    note                  NVARCHAR(500)  NULL,
    recorded_at           DATETIME2(3)   NOT NULL,
    created_at            DATETIME2(3)   NOT NULL CONSTRAINT df_stock_movement__created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_stock_movement PRIMARY KEY (id),
    CONSTRAINT ck_stock_movement__change_quantity CHECK (change_quantity <> 0)
);

/* No lifecycle (D-ERD-07): the row itself means "submitted"; no updated_at. */
CREATE TABLE dbo.replenishment_request (
    id                      BIGINT IDENTITY(1,1) NOT NULL,
    inventory_item_id       BIGINT          NOT NULL,
    requested_by_user_id    BIGINT          NOT NULL,
    quantity                DECIMAL(12,3)   NOT NULL,
    reason                  NVARCHAR(1000)  NULL,
    submitted_at            DATETIME2(3)    NOT NULL,
    created_at              DATETIME2(3)    NOT NULL CONSTRAINT df_replenishment_request__created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_replenishment_request PRIMARY KEY (id),
    CONSTRAINT ck_replenishment_request__quantity CHECK (quantity > 0)
);

/* ---------- D10 Notification + Audit ---------- */

CREATE TABLE dbo.notification (
    id                  BIGINT IDENTITY(1,1) NOT NULL,
    recipient_user_id   BIGINT          NOT NULL,
    category            VARCHAR(20)     NOT NULL,
    notification_type   VARCHAR(50)     NOT NULL,
    title               NVARCHAR(200)   NOT NULL,
    message             NVARCHAR(1000)  NOT NULL,
    read_at             DATETIME2(3)    NULL,
    target_type         VARCHAR(50)     NULL,
    target_id           BIGINT          NULL,
    is_simulated        BIT             NOT NULL CONSTRAINT df_notification__is_simulated DEFAULT 0,
    created_at          DATETIME2(3)    NOT NULL CONSTRAINT df_notification__created_at DEFAULT SYSUTCDATETIME(),
    updated_at          DATETIME2(3)    NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id),
    CONSTRAINT ck_notification__category CHECK (category IN ('ALERTS','MEDICAL','SCHEDULE')),
    CONSTRAINT ck_notification__target_pair CHECK (
           (target_type IS NULL     AND target_id IS NULL)
        OR (target_type IS NOT NULL AND target_id IS NOT NULL))
);

/* Append-only (application never updates or deletes). Never stores password, hash, JWT, refresh token or secret. */
CREATE TABLE dbo.audit_log (
    id              BIGINT IDENTITY(1,1) NOT NULL,
    actor_user_id   BIGINT          NULL,
    occurred_at     DATETIME2(3)    NOT NULL CONSTRAINT df_audit_log__occurred_at DEFAULT SYSUTCDATETIME(),
    actor_role      VARCHAR(30)     NULL,
    action          VARCHAR(100)    NOT NULL,
    target_type     VARCHAR(50)     NULL,
    target_id       BIGINT          NULL,
    safe_summary    NVARCHAR(MAX)   NULL,
    source          VARCHAR(30)     NULL,
    created_at      DATETIME2(3)    NOT NULL CONSTRAINT df_audit_log__created_at DEFAULT SYSUTCDATETIME(),
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT ck_audit_log__actor_role CHECK (actor_role IS NULL OR actor_role IN ('CLUB_MANAGER','HEAD_TRAINER','TRAINER','WORK_RIDER','VETERINARIAN','GROOM','HORSE_OWNER')),
    CONSTRAINT ck_audit_log__target_pair CHECK (
           (target_type IS NULL     AND target_id IS NULL)
        OR (target_type IS NOT NULL AND target_id IS NOT NULL))
);

/* =====================================================================
   PART 2 — FOREIGN KEYS (102 logical relationships -> 102 FK constraints)
   All ON DELETE NO ACTION ON UPDATE NO ACTION: business rows are deactivated /
   closed / archived, never hard-deleted (BR-D07); IDENTITY keys never change.
   ===================================================================== */

-- D01
ALTER TABLE dbo.user_role ADD CONSTRAINT fk_user_role__user_id FOREIGN KEY (user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.user_role ADD CONSTRAINT fk_user_role__role_id FOREIGN KEY (role_id) REFERENCES dbo.role (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.user_role ADD CONSTRAINT fk_user_role__assigned_by_user_id FOREIGN KEY (assigned_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.user_skill ADD CONSTRAINT fk_user_skill__user_id FOREIGN KEY (user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.user_skill ADD CONSTRAINT fk_user_skill__skill_id FOREIGN KEY (skill_id) REFERENCES dbo.skill (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_ownership ADD CONSTRAINT fk_horse_ownership__owner_user_id FOREIGN KEY (owner_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_ownership ADD CONSTRAINT fk_horse_ownership__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- D02
ALTER TABLE dbo.horse ADD CONSTRAINT fk_horse__sire_horse_id FOREIGN KEY (sire_horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse ADD CONSTRAINT fk_horse__dam_horse_id FOREIGN KEY (dam_horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.weight_record ADD CONSTRAINT fk_weight_record__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.weight_record ADD CONSTRAINT fk_weight_record__recorded_by_user_id FOREIGN KEY (recorded_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_photo ADD CONSTRAINT fk_horse_photo__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_stable_assignment ADD CONSTRAINT fk_horse_stable_assignment__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_stable_assignment ADD CONSTRAINT fk_horse_stable_assignment__stable_box_id FOREIGN KEY (stable_box_id) REFERENCES dbo.stable_box (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_stable_assignment ADD CONSTRAINT fk_horse_stable_assignment__assigned_by_user_id FOREIGN KEY (assigned_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- D03
ALTER TABLE dbo.care_plan ADD CONSTRAINT fk_care_plan__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.care_plan ADD CONSTRAINT fk_care_plan__managed_by_user_id FOREIGN KEY (managed_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.care_plan_item ADD CONSTRAINT fk_care_plan_item__care_plan_id FOREIGN KEY (care_plan_id) REFERENCES dbo.care_plan (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.nutrition_plan ADD CONSTRAINT fk_nutrition_plan__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.nutrition_plan ADD CONSTRAINT fk_nutrition_plan__maintained_by_user_id FOREIGN KEY (maintained_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.nutrition_plan_item ADD CONSTRAINT fk_nutrition_plan_item__nutrition_plan_id FOREIGN KEY (nutrition_plan_id) REFERENCES dbo.nutrition_plan (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.care_task ADD CONSTRAINT fk_care_task__care_plan_item_id FOREIGN KEY (care_plan_item_id) REFERENCES dbo.care_plan_item (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.care_task ADD CONSTRAINT fk_care_task__nutrition_plan_item_id FOREIGN KEY (nutrition_plan_item_id) REFERENCES dbo.nutrition_plan_item (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.care_task ADD CONSTRAINT fk_care_task__treatment_plan_id FOREIGN KEY (treatment_plan_id) REFERENCES dbo.treatment_plan (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.care_task ADD CONSTRAINT fk_care_task__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.care_task ADD CONSTRAINT fk_care_task__completed_by_user_id FOREIGN KEY (completed_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- D04
ALTER TABLE dbo.template_version ADD CONSTRAINT fk_template_version__training_program_template_id FOREIGN KEY (training_program_template_id) REFERENCES dbo.training_program_template (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.training_program_template ADD CONSTRAINT fk_training_program_template__duplicated_from_template_id FOREIGN KEY (duplicated_from_template_id) REFERENCES dbo.training_program_template (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.template_version ADD CONSTRAINT fk_template_version__created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.template_version_phase ADD CONSTRAINT fk_template_version_phase__template_version_id FOREIGN KEY (template_version_id) REFERENCES dbo.template_version (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.template_required_skill ADD CONSTRAINT fk_template_required_skill__template_version_id FOREIGN KEY (template_version_id) REFERENCES dbo.template_version (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.template_required_skill ADD CONSTRAINT fk_template_required_skill__skill_id FOREIGN KEY (skill_id) REFERENCES dbo.skill (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_training_program ADD CONSTRAINT fk_horse_training_program__template_version_id FOREIGN KEY (template_version_id) REFERENCES dbo.template_version (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_training_program ADD CONSTRAINT fk_horse_training_program__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_training_program ADD CONSTRAINT fk_horse_training_program__target_race_id FOREIGN KEY (target_race_id) REFERENCES dbo.race (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_training_program ADD CONSTRAINT fk_horse_training_program__created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_program_phase ADD CONSTRAINT fk_horse_program_phase__horse_training_program_id FOREIGN KEY (horse_training_program_id) REFERENCES dbo.horse_training_program (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_program_phase ADD CONSTRAINT fk_horse_program_phase__template_version_phase_id FOREIGN KEY (template_version_phase_id) REFERENCES dbo.template_version_phase (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.trainer_assignment ADD CONSTRAINT fk_trainer_assignment__horse_training_program_id FOREIGN KEY (horse_training_program_id) REFERENCES dbo.horse_training_program (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.trainer_assignment ADD CONSTRAINT fk_trainer_assignment__trainer_user_id FOREIGN KEY (trainer_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.trainer_assignment ADD CONSTRAINT fk_trainer_assignment__assigned_by_user_id FOREIGN KEY (assigned_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.program_adjustment_request ADD CONSTRAINT fk_program_adjustment_request__horse_training_program_id FOREIGN KEY (horse_training_program_id) REFERENCES dbo.horse_training_program (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.program_adjustment_request ADD CONSTRAINT fk_program_adjustment_request__horse_program_phase_id FOREIGN KEY (horse_program_phase_id) REFERENCES dbo.horse_program_phase (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.program_adjustment_request ADD CONSTRAINT fk_program_adjustment_request__requested_by_user_id FOREIGN KEY (requested_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.program_adjustment_request ADD CONSTRAINT fk_program_adjustment_request__reviewed_by_user_id FOREIGN KEY (reviewed_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- D05
ALTER TABLE dbo.training_session ADD CONSTRAINT fk_training_session__horse_program_phase_id FOREIGN KEY (horse_program_phase_id) REFERENCES dbo.horse_program_phase (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.training_session ADD CONSTRAINT fk_training_session__created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.training_session ADD CONSTRAINT fk_training_session__work_rider_user_id FOREIGN KEY (work_rider_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.session_exercise ADD CONSTRAINT fk_session_exercise__training_session_id FOREIGN KEY (training_session_id) REFERENCES dbo.training_session (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.session_exercise ADD CONSTRAINT fk_session_exercise__exercise_definition_id FOREIGN KEY (exercise_definition_id) REFERENCES dbo.exercise_definition (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.pre_session_check ADD CONSTRAINT fk_pre_session_check__training_session_id FOREIGN KEY (training_session_id) REFERENCES dbo.training_session (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.pre_session_check ADD CONSTRAINT fk_pre_session_check__performed_by_user_id FOREIGN KEY (performed_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.incident ADD CONSTRAINT fk_incident__pre_session_check_id FOREIGN KEY (pre_session_check_id) REFERENCES dbo.pre_session_check (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.training_metric_sample ADD CONSTRAINT fk_training_metric_sample__training_session_id FOREIGN KEY (training_session_id) REFERENCES dbo.training_session (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.monitoring_alert ADD CONSTRAINT fk_monitoring_alert__training_session_id FOREIGN KEY (training_session_id) REFERENCES dbo.training_session (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.monitoring_alert ADD CONSTRAINT fk_monitoring_alert__acknowledged_by_user_id FOREIGN KEY (acknowledged_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.session_result ADD CONSTRAINT fk_session_result__training_session_id FOREIGN KEY (training_session_id) REFERENCES dbo.training_session (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.session_result ADD CONSTRAINT fk_session_result__evaluated_by_user_id FOREIGN KEY (evaluated_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.rider_feedback ADD CONSTRAINT fk_rider_feedback__training_session_id FOREIGN KEY (training_session_id) REFERENCES dbo.training_session (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.rider_feedback ADD CONSTRAINT fk_rider_feedback__submitted_by_user_id FOREIGN KEY (submitted_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- D06
ALTER TABLE dbo.medical_case ADD CONSTRAINT fk_medical_case__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.medical_case ADD CONSTRAINT fk_medical_case__opened_by_user_id FOREIGN KEY (opened_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.veterinary_examination ADD CONSTRAINT fk_veterinary_examination__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.veterinary_examination ADD CONSTRAINT fk_veterinary_examination__medical_case_id FOREIGN KEY (medical_case_id) REFERENCES dbo.medical_case (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.veterinary_examination ADD CONSTRAINT fk_veterinary_examination__performed_by_user_id FOREIGN KEY (performed_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.injury ADD CONSTRAINT fk_injury__medical_case_id FOREIGN KEY (medical_case_id) REFERENCES dbo.medical_case (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.injury ADD CONSTRAINT fk_injury__veterinary_examination_id FOREIGN KEY (veterinary_examination_id) REFERENCES dbo.veterinary_examination (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.treatment_plan ADD CONSTRAINT fk_treatment_plan__medical_case_id FOREIGN KEY (medical_case_id) REFERENCES dbo.medical_case (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.treatment_plan ADD CONSTRAINT fk_treatment_plan__created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.medical_restriction ADD CONSTRAINT fk_medical_restriction__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.medical_restriction ADD CONSTRAINT fk_medical_restriction__medical_case_id FOREIGN KEY (medical_case_id) REFERENCES dbo.medical_case (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.medical_restriction ADD CONSTRAINT fk_medical_restriction__set_by_user_id FOREIGN KEY (set_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.veterinary_examination ADD CONSTRAINT fk_veterinary_examination__changed_medical_restriction_id FOREIGN KEY (changed_medical_restriction_id) REFERENCES dbo.medical_restriction (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.restriction_exercise_rule ADD CONSTRAINT fk_restriction_exercise_rule__medical_restriction_id FOREIGN KEY (medical_restriction_id) REFERENCES dbo.medical_restriction (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.restriction_exercise_rule ADD CONSTRAINT fk_restriction_exercise_rule__exercise_definition_id FOREIGN KEY (exercise_definition_id) REFERENCES dbo.exercise_definition (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_threshold_override ADD CONSTRAINT fk_horse_threshold_override__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.horse_threshold_override ADD CONSTRAINT fk_horse_threshold_override__set_by_user_id FOREIGN KEY (set_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- D07
ALTER TABLE dbo.incident ADD CONSTRAINT fk_incident__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.incident ADD CONSTRAINT fk_incident__reported_by_user_id FOREIGN KEY (reported_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.incident ADD CONSTRAINT fk_incident__reviewed_by_user_id FOREIGN KEY (reviewed_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.incident ADD CONSTRAINT fk_incident__medical_case_id FOREIGN KEY (medical_case_id) REFERENCES dbo.medical_case (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.incident ADD CONSTRAINT fk_incident__training_session_id FOREIGN KEY (training_session_id) REFERENCES dbo.training_session (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.incident ADD CONSTRAINT fk_incident__care_task_id FOREIGN KEY (care_task_id) REFERENCES dbo.care_task (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.incident_photo ADD CONSTRAINT fk_incident_photo__incident_id FOREIGN KEY (incident_id) REFERENCES dbo.incident (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.preventive_task ADD CONSTRAINT fk_preventive_task__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.preventive_task ADD CONSTRAINT fk_preventive_task__responsible_user_id FOREIGN KEY (responsible_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.preventive_task ADD CONSTRAINT fk_preventive_task__completed_by_user_id FOREIGN KEY (completed_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.preventive_task ADD CONSTRAINT fk_preventive_task__medical_case_id FOREIGN KEY (medical_case_id) REFERENCES dbo.medical_case (id) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- D08
ALTER TABLE dbo.race_registration ADD CONSTRAINT fk_race_registration__race_id FOREIGN KEY (race_id) REFERENCES dbo.race (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.race_registration ADD CONSTRAINT fk_race_registration__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.race_registration ADD CONSTRAINT fk_race_registration__registered_by_user_id FOREIGN KEY (registered_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.race_result ADD CONSTRAINT fk_race_result__race_registration_id FOREIGN KEY (race_registration_id) REFERENCES dbo.race_registration (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.race_result ADD CONSTRAINT fk_race_result__recorded_by_user_id FOREIGN KEY (recorded_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.race ADD CONSTRAINT fk_race__created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.cost_record ADD CONSTRAINT fk_cost_record__horse_id FOREIGN KEY (horse_id) REFERENCES dbo.horse (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.cost_record ADD CONSTRAINT fk_cost_record__created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- D09
ALTER TABLE dbo.stock_movement ADD CONSTRAINT fk_stock_movement__inventory_item_id FOREIGN KEY (inventory_item_id) REFERENCES dbo.inventory_item (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.stock_movement ADD CONSTRAINT fk_stock_movement__recorded_by_user_id FOREIGN KEY (recorded_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.replenishment_request ADD CONSTRAINT fk_replenishment_request__inventory_item_id FOREIGN KEY (inventory_item_id) REFERENCES dbo.inventory_item (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.replenishment_request ADD CONSTRAINT fk_replenishment_request__requested_by_user_id FOREIGN KEY (requested_by_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;

-- D10
ALTER TABLE dbo.notification ADD CONSTRAINT fk_notification__recipient_user_id FOREIGN KEY (recipient_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;
ALTER TABLE dbo.audit_log ADD CONSTRAINT fk_audit_log__actor_user_id FOREIGN KEY (actor_user_id) REFERENCES dbo.app_user (id) ON DELETE NO ACTION ON UPDATE NO ACTION;

/* =====================================================================
   PART 3 — ACTIVE-STATE FILTERED UNIQUE INDEXES (ERD-03 rule annotations)
   ===================================================================== */

-- ≤ 1 open box assignment per horse and per box (BR-03)
CREATE UNIQUE INDEX ux_horse_stable_assignment__open_per_horse ON dbo.horse_stable_assignment (horse_id) WHERE effective_to IS NULL;
CREATE UNIQUE INDEX ux_horse_stable_assignment__open_per_box ON dbo.horse_stable_assignment (stable_box_id) WHERE effective_to IS NULL;
-- ≤ 1 open trainer assignment per program (BR-D15)
CREATE UNIQUE INDEX ux_trainer_assignment__open_per_program ON dbo.trainer_assignment (horse_training_program_id) WHERE effective_to IS NULL;
-- ≤ 1 active (not lifted) restriction per horse (BR-11, SCR-MED-010 / 011)
CREATE UNIQUE INDEX ux_medical_restriction__active_per_horse ON dbo.medical_restriction (horse_id) WHERE lifted_at IS NULL;
-- ≤ 1 open-ended override per horse and metric (BR-D11)
CREATE UNIQUE INDEX ux_horse_threshold_override__open_per_horse_metric ON dbo.horse_threshold_override (horse_id, metric) WHERE effective_to IS NULL;
-- ≤ 1 BASELINE examination per horse (BR-07)
CREATE UNIQUE INDEX ux_veterinary_examination__baseline_per_horse ON dbo.veterinary_examination (horse_id) WHERE exam_type = 'BASELINE';
-- optional business identifier, unique when present
CREATE UNIQUE INDEX ux_horse__registration_no ON dbo.horse (registration_no) WHERE registration_no IS NOT NULL;
-- PreSessionCheck 0..1 — 0..1 Incident
CREATE UNIQUE INDEX ux_incident__pre_session_check_id ON dbo.incident (pre_session_check_id) WHERE pre_session_check_id IS NOT NULL;

/* =====================================================================
   PART 4 — ACCESS-PATH INDEXES
   Leading FK columns that are navigation / filter paths. FKs covered by a
   UNIQUE constraint or unique index above are not indexed again.
   ===================================================================== */

-- Identity / ownership
CREATE INDEX ix_user_role__role_id ON dbo.user_role (role_id);
CREATE INDEX ix_user_skill__skill_id ON dbo.user_skill (skill_id);
CREATE INDEX ix_horse_ownership__horse_id ON dbo.horse_ownership (horse_id, effective_from);
CREATE INDEX ix_horse_ownership__owner_user_id ON dbo.horse_ownership (owner_user_id, horse_id) INCLUDE (effective_from, effective_to);

-- Horse / stable
CREATE INDEX ix_horse__lifecycle ON dbo.horse (lifecycle) INCLUDE (horse_code, name, health_status);
CREATE INDEX ix_horse__sire_horse_id ON dbo.horse (sire_horse_id) WHERE sire_horse_id IS NOT NULL;
CREATE INDEX ix_horse__dam_horse_id ON dbo.horse (dam_horse_id) WHERE dam_horse_id IS NOT NULL;
CREATE INDEX ix_weight_record__horse_id_measured_at ON dbo.weight_record (horse_id, measured_at DESC) INCLUDE (weight);
CREATE INDEX ix_horse_photo__horse_id ON dbo.horse_photo (horse_id);
CREATE INDEX ix_horse_stable_assignment__horse_id ON dbo.horse_stable_assignment (horse_id, effective_from DESC);
CREATE INDEX ix_horse_stable_assignment__stable_box_id ON dbo.horse_stable_assignment (stable_box_id, effective_from DESC);

-- Care / nutrition
CREATE INDEX ix_care_plan__horse_id ON dbo.care_plan (horse_id, effective_from DESC);
CREATE INDEX ix_care_plan_item__care_plan_id ON dbo.care_plan_item (care_plan_id);
CREATE INDEX ix_nutrition_plan__horse_id ON dbo.nutrition_plan (horse_id, effective_from DESC);
CREATE INDEX ix_nutrition_plan_item__nutrition_plan_id ON dbo.nutrition_plan_item (nutrition_plan_id);
CREATE INDEX ix_care_task__task_date ON dbo.care_task (task_date, horse_id) INCLUDE (completed_at);
CREATE INDEX ix_care_task__horse_id_task_date ON dbo.care_task (horse_id, task_date DESC);
CREATE INDEX ix_care_task__care_plan_item_id ON dbo.care_task (care_plan_item_id) WHERE care_plan_item_id IS NOT NULL;
CREATE INDEX ix_care_task__nutrition_plan_item_id ON dbo.care_task (nutrition_plan_item_id) WHERE nutrition_plan_item_id IS NOT NULL;
CREATE INDEX ix_care_task__treatment_plan_id ON dbo.care_task (treatment_plan_id) WHERE treatment_plan_id IS NOT NULL;

-- Templates / programs
CREATE INDEX ix_training_program_template__status ON dbo.training_program_template (status);
CREATE INDEX ix_template_required_skill__skill_id ON dbo.template_required_skill (skill_id);
CREATE INDEX ix_horse_training_program__horse_id ON dbo.horse_training_program (horse_id, status);
CREATE INDEX ix_horse_training_program__template_version_id ON dbo.horse_training_program (template_version_id);
CREATE INDEX ix_horse_training_program__target_race_id ON dbo.horse_training_program (target_race_id) WHERE target_race_id IS NOT NULL;
CREATE INDEX ix_horse_program_phase__template_version_phase_id ON dbo.horse_program_phase (template_version_phase_id) WHERE template_version_phase_id IS NOT NULL;
CREATE INDEX ix_trainer_assignment__horse_training_program_id ON dbo.trainer_assignment (horse_training_program_id, effective_from DESC);
CREATE INDEX ix_trainer_assignment__trainer_user_id_open ON dbo.trainer_assignment (trainer_user_id) INCLUDE (horse_training_program_id) WHERE effective_to IS NULL;
CREATE INDEX ix_program_adjustment_request__program_id ON dbo.program_adjustment_request (horse_training_program_id, requested_at DESC);
CREATE INDEX ix_program_adjustment_request__status ON dbo.program_adjustment_request (status, requested_at) WHERE status = 'PENDING';

-- Sessions / realtime
CREATE INDEX ix_training_session__phase_id_scheduled_start ON dbo.training_session (horse_program_phase_id, scheduled_start);
CREATE INDEX ix_training_session__scheduled_start ON dbo.training_session (scheduled_start) INCLUDE (status);
CREATE INDEX ix_training_session__work_rider_scheduled_start ON dbo.training_session (work_rider_user_id, scheduled_start);
CREATE INDEX ix_training_session__created_by_scheduled_start ON dbo.training_session (created_by_user_id, scheduled_start);
CREATE INDEX ix_session_exercise__exercise_definition_id ON dbo.session_exercise (exercise_definition_id);
CREATE INDEX ix_training_metric_sample__session_id_recorded_at ON dbo.training_metric_sample (training_session_id, recorded_at);
CREATE INDEX ix_monitoring_alert__session_id_raised_at ON dbo.monitoring_alert (training_session_id, raised_at);
CREATE INDEX ix_monitoring_alert__unacknowledged ON dbo.monitoring_alert (raised_at) INCLUDE (training_session_id) WHERE acknowledged_at IS NULL;

-- Medical
CREATE INDEX ix_medical_case__horse_id_opened_at ON dbo.medical_case (horse_id, opened_at DESC) INCLUDE (status);
CREATE INDEX ix_medical_case__status ON dbo.medical_case (status) WHERE status = 'OPEN';
CREATE INDEX ix_veterinary_examination__horse_id_examined_at ON dbo.veterinary_examination (horse_id, examined_at DESC);
CREATE INDEX ix_veterinary_examination__medical_case_id ON dbo.veterinary_examination (medical_case_id, examined_at) WHERE medical_case_id IS NOT NULL;
CREATE INDEX ix_veterinary_examination__changed_restriction_id ON dbo.veterinary_examination (changed_medical_restriction_id) WHERE changed_medical_restriction_id IS NOT NULL;
CREATE INDEX ix_injury__medical_case_id ON dbo.injury (medical_case_id);
CREATE INDEX ix_injury__veterinary_examination_id ON dbo.injury (veterinary_examination_id) WHERE veterinary_examination_id IS NOT NULL;
CREATE INDEX ix_medical_restriction__horse_id_effective_from ON dbo.medical_restriction (horse_id, effective_from DESC);
CREATE INDEX ix_medical_restriction__medical_case_id ON dbo.medical_restriction (medical_case_id) WHERE medical_case_id IS NOT NULL;
CREATE INDEX ix_restriction_exercise_rule__restriction_id ON dbo.restriction_exercise_rule (medical_restriction_id);
CREATE INDEX ix_restriction_exercise_rule__exercise_definition_id ON dbo.restriction_exercise_rule (exercise_definition_id) WHERE exercise_definition_id IS NOT NULL;
CREATE INDEX ix_horse_threshold_override__horse_id_metric ON dbo.horse_threshold_override (horse_id, metric, effective_from DESC);

-- Incident / preventive
CREATE INDEX ix_incident__horse_id_reported_at ON dbo.incident (horse_id, reported_at DESC);
CREATE INDEX ix_incident__status_reported_at ON dbo.incident (status, reported_at DESC);
CREATE INDEX ix_incident__medical_case_id ON dbo.incident (medical_case_id) WHERE medical_case_id IS NOT NULL;
CREATE INDEX ix_incident__training_session_id ON dbo.incident (training_session_id) WHERE training_session_id IS NOT NULL;
CREATE INDEX ix_incident__care_task_id ON dbo.incident (care_task_id) WHERE care_task_id IS NOT NULL;
CREATE INDEX ix_incident_photo__incident_id ON dbo.incident_photo (incident_id);
CREATE INDEX ix_preventive_task__horse_id_due_date ON dbo.preventive_task (horse_id, due_date);
CREATE INDEX ix_preventive_task__open_due_date ON dbo.preventive_task (due_date) INCLUDE (horse_id, task_type) WHERE completed_at IS NULL;
CREATE INDEX ix_preventive_task__responsible_user_id ON dbo.preventive_task (responsible_user_id) WHERE responsible_user_id IS NOT NULL;
CREATE INDEX ix_preventive_task__medical_case_id ON dbo.preventive_task (medical_case_id) WHERE medical_case_id IS NOT NULL;

-- Race / finance
CREATE INDEX ix_race__race_date ON dbo.race (race_date);
CREATE INDEX ix_race_registration__race_id ON dbo.race_registration (race_id, status);
CREATE INDEX ix_race_registration__horse_id ON dbo.race_registration (horse_id, registered_at DESC);
CREATE INDEX ix_cost_record__cost_date ON dbo.cost_record (cost_date) INCLUDE (category, amount, currency_code);
CREATE INDEX ix_cost_record__horse_id_cost_date ON dbo.cost_record (horse_id, cost_date) WHERE horse_id IS NOT NULL;

-- Inventory
CREATE INDEX ix_stock_movement__item_id_recorded_at ON dbo.stock_movement (inventory_item_id, recorded_at DESC);
CREATE INDEX ix_replenishment_request__item_id_submitted_at ON dbo.replenishment_request (inventory_item_id, submitted_at DESC);

-- Notification / audit
CREATE INDEX ix_notification__recipient_created_at ON dbo.notification (recipient_user_id, created_at DESC);
CREATE INDEX ix_notification__recipient_unread ON dbo.notification (recipient_user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX ix_audit_log__occurred_at ON dbo.audit_log (occurred_at DESC);
CREATE INDEX ix_audit_log__target ON dbo.audit_log (target_type, target_id, occurred_at DESC) WHERE target_type IS NOT NULL;
CREATE INDEX ix_audit_log__actor_user_id_occurred_at ON dbo.audit_log (actor_user_id, occurred_at DESC) WHERE actor_user_id IS NOT NULL;

/* End of schema.sql — 51 tables, 102 foreign keys. No seed data. */
