SET NOCOUNT ON;
INSERT dbo.app_user(user_code,full_name,email,account_status) VALUES('TEST1',N'Groom Test','core026-test@example.invalid','ACTIVE');
INSERT dbo.horse(horse_code,name,lifecycle) VALUES('TEST1',N'Horse Test','ACTIVE');
INSERT dbo.training_program_template(template_code,status) VALUES('TEST1','ACTIVE');
INSERT dbo.template_version(training_program_template_id,version_no,name) VALUES(1,1,N'Test');
INSERT dbo.horse_training_program(horse_id,template_version_id,created_by_user_id,name,period_start,period_end,status,activated_at)
VALUES(1,1,1,N'Test','2026-01-01','2026-12-31','ACTIVE',SYSUTCDATETIME());
INSERT dbo.horse_program_phase(horse_training_program_id,phase_order,name,start_date,end_date,is_customized)
VALUES(1,1,N'Test','2026-01-01','2026-12-31',1);
INSERT dbo.training_session(session_code,horse_program_phase_id,created_by_user_id,work_rider_user_id,scheduled_start,status)
VALUES('TEST1',1,1,1,'2026-10-13T09:00:00','SCHEDULED');
