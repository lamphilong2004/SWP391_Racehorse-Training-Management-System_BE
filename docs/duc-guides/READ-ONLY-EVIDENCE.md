# Đọc lại SQL cho ca y tế mà không sửa dữ liệu

Mẫu [duc-medical-readback.sql](../examples/duc-medical-readback.sql) chỉ đọc metadata của một ngựa: vòng đời/sức khỏe, ca, task điều trị, restriction và incident. Nó không phải migration, seed, công cụ sửa lỗi hay script nghiệm thu tự động.

## Trước khi dùng

1. Chỉ dùng trên DB local/test được phép. Trong SSMS chọn đúng DB và ghi lại tên môi trường; `DB_NAME()` trong kết quả giúp phát hiện chọn nhầm.
2. Xác nhận ngựa theo tên/mã/nhãn bộ dữ liệu ở UI hiện hành, lấy id của chính DB đó rồi thay `@horseId = NULL` bằng id đã xác nhận.
3. Để nguyên NULL sẽ báo dừng, không tự chọn ngựa 1/7 hoặc mã ca trong báo cáo máy khác.
4. DB cần schema/migrations của repo hiện hành, gồm cột `assigned_groom_user_id` từ migration V20261020_01. Không tự ALTER schema nếu cột thiếu; đối chiếu [hướng dẫn cài DB](../TEAM-DATABASE-SETUP.md) trước.

Script không chứa INSERT, UPDATE, DELETE, MERGE, tạo/xóa bảng, seed hoặc gọi stored procedure. SET NOCOUNT và DECLARE chỉ áp dụng trong lượt chạy; THROW chặn đầu vào còn thiếu. Không có thay đổi schema hay dữ liệu được lưu bởi mẫu này.

## Đối chiếu từng kết quả

| Kết quả SQL | So với UI/API nào | Không được suy ra |
|---|---|---|
| horse.lifecycle, health_status | Hồ sơ và health projection của đúng ngựa | FIT không có nghĩa mọi restriction đã được gỡ |
| medical_case.status, closed_at | Vet xem lại đúng ca sau POST close | CLOSED không chứng minh một session khác đã được Rider bắt đầu |
| Task gắn treatment_plan → case, người nhận/người làm/thời gian | Groom `/care/tasks` và Vet đọc progress của đúng phác đồ | Một task xong không có nghĩa mọi task/ngày khác đều xong |
| restriction.lifted_at | Vet xem lại restriction đã gỡ | Không có dòng cho một ca không chứng minh ngựa không còn restriction khác; phải đọc theo toàn ngựa/API availability |
| Incident source/case/status | Trang sự cố sau tiếp nhận, gắn ca hoặc đóng | Status SQL không chứng minh tài khoản Groom/Owner có quyền đọc API bệnh án |

Mẫu tránh xuất triệu chứng, chẩn đoán, nội dung thuốc và mật khẩu. Dù vậy, chỉ chia sẻ phần kết quả cần thiết và được phép; SQL dùng quyền tài khoản DB, không chạy qua role guard của ứng dụng.

## Ghi bằng chứng chính xác

- Lưu FE SHA, BE SHA, DB, thời gian, role → route → thao tác → method/path/status → kết quả đọc lại.
- SELECT không tạo dữ liệu. Không có dòng có thể do ngựa chưa có ca/task hoặc đang chọn sai ID/DB; không tự chuyển thành PASS.
- Dữ liệu hiện tại không tái tạo được toàn bộ trạng thái quá khứ. Giữ bản readback cũ và timestamp khi đã có evidence; không sửa SQL để làm khớp một ảnh cũ.
- Đọc SQL bằng tài khoản chủ DB không phải test RBAC. Muốn kiểm quyền phải dùng phiên của role và API thật riêng.

## Phạm vi kiểm tra mẫu

Tên bảng/cột được đối chiếu với [schema.sql](../../database/schema.sql) và [migration Groom assignment](../../database/migrations/V20261020_01__add_care_task_assigned_groom_user_id.sql). Mẫu không tự gắn dấu PASS và chưa được coi là bằng chứng E2E chỉ vì có trong repository. Cần ghi riêng kết quả mỗi lần thực sự dùng trên một môi trường cụ thể.

Mẫu đã được kiểm cú pháp bằng SQL Server PARSEONLY trong batch riêng, không thực thi các SELECT của mẫu. Kiểm cú pháp không thay thế kiểm schema/migration hay kết quả đọc thật ở DB đích.
