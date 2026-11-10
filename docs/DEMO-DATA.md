# Dữ liệu demo theo từng phần

Buổi demo chạy trên máy Long. Thành viên chạy cùng script trên DB local của mình để tập trước.
Script chỉ gọi API của BE đang chạy, mỗi lần chạy tạo một bộ ngựa mới có nhãn thời gian
(ví dụ `H-khóa 1109-1738`), nên chạy lại bao nhiêu lần cũng không đụng dữ liệu cũ.

## Chuẩn bị (mỗi máy một lần)

1. BE chạy ở `http://127.0.0.1:8080` với DB của mình (xem `docs/TEAM-DATABASE-SETUP.md`).
2. Cần ít nhất 16 ô chuồng trống. API không tạo được ô chuồng, nên thêm tối đa 99 ô `C-01 … C-99` vào DB của mình (mỗi lần chạy script tạo dữ liệu dùng 15 ô; hết ô trống thì chạy lại file này hoặc tăng số trong file):

```powershell
sqlcmd -S localhost -E -l 90 -d <tên DB của bạn> -i scripts/demo/add-demo-stable-boxes.sql
```

File chỉ thêm ô còn thiếu, không sửa hay xóa gì, chạy lại an toàn.

## Chạy

Chạy khoảng 15 đến 30 phút trước khi demo/tập. Buổi tập bắt đầu được sau khoảng 6 phút và mở trong 4 giờ
(đổi bằng `RH_DEMO_HOURS`). Kiểm tra trước buổi tập chỉ hợp lệ trong 12 giờ trước giờ bắt đầu, nên đừng chạy từ hôm trước.

```powershell
$env:RH_DEMO_CM_EMAIL = 'clubmanager@gmail.com'   # máy thành viên: email Club Manager seed của máy mình
$env:RH_DEMO_CM_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host 'Mật khẩu Club Manager' -AsSecureString)).Password
$env:RH_DEMO_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host 'Mật khẩu chung cho tài khoản demo' -AsSecureString)).Password
node scripts/demo/seed-demo-data.mjs
```

Script dùng các tài khoản giống máy Long: `headtrainer@gmail.com`, `trainer1@gmail.com`, `veterinarian@gmail.com`,
`groom@gmail.com`, `workrider1/2/3@gmail.com`, `horseowner1/2@gmail.com`. Tài khoản nào chưa có thì Club Manager tạo;
có rồi nhưng sai mật khẩu thì Club Manager đặt lại thành `RH_DEMO_PASSWORD` (chỉ trên DB local của máy đó).
Script không in mật khẩu.

Kết quả cần thấy: `DEMO_DATA_PASS tag MMDD-HHMM` và bảng SUMMARY liệt kê ngựa, mã buổi tập, số ca cho từng phần.
Chỉ tạo một phần: `$env:RH_DEMO_SETS = 'T,Y'`.

## Bộ dữ liệu

| Bộ | Người | Nội dung |
|---|---|---|
| D | Dũng | `D-chờ-khám` (nháp, đủ hồ sơ, chỉ chờ Vet khám ban đầu); `D-hoạt-động` (của horseowner1); `D-của-chủ-2` (của horseowner2) |
| H | Hoàng | `H-chưa-có-chương-trình` (hạn chế đã gỡ); `H-có-chương-trình` (giao trainer1, 2 đề xuất điều chỉnh chờ duyệt); `H-khóa` (Khóa); `H-hạn-chế` (cấm "Breeze nước rút") |
| T | Tiến | `T-tập-đủ` (chưa kiểm tra, workrider1); `T-kiểm-tra-bất-thường` (chưa kiểm tra, workrider3); `T-báo-vấn-đề` (đã kiểm tra đạt, workrider2); buổi đã hoàn thành chờ trainer1 đánh giá (trên ngựa T-tập-đủ) |
| Y | Đức | `Y-nhẹ-và-tối-thiểu` (2 sự cố Mới báo); `Y-đầy-đủ` (sự cố Khập khiễng mức cao); `Y-ca-đang-mở` (ca mở có phác đồ, 1 việc Groom chưa làm, kèm 1 sự cố Mới báo để gắn ca); `Y-ca-sẵn-sàng-đóng` (ca có phác đồ, việc Groom đã xong, đang Khóa, sức khỏe Chấn thương) |
| L | Long | `L-chăm-sóc` (kế hoạch chăm sóc: 1 việc **quá giờ**, 1 việc **cần làm**, 1 việc 23:30 **chưa đến giờ**; kế hoạch dinh dưỡng: Cỏ khô 4 kg + Thức ăn hạt 2 kg); vật tư `Cỏ khô demo` 200 kg; 2 yêu cầu đăng ký chờ duyệt |

Mỗi bộ dùng ngựa riêng, nên thao tác ở bộ này không làm đổi dữ liệu của bộ khác.
