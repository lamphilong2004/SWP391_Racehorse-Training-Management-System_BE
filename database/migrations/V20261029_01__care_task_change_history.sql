-- Flow 4 extension: append-only cancellation/reassignment history.
-- Applies once through the reviewed migration manifest. No business rows are deleted or rewritten.
-- Canonical DB-01 and earlier migrations remain byte-identical.
CREATE TABLE dbo.care_task_change (
    id                       BIGINT IDENTITY(1,1) NOT NULL,
    care_task_id             BIGINT NOT NULL,
    change_type              VARCHAR(20) NOT NULL,
    previous_groom_user_id   BIGINT NOT NULL,
    new_groom_user_id        BIGINT NULL,
    changed_by_user_id       BIGINT NOT NULL,
    changed_at              DATETIME2(3) NOT NULL,
    reason                  NVARCHAR(500) NOT NULL,
    CONSTRAINT pk_care_task_change PRIMARY KEY (id),
    CONSTRAINT fk_care_task_change__task FOREIGN KEY (care_task_id) REFERENCES dbo.care_task(id),
    CONSTRAINT fk_care_task_change__previous_groom FOREIGN KEY (previous_groom_user_id) REFERENCES dbo.app_user(id),
    CONSTRAINT fk_care_task_change__new_groom FOREIGN KEY (new_groom_user_id) REFERENCES dbo.app_user(id),
    CONSTRAINT fk_care_task_change__actor FOREIGN KEY (changed_by_user_id) REFERENCES dbo.app_user(id),
    CONSTRAINT ck_care_task_change__type CHECK (change_type IN ('REASSIGN','CANCEL')),
    CONSTRAINT ck_care_task_change__assignment CHECK (
        (change_type='CANCEL' AND new_groom_user_id IS NULL) OR
        (change_type='REASSIGN' AND new_groom_user_id IS NOT NULL AND new_groom_user_id<>previous_groom_user_id)),
    CONSTRAINT ck_care_task_change__reason CHECK (LEN(LTRIM(RTRIM(reason)))>0)
);
CREATE INDEX ix_care_task_change__task ON dbo.care_task_change(care_task_id,id);
CREATE UNIQUE INDEX ux_care_task_change__cancel ON dbo.care_task_change(care_task_id) WHERE change_type='CANCEL';
