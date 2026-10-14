# CORE-026 — Incident API và Groom pre-session gate

Trạng thái: **BLOCKED cho tích hợp/merge; phần độc lập có thể review**. Không commit,
push, tạo PR hoặc thay đổi branch/worktree. Chỉ thêm file thuộc CORE-026.

## Nguồn đã kiểm tra

- Worktree `SWP391_Racehorse-Training-Management-System_BE_CORE026`, branch
  `feat/CORE-026-incident-presession-gate`, base foundation `9531ca2`.
- `pom.xml`, common response/error handling, application/test profiles và README.
- DB-01 `database/schema.sql`, SHA256
  `416eb579e59f926e01c08bdfb25af6db5bc9376e87ec93eb7b6aa35d512a9141`, giữ nguyên.
- Read-only Git: CORE-005 `97a588a`, `docs/api/flow2-training.openapi.yaml`;
  CORE-006 `6ddc4ca`, `flow3-medical.openapi.yaml` và `CORE-006-review.md`;
  CORE-003 shared OpenAPI và permission matrix (cũng có trên `origin/dev`).
- `project-context/RACEHORSE_AI_PROMPT_DUC.md`, execution guide và workbook.
  Workbook chỉ có sheet **Core Flow Execution**; không có Task Playbook, Figma
  Screen Registry hoặc Role Coverage Matrix. CORE-026 phụ thuộc CORE-001,
  CORE-005, CORE-006, CORE-007, CORE-024. Workbook chưa xác nhận Done.

## Phạm vi đã viết

| Endpoint/service | Hành vi |
|---|---|
| `POST /api/incidents` | 201; reporter từ actor đã xác thực; code server `INC-` + 16 hex; status luôn REPORTED; timestamps UTC millisecond; validation type/severity/IDs/description; zero hoặc một context cùng horse |
| `GET /api/incidents` | Page 0-based, size 1–100, default 20; reporter scope áp dụng trong SQL trước pagination và count; sort `reported_at DESC, id DESC` |
| `GET /api/incidents/{incidentId}` | Reporter-facing projection; không join clinical case; 403 cho record ngoài scope; 404 nếu không tồn tại |
| `GET /api/groom/sessions?date=YYYY-MM-DD` | GROOM-only, cần trusted work-list và complete SessionSummary adapter; chỉ SCHEDULED đúng UTC date; giữ nguyên medical/other blocked state của CORE-024 |
| `POST /api/training-sessions/{id}/pre-session-check` | 201; GROOM-only và work scope; SCHEDULED; một check/session; observations JSON gồm conditionNormal, legsHoovesNormal, temperatureC, notes; performer/time server |
| `PreSessionStartGate.evaluate/requirePass` | Missing → PRE_CHECK_MISSING; ABNORMAL → PRE_CHECK_ABNORMAL; ALL_NORMAL → PASS cho riêng pre-check gate; không cập nhật lifecycle |

Không có request actor/status. Unknown Incident fields (bao gồm reporter/status)
trả 400. Status đọc ra được kiểm tra bằng enum REPORTED/UNDER_REVIEW/RESOLVED;
review/update status thuộc owner khác, không triển khai ở đây.

HEAD_TRAINER/GROOM có thể tạo independent reports theo matrix. TRAINER cần open,
effective assignment cho horse/program. WORK_RIDER cần context session IN_PROGRESS
của chính họ. Groom context session/care-task cần approved work scope. Read:
CM/HT/VET toàn bộ reporter projection; TR/WR/GR chỉ own reports; HORSE_OWNER bị từ
chối. Multi-role grants được hợp nhất, không có SYSTEM role.

SQL Server UNIQUE code, filtered unique non-NULL pre-check FK và unique session
check là lớp bảo vệ cuối. Duplicate hoặc concurrent FK/uniqueness conflict → 409;
không áp đặt uniqueness không có trong schema cho training-session/care-task.
Observations phải vừa NVARCHAR(4000) **sau** JSON escaping. ALL_NORMAL yêu cầu cả
conditionNormal và legsHoovesNormal true; không tự suy ra kết quả từ nhiệt độ.

## Các adapter bắt buộc và start transaction

`IntegrationPorts.ActorResolver` phải được CORE-007 nối với authentication thực:
JWT đã xác minh, account ACTIVE, ID và 7-role grants từ nguồn server. Không có
adapter → 401. Không đọc X-Role/X-User-Id, không có actor giả ở production.

`GroomWorkScope` phải lấy membership từ nguồn đã duyệt. DB-01 không có Groom
assignment FK; không dùng assumption “mọi Groom được xem/check mọi session”.
Không có adapter → 403. `SessionProjection` do CORE-024 cung cấp complete
SessionSummary và mọi blocked reason; thiếu adapter → list 403, không trả false
blocked state. Đây là integration boundaries có chủ đích, không phải flow đã nối.

CORE-024 gọi `PreSessionStartGate.requirePass(sessionId)` **bên trong transaction
của start**, cùng transaction quản lý state update. Gate đọc session với
`UPDLOCK,HOLDLOCK`; submission cũng dùng khóa này. Phải giữ transaction đến khi
state update hoàn tất và vẫn kiểm tra medical restrictions, exercise suitability,
program state, schedule và các start requirements khác. Gọi gate standalone rồi
start ở transaction khác không đảm bảo atomicity.

## Xung đột cần Long quyết định

CORE-005 yêu cầu ABNORMAL tự tạo Incident, incident payload bắt buộc và notify
Vet/Trainer, dẫn BR-23/DEC-007. CORE-006 review G6 yêu cầu explicit POST nếu chưa
xác nhận rule. Workbook cũng mô tả automatic Incident/notification nhưng không
thay thế approved decision. Không tìm thấy nguyên văn BR-23/DEC-007 đã duyệt
trong nguồn hiện có; các contract branch vẫn chưa merged.

Theo yêu cầu hiện tại: ABNORMAL được lưu và chặn start, **không tự tạo Incident**.
Request có nested `incident` bị từ chối 400 để tránh bỏ dữ liệu âm thầm. Sau khi
lưu check, client có thể POST Incident riêng với `preSessionCheckId`; unique FK
bảo vệ duplicate. `incidentId` response check là null. Đây là khác biệt có chủ
đích với phần automatic chưa phê duyệt, cần CORE-005/006 reconciliation trước
consumer integration. Không sửa các file contract của CORE-005/006.

Long cần xác nhận BR-23/DEC-007, cùng Tiến thống nhất automatic hay explicit,
approved Groom scope và DTO/handoff behavior. Không bật automatic bằng feature
flag hoặc code path ẩn khi chưa được duyệt.

## Notifications

Foundation có bảng `notification` nhưng không có delivery service, trusted
recipient selector hoặc implementation JWT/STOMP. Không ghi notification giả,
không gọi external messenger, không báo gửi thành công.

Chỉ publish domain integration events `IncidentReported`/`CheckRecorded` trong
transaction. Chúng **không phải delivery receipts**, không phải durable outbox.
Future notification owner phải xác nhận recipient policy, delivery/retry behavior
và dùng AFTER_COMMIT listener (không dùng synchronous delivery trong transaction).
Test chứng minh rollback không gọi AFTER_COMMIT consumer. Notification flow vẫn
BLOCKED, không cam kết delivery khi process crash.

## Kiểm thử và cách chạy

`mvn compile` và `mvn test`/`mvn verify` với Java release 21. Máy này dùng JDK 25
bundled trong VS Code Red Hat Java extension và Maven 3.9.15 bundled trong NetBeans;
Java mặc định trên PATH là 17. Không sửa pom hoặc cài thêm dependency.

Application tests dùng fixture H2 riêng. Override SQL Server lock hint **chỉ trong
test**; UNIQUE NULLS DISTINCT mô hình hóa filtered non-NULL source uniqueness.
H2 không chứng minh canonical schema hoặc SQL Server JDBC compatibility.

Real SQL Server tests opt-in:

```powershell
$env:CORE026_SQLCMD_SERVER = 'localhost'
# Optional: CORE026_SQLCMD_PATH = full sqlcmd executable path
mvn test
```

Cần Windows integrated SQL access có quyền CREATE/DROP test database và đọc lock
DMVs. Test tạo tên ngẫu nhiên `core026_test_<32 hex>`, áp dụng canonical schema
nguyên bản, seed test-only, kiểm tra CHECK/FK/filtered unique, Unicode, DATETIME2(3)
và concurrent UPDLOCK/HOLDLOCK, rồi xóa đúng database do nó tạo. Không động đến
database ứng dụng. Không có opt-in → 2 tests được skip rõ ràng.

Máy này truy cập được SQL Server qua sqlcmd/Windows auth, nhưng TCP localhost:1433
timeout. Test SQL thật xác minh schema/lock semantics, **chưa chứng minh service
JDBC end-to-end trên SQL Server**. Cần configured JDBC test environment cho bước đó.

Kết quả chạy thực tế được ghi ở phần Verification bên dưới.

## File inventory

- Production (8): `Contracts`, `IntegrationPorts`, `AccessPolicy`, `Core026Store`,
  `IncidentService`, `PreSessionCheckService`, `PreSessionStartGate`,
  `Core026Controller`, trong `src/main/java/com/racehorse/core026/`.
- Tests (4): `Core026Test`, `FailClosedTest`, `IntegrationBoundaryTest`,
  `SqlServerSchemaTest`, trong `src/test/java/com/racehorse/core026/`.
- Fixtures (3): `core026-fixture.sql`, `core026-sqlserver-seed.sql`,
  `core026-sqlserver-assertions.sql`, trong `src/test/resources/`.
- Handoff: file này. Không sửa file foundation, canonical schema, medical,
  restriction, training lifecycle hoặc Frontend.

## Verification

Ngày 2026-10-13 (Asia/Bangkok):

- Maven compile: **PASS**, release 21 với JDK 25.
- Test phase của `mvn -o -B verify`: **60 tests, 0 failures, 0 errors, 0 skipped**:
  Foundation 9; Core026Test 42; FailClosedTest 2; IntegrationBoundaryTest 5;
  SqlServerSchemaTest 2. Có 51 tests mới thuộc CORE-026.
- Hai tests SQL Server thật đã qua: full canonical schema/constraint assertions
  và concurrent lifecycle UPDATE bị session lock chặn. Test database đã cleanup.
- Lượt verify offline ban đầu fail sau khi tests pass vì cache thiếu
  `plexus-archiver/FileSet` của Maven JAR plugin. Sau khi Maven tải dependency
  còn thiếu, `mvn -B -DskipTests verify`: **BUILD SUCCESS**, executable JAR tạo được.
  Không chạy lại các tests đã pass, không sửa pom để né lỗi.
- `git diff --check`: **PASS**. Các file mới cũng được kiểm tra bằng no-index diff;
  không có whitespace errors (Git chỉ cảnh báo LF/CRLF cho SQL fixtures).
- Schema checksum vẫn khớp DB-01. Git có **16 file mới untracked**, không sửa
  tracked files, không commit/push/PR/merge.

Trạng thái dependency vẫn **BLOCKED**. Chưa xác minh service JDBC end-to-end trên
SQL Server; notification/auth/Groom scope/CORE-024 integration chưa được nối.
