# Đọc code khám ban đầu, cân nặng và kích hoạt — CORE-014

Đây là tài liệu đọc code dùng khi phối hợp FE Vet/History. Quyền tác giả implementation theo lịch sử Git, không thay đổi vì phần hướng dẫn này.

Nguồn: [HorseBaselineService.java](../../src/main/java/com/racehorse/horse/baseline/HorseBaselineService.java) và [HorseActivationService.java](../../src/main/java/com/racehorse/horse/activation/HorseActivationService.java).

## Phân biệt hai thao tác Vet

| Method | Ý nghĩa | Không được suy ra |
|---|---|---|
| recordBaseline | Ghi lần khám ban đầu và cân nặng ban đầu của ngựa | Không phải mỗi lần cân đều tạo baseline mới |
| recordWeight | Ghi thêm một số đo cân nặng vào lịch sử | Không tự tạo lần khám, tự đổi sức khỏe hay gọi kích hoạt |

## `recordBaseline` làm những gì trong một transaction

1. Lấy actor đang ACTIVE; yêu cầu Veterinarian.
2. Kiểm ngựa tồn tại và chưa có baseline. Request thứ hai có thể bị 409, không ghi đè lần khám đầu.
3. Kiểm ngày giờ khám hợp lệ bằng `examinationTime`.
4. Ghi veterinary examination và weight của cùng ngựa/người/thời điểm.
5. Cập nhật health_status được Vet chọn.
6. Gọi `activationService.reevaluate(horseId)` để xem các điều kiện kích hoạt đã đủ chưa.
7. Trả id/thời gian của bản ghi thật đã lưu.

Transaction bao các bước này để tránh trạng thái dở dang khi một bước ghi thất bại. Nó không thay thế kiểm quyền và không phải lý do bỏ qua lỗi SQL. DataIntegrityViolation do trùng baseline được đổi thành xung đột phù hợp; lỗi integrity khác vẫn được ném tiếp.

## Kích hoạt tự động, không thêm nút duyệt tay

Khám ban đầu là một điều kiện, không phải lời hứa mọi ngựa được khám đều chuyển ACTIVE. Service activation còn kiểm hồ sơ, phả hệ, chủ sở hữu và ô chuồng theo rule hiện hành. HT readiness là khuyến nghị, không được tự biến thành một gate bắt buộc mới ở FE.

FE nên đọc lại lifecycle/checklist sau lưu. Không tự gán ACTIVE chỉ vì nút Lưu đã được nhấn, và không tạo endpoint manual approval để vượt bước còn thiếu.

## `recordWeight` và ngày giờ

- Chỉ Vet được ghi thêm; cần ngựa tồn tại và tài khoản hợp lệ.
- Không gửi recordedAt thì dùng thời điểm hiện tại; timestamp tương lai bị từ chối.
- Service quy về UTC với độ chính xác millisecond trước khi ghi. UI muốn hiện ngày giờ Việt Nam phải chuyển ở lớp trình bày, không đổi dữ liệu DB bằng giờ máy tùy ý.
- Một response thành công có id/số đo/thời gian. Biểu đồ phải dựa trên lịch sử đọc lại, không tự thêm một điểm giả khi POST lỗi.

## Quyền đọc cân nặng

`listWeights` kiểm role và scope. Trainer thông thường chỉ đọc ngựa được giao; các role đọc không giới hạn được liệt kê riêng trong code. Owner có projection riêng, không được suy ra Owner có quyền dùng toàn bộ endpoint bệnh án chỉ vì giao diện có biểu đồ.

Khi demo phải ghi đúng DB/ngựa/SHA và phân biệt đọc code với bằng chứng SQL thật. Tài liệu không cấp tài khoản mới, không lưu mật khẩu và không reset dữ liệu.
