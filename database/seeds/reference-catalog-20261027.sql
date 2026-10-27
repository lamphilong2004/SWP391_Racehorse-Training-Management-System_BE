-- Reference data for the three-flow review (Leader-approved 2026-10-27). Idempotent: rows are inserted only when
-- their template_code / skill name is missing; existing rows are never updated or deleted.
-- Requires migration V20261027_01. Source: database/reference/training-catalog-20261027-v2.json (cited sources inside).
SET QUOTED_IDENTIFIER ON; SET ANSI_NULLS ON; SET ANSI_PADDING ON; SET ANSI_WARNINGS ON; SET ARITHABORT ON; SET CONCAT_NULL_YIELDS_NULL ON; SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;
BEGIN TRANSACTION;
DECLARE @t BIGINT, @v BIGINT;
IF NOT EXISTS (SELECT 1 FROM dbo.training_program_template WHERE template_code=N'RHREF-BASE-01')
BEGIN
    INSERT INTO dbo.training_program_template(template_code,status) VALUES (N'RHREF-BASE-01','ACTIVE');
    SET @t = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version(training_program_template_id,version_no,created_by_user_id,name,goals,suitable_horse_profile,recommended_distance_use_case,recommended_duration,workload_intensity_guidance,preferred_surfaces,recovery_guidance)
    VALUES (@t,1,NULL,N'Thể lực nền · Làm quen và phát triển nền tảng',N'Xây nền trước khi tăng tải. Mẫu tham khảo RHREF-BASE-01, không phải chương trình được chứng nhận; nguồn BHS-FITNESS và OSU-3983.',N'Ngựa đã được kiểm tra sức khỏe; Head Trainer cần xác định mức thể lực ban đầu. Không suy ra năng lực từ phả hệ.',N'Chuẩn bị thể lực chung; chưa mặc định cự ly thi đấu.',N'Head Trainer xác định sau đánh giá; không tự chuyển hướng dẫn thành ngày.',N'Bắt đầu bằng đi bộ có kiểm soát, tăng dần theo đáp ứng; chưa thêm tốc độ cao khi nền tảng chưa đủ.',N'Mặt sân phù hợp, ổn định; người phụ trách kiểm tra trước buổi.',N'Theo dõi phục hồi, khởi động và thả lỏng. Nguồn: https://extension.okstate.edu/fact-sheets/physical-conditioning-of-horses ; https://www.bhs.org.uk/horse-care-and-welfare/health-care-management/horse-health/horse-fitness/implementing-a-fitness-programme/ . Không dùng thay chỉ định thú y.');
    SET @v = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,1,N'Đánh giá và làm quen',N'Quan sát đáp ứng khi đi bộ.',N'Theo đánh giá đầu kỳ',N'Nhẹ, có kiểm soát.');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,2,N'Phát triển nền',N'Tăng khối lượng từng bước.',N'Theo tiến độ được duyệt',N'Không tăng nhiều yếu tố cùng lúc.');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,3,N'Đánh giá chuyển tiếp',N'Xem xét khả năng chuyển giai đoạn.',N'Đánh giá cuối khối',N'Chỉ tiến triển khi đáp ứng phù hợp.');
    INSERT INTO dbo.audit_log(action,target_type,target_id,safe_summary,source) VALUES ('REFERENCE_TEMPLATE_IMPORTED','TRAINING_PROGRAM_TEMPLATE',@t,N'Reference template RHREF-BASE-01 from training-catalog-20261027-v2; system initialization.','LOCAL_CATALOG_IMPORT');
END;
IF NOT EXISTS (SELECT 1 FROM dbo.training_program_template WHERE template_code=N'RHREF-ENDUR-01')
BEGIN
    INSERT INTO dbo.training_program_template(template_code,status) VALUES (N'RHREF-ENDUR-01','ACTIVE');
    SET @t = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version(training_program_template_id,version_no,created_by_user_id,name,goals,suitable_horse_profile,recommended_distance_use_case,recommended_duration,workload_intensity_guidance,preferred_surfaces,recovery_guidance)
    VALUES (@t,1,NULL,N'Sức bền · Duy trì vận động ổn định',N'Phát triển sức bền theo mục tiêu riêng. Mẫu tham khảo RHREF-ENDUR-01; nguồn UKY-ASC275 và OSU-3983. Không áp nguyên chương trình ngựa đường dài cho ngựa đua.',N'Ngựa có mục tiêu sức bền được Head Trainer xác nhận, đủ điều kiện y tế cho các bài đã chọn.',N'Cự ly và thời lượng do Head Trainer/Trainer lựa chọn theo thực tế, không tự gán cự ly thi đấu.',N'Cá thể hóa theo mục tiêu và đáp ứng',N'Ưu tiên nhịp ổn định; điều chỉnh tần suất, thời lượng và cường độ theo từng ngựa.',N'Sân và địa hình đã được kiểm tra.',N'Đánh giá khả năng phục hồi giữa buổi và giữa các ngày. Nguồn: https://publications.mgcafe.uky.edu/asc-275 . Tài liệu này bàn về ngựa trail/đường dài; mẫu chỉ vận dụng nguyên tắc cá thể hóa, không sao chép thành phác đồ đua.');
    SET @v = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,1,N'Mốc sức bền ban đầu',N'Xác định khối lượng đang đáp ứng được.',N'Theo đánh giá',N'Không bắt đầu ở mức tối đa.');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,2,N'Phát triển sức bền',N'Duy trì chất lượng khi tăng khối lượng.',N'Theo tiến độ',N'Tăng tải từng bước, theo dõi phản ứng.');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,3,N'Đối chiếu mục tiêu',N'So sánh kết quả quan sát được.',N'Cuối khối',N'Không bịa chỉ số thiếu đo đạc.');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'STAYING');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'ENDURANCE');
    INSERT INTO dbo.audit_log(action,target_type,target_id,safe_summary,source) VALUES ('REFERENCE_TEMPLATE_IMPORTED','TRAINING_PROGRAM_TEMPLATE',@t,N'Reference template RHREF-ENDUR-01 from training-catalog-20261027-v2; system initialization.','LOCAL_CATALOG_IMPORT');
END;
IF NOT EXISTS (SELECT 1 FROM dbo.training_program_template WHERE template_code=N'RHREF-STR-01')
BEGIN
    INSERT INTO dbo.training_program_template(template_code,status) VALUES (N'RHREF-STR-01','ACTIVE');
    SET @t = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version(training_program_template_id,version_no,created_by_user_id,name,goals,suitable_horse_profile,recommended_distance_use_case,recommended_duration,workload_intensity_guidance,preferred_surfaces,recovery_guidance)
    VALUES (@t,1,NULL,N'Sức mạnh · Kiểm soát vận động và địa hình',N'Phát triển khả năng vận động có kiểm soát. Mẫu tham khảo RHREF-STR-01; nguồn OSU-3983 và BHS-FITNESS.',N'Ngựa có nền tảng phù hợp và mục tiêu sức mạnh được xác nhận; không có hạn chế xung đột bài tập.',N'Không gắn mặc định với cự ly đua.',N'Theo đánh giá thể lực và địa hình',N'Có thể dùng địa hình dốc phù hợp để thay đổi tải; chỉ tăng dần sau khi đánh giá.',N'Địa hình có độ bám tốt; không mặc định mọi ngựa đều phù hợp tập dốc.',N'Luân phiên khối lượng phù hợp và thời gian hồi phục. Nguồn: https://extension.okstate.edu/fact-sheets/physical-conditioning-of-horses . Hạn chế y tế luôn được ưu tiên.');
    SET @v = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,1,N'Kiểm soát tải ban đầu',N'Đánh giá vận động trên sân hiện có.',N'Theo đánh giá',N'Tải thấp trước.');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,2,N'Phát triển sức mạnh',N'Chọn tải và địa hình phù hợp.',N'Theo tiến độ',N'Tăng dần, không tự áp tải bổ sung.');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,3,N'Củng cố chất lượng',N'Đối chiếu chất lượng và khả năng phục hồi.',N'Cuối khối',N'Giảm tải nếu chất lượng suy giảm.');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'STRENGTH');
    INSERT INTO dbo.audit_log(action,target_type,target_id,safe_summary,source) VALUES ('REFERENCE_TEMPLATE_IMPORTED','TRAINING_PROGRAM_TEMPLATE',@t,N'Reference template RHREF-STR-01 from training-catalog-20261027-v2; system initialization.','LOCAL_CATALOG_IMPORT');
END;
IF NOT EXISTS (SELECT 1 FROM dbo.training_program_template WHERE template_code=N'RHREF-BAL-01')
BEGIN
    INSERT INTO dbo.training_program_template(template_code,status) VALUES (N'RHREF-BAL-01','ACTIVE');
    SET @t = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version(training_program_template_id,version_no,created_by_user_id,name,goals,suitable_horse_profile,recommended_distance_use_case,recommended_duration,workload_intensity_guidance,preferred_surfaces,recovery_guidance)
    VALUES (@t,1,NULL,N'Thăng bằng · Nhịp và chuyển tiếp',N'Cải thiện chất lượng vận động. Mẫu tham khảo RHREF-BAL-01; nguồn BHS-FITNESS. Không tự thêm môn thi đấu khác vào hệ thống.',N'Ngựa cần cải thiện thăng bằng/nhịp theo đánh giá thực tế của Head Trainer.',N'Kỹ thuật nền, chưa mặc định thành tích cự ly.',N'Theo mức kỹ thuật hiện có',N'Ưu tiên chất lượng, các đoạn có kiểm soát và chuyển tiếp phù hợp.',N'Sân ổn định, không chuyển mặt sân đột ngột.',N'Giữ cơ hội đánh giá sau từng buổi. Nguồn: https://www.bhs.org.uk/horse-care-and-welfare/health-care-management/horse-health/horse-fitness/implementing-a-fitness-programme/ . Không dùng thay đánh giá thú y.');
    SET @v = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,1,N'Nhịp nền',N'Quan sát nhịp và sự ổn định.',N'Theo đánh giá',N'Nhẹ, ưu tiên chất lượng.');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,2,N'Chuyển tiếp có kiểm soát',N'Duy trì thăng bằng qua chuyển tiếp.',N'Theo tiến độ',N'Trainer chọn bài phù hợp.');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,3,N'Củng cố kỹ thuật',N'Đánh giá lại tính nhất quán.',N'Cuối khối',N'Không tăng tốc để che lỗi kỹ thuật.');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'BALANCE');
    INSERT INTO dbo.audit_log(action,target_type,target_id,safe_summary,source) VALUES ('REFERENCE_TEMPLATE_IMPORTED','TRAINING_PROGRAM_TEMPLATE',@t,N'Reference template RHREF-BAL-01 from training-catalog-20261027-v2; system initialization.','LOCAL_CATALOG_IMPORT');
END;
IF NOT EXISTS (SELECT 1 FROM dbo.training_program_template WHERE template_code=N'RHREF-SPEED-01')
BEGIN
    INSERT INTO dbo.training_program_template(template_code,status) VALUES (N'RHREF-SPEED-01','ACTIVE');
    SET @t = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version(training_program_template_id,version_no,created_by_user_id,name,goals,suitable_horse_profile,recommended_distance_use_case,recommended_duration,workload_intensity_guidance,preferred_surfaces,recovery_guidance)
    VALUES (@t,1,NULL,N'Tốc độ · Tăng tốc có kiểm soát',N'Phát triển tốc độ trên nền tảng đã được đánh giá. Mẫu tham khảo RHREF-SPEED-01; nguồn OSU-3983. Không mặc định năng lực từ giống hoặc phả hệ.',N'Head Trainer đã xác nhận nền tảng và mục tiêu tốc độ; ngựa đủ điều kiện thực hiện các bài được chọn.',N'Các đoạn tốc độ được cá thể hóa; không tự ấn định cự ly hay tốc độ đích.',N'Chỉ triển khai sau khi nền tảng phù hợp',N'Xen các đoạn nhanh được kiểm soát với hồi phục; không mặc định chạy hết sức.',N'Mặt sân thích hợp đã được kiểm tra.',N'Trainer theo dõi hồi phục trước khi lặp hoặc tăng tải. Nguồn: https://extension.okstate.edu/fact-sheets/physical-conditioning-of-horses . Không tự sinh ngưỡng nhịp tim, tốc độ hoặc công thức an toàn.');
    SET @v = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,1,N'Xác nhận nền tảng',N'Xác nhận điều kiện trước bài tốc độ.',N'Theo đánh giá',N'Chưa tăng tốc khi nền tảng chưa đủ.');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,2,N'Các đoạn tốc độ',N'Thực hiện mục tiêu từng đoạn.',N'Theo kế hoạch cá thể',N'Tải do người phụ trách chọn.');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,3,N'Đánh giá và điều chỉnh',N'Đối chiếu kết quả và phục hồi.',N'Cuối khối',N'Không suy diễn chỉ số chưa đo.');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'SPRINT');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'SPEED');
    INSERT INTO dbo.audit_log(action,target_type,target_id,safe_summary,source) VALUES ('REFERENCE_TEMPLATE_IMPORTED','TRAINING_PROGRAM_TEMPLATE',@t,N'Reference template RHREF-SPEED-01 from training-catalog-20261027-v2; system initialization.','LOCAL_CATALOG_IMPORT');
END;
IF NOT EXISTS (SELECT 1 FROM dbo.training_program_template WHERE template_code=N'RHREF-MAINT-01')
BEGIN
    INSERT INTO dbo.training_program_template(template_code,status) VALUES (N'RHREF-MAINT-01','ACTIVE');
    SET @t = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version(training_program_template_id,version_no,created_by_user_id,name,goals,suitable_horse_profile,recommended_distance_use_case,recommended_duration,workload_intensity_guidance,preferred_surfaces,recovery_guidance)
    VALUES (@t,1,NULL,N'Duy trì thể lực · Vận động nhẹ xen kẽ',N'Tổ chức khối lượng duy trì giữa các buổi chính. Mẫu tham khảo RHREF-MAINT-01; nguồn BHS-FITNESS và UKY-ASC275. Không phải phác đồ điều trị/phục hồi chấn thương.',N'Ngựa đã có nền tảng, được phép vận động và có mục tiêu duy trì được xác nhận.',N'Không đặt mục tiêu thành tích mới.',N'Theo lịch thực tế của chương trình',N'Tổ chức các buổi nhẹ phù hợp giữa những buổi chính; theo dõi phản ứng thực tế.',N'Sân quen thuộc, điều kiện phù hợp.',N'Khởi động, thả lỏng và xem xét đáp ứng. Nguồn: https://www.bhs.org.uk/horse-care-and-welfare/health-care-management/horse-health/horse-fitness/implementing-a-fitness-programme/ . Ngựa có vấn đề sức khỏe cần quy trình y tế riêng; mẫu không tự gỡ hạn chế.');
    SET @v = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,1,N'Rà khối lượng hiện tại',N'Xem lại các buổi chính đã có.',N'Theo lịch chương trình',N'Không nhân đôi tải đang có.');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,2,N'Vận động duy trì',N'Giữ chất lượng ở tải phù hợp.',N'Theo tiến độ',N'Điều chỉnh theo quan sát.');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,3,N'Rà soát kế hoạch tiếp',N'Báo cáo để Head Trainer quyết định.',N'Cuối khối',N'Không tự đổi chiến lược chương trình.');
    INSERT INTO dbo.audit_log(action,target_type,target_id,safe_summary,source) VALUES ('REFERENCE_TEMPLATE_IMPORTED','TRAINING_PROGRAM_TEMPLATE',@t,N'Reference template RHREF-MAINT-01 from training-catalog-20261027-v2; system initialization.','LOCAL_CATALOG_IMPORT');
END;
IF NOT EXISTS (SELECT 1 FROM dbo.training_program_template WHERE template_code=N'RHREF-SPRINT-01')
BEGIN
    INSERT INTO dbo.training_program_template(template_code,status) VALUES (N'RHREF-SPRINT-01','ACTIVE');
    SET @t = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version(training_program_template_id,version_no,created_by_user_id,name,goals,suitable_horse_profile,recommended_distance_use_case,recommended_duration,workload_intensity_guidance,preferred_surfaces,recovery_guidance)
    VALUES (@t,1,NULL,N'Nước rút · Chuẩn bị cự ly ngắn (dưới 1.400 m)',N'Chuẩn bị ngựa có thế mạnh nước rút: nền galop trước, sau đó các buổi breeze ngắn tăng dần để phát triển khả năng tăng tốc. Mẫu tham khảo RHREF-SPRINT-01; nguồn THEHORSE-BLEA, AQHA-RACE-FIT, RACEFORM-STAMINA, ANIMALS-2022-SCHRURS.',N'Ngựa đã kích hoạt, Veterinarian không hạn chế bài tốc độ, Club Manager khai báo thế mạnh nước rút hoặc tốc độ. Head Trainer xác nhận lại sau quan sát; không suy ra năng lực từ phả hệ.',N'Cự ly thi đấu dưới 1.400 m theo nhóm cự ly của RACEFORM-STAMINA. Nghiên cứu ANIMALS-2022-SCHRURS ghi nhận ngựa nước rút có sải ngắn hơn, tần số cao hơn và đi các furlong nhanh hơn khi xuất phát đứng.',N'Khoảng 8–12 tuần, Head Trainer điều chỉnh theo đáp ứng',N'Bắt đầu bằng vài tuần jog và galop dài tốc độ thấp, tăng quãng đường mỗi 2–3 tuần (AQHA-RACE-FIT). Breeze đầu tiên khoảng ¼ dặm (~400 m), mỗi lần thêm khoảng ⅛ dặm (~200 m) khi thể lực, độ lành lặn và tinh thần cho phép, ở khoảng 75–80% tốc độ đua, khoảng 6–7 ngày một lần (THEHORSE-BLEA). Không tăng nhiều yếu tố cùng lúc.',N'Mặt sân đã được kiểm tra trước buổi; nếu biết mặt sân thi đấu, ưu tiên tập trên mặt sân tương tự.',N'Chu kỳ tham khảo sau mỗi buổi breeze: hôm sau đi bộ, 1–2 ngày chạy nước kiệu (jog), 2–3 ngày galop, rồi mới breeze tiếp (THEHORSE-BLEA). Trước mỗi buổi nhanh: xem ngựa có xử lý tốt buổi trước, mức thở dốc, ăn uống, sức khỏe chung và mọi dấu hiệu chưa lành lặn. Hạn chế của Veterinarian luôn được ưu tiên.');
    SET @v = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,1,N'Nền jog và galop',N'Cho hệ cơ xương thích nghi trước khi thêm tốc độ; quan sát đáp ứng.',N'Vài tuần đầu, theo mức chuẩn bị',N'Thấp: jog, galop dài nhịp ổn định');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,2,N'Breeze ngắn tăng dần',N'Phát triển tăng tốc: breeze ~400 m, thêm ~200 m mỗi lần khi đáp ứng tốt.',N'Khoảng 6–7 ngày/lần breeze',N'Trung bình–cao: breeze 75–80% tốc độ đua, xen ngày nhẹ');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,3,N'Duy trì tốc độ và đánh giá',N'Giữ độ sắc của tốc độ, đánh giá sẵn sàng thi đấu cùng Head Trainer.',N'Theo lịch thi đấu được duyệt',N'Cao có kiểm soát; giữ chu kỳ hồi phục');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'SPRINT');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'FRONT_RUNNER');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'SPEED');
    INSERT INTO dbo.audit_log(action,target_type,target_id,safe_summary,source) VALUES ('REFERENCE_TEMPLATE_IMPORTED','TRAINING_PROGRAM_TEMPLATE',@t,N'Reference template RHREF-SPRINT-01 from training-catalog-20261027-v2; system initialization.','LOCAL_CATALOG_IMPORT');
END;
IF NOT EXISTS (SELECT 1 FROM dbo.training_program_template WHERE template_code=N'RHREF-ROUTE-01')
BEGIN
    INSERT INTO dbo.training_program_template(template_code,status) VALUES (N'RHREF-ROUTE-01','ACTIVE');
    SET @t = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version(training_program_template_id,version_no,created_by_user_id,name,goals,suitable_horse_profile,recommended_distance_use_case,recommended_duration,workload_intensity_guidance,preferred_surfaces,recovery_guidance)
    VALUES (@t,1,NULL,N'Cự ly dặm – trung bình · 1.400–2.399 m',N'Cân bằng sức bền và tốc độ cho ngựa thi đấu cự ly dặm hoặc trung bình, tập giữ vị trí bám đuổi rồi tăng tốc. Mẫu tham khảo RHREF-ROUTE-01; nguồn THEHORSE-BLEA, AQHA-RACE-FIT, RACEFORM-STAMINA, BRISNET-RUNSTYLE.',N'Ngựa đã kích hoạt có khai báo cự ly dặm, cự ly trung bình hoặc lối chạy bám đuổi; không bị Veterinarian hạn chế bài nhanh. Head Trainer xác nhận lại sau quan sát.',N'Nhóm cự ly 1.400–1.999 m (dặm) và 2.000–2.399 m (trung bình) theo RACEFORM-STAMINA. Lối chạy bám đuổi: giữ gần nhóm đầu rồi bứt lên ở đoạn cuối (BRISNET-RUNSTYLE).',N'Khoảng 10–14 tuần, Head Trainer điều chỉnh theo đáp ứng',N'Giai đoạn nền dài hơn mẫu nước rút: galop chậm quãng dài để phát triển hiếu khí, tăng quãng đường mỗi 2–3 tuần; sau đó xen các buổi nhanh vào giữa các ngày chậm (AQHA-RACE-FIT). Breeze bắt đầu ~400 m, tăng ~200 m mỗi lần, 75–80% tốc độ đua, khoảng 6–7 ngày/lần (THEHORSE-BLEA).',N'Mặt sân đã kiểm tra; ưu tiên mặt sân trùng với mặt sân ngựa được khai báo chạy tốt.',N'Chu kỳ tham khảo sau mỗi buổi breeze: hôm sau đi bộ, 1–2 ngày chạy nước kiệu (jog), 2–3 ngày galop, rồi mới breeze tiếp (THEHORSE-BLEA). Duy trì các buổi chậm quãng dài ngay cả khi đã có bài nhanh để giữ sức chịu đựng của cơ và xương. Hạn chế của Veterinarian luôn được ưu tiên.');
    SET @v = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,1,N'Nền hiếu khí',N'Galop chậm quãng dài, tăng quãng đường từng bước.',N'Khoảng 4–6 tuần',N'Thấp–trung bình');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,2,N'Kết hợp nhịp và tăng tốc',N'Xen breeze vào giữa các ngày chậm; tập giữ nhịp ổn định rồi tăng tốc đoạn cuối.',N'Khoảng 6–7 ngày/lần breeze',N'Trung bình–cao, không liên tiếp hai ngày nhanh');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,3,N'Hoàn thiện và đánh giá',N'Đánh giá khả năng giữ tốc độ ở cự ly mục tiêu do Head Trainer chọn.',N'Theo lịch thi đấu được duyệt',N'Cao có kiểm soát; giữ chu kỳ hồi phục');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'MILE');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'MIDDLE_DISTANCE');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'STALKER');
    INSERT INTO dbo.audit_log(action,target_type,target_id,safe_summary,source) VALUES ('REFERENCE_TEMPLATE_IMPORTED','TRAINING_PROGRAM_TEMPLATE',@t,N'Reference template RHREF-ROUTE-01 from training-catalog-20261027-v2; system initialization.','LOCAL_CATALOG_IMPORT');
END;
IF NOT EXISTS (SELECT 1 FROM dbo.training_program_template WHERE template_code=N'RHREF-STAY-01')
BEGIN
    INSERT INTO dbo.training_program_template(template_code,status) VALUES (N'RHREF-STAY-01','ACTIVE');
    SET @t = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version(training_program_template_id,version_no,created_by_user_id,name,goals,suitable_horse_profile,recommended_distance_use_case,recommended_duration,workload_intensity_guidance,preferred_surfaces,recovery_guidance)
    VALUES (@t,1,NULL,N'Đường dài · Cự ly từ 2.400 m',N'Phát triển sức bền cho ngựa đường dài và lối chạy bứt phá cuối: khối lượng hiếu khí lớn, bài nhanh thưa hơn, giữ sức cho đoạn cuối. Mẫu tham khảo RHREF-STAY-01; nguồn AQHA-RACE-FIT, THEHORSE-BLEA, RACEFORM-STAMINA, BRISNET-RUNSTYLE, ANIMALS-2022-SCHRURS.',N'Ngựa đã kích hoạt có khai báo đường dài, sức bền hoặc lối chạy bứt phá cuối. Nhiều ngựa chuyển sang cự ly dài hơn khi trưởng thành (ANIMALS-2022-SCHRURS); Head Trainer xác nhận lại sau quan sát.',N'Cự ly từ 2.400 m theo nhóm cự ly của RACEFORM-STAMINA. Lối chạy bứt phá cuối: đi phía sau rồi tăng tốc một lần ở cuối cuộc đua (BRISNET-RUNSTYLE).',N'Khoảng 12–16 tuần, Head Trainer điều chỉnh theo đáp ứng',N'Ưu tiên galop chậm quãng dài để tăng khả năng hiếu khí, sức chịu của chi và thích nghi cơ; tăng quãng đường mỗi 2–3 tuần (AQHA-RACE-FIT). Thêm breeze dài dần ở 75–80% tốc độ đua khi nền đã đủ (THEHORSE-BLEA). Không bịa chỉ số tim mạch khi không đo được.',N'Mặt sân ổn định, đã kiểm tra; tránh đổi mặt sân đột ngột trong giai đoạn tăng khối lượng.',N'Chu kỳ tham khảo sau mỗi buổi breeze: hôm sau đi bộ, 1–2 ngày chạy nước kiệu (jog), 2–3 ngày galop, rồi mới breeze tiếp (THEHORSE-BLEA). Theo dõi phục hồi giữa các ngày vì khối lượng tích lũy lớn; giảm tải khi ngựa ăn kém hoặc thở dốc kéo dài. Hạn chế của Veterinarian luôn được ưu tiên.');
    SET @v = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,1,N'Nền hiếu khí mở rộng',N'Galop chậm quãng dài, tăng quãng đường từng bước.',N'Khoảng 6–8 tuần',N'Thấp–trung bình, nhịp đều');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,2,N'Sức bền và tăng tốc cuối',N'Giữ nhịp quãng dài, kết thúc bằng đoạn tăng tốc ngắn có kiểm soát.',N'Theo tiến độ được duyệt',N'Trung bình–cao, bài nhanh thưa');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,3,N'Hoàn thiện cự ly',N'Đánh giá khả năng giữ sức ở cự ly mục tiêu.',N'Theo lịch thi đấu được duyệt',N'Cao có kiểm soát; ưu tiên hồi phục');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'STAYING');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'CLOSER');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'ENDURANCE');
    INSERT INTO dbo.audit_log(action,target_type,target_id,safe_summary,source) VALUES ('REFERENCE_TEMPLATE_IMPORTED','TRAINING_PROGRAM_TEMPLATE',@t,N'Reference template RHREF-STAY-01 from training-catalog-20261027-v2; system initialization.','LOCAL_CATALOG_IMPORT');
END;
IF NOT EXISTS (SELECT 1 FROM dbo.training_program_template WHERE template_code=N'RHREF-TURF-01')
BEGIN
    INSERT INTO dbo.training_program_template(template_code,status) VALUES (N'RHREF-TURF-01','ACTIVE');
    SET @t = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version(training_program_template_id,version_no,created_by_user_id,name,goals,suitable_horse_profile,recommended_distance_use_case,recommended_duration,workload_intensity_guidance,preferred_surfaces,recovery_guidance)
    VALUES (@t,1,NULL,N'Mặt sân · Làm quen và tập trên sân cỏ',N'Giúp ngựa có thế mạnh sân cỏ làm quen mặt sân thi đấu và duy trì chất lượng sải trên cỏ. Mẫu tham khảo RHREF-TURF-01; nguồn RRP-TURF-DIRT, THEHORSE-BLEA.',N'Ngựa đã kích hoạt có khai báo thế mạnh sân cỏ. Huấn luyện viên thường nhận ra sở thích mặt sân qua cách ngựa di chuyển trên sân (RRP-TURF-DIRT); Head Trainer xác nhận.',N'Kết hợp với mẫu cự ly phù hợp; mẫu này chỉ tập trung vào mặt sân.',N'Khoảng 4–6 tuần, có thể chạy song song khối cự ly',N'Bắt đầu bằng đi bộ, jog và galop nhẹ trên cỏ; chỉ thêm breeze trên cỏ khi ngựa di chuyển tự tin. Mức độ cứng–mềm của sân cỏ thay đổi theo thời tiết; ghi nhận điều kiện sân mỗi buổi.',N'Sân cỏ đã kiểm tra độ cứng và độ trơn trước buổi.',N'Chu kỳ tham khảo sau mỗi buổi breeze: hôm sau đi bộ, 1–2 ngày chạy nước kiệu (jog), 2–3 ngày galop, rồi mới breeze tiếp (THEHORSE-BLEA). Theo dõi dáng đi và cơ sau các buổi đầu trên cỏ. Hạn chế của Veterinarian luôn được ưu tiên.');
    SET @v = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,1,N'Làm quen sân cỏ',N'Đi bộ, jog, galop nhẹ để ngựa quen mặt sân.',N'Khoảng 1–2 tuần',N'Thấp');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,2,N'Galop và breeze trên cỏ',N'Đưa các bài galop và breeze của khối cự ly lên sân cỏ.',N'Theo tiến độ được duyệt',N'Trung bình–cao có kiểm soát');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'TURF');
    INSERT INTO dbo.audit_log(action,target_type,target_id,safe_summary,source) VALUES ('REFERENCE_TEMPLATE_IMPORTED','TRAINING_PROGRAM_TEMPLATE',@t,N'Reference template RHREF-TURF-01 from training-catalog-20261027-v2; system initialization.','LOCAL_CATALOG_IMPORT');
END;
IF NOT EXISTS (SELECT 1 FROM dbo.training_program_template WHERE template_code=N'RHREF-DIRT-01')
BEGIN
    INSERT INTO dbo.training_program_template(template_code,status) VALUES (N'RHREF-DIRT-01','ACTIVE');
    SET @t = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version(training_program_template_id,version_no,created_by_user_id,name,goals,suitable_horse_profile,recommended_distance_use_case,recommended_duration,workload_intensity_guidance,preferred_surfaces,recovery_guidance)
    VALUES (@t,1,NULL,N'Mặt sân · Tập trên sân đất và sân tổng hợp',N'Giúp ngựa có thế mạnh sân đất hoặc sân tổng hợp làm quen mặt sân thi đấu và chuyển đổi giữa hai loại sân. Mẫu tham khảo RHREF-DIRT-01; nguồn RRP-TURF-DIRT, THEHORSE-BLEA.',N'Ngựa đã kích hoạt có khai báo thế mạnh sân đất hoặc sân tổng hợp; Head Trainer xác nhận sau quan sát.',N'Kết hợp với mẫu cự ly phù hợp; mẫu này chỉ tập trung vào mặt sân.',N'Khoảng 4–6 tuần, có thể chạy song song khối cự ly',N'Tập dần trên mặt sân thi đấu; khi chuyển giữa sân tổng hợp và sân đất, giảm tốc độ các buổi đầu để ngựa thích nghi với độ bám và độ lún khác nhau.',N'Sân đất hoặc sân tổng hợp đã kiểm tra; ghi nhận tình trạng sân (khô, ướt) mỗi buổi.',N'Chu kỳ tham khảo sau mỗi buổi breeze: hôm sau đi bộ, 1–2 ngày chạy nước kiệu (jog), 2–3 ngày galop, rồi mới breeze tiếp (THEHORSE-BLEA). Theo dõi dáng đi sau khi đổi loại sân. Hạn chế của Veterinarian luôn được ưu tiên.');
    SET @v = SCOPE_IDENTITY();
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,1,N'Làm quen mặt sân',N'Đi bộ, jog, galop nhẹ trên mặt sân mục tiêu.',N'Khoảng 1–2 tuần',N'Thấp');
    INSERT INTO dbo.template_version_phase(template_version_id,phase_order,name,objective,recommended_duration,intensity_guidance) VALUES (@v,2,N'Galop và breeze trên mặt sân',N'Đưa các bài của khối cự ly lên mặt sân thi đấu.',N'Theo tiến độ được duyệt',N'Trung bình–cao có kiểm soát');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'DIRT');
    INSERT INTO dbo.template_version_strength_focus(template_version_id,strength_code) VALUES (@v,'SYNTHETIC');
    INSERT INTO dbo.audit_log(action,target_type,target_id,safe_summary,source) VALUES ('REFERENCE_TEMPLATE_IMPORTED','TRAINING_PROGRAM_TEMPLATE',@t,N'Reference template RHREF-DIRT-01 from training-catalog-20261027-v2; system initialization.','LOCAL_CATALOG_IMPORT');
END;
IF NOT EXISTS (SELECT 1 FROM dbo.skill WHERE kind='RIDER_SKILL' AND name=N'Tập tốc độ và breeze') INSERT INTO dbo.skill(name,kind) VALUES (N'Tập tốc độ và breeze','RIDER_SKILL');
IF NOT EXISTS (SELECT 1 FROM dbo.skill WHERE kind='RIDER_SKILL' AND name=N'Giữ nhịp cự ly dài') INSERT INTO dbo.skill(name,kind) VALUES (N'Giữ nhịp cự ly dài','RIDER_SKILL');
IF NOT EXISTS (SELECT 1 FROM dbo.skill WHERE kind='RIDER_SKILL' AND name=N'Tập trên sân cỏ') INSERT INTO dbo.skill(name,kind) VALUES (N'Tập trên sân cỏ','RIDER_SKILL');
IF NOT EXISTS (SELECT 1 FROM dbo.skill WHERE kind='RIDER_SKILL' AND name=N'Tập trên sân đất và sân tổng hợp') INSERT INTO dbo.skill(name,kind) VALUES (N'Tập trên sân đất và sân tổng hợp','RIDER_SKILL');
IF NOT EXISTS (SELECT 1 FROM dbo.skill WHERE kind='RIDER_SKILL' AND name=N'Kiểm soát sức mạnh và thăng bằng') INSERT INTO dbo.skill(name,kind) VALUES (N'Kiểm soát sức mạnh và thăng bằng','RIDER_SKILL');
IF NOT EXISTS (SELECT 1 FROM dbo.skill WHERE kind='RIDER_SKILL' AND name=N'Xử lý ngựa khó') INSERT INTO dbo.skill(name,kind) VALUES (N'Xử lý ngựa khó','RIDER_SKILL');
COMMIT TRANSACTION;
