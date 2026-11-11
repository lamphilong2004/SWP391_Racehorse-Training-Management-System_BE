# Sáng 11/11 — review 09:30–12:00 trên máy Long

Dữ liệu demo **đã tạo sẵn đêm 11/11**, dùng tên thật cho người, ngựa, phả hệ và kho. Sáng nay **KHÔNG chạy lại script tạo dữ liệu.**

- **Nhãn bộ dữ liệu:** `1111-0120`. Nhãn này chỉ còn xuất hiện trong email của 2 người đăng ký; tên ngựa không còn kèm nhãn.
- **Bản code:**
  - FE: bản đang chạy ở 5173 = nhánh `release/review-final-1111` (PR #47, chờ 1 người duyệt rồi merge vào `main`).
  - BE: `main` `1b3f004`. Code Java giống hệt jar đang chạy; các commit sau đó chỉ thêm script, tài liệu và lịch sử PR.
- **Mật khẩu mọi tài khoản demo:** `123456789a`

## 1. Trước 09:15

1. Cắm sạc, tắt chế độ ngủ. Tắt Claude, Codex, Zalo và các ứng dụng không dùng (máy chỉ có 8 GB RAM).
2. Bật SQL Server, rồi chạy `START-RACEHORSE-LOCAL.cmd`, hoặc chạy launcher:
   `pwsh -NoProfile -File _gate-monitor/enterprise-v4/Start-LocalReview.ps1 -PreparedDemo -FrontendPort 5173`
3. Mở http://localhost:5173. Đăng nhập lần lượt 7 tài khoản, mỗi tài khoản mở 1–2 trang để hệ thống "nóng" trước khi review.
4. Nhờ 1 thành viên (không phải Long) **Approve PR #47** trên FE rồi bấm Merge. Sau khi merge, GitHub tự đánh dấu các PR cũ của thành viên là Merged.

## 2. Tài khoản

| Vai trò | Email | Tên hiển thị |
|---|---|---|
| Club Manager | clubmanager@gmail.com | Lâm Quốc Bảo |
| Head Trainer | headtrainer@gmail.com | Nguyễn Hoàng Phúc |
| Trainer | trainer1@gmail.com (dùng cho demo) · trainer@ · trainer2@ · trainer3@ | Lê Minh Quân · Lê Thành Đạt · Đỗ Hà Linh · Vũ An Nhiên |
| Veterinarian | veterinarian@gmail.com · veterinarian1@ | Phạm Thanh Tâm · Hoàng Mai Anh |
| Groom | groom@gmail.com · groom1@ · groom2@ | Trần Văn Hậu · Lý Văn Tài · Mai Thị Hồng |
| Work Rider | workrider1@ · workrider2@ · workrider3@ · workrider@ | Phạm Đức Anh · Võ Thanh Tùng · Đặng Khánh Linh · Huỳnh Gia Bảo |
| Horse Owner | horseowner1@gmail.com · horseowner2@ · horseowner3@ · horseowner@ | Nguyễn Minh Khang · Trần Bảo Ngọc · Lê Gia Huy · Đặng Thu Hương |

## 3. Dữ liệu đã có (17 ngựa, mỗi con 1 ảnh khác nhau; chỉ 17 ô chuồng có ngựa, 112 ô trống)

| Người | Ngựa (mã) | Tình huống | Dùng để |
|---|---|---|---|
| Dũng | **Phong Vân** (RH-001) | nháp, chờ Vet khám ban đầu | Vet khám ban đầu → tự Hoạt động |
| | **Hoàng Kim** (RH-002) | Hoạt động, của Nguyễn Minh Khang (horseowner1) | cổng chủ ngựa chỉ thấy ngựa của mình |
| | **Ngọc Trai** (RH-003) | của Trần Bảo Ngọc (horseowner2) | horseowner1 không thấy con này |
| Hoàng | **Thanh Long** (RH-004) | chưa có chương trình | wizard tạo chương trình |
| | **Kim Sư** (RH-005) | có chương trình giao Lê Minh Quân, 2 đề xuất chờ duyệt | duyệt 1 / từ chối 1 |
| | **Mặc Vũ** (RH-006) | đang Khóa | lập chương trình bị chặn |
| | **Bão Cát** (RH-007) | Hạn chế, cấm "Breeze nước rút" | Trainer tạo buổi có bài này bị chặn |
| Tiến | **Xích Thố** | buổi 09:30–12:00, workrider1, chưa kiểm tra, ngưỡng nhịp tim 180 do Vet đặt; thêm 1 buổi đã hoàn thành chờ đánh giá | Groom kiểm tra Bình thường → Rider bắt đầu, nhập số đo (nhịp tim > 180 → cảnh báo), kết thúc; Trainer đánh giá |
| | **Lôi Phong** | buổi 09:30–12:00, workrider3, chưa kiểm tra | Groom kiểm tra Bất thường → báo sự cố, buổi bị chặn |
| | **Ánh Dương** | buổi 09:30–12:00, workrider2, đã kiểm tra đạt | Rider báo Khập khiễng khi đang cưỡi |
| Đức | **Sương Mai** (RH-011) | 2 sự cố Mới báo (Bỏ ăn, Vấn đề móng) | đóng ngay; quy trình tối thiểu |
| | **Hắc Phong** (RH-012) | sự cố Khập khiễng mức cao | quy trình đầy đủ: chấn thương khai báo bằng chữ (DEC-015), phác đồ chọn Groom theo tên, Khóa |
| | **Tuyết Sơn** (RH-013) | ca #1 đang mở + 1 sự cố mới | gắn sự cố vào ca |
| | **Thiên Mã** (RH-014) | ca #2, việc Groom đã xong, đang Khóa | tái khám → gỡ hạn chế → đóng ca |
| | **Liên Hoa** (RH-015) | sự cố từ phiếu kiểm tra trước buổi tập | nguồn "Kiểm tra trước buổi tập" |
| | **Phi Vân** (RH-016) | sự cố Work Rider báo khi đang cưỡi | nguồn "Buổi tập" |
| Long | **An Khang** (RH-017) | việc chăm sóc 08:00 quá giờ, 11:00 cần làm, 23:30 chưa đến giờ; khẩu phần 11:00 | chăm sóc hôm nay; đổi Groom / hủy việc; Groom hoàn thành; báo bất thường |
| | kho **Cỏ khô Timothy** | 200 kg, ngưỡng 50 kg | nhập kho; Groom yêu cầu bổ sung |
| | đăng ký chờ duyệt | **Bùi Thị Lan** `lan.bui.11110120@gmail.com` · **Phan Quốc Việt** `viet.phan.11110120@gmail.com` | duyệt 1 (gán vai trò) / từ chối 1; phân quyền; nhật ký |

Mỗi ngựa có phả hệ thật (ví dụ Phong Vân: cha Dubawi, mẹ Princess Highway), giới tính, năm sinh và nước xuất xứ riêng.

**2 ngựa Long tự tạo trong buổi review:** dùng 2 ảnh trong thư mục `ANH-NGUA-CHO-MAI/` (`ngua-moi-1.jpg`, `ngua-moi-2.jpg`). Hai ảnh này khác hẳn 17 ảnh đã dùng. Khi chọn ô chuồng, chọn một ô "Còn trống" bất kỳ.

Buổi tập của Tiến chỉ bắt đầu được trong khung 09:30–12:00; việc chăm sóc chỉ hoàn thành được khi đã đến giờ. Đây là quy tắc nghiệp vụ, không phải lỗi.

## 4. Nếu có sự cố

- **BE tắt:** chạy lại launcher, đợi khoảng 30 giây, đăng nhập lại. Dữ liệu không mất.
- **Bản sao lưu đã kiểm tra** (thư mục `C:\Program Files\Microsoft SQL Server\MSSQL16.MSSQLSERVER\MSSQL\Backup\`):
  - `Racehorse_training_demo_final_11110120.bak`: đúng dữ liệu demo sáng nay, chưa ai thao tác.
  - `Racehorse_training_before_final_reset_20261111_0130.bak`: dữ liệu trước lần reset cuối (gồm các thao tác Long thử tối 10/11).
- **Nếu bắt buộc phải tạo lại dữ liệu** (trước 09:30): `RH_PW=123456789a bash .preview-local/release-20261111/reseed-final.sh`. Script reset dữ liệu nghiệp vụ, đặt lại tên thật cho tài khoản rồi chạy script tạo dữ liệu với tên thật. Nhãn mới sẽ khác.
