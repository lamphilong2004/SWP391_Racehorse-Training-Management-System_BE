-- Read-only reference for the schema in this repository.
-- Choose the approved database in SSMS first. Never assume an id is shared across machines.
-- Replace NULL with the horse id verified in the current environment.
SET NOCOUNT ON;
DECLARE @horseId BIGINT = NULL;
IF @horseId IS NULL OR @horseId <= 0
    THROW 51000, 'Set horseId from the current approved database before reading.', 1;

SELECT DB_NAME() AS database_name, SYSUTCDATETIME() AS read_at_utc;

SELECT id, horse_code, name, lifecycle, health_status
FROM dbo.horse
WHERE id = @horseId;

SELECT id, horse_id, status, opened_at, closed_at
FROM dbo.medical_case
WHERE horse_id = @horseId
ORDER BY opened_at DESC, id DESC;

-- Only task status/assignment metadata; no clinical diagnosis or treatment text.
SELECT ct.id AS task_id, ct.horse_id, tp.medical_case_id,
       ct.assigned_groom_user_id, ct.task_date, ct.scheduled_slot,
       ct.completed_by_user_id, ct.completed_at
FROM dbo.care_task AS ct
INNER JOIN dbo.treatment_plan AS tp ON tp.id = ct.treatment_plan_id
WHERE ct.horse_id = @horseId
ORDER BY ct.task_date DESC, ct.id DESC;

SELECT id, horse_id, medical_case_id, restriction_level, effective_from, lifted_at
FROM dbo.medical_restriction
WHERE horse_id = @horseId
ORDER BY effective_from DESC, id DESC;

SELECT id, horse_id, medical_case_id, pre_session_check_id,
       training_session_id, care_task_id, status
FROM dbo.incident
WHERE horse_id = @horseId
ORDER BY id DESC;

-- No automatic PASS/FAIL: compare these rows with the role/UI/API evidence.
