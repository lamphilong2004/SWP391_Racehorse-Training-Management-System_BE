/* DEMO DATA ONLY (demo/main-flow-hour1). Runs after demo-reset.sql. Deterministic: IDs restart at 1.
   Accounts are ACTIVE with password_hash NULL; DemoDataInitializer sets hashes from DEMO_PASSWORD.
   Thunder Bolt (TB-001) is NOT seeded: the Club Manager creates it live in Flow 1.
   Thresholds (180 bpm critical, 10 bpm warning margin) live in application-demo.yml and are DEMO DATA. */

INSERT INTO dbo.role (role_code, display_name) VALUES
 ('CLUB_MANAGER', N'Club Manager'), ('HEAD_TRAINER', N'Head Trainer'), ('TRAINER', N'Trainer'),
 ('WORK_RIDER', N'Work Rider'), ('VETERINARIAN', N'Veterinarian'), ('GROOM', N'Groom / Stable Hand'),
 ('HORSE_OWNER', N'Horse Owner');

INSERT INTO dbo.app_user (user_code, full_name, email, password_hash, account_status, experience_years, experience_level) VALUES
 ('U-CM-001', N'Hoang Minh Tuan', N'cm@demo.racehorse.local', NULL, 'ACTIVE', 12, N'Senior'),
 ('U-HT-001', N'Vo Thanh Son', N'ht@demo.racehorse.local', NULL, 'ACTIVE', 18, N'Senior'),
 ('U-TR-001', N'Nguyen Minh Khoa', N'trainer@demo.racehorse.local', NULL, 'ACTIVE', 9, N'Senior'),
 ('U-TR-002', N'Le Van Duc', N'trainer2@demo.racehorse.local', NULL, 'ACTIVE', 4, N'Intermediate'),
 ('U-WR-001', N'Pham Quang Huy', N'rider@demo.racehorse.local', NULL, 'ACTIVE', 6, N'Senior'),
 ('U-WR-002', N'Le Thi Mai', N'rider2@demo.racehorse.local', NULL, 'ACTIVE', 2, N'Junior'),
 ('U-VET-001', N'Dr. Nguyen Thu Ha', N'vet@demo.racehorse.local', NULL, 'ACTIVE', 11, N'Senior'),
 ('U-GR-001', N'Bui Van Hai', N'groom@demo.racehorse.local', NULL, 'ACTIVE', 7, N'Senior'),
 ('U-OW-001', N'Tran Quoc Bao', N'owner@demo.racehorse.local', NULL, 'ACTIVE', NULL, NULL);

INSERT INTO dbo.user_role (user_id, role_id, assigned_at)
SELECT u.id, r.id, SYSUTCDATETIME() FROM dbo.app_user u JOIN dbo.role r ON r.role_code =
 CASE LEFT(u.user_code, 5) WHEN 'U-CM-' THEN 'CLUB_MANAGER' WHEN 'U-HT-' THEN 'HEAD_TRAINER' WHEN 'U-TR-' THEN 'TRAINER'
  WHEN 'U-WR-' THEN 'WORK_RIDER' WHEN 'U-VET' THEN 'VETERINARIAN' WHEN 'U-GR-' THEN 'GROOM' WHEN 'U-OW-' THEN 'HORSE_OWNER' END;

INSERT INTO dbo.skill (name, kind) VALUES
 (N'Speed Development', 'TRAINER_SPECIALIZATION'), (N'Rehabilitation', 'TRAINER_SPECIALIZATION'),
 (N'Endurance', 'TRAINER_SPECIALIZATION'), (N'Gallop Work', 'RIDER_SKILL'), (N'Difficult Horse Handling', 'RIDER_SKILL');

INSERT INTO dbo.user_skill (user_id, skill_id)
SELECT u.id, s.id FROM dbo.app_user u JOIN dbo.skill s ON
 (u.user_code = 'U-TR-001' AND s.name IN (N'Speed Development', N'Rehabilitation')) OR
 (u.user_code = 'U-TR-002' AND s.name = N'Endurance') OR
 (u.user_code = 'U-WR-001' AND s.name IN (N'Gallop Work', N'Difficult Horse Handling'));

INSERT INTO dbo.stable_box (box_code, yard) VALUES
 ('A-10', N'North Yard'), ('A-11', N'North Yard'), ('A-12', N'North Yard'), ('B-01', N'South Yard'), ('B-02', N'South Yard');

INSERT INTO dbo.exercise_definition (name, exercise_type, description, target_metric) VALUES
 (N'Walk', N'WALK', N'Warm-up / cool-down walk', N'Heart rate settles'),
 (N'Trot', N'TROT', N'Steady trot', N'Aerobic base'),
 (N'Canter', N'CANTER', N'Controlled canter', N'Aerobic capacity'),
 (N'Gallop Intervals', N'GALLOP', N'Repeated gallop work with walk recovery', N'Speed development'),
 (N'Long Canter', N'CANTER', N'Extended steady canter', N'Endurance');

/* Program Template Library (DEC-005) */
INSERT INTO dbo.training_program_template (template_code, status) VALUES ('TPL-003', 'ACTIVE'), ('TPL-004', 'ACTIVE');
INSERT INTO dbo.template_version (training_program_template_id, created_by_user_id, version_no, name, goals, suitable_horse_profile,
 recommended_distance_use_case, recommended_duration, workload_intensity_guidance, preferred_surfaces, recovery_guidance)
SELECT t.id, (SELECT id FROM dbo.app_user WHERE user_code='U-HT-001'), v.version_no, v.name, v.goals, v.profile, v.use_case, v.duration,
 v.guidance, v.surfaces, v.recovery
FROM dbo.training_program_template t JOIN (VALUES
 ('TPL-003', 1, N'Race Preparation 1600m', N'Prepare a fit horse for a 1600 m race', N'Fit 3–5 year old', N'1600 m', N'4–6 weeks', N'Low → High', N'Turf Track', N'Walk recovery between reps'),
 ('TPL-003', 2, N'Race Preparation 1600m', N'Prepare a fit horse for a 1600 m race (v2: longer sharpening)', N'Fit 3–5 year old', N'1600 m', N'4–6 weeks', N'Low → High', N'Turf Track', N'Walk recovery between reps'),
 ('TPL-004', 1, N'Recovery Conditioning', N'Return to work after a minor injury', N'Horse under LIMITED restriction', N'Short work', N'3 weeks', N'Low → Medium', N'Sand Track', N'Daily vet-cleared progression')
) v(code, version_no, name, goals, profile, use_case, duration, guidance, surfaces, recovery) ON v.code = t.template_code;

INSERT INTO dbo.template_version_phase (template_version_id, phase_order, name, objective, recommended_duration, intensity_guidance)
SELECT tv.id, p.phase_order, p.name, p.objective, p.duration, p.guidance
FROM dbo.template_version tv JOIN dbo.training_program_template t ON t.id = tv.training_program_template_id
JOIN (VALUES
 ('TPL-003', 1, 1, N'Base Fitness', N'Aerobic base', N'1 week', N'Low → Medium'),
 ('TPL-003', 1, 2, N'Speed Development', N'Speed work', N'2 weeks', N'Medium → High'),
 ('TPL-003', 2, 1, N'Base Fitness', N'Aerobic base', N'1 week', N'Low → Medium'),
 ('TPL-003', 2, 2, N'Speed Development', N'Speed work', N'2 weeks', N'Medium → High'),
 ('TPL-003', 2, 3, N'Race Sharpening', N'Race-pace sharpening', N'1 week', N'Medium → High'),
 ('TPL-004', 1, 1, N'Walking Rehab', N'Controlled walking', N'1 week', N'Low'),
 ('TPL-004', 1, 2, N'Light Trot', N'Reintroduce trot', N'2 weeks', N'Low → Medium')
) p(code, version_no, phase_order, name, objective, duration, guidance) ON p.code = t.template_code AND p.version_no = tv.version_no;

INSERT INTO dbo.template_required_skill (template_version_id, skill_id)
SELECT tv.id, s.id FROM dbo.template_version tv JOIN dbo.training_program_template t ON t.id = tv.training_program_template_id
JOIN dbo.skill s ON (t.template_code = 'TPL-003' AND s.name = N'Speed Development') OR (t.template_code = 'TPL-004' AND s.name = N'Rehabilitation');

/* Seeded ACTIVE horses for the interlock demonstration */
INSERT INTO dbo.horse (horse_code, name, breed, sex, foaled_date, country_of_origin, registration_no, lifecycle, health_status,
 sire_name, dam_name, activated_at) VALUES
 ('ST-014', N'Storm', N'Thoroughbred', N'Mare', '2021-03-11', N'Australia', 'AUS-ST-2021', 'ACTIVE', 'MONITORING', N'Storm Front', N'Grey Lady', '2026-09-01T08:00:00'),
 ('NR-007', N'Night Runner', N'Thoroughbred', N'Gelding', '2020-05-02', N'Ireland', 'IRE-NR-2020', 'ACTIVE', 'MONITORING', N'Midnight Star', N'River Song', '2026-08-15T08:00:00');

INSERT INTO dbo.horse_ownership (horse_id, owner_user_id, share_percentage, effective_from, is_managing_owner)
SELECT h.id, (SELECT id FROM dbo.app_user WHERE user_code='U-OW-001'), 100, '2026-08-01', 1 FROM dbo.horse h;

INSERT INTO dbo.horse_stable_assignment (horse_id, stable_box_id, assigned_by_user_id, effective_from, reason)
SELECT h.id, b.id, (SELECT id FROM dbo.app_user WHERE user_code='U-CM-001'), '2026-08-01', N'Initial box'
FROM dbo.horse h JOIN dbo.stable_box b ON (h.horse_code='ST-014' AND b.box_code='A-10') OR (h.horse_code='NR-007' AND b.box_code='A-11');

INSERT INTO dbo.veterinary_examination (horse_id, performed_by_user_id, exam_type, examined_at, findings, health_status_set)
SELECT id, (SELECT id FROM dbo.app_user WHERE user_code='U-VET-001'), 'BASELINE', '2026-08-01T07:00:00', N'Baseline: sound, fit for training', 'FIT' FROM dbo.horse;

INSERT INTO dbo.weight_record (horse_id, recorded_by_user_id, weight, measured_at, context)
SELECT id, (SELECT id FROM dbo.app_user WHERE user_code='U-VET-001'), CASE horse_code WHEN 'ST-014' THEN 468 ELSE 495 END, '2026-08-01T07:00:00', N'BASELINE' FROM dbo.horse;

/* Programs assigned to Nguyen Minh Khoa (so the Trainer can try to schedule them) */
INSERT INTO dbo.horse_training_program (horse_id, template_version_id, target_race_id, created_by_user_id, name, goals, target_metrics,
 period_start, period_end, status, notes, activated_at)
SELECT h.id, tv.id, NULL, (SELECT id FROM dbo.app_user WHERE user_code='U-HT-001'), p.name, p.goals, NULL, '2026-10-01', '2026-11-15', 'ACTIVE', NULL, '2026-10-01T08:00:00'
FROM dbo.horse h JOIN (VALUES ('ST-014', 'TPL-004', 1, N'Storm Recovery Conditioning', N'Return to full work'),
                              ('NR-007', 'TPL-003', 2, N'Night Runner Race Preparation', N'Prepare for a 1600 m race')) p(code, tpl, ver, name, goals)
 ON p.code = h.horse_code
JOIN dbo.training_program_template t ON t.template_code = p.tpl JOIN dbo.template_version tv ON tv.training_program_template_id = t.id AND tv.version_no = p.ver;

INSERT INTO dbo.horse_program_phase (horse_training_program_id, template_version_phase_id, phase_order, name, objective, start_date, end_date,
 intensity_min, intensity_max, workload, is_customized)
SELECT p.id, tp.id, tp.phase_order, tp.name, tp.objective,
 DATEADD(DAY, (tp.phase_order - 1) * 15, '2026-10-01'), DATEADD(DAY, tp.phase_order * 15 - 1, '2026-10-01'),
 'LOW', CASE WHEN tp.phase_order = 1 THEN 'MEDIUM' ELSE 'HIGH' END, tp.recommended_duration, 0
FROM dbo.horse_training_program p JOIN dbo.template_version_phase tp ON tp.template_version_id = p.template_version_id;

INSERT INTO dbo.trainer_assignment (horse_training_program_id, trainer_user_id, assigned_by_user_id, effective_from, reason)
SELECT id, (SELECT id FROM dbo.app_user WHERE user_code='U-TR-001'), (SELECT id FROM dbo.app_user WHERE user_code='U-HT-001'), '2026-10-01', N'Initial assignment'
FROM dbo.horse_training_program;

/* Active restrictions: Storm LIMITED (gallop prohibited), Night Runner LOCKED */
INSERT INTO dbo.medical_case (horse_id, opened_by_user_id, status, opened_at, condition_summary)
SELECT id, (SELECT id FROM dbo.app_user WHERE user_code='U-VET-001'), 'OPEN', '2026-10-10T07:30:00',
 CASE horse_code WHEN 'ST-014' THEN N'Mild tendon soreness, near fore' ELSE N'Suspected fever after travel' END FROM dbo.horse;

INSERT INTO dbo.medical_restriction (horse_id, medical_case_id, set_by_user_id, restriction_level, reason, effective_from, review_date)
SELECT h.id, c.id, (SELECT id FROM dbo.app_user WHERE user_code='U-VET-001'),
 CASE h.horse_code WHEN 'ST-014' THEN 'LIMITED' ELSE 'LOCKED' END,
 CASE h.horse_code WHEN 'ST-014' THEN N'Mild tendon soreness — walk, trot and canter only; no gallop work' ELSE N'Suspected fever — complete rest until re-examination' END,
 '2026-10-10T08:00:00', '2026-10-24'
FROM dbo.horse h JOIN dbo.medical_case c ON c.horse_id = h.id;

INSERT INTO dbo.restriction_exercise_rule (medical_restriction_id, exercise_definition_id, rule_type)
SELECT r.id, e.id, CASE e.name WHEN N'Gallop Intervals' THEN 'PROHIBITED' ELSE 'ALLOWED' END
FROM dbo.medical_restriction r JOIN dbo.horse h ON h.id = r.horse_id AND h.horse_code = 'ST-014'
JOIN dbo.exercise_definition e ON e.name IN (N'Walk', N'Trot', N'Canter', N'Gallop Intervals');
