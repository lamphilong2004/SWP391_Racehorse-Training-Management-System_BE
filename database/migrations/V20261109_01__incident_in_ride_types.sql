-- DEC-013: in-ride incident types for Work Rider / Trainer reports.
-- Widens ck_incident__incident_type only. No business rows are inserted, updated or deleted.
-- Canonical DB-01 and earlier migrations remain byte-identical.
ALTER TABLE dbo.incident DROP CONSTRAINT ck_incident__incident_type;
ALTER TABLE dbo.incident ADD CONSTRAINT ck_incident__incident_type CHECK (incident_type IN (
    'OFF_FEED','COLIC_SIGNS','FEVER','HOOF_OR_LEG_PROBLEM','OTHER',
    'LAMENESS','INJURY','BREATHING_PROBLEM','BEHAVIOUR_PROBLEM','EXHAUSTION'));
