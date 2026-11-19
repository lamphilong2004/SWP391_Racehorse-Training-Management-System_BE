# Đọc code kiểm tra trước buổi tập và sự cố — CORE-026

Tài liệu hỗ trợ hiểu phần giao giữa Groom/Vet và Training. Code Backend phối hợp có lịch sử tác giả riêng; thêm ghi chú không có nghĩa tác giả tài liệu đã viết lại API.

Điểm bắt đầu: [Core026Service.java](../../src/main/java/com/racehorse/medical/incident/Core026Service.java) và [Core026Repository.java](../../src/main/java/com/racehorse/medical/incident/Core026Repository.java).

## Vì sao Groom không thấy hoặc không lưu được kiểm tra

Trong `submitPrecheck`, thứ tự quan trọng là:

1. Phải là Groom từ phiên xác thực, không lấy userId tùy ý từ form.
2. Khóa session và tìm session thật; chỉ nhận khi SCHEDULED.
3. Groom phải có phân công/thông báo đúng session. Chọn Groom nhưng chưa lưu không đủ.
4. Session chưa có kiểm tra trước đó; mỗi session chỉ có một precheck theo contract hiện hành.
5. Hiện tại phải trong cửa sổ từ 12 giờ trước scheduledStart đến planned end; nếu không có end thì dùng quy tắc fallback trong code.
6. Request có đúng 5 key: GENERAL_CONDITION, APPETITE_BEHAVIOUR, LEGS_HOOVES, MOVEMENT, TEMPERATURE.
7. Mỗi mục NORMAL hoặc ABNORMAL. Tất cả NORMAL mới thành ALL_NORMAL; một mục ABNORMAL làm tổng ABNORMAL và phát thông báo theo service.

Nhấn lưu precheck không tự bắt đầu cưỡi. Rider còn phải đúng phân công, giờ tập và điều kiện y tế. Không đổi đồng hồ/DB để ép session qua gate.

## Nguồn sự cố phải thuộc cùng ngựa

`create` nhận tối đa một trong preSessionCheckId, trainingSessionId, careTaskId. Service đọc context thật để kiểm horseId, không tin rằng form đã chọn đúng.

- Có precheck: lấy session/program từ precheck đó.
- Có session: ngựa trong session phải đúng horseId.
- Có care task: task phải thuộc ngựa, Groom được giao và ngày đến hạn được phép báo.
- Báo tại chuồng không có context: Groom chỉ báo theo phạm vi ngựa ACTIVE/đang ở chuồng mà code cho phép.
- Trainer/Rider phải đúng scope; HTTP 403 không phải lỗi cần bỏ qua bằng cách dùng tài khoản quyền cao.

Service chặn nhiều incident trỏ cùng source context và xung đột ghi đồng thời. Nếu nhận 409, đọc lại trước khi thử; không tự đổi sourceId để tạo bản sao.

## Vet xử lý khác với người báo

`review` chỉ cho Veterinarian:

- OPEN_CASE: incident phải REPORTED và chưa gắn ca; server tự tạo id ca.
- LINK_CASE: chọn ca đã có, phải OPEN và cùng ngựa; không có nghĩa hai báo cáo chắc chắn là cùng bệnh.
- RESOLVE: chỉ chuyển từ REPORTED hoặc UNDER_REVIEW; không nhận caId tùy ý cho action này.

`transitionIncident` dùng trạng thái cũ mong đợi để phát hiện người khác đã sửa trước. Không cập nhật UI như thành công nếu server báo xung đột.

## Câu nói khi demo

“Groom và Rider ghi điều quan sát; Vet quyết định xử lý y tế. Nguồn báo cáo cho biết phát hiện ở đâu, không tự chẩn đoán. Hoàn tất việc chăm sóc không tự gỡ restriction.”

Tài liệu không tạo mẫu dữ liệu, không thay khung thời gian, không xác nhận E2E trên máy khác. Nếu sessionId/horseId giống nhau ở hai DB, vẫn phải kiểm tên/nhãn dữ liệu của từng môi trường.
