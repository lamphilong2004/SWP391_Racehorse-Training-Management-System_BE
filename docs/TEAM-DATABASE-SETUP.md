# Cài DB mới cho từng thành viên — 09/11/2026

Đây là bộ **tạo database local mới**, không phải bản sao dữ liệu/mật khẩu của Long.
Mỗi máy dùng cùng schema và sáu migrations; dữ liệu thao tác độc lập.
DB đang demo trên máy Long (`Racehorse_training`) không bị thay đổi bởi việc đóng gói này.
Không chạy script xóa trên máy Long. Không xóa DB trước khi bản mới đã chạy được.

## 1. Lấy đúng source

Giữ lại mọi WIP trước khi chuyển nhánh. Trong từng repository, chỉ pull fast-forward:

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
git rev-parse HEAD
```

Nếu Git báo local changes hoặc diverged, dừng; không reset/force-push.
BE cần có `scripts/db/Retire-OldLocalDatabase.ps1` và manifest gồm sáu migration,
kết thúc bằng `V20261109_01`. Báo lại kết quả `git rev-parse HEAD` của cả hai repository.
FE main cần có PR #37 (merge commit `0a315f6`); BE main cần có PR #38 (`e68a43b`) và commit chứa tài liệu này.

## 2. Chuẩn bị máy

- Windows, PowerShell 7, Java 21, Maven; Node >=22.13 cho FE.
- SQL Server 2022 Developer local, dịch vụ Running, TCP endpoint local hoạt động.
  Không mở cổng SQL ra Internet; installer không sửa service, firewall hoặc certificate.
- SQL certificate còn hạn, Server Authentication EKU, SAN khớp hostname JDBC,
  SQL service đọc được private key. Chủ máy tự cấu hình nếu cần maintenance.
- Lấy **public** CA/certificate của chính máy mình, kiểm tra fingerprint, rồi tạo
  truststore Java PKCS12 riêng bằng `keytool -importcert` (nhập mật khẩu tương tác).
  Không chia sẻ PFX/private key, không `sqlcmd -C`, không bỏ TLS validation.
- Cần quyền CREATE DATABASE và đọc metadata để cài; quyền runtime giới hạn vào DB của mình.
  Chỉ bước tùy chọn xóa DB cũ cần quyền BACKUP/RESTORE VERIFYONLY/DROP và VIEW SERVER STATE.
- JDBC driver jar và DLL Windows integrated authentication phải cùng phiên bản/kiến trúc.
  Có thể lấy jar từ dependency Maven đã resolve trong repository.

## 3. Cấu hình riêng, tạo và verify DB

Mở PowerShell 7 tại BE repository. Đọc `docs/examples/local-first.env.example`.
Template không tự được Spring hoặc PowerShell nạp. Thiết lập biến trong shell riêng:

```powershell
# Thay duc bằng tên mình. Tên mới phải chưa tồn tại; không dùng DB demo đang chạy.
$env:RH_DATABASE = 'racehorse_local_duc_20261109'
$env:RH_SQL_HOST = 'localhost'
$env:RH_SQL_PORT = '1433'
$env:RH_SQL_CERT_HOST = Read-Host 'Hostname có trong SAN certificate SQL của máy bạn'
$env:RH_SQL_TRUSTSTORE = Read-Host 'Đường dẫn truststore .p12 của máy bạn'
$env:RH_SQL_TRUSTSTORE_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host 'Mật khẩu truststore' -AsSecureString)).Password
$env:RH_JDBC_DRIVER = Read-Host 'Đường dẫn mssql-jdbc jar'
$env:RH_JDBC_NATIVE_DIR = Read-Host 'Thư mục DLL integrated-auth khớp driver'
# Mặc định Windows Authentication của chính người chạy:
$env:RH_SQL_USERNAME = $null; $env:RH_SQL_PASSWORD = $null
$env:DB_USERNAME = $null; $env:DB_PASSWORD = $null

./scripts/db/Install-LocalDatabase.ps1 -SeedTestAccounts -SeedReferenceData
./scripts/db/Verify-LocalDatabase.ps1
```

Nếu dùng SQL Authentication, thiết lập `RH_SQL_USERNAME`, nhập `RH_SQL_PASSWORD`
bằng Read-Host -AsSecureString tương tự trên, đồng thời đặt `DB_USERNAME`/`DB_PASSWORD`
cho tài khoản runtime của chính máy bạn. Không lưu secrets vào source hoặc history.

Kết quả cần thấy: `LOCAL_INSTALL_PASS`, `LOCAL_RECEIPT_AND_LIVE_SCHEMA_PASS`,
`strictTlsEncryptedTcp:true`, collation `SQL_Latin1_General_CP1_CI_AS`.
Verifier đối chiếu cấu trúc thực tế: bảng/cột/PK/FK/CHECK/index/default/collation;
không chỉ đếm bảng. Hiện có 56 bảng/526 cột sau sáu migrations.
Normalized email giữ `Latin1_General_100_BIN2` theo DB-01.

Installer từ chối mọi DB đã tồn tại, kể cả DB rỗng hoặc cài dở. Không reset/drop tự động.
Receipt nằm ngoài Git tại `%LOCALAPPDATA%/Racehorse/LocalDb/<DB>/receipt.json`.
Receipt không phải giấy phép migrate DB cũ. Không tự sửa byte schema/migration.

Seed có 12 ô chuồng, danh mục chuyên môn/kỹ năng và bảy tài khoản ACTIVE để kiểm thử local.
Passwords BCrypt được tạo ngẫu nhiên theo máy, DPAPI bảo vệ dưới Windows user hiện tại.
Không sử dụng mật khẩu demo của Long. Không kèm ngựa/ca bệnh/lịch cũ hoặc ảnh cá nhân.
Tạo ngựa và dữ liệu nghiệp vụ qua API/UI thật; không INSERT để giả lập trạng thái thành công.
Các template/exercise nghiệp vụ cần tạo qua UI hoặc seed được review riêng; không giả định có sẵn.

## 4. Chạy FE/BE và đăng nhập

Trong shell BE còn RH_*:

```powershell
mvn -B -ntp -DskipTests package
./scripts/local/Start-LocalBackend.ps1 -Port 8080
```

Launcher bind loopback, JPA validate, strict TLS, pool4, heap tối đa384MB;
JWT key sinh riêng và lưu DPAPI. Ảnh ngựa lưu tại thư mục runtime local cạnh receipt.
Không chạy demo seeder hoặc profile cloud. Giữ thư mục ảnh khi cần backup/di chuyển dữ liệu.

Trong terminal khác tại FE repository:

```powershell
npm ci
$env:VITE_API_BASE_URL = '/'
$env:VITE_API_PROXY_TARGET = 'http://127.0.0.1:8080'
$env:VITE_BUSINESS_DATA_SOURCE = 'api'
$env:VITE_ROLE_TEST_LOGIN = 'false'
npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

Mở http://localhost:5173. Không mở thêm server khi cổng đã có đúng Racehorse.
Launcher BE cho phép cả localhost và127.0.0.1:5173.

Trong PowerShell riêng, chọn đúng DB và role, lấy email và copy password vào clipboard
trực tiếp từ DPAPI; **không Write-Output password**, không chạy trong transcript/stream:

```powershell
$db = 'racehorse_local_duc_20261109'
$role = 'CLUB_MANAGER' # HEAD_TRAINER/TRAINER/WORK_RIDER/VETERINARIAN/GROOM/HORSE_OWNER
$accounts = Get-Content "$env:LOCALAPPDATA/Racehorse/LocalDb/$db/accounts.dpapi.json" -Raw | ConvertFrom-Json
$account = @($accounts | Where-Object role -eq $role)
if ($account.Count -ne 1) { throw 'ACCOUNT_NOT_UNIQUE' }
$account[0].email
Set-Clipboard -Value ([Net.NetworkCredential]::new('', ($account[0].encryptedPassword | ConvertTo-SecureString)).Password)
# Paste vào form login, sau đó xóa clipboard:
# Set-Clipboard -Value ''
```

Verify: login đúng role -> tạo ngựa DRAFT, upload ảnh -> danh sách/chi tiết/refresh
đọc lại dữ liệu thật. Vet khám; hoàn thiện owner/pedigree/ô chuồng theo UI để kích hoạt.
Thành viên tự báo SQL version, manifest version, checksum, strictTLS, BE startup, login/readback.
PASS trên máy Long không phải PASS máy khác. Không chạy full suite để chỉ kiểm tra cài đặt.

## 5. Tùy chọn xóa đúng DB cũ — sau khi bản mới chạy được

Đây là thao tác mất DB cũ. Không bắt buộc để chạy ứng dụng. Giữ một DB hoạt động sau khi
người vận hành xác nhận bản mới đủ dữ liệu cần giữ. Script không chuyển dữ liệu nghiệp vụ
hay thư mục ảnh cũ; backup .bak không chứa file ảnh trên filesystem.

1. Xác định chính xác tên DB cũ trên chính máy mình; không đoán theo ví dụ.
2. Đóng BE cũ và các kết nối SSMS đang trỏ DB đó. Không dừng SQL service.
3. Chọn file .bak **chưa tồn tại** trong thư mục local SQL service có quyền ghi.
4. `$env:RH_DATABASE` vẫn trỏ bản mới đã install/verify; cũ và mới phải khác tên.
5. Chạy PLAN trước, kiểm tra tên/máy/path, rồi thêm `-Execute` và xác nhận PowerShell:

```powershell
./scripts/db/Retire-OldLocalDatabase.ps1 `
  -OldDatabase 'racehorse_old_REPLACE_WITH_ACTUAL_NAME' `
  -ConfirmOldDatabase 'racehorse_old_REPLACE_WITH_ACTUAL_NAME' `
  -ExpectedMachine $env:COMPUTERNAME `
  -BackupFile 'C:\REPLACE_WITH_SQL_BACKUP_FOLDER\old-racehorse-unique.bak'
# Chạy lại đúng lệnh với -Execute CHỈ khi đã kiểm tra mọi giá trị.
```

Script verify schema mới trước, kiểm tra local instance, nhận diện bảng Racehorse của DB cũ,
không có session/transaction, backup FULL COPY_ONLY CHECKSUM, RESTORE VERIFYONLY CHECKSUM,
kiểm tra backup header/DB GUID và kiểm tra lại workload rồi DROP **đúng một tên đã xác nhận**.
Không wildcard, không KILL, không ROLLBACK IMMEDIATE, không overwrite backup.
Nếu lỗi bất cứ gate nào thì dừng; không dùng force hoặc tự restore đè DB khác.
Nếu workload xuất hiện ngay lúc DROP, SQL có thể từ chối; giữ backup, kiểm tra rồi xử lý riêng.
Script backup/xóa chưa được chạy trên DB thật trong đợt đóng gói này; chỉ có fixture/guard tests.
Backup chứa dữ liệu cũ nhạy cảm: giữ local an toàn, không đưa vào ZIP/Git hoặc gửi chung.

## 6. Lỗi thường gặp và phục hồi

- PKIX/hostname mismatch: dùng đúng CA/public chain, SAN và truststore của máy mình;
  không bật trustServerCertificate. Không chia sẻ key/PFX của Long.
- 08S01/refused: chủ máy kiểm tra SQL Running và TCP local; không tự đổi firewall/service.
- CREATE/BACKUP permission denied: nhờ chủ máy cấp đúng quyền local, không sửa SQL credentials chung.
- DATABASE_ALREADY_EXISTS: chọn tên mới; không chạy schema lên DB cũ.
- SCHEMA_DRIFT: giữ DB, lưu lỗi/manifest; không dùng Hibernate update hoặc sửa verifier để vượt lỗi.
- Native auth DLL: khớp Windows architecture, driver version và RH_JDBC_NATIVE_DIR.
- 401: dùng đúng email trong accounts.dpapi.json của DB mới; kiểm tra BE đang trỏ DB nào.
- 403: kiểm tra role/phạm vi; không nới quyền để vượt lỗi.
- 8080/5173 bận: xác minh process trước; không kill ứng dụng không liên quan.
- Backup VERIFYONLY không bằng full restore rehearsal. Nếu cần restore, dùng tên DB mới,
  RESTORE FILELISTONLY và MOVE đến file mới; không WITH REPLACE. Không tự restore vào DB đang dùng.

Không export connection secrets, JWT, DPAPI file hoặc ảnh/dữ liệu riêng của Long.
Không thay certificate, restart SQL, chạy cloud deployment hoặc xóa DB nào trong quá trình lấy gói.
