# Điểm bắt đầu đọc Backend cho phần Vet/Groom và History

Đây là mục lục học code và phối hợp FE–BE. Các hướng dẫn ghi rõ hành vi hiện có, không nhận tác quyền implementation thay cho thành viên khác và không bổ sung contract mới.

## Chọn tài liệu theo câu hỏi

| Muốn hiểu | Tài liệu |
|---|---|
| Vet lưu ca/khám/phác đồ, transaction và checklist đóng ca | [MEDICAL-CASE.md](MEDICAL-CASE.md) |
| Groom kiểm tra trước buổi tập, cửa sổ 12 giờ, nguồn sự cố và Vet xử lý | [PRECHECK-INCIDENT.md](PRECHECK-INCIDENT.md) |
| Khám ban đầu khác với cân thêm, quyền đọc cân nặng, kích hoạt tự động | [BASELINE-ACTIVATION.md](BASELINE-ACTIVATION.md) |

## Đi từ màn FE đến lớp Java

| Thao tác FE | Controller nhận HTTP | Service kiểm nghiệp vụ |
|---|---|---|
| Tạo/đọc ca, khám, chấn thương, phác đồ, tái khám, đóng ca | [MedicalController](../../src/main/java/com/racehorse/core035/MedicalController.java) | [MedicalService](../../src/main/java/com/racehorse/core035/MedicalService.java) |
| Báo sự cố/tiếp nhận, gắn ca hoặc đóng sự cố; precheck | [Core026Controller](../../src/main/java/com/racehorse/medical/incident/Core026Controller.java) | [Core026Service](../../src/main/java/com/racehorse/medical/incident/Core026Service.java) |
| Groom xem/hoàn thành việc điều trị | [GroomTreatmentTaskController](../../src/main/java/com/racehorse/medical/groom/GroomTreatmentTaskController.java) | [GroomTreatmentTaskService](../../src/main/java/com/racehorse/medical/groom/GroomTreatmentTaskService.java) |
| Đọc khả năng tập, tạo/gỡ hạn chế | [MedicalRestrictionController](../../src/main/java/com/racehorse/medical/restriction/api/MedicalRestrictionController.java) | [MedicalRestrictionService](../../src/main/java/com/racehorse/medical/restriction/MedicalRestrictionService.java) |
| Khám ban đầu/cân nặng | [HorseBaselineController](../../src/main/java/com/racehorse/horse/baseline/HorseBaselineController.java) | [HorseBaselineService](../../src/main/java/com/racehorse/horse/baseline/HorseBaselineService.java) |
| Lịch chăm sóc, dinh dưỡng, kho/yêu cầu bổ sung | [StableCareController](../../src/main/java/com/racehorse/stablecare/StableCareController.java) | [StableCareService](../../src/main/java/com/racehorse/stablecare/StableCareService.java) |

Đây là các điểm code liên quan khi phối hợp, không phải phân công nhận làm lại phần Backend của cả nhóm.

## Cách lần một request trong VS Code

1. FE gọi URL nào và dùng GET/POST/PUT? Ghi đúng URL từ Network, không đoán theo tên menu.
2. Trong BE nhấn **Ctrl+Shift+F** tìm phần đường dẫn endpoint. Class có thể dùng prefix ở `@RequestMapping` và phần còn lại ở `@GetMapping`/`@PostMapping`.
3. Mở method controller, xem DTO đầu vào và method service được gọi. Nhấn **F12** để đi tiếp.
4. Trong service, tìm kiểm actor/role/scope, điều kiện nghiệp vụ, transaction rồi mới đến repository/JdbcTemplate.
5. Đọc response DTO và status trả ra. Chỉ sau thành công mới so với dữ liệu GET/SQL readback của đúng DB.

Controller định tuyến không đồng nghĩa ai cũng gọi được endpoint. DTO validation cũng không thay thế việc kiểm người dùng được giao đúng ngựa/session/task.

## Hiểu lỗi trước khi sửa

| Biểu hiện | Kiểm tra trước | Không nên kết luận ngay |
|---|---|---|
| 401 | Phiên/JWT còn hợp lệ và request có đi qua đúng auth client không | Không coi là DB thiếu dữ liệu rồi seed lại |
| 403 | Role và scope của ngựa/session/task; ai đang được phân công | Không bỏ RBAC hoặc mượn tài khoản quyền cao để coi flow đã PASS |
| 404 | ID/tài nguyên có trong chính DB đang chạy không | Không dùng ID từ môi trường khác |
| 409 hoặc lỗi business rule | Trạng thái hiện tại, thời gian, task/ca đang được xử lý; đọc message cụ thể | Không gửi lại liên tục hay sửa SQL để vượt gate |
| 500 | Log cùng thời điểm/request, nguyên nhân transaction/SQL và cấu hình runtime | Không đổi thành response thành công giả ở FE |

## Phân biệt ba mức bằng chứng

- Đọc code/tài liệu: giải thích service dự định và hiện đang làm gì.
- Test profile H2/MockMvc: kiểm hành vi trong môi trường test cô lập, không chứng nhận SQL Server demo.
- FE → BE → SQL thật: cần phiên bản FE/BE, tên DB được phép, thời gian, role, request/status và dữ liệu đọc lại. Không đăng JWT/mật khẩu khi chia sẻ evidence.

Khi chỉ bổ sung mục lục hoặc comment, kiểm liên kết và diff là phù hợp; không ghi/xóa dữ liệu để chứng minh một chỉnh sửa tài liệu. Luật runtime vẫn ở source/contract được nhóm duyệt.
