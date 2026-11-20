# Cập nhật DB cho DEC-016 (sơ đồ chuồng + thanh toán)

Backend bản DEC-016 kiểm tra schema khi khởi động (`ddl-auto: validate`). DB chưa có 3 migration dưới đây thì **backend không chạy được**.

| Thứ tự | Migration | Nội dung |
|---|---|---|
| 7 | `V20261120_01__stable_layout_and_service_billing.sql` | Khu chuồng (lưới hàng × cột), vị trí ô, bảo trì ô, bảng giá, hóa đơn, giao dịch |
| 8 | `V20261120_02__payment_confirmed_via_return.sql` | Cho phép xác nhận VNPay từ trang trả về có chữ ký |
| 9 | `V20261120_03__club_bank_account_and_qr_transfer.sql` | Tài khoản nhận tiền của CLB, thanh toán chuyển khoản VietQR |

Cả 3 file **chỉ thêm**: bảng mới, cột mới, ràng buộc mới. Không xóa dòng dữ liệu nào và **chạy lại nhiều lần vẫn an toàn**.
- Ô cũ nằm ngoài sơ đồ mà còn trống thì chuyển sang `RETIRED`. Dòng vẫn được giữ lại, chỉ không xếp ngựa vào được nữa.
- Ngựa đang ở ô ngoài sơ đồ thì được giữ nguyên. Sơ đồ báo "ngoài sơ đồ" để Club Manager chuyển ngựa đi.

## Cách 1: DB đang dùng (giữ dữ liệu)

Mở PowerShell trong thư mục repo BE, thay `<TEN_DB>` bằng tên DB của bạn.

```powershell
$db = '<TEN_DB>'
sqlcmd -S localhost -E -I -b -Q "BACKUP DATABASE [$db] TO DISK=N'$db-truoc-DEC016.bak' WITH COPY_ONLY, INIT, CHECKSUM"
Push-Location database/migrations
foreach ($f in 'V20261120_01__stable_layout_and_service_billing.sql','V20261120_02__payment_confirmed_via_return.sql','V20261120_03__club_bank_account_and_qr_transfer.sql') {
  sqlcmd -S localhost -E -I -b -l 120 -d $db -f 65001 -i $f
  if ($LASTEXITCODE -ne 0) { throw "Loi o $f" }
}
Pop-Location
sqlcmd -S localhost -E -I -h -1 -W -d $db -Q "SELECT COUNT(*) AS so_khu FROM dbo.stable_yard; SELECT COUNT(*) AS so_o_trong_so_do FROM dbo.stable_box WHERE stable_yard_id IS NOT NULL"
```

Kết quả mong đợi: `so_khu = 3`, `so_o_trong_so_do = 60`.

Lưu ý: dùng **đường dẫn tương đối** (`Push-Location` như trên). sqlcmd chạy trong Git Bash với đường dẫn tuyệt đối dài có thể báo lỗi lạ.

## Cách 2: Cài DB mới

`scripts/db/Install-LocalDatabase.ps1` đã có đủ **9 migration** và kiểm tra schema chuẩn `database/verification/expected-schema-v20261120.json` (62 bảng). Cài như trước đây.

## Sau khi cập nhật

1. Đăng nhập Club Manager → **Phí dịch vụ & hóa đơn → Bảng giá**. Đặt giá và nhập **Tài khoản nhận tiền**.
2. Muốn thử VNPay sandbox: đặt biến môi trường `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` (xin trưởng nhóm, **không commit**). Không có key thì nút VNPay bị khóa, còn thanh toán QR vẫn dùng bình thường.

Quyết định chi tiết: `project-docs/decisions/DEC-016-stable-layout-and-owner-billing.md` (workspace dự án).
