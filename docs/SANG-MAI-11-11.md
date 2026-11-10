# Sáng 11/11 — review 09:30–12:00 trên máy Long

Dữ liệu demo **đã tạo sẵn từ tối 10/11**. Sáng mai **KHÔNG chạy lại script tạo dữ liệu.**

- **Nhãn bộ dữ liệu:** `1110-2245`
- **Bản code:**
  - FE `main` `981fe36`
  - BE `main` `25c2002`. Code Java giống hệt jar đang chạy (`96401af`); bản `25c2002` chỉ thêm script và tài liệu.

## 1. Trước 09:15

1. Cắm sạc, tắt chế độ ngủ. Tắt Claude, Codex, Zalo và các ứng dụng không dùng (máy chỉ có 8 GB RAM).
2. Bật SQL Server, rồi chạy `START-RACEHORSE-LOCAL.cmd`, hoặc chạy launcher:
   `pwsh -NoProfile -File _gate-monitor/enterprise-v4/Start-LocalReview.ps1 -PreparedDemo -FrontendPort 5173`
3. Mở http://localhost:5173. Đăng nhập lần lượt 7 tài khoản, mỗi tài khoản mở 1–2 trang để hệ thống "nóng" trước khi review.

## 2. Dữ liệu đã có (17 ngựa, mỗi con 1 ảnh khác nhau; chỉ 17 ô chuồng có ngựa, 112 ô trống)

| Người | Ngựa / dữ liệu | Dùng để |
|---|---|---|
| Dũng | **D-chờ-khám** (RH-001, nháp) · **D-hoạt-động** (RH-002, của horseowner1) · **D-của-chủ-2** (RH-003, của horseowner2) | Vet khám ban đầu → tự Hoạt động; cổng chủ ngựa chỉ thấy ngựa của mình |
| Hoàng | **H-chưa-có-chương-trình** (RH-004) · **H-có-chương-trình** (RH-005, 2 đề xuất chờ duyệt) · **H-khóa** (RH-006) · **H-hạn-chế** (RH-007, cấm "Breeze nước rút") | Wizard tạo chương trình; duyệt / từ chối đề xuất; bị chặn khi Khóa; bị chặn bài cấm |
| Tiến | Buổi **09:30–12:00**: **T-tập-đủ** (workrider1, chưa kiểm tra, ngưỡng nhịp tim 180) · **T-kiểm-tra-bất-thường** (workrider3, chưa kiểm tra) · **T-báo-vấn-đề** (workrider2, **đã kiểm tra đạt**) · 1 buổi **đã hoàn thành chờ đánh giá** | Groom kiểm tra Bình thường / Bất thường → báo sự cố; Rider bắt đầu, nhập số đo (nhịp tim > 180 → cảnh báo CRITICAL), dừng vì lý do sức khỏe; Rider báo Khập khiễng khi đang cưỡi; Trainer đánh giá |
| Đức | **Y-nhẹ-và-tối-thiểu** (Bỏ ăn + Vấn đề móng) · **Y-đầy-đủ** (Khập khiễng mức cao) · **Y-ca-đang-mở** (ca #1 + 1 sự cố để gắn ca) · **Y-ca-sẵn-sàng-đóng** (ca #2, đang Khóa) · **Y-groom-báo-kiểm-tra** (sự cố **từ phiếu kiểm tra trước buổi tập**) · **Y-rider-báo-khi-cưỡi** (sự cố **Work Rider báo khi đang cưỡi**) | Danh sách sự cố có cột người báo và nguồn; đóng ngay; quy trình tối thiểu; quy trình đầy đủ (chấn thương **khai báo bằng chữ**, DEC-015); phác đồ chọn Groom **theo tên**; gắn ca; tái khám → gỡ hạn chế → đóng ca |
| Long | **L-chăm-sóc** (RH-017): việc **08:00 quá giờ**, **11:00 cần làm**, 23:30 chưa đến giờ; khẩu phần 11:00 · kho **Cỏ khô demo** · đăng ký chờ duyệt `demo.dangky1/2.11102245@gmail.com` | Chăm sóc hôm nay; đổi Groom / hủy việc; Groom hoàn thành; báo bất thường; kho; duyệt / từ chối đăng ký; phân quyền; nhật ký |

**2 ngựa Long tự tạo trong buổi review:** dùng 2 ảnh trong thư mục `ANH-NGUA-CHO-MAI/` (`ngua-moi-1.jpg`, `ngua-moi-2.jpg`). Hai ảnh này khác hẳn 17 ảnh đã dùng. Khi chọn ô chuồng, chọn một ô "Còn trống" bất kỳ.

## 3. Nếu có sự cố

- **BE tắt:** chạy lại launcher, đợi khoảng 30 giây, đăng nhập lại. Dữ liệu không mất.
- **Cần quay về dữ liệu cũ trước khi reset:** bản sao lưu đã kiểm tra nằm ở
  `C:\Program Files\Microsoft SQL Server\MSSQL16.MSSQLSERVER\MSSQL\Backup\Racehorse_training_before_demo_reset_20261110_2250.bak`.
- **Nếu bắt buộc phải tạo lại dữ liệu:** dùng `.preview-local/release-20261111/reset-demo-data-20261110.sql` (chỉ sau khi đã sao lưu), rồi chạy script tạo dữ liệu với `RH_DEMO_START` và `RH_DEMO_PHOTO_DIR` như trong `docs/DEMO-DATA.md`. Phải chạy trong vòng 12 giờ trước 09:30.
