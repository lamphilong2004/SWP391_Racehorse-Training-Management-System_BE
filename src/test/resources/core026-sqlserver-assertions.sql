SET NOCOUNT ON;
INSERT dbo.pre_session_check(training_session_id,performed_by_user_id,performed_at,observations,overall_result)
VALUES(1,1,'2026-10-13T09:05:06.123',N'{"conditionNormal":false,"notes":"Bỏ ăn"}','ABNORMAL');
BEGIN TRY
 INSERT dbo.pre_session_check(training_session_id,performed_by_user_id,performed_at,overall_result) VALUES(1,1,SYSUTCDATETIME(),'ALL_NORMAL');
 THROW 51000,'Duplicate check accepted',1;
END TRY BEGIN CATCH IF ERROR_NUMBER() NOT IN(2601,2627) THROW; END CATCH;

INSERT dbo.incident(incident_code,horse_id,reported_by_user_id,observed_at,reported_at,incident_type,observed_severity,status)
VALUES('NULL1',1,1,'2026-10-13T09:05:06.123',SYSUTCDATETIME(),'OFF_FEED','LOW','REPORTED'),
      ('NULL2',1,1,SYSUTCDATETIME(),SYSUTCDATETIME(),'OTHER','HIGH','REPORTED');
-- Filtered index must allow many NULL sources while rejecting duplicate non-NULL sources.
DECLARE @check BIGINT=(SELECT id FROM dbo.pre_session_check WHERE training_session_id=1);
INSERT dbo.incident(incident_code,horse_id,reported_by_user_id,pre_session_check_id,observed_at,reported_at,incident_type,observed_severity,status)
VALUES('CHECK1',1,1,@check,SYSUTCDATETIME(),SYSUTCDATETIME(),'OTHER','MEDIUM','REPORTED');
BEGIN TRY
 INSERT dbo.incident(incident_code,horse_id,reported_by_user_id,pre_session_check_id,observed_at,reported_at,incident_type,observed_severity,status)
 VALUES('CHECK2',1,1,@check,SYSUTCDATETIME(),SYSUTCDATETIME(),'OTHER','MEDIUM','REPORTED');
 THROW 51000,'Duplicate incident check source accepted',1;
END TRY BEGIN CATCH IF ERROR_NUMBER() NOT IN(2601,2627) THROW; END CATCH;
-- Use a fresh, valid check source: otherwise duplicate-index validation may precede the CHECK.
INSERT dbo.training_session(session_code,horse_program_phase_id,created_by_user_id,work_rider_user_id,scheduled_start,status)
VALUES('TEST2',1,1,1,'2026-10-13T10:00:00','SCHEDULED');
DECLARE @session2 BIGINT=SCOPE_IDENTITY();
INSERT dbo.pre_session_check(training_session_id,performed_by_user_id,performed_at,overall_result)
VALUES(@session2,1,SYSUTCDATETIME(),'ALL_NORMAL');
DECLARE @freshCheck BIGINT=SCOPE_IDENTITY();
BEGIN TRY
 INSERT dbo.incident(incident_code,horse_id,reported_by_user_id,pre_session_check_id,training_session_id,observed_at,reported_at,incident_type,observed_severity,status)
 VALUES('MULTI',1,1,@freshCheck,1,SYSUTCDATETIME(),SYSUTCDATETIME(),'OTHER','LOW','REPORTED');
 THROW 51000,'Multiple contexts accepted',1;
END TRY BEGIN CATCH IF ERROR_NUMBER()<>547 THROW; END CATCH;
BEGIN TRY
 INSERT dbo.incident(incident_code,horse_id,reported_by_user_id,observed_at,reported_at,incident_type,observed_severity,status)
 VALUES('BADTYPE',1,1,SYSUTCDATETIME(),SYSUTCDATETIME(),'INVALID','LOW','REPORTED');
 THROW 51000,'Invalid type accepted',1;
END TRY BEGIN CATCH IF ERROR_NUMBER()<>547 THROW; END CATCH;
BEGIN TRY
 INSERT dbo.incident(incident_code,horse_id,reported_by_user_id,observed_at,reported_at,incident_type,observed_severity,status)
 VALUES('BADSEV',1,1,SYSUTCDATETIME(),SYSUTCDATETIME(),'OTHER','INVALID','REPORTED');
 THROW 51000,'Invalid severity accepted',1;
END TRY BEGIN CATCH IF ERROR_NUMBER()<>547 THROW; END CATCH;
BEGIN TRY
 INSERT dbo.incident(incident_code,horse_id,reported_by_user_id,observed_at,reported_at,incident_type,observed_severity,status)
 VALUES('BADSTATUS',1,1,SYSUTCDATETIME(),SYSUTCDATETIME(),'OTHER','LOW','INVALID');
 THROW 51000,'Invalid status accepted',1;
END TRY BEGIN CATCH IF ERROR_NUMBER()<>547 THROW; END CATCH;
BEGIN TRY
 INSERT dbo.incident(incident_code,horse_id,reported_by_user_id,observed_at,reported_at,incident_type,observed_severity,status)
 VALUES('BADFK',999,1,SYSUTCDATETIME(),SYSUTCDATETIME(),'OTHER','LOW','REPORTED');
 THROW 51000,'Invalid horse FK accepted',1;
END TRY BEGIN CATCH IF ERROR_NUMBER()<>547 THROW; END CATCH;
IF (SELECT observed_at FROM dbo.incident WHERE incident_code='NULL1')<>CONVERT(DATETIME2(3),'2026-10-13T09:05:06.123')
 THROW 51000,'Timestamp precision lost',1;
IF (SELECT COUNT(*) FROM dbo.incident)<>3 THROW 51000,'Unexpected incident count',1;
IF (SELECT observations FROM dbo.pre_session_check WHERE training_session_id=1) NOT LIKE N'%Bỏ ăn%'
 THROW 51000,'Unicode observation lost',1;
