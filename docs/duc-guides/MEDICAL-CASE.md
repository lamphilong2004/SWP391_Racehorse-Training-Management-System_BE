# Đọc code Backend ca y tế — CORE-035

Tài liệu hướng dẫn đọc và giải thích nghiệp vụ, không nhận quyền tác giả cho phần code của thành viên khác. Không đổi API, schema, SQL hay quy tắc y tế.

## Mở từ đâu

- [MedicalController.java](../../src/main/java/com/racehorse/core035/MedicalController.java): nhận HTTP và chuyển DTO vào service.
- [MedicalService.java](../../src/main/java/com/racehorse/core035/MedicalService.java): xác thực actor, kiểm nghiệp vụ, gọi storage và ghép response.
- [MedicalPorts.java](../../src/main/java/com/racehorse/core035/MedicalPorts.java): các ranh giới storage/actor/materializer mà service cần.

Controller không phải nơi FE được tự quyết định ai là Vet. `vet()` lấy actor từ phiên xác thực và kiểm role. Request không thể cấp quyền chỉ bằng cách gửi một chuỗi role.

## Một lần bấm Lưu đi qua đâu

1. FE gửi request bằng shared HTTP client/JWT.
2. Controller đọc JSON thành request DTO; validation từ DTO và service đều có thể từ chối.
3. `open(id)` tìm và khóa ca: không tồn tại thì NOT_FOUND; CLOSED thì CONFLICT, không tự mở lại.
4. `examine`, `injure`, `putPlan`, `reexamine` kiểm đúng dữ liệu rồi ghi trong transaction.
5. Service tạo response của bản ghi đã lưu; FE đọc lại để hiển thị, không được báo thành công trước bước này.

`examine` và `reexamine` có thể cập nhật health_status khi request có trường này. Chúng không tự gỡ medical restriction. `injure` kiểm lần khám được tham chiếu phải thuộc đúng ca và đúng ngựa. Thuốc trong `putPlan` đang là nội dung văn bản, không phải danh mục thuốc mới.

## Phác đồ và việc Groom

- `putPlan` kiểm nội dung điều trị, ngày/người nhận/hướng dẫn task và các task trùng.
- Có task cần giao nhưng materializer chưa tích hợp thì trả lỗi; không giả rằng Groom đã nhận việc.
- Materializer reconcile danh sách task trong luồng lưu phác đồ; không được hiểu rằng lưu lại phác đồ luôn tạo một task trùng mới.
- Khi Groom hoàn thành, kết quả nằm ở care_task. Việc hoàn thành không phải quyền cập nhật chẩn đoán hay gỡ hạn chế.

## Điều kiện đóng ca, theo `requireClosureConditions`

- Có ít nhất một bản ghi khám lâm sàng.
- Nếu có chấn thương hoặc phác đồ, lần tái khám mới nhất phải RECOVERED.
- Khi có phác đồ, các task điều trị liên quan phải có cả người và thời gian hoàn thành.
- Các restriction gắn đúng ca phải được gỡ. Không suy ra ca này sẽ gỡ mọi restriction của cùng ngựa.
- `close` chuyển ca sang CLOSED và xử lý các incident liên quan theo DEC-014 trong cùng transaction; không cần bắt người demo đóng incident bằng tay trước để vượt điều kiện.

## Quyền đọc và cách trình bày

Vet được đọc chi tiết lâm sàng. Manager/HT và Trainer theo phạm vi được phép có projection riêng; không tái sử dụng toàn bộ bệnh án Vet cho summary. Đọc `readRole`, `get`/`list` và `trainerAssigned` khi gặp 403; không sửa role hoặc bỏ kiểm scope để demo.

OPEN/CLOSED là trạng thái ca; FIT/MONITORING/INJURED/QUARANTINED là sức khỏe; AVAILABLE/LIMITED/LOCKED là khả năng tập. Hồi phục và đóng ca không đồng nghĩa Rider đã có phân công/đến giờ để bắt đầu tập.

Tài liệu này mô tả code, không phải bằng chứng đã chạy API/SQL. Khi kiểm runtime cần ghi riêng FE SHA, BE SHA, DB, thời gian và response/readback trên đúng môi trường.
