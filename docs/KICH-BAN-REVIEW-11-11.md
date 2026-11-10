# Kịch bản review 11/11 — Racehorse Ops

Demo trên máy Long: FE `http://localhost:5173`, BE `8080`, DB `Racehorse_training`.
Bản đã chốt: FE `main` `3cc5401` (+ FE PR #44 sửa nhãn tiếng Việt), BE `main` `7555c1f`.

Đã kiểm tra lúc 17:00–18:30 ngày 10/11:
- **Flow 1 trên giao diện:** đăng nhập thật và bấm qua giao diện, 12/14 bước qua. 2 bước "lỗi" là lỗi của chính bộ kiểm tra, xem mục 1.
- **Flow 2–4 qua API:** 26/26 bước qua. Kiểm tra bằng đúng các endpoint mà giao diện dùng.
- **37 màn hình của 7 vai trò:** tất cả tải được, không có màn lỗi, không có lỗi console.
- **Test tự động:** FE 1167/1167, BE 352/352.
- **Cảnh báo realtime:** số đo 205 bpm sinh cảnh báo CRITICAL theo ngưỡng 180; dừng vì lý do sức khỏe thì tạo sự cố.

Mỗi phần dùng **bộ dữ liệu riêng**, nên người trước làm sai cũng không ảnh hưởng người sau. Các tên ngựa trong tài liệu này có dạng `D-hoạt-động <nhãn>`, với `<nhãn>` là nhãn Long gửi sáng mai, ví dụ `1111-0815`.

---

## 0. Long chuẩn bị (sáng 11/11)

| Giờ | Việc |
|---|---|
| Trước 30 phút | **Khởi động lại máy.** Máy chỉ có 8 GB RAM: hôm nay còn trống 1,3 GB và SQL Server bị ép còn khoảng 100 MB, làm trang lúc nhanh lúc treo 10–40 giây. Sau khi khởi động lại, chỉ mở: SQL Server, launcher, 1 trình duyệt Chrome, phần mềm trình chiếu. **Tắt** Claude, Codex, Zalo, IDE và các tab không dùng |
| | Cắm sạc, tắt chế độ ngủ (Settings → Power → Never). Máy ngủ sẽ làm BE mất kết nối SQL |
| | Chạy launcher: `pwsh -NoProfile -File _gate-monitor/enterprise-v4/Start-LocalReview.ps1 -PreparedDemo -FrontendPort 5173` |
| Trước 15–30 phút | Trong repo BE: `node scripts/demo/seed-demo-data.mjs` (lệnh đầy đủ trong `GIAO-VIEC-10-11/Long.md`). Ghi lại `DEMO_DATA_PASS tag …`, gửi nhãn vào nhóm. **Chỉ chạy 1 lần.** Chạy lại trong vòng 4 giờ thì bộ T báo trùng lịch Work Rider; nếu bắt buộc phải chạy lại, dùng `$env:RH_DEMO_SETS = 'D,H,Y,L'` |
| Trước 10 phút | **Làm nóng hệ thống:** đăng nhập lần lượt 7 tài khoản, mỗi tài khoản mở 1–2 trang. Lần mở đầu tiên chậm vì SQL vừa khởi động |
| | Mở sẵn 2 cửa sổ trình duyệt: 1 cửa sổ thường, 1 cửa sổ ẩn danh. Hai cửa sổ dùng 2 tài khoản khác nhau, đổi vai trò khỏi phải đăng xuất |

Tài khoản: `clubmanager`, `headtrainer`, `trainer1`, `veterinarian`, `groom`, `workrider1/2/3`, `horseowner1/2` (`@gmail.com`). Mật khẩu chung Long gửi riêng, **không chiếu lên màn hình**.

Nếu BE tắt giữa chừng: chạy lại launcher, đợi khoảng 30 giây, đăng nhập lại. Dữ liệu không mất.

---

## Cách thuyết trình mỗi phần (khoảng 6–7 phút/người)

Mỗi người theo đúng 4 bước, **vào thẳng màn hình, không giới thiệu chung**:

1. **Một câu mở đầu (10 giây):** "Em phụ trách Flow X — MF-…, gồm các vai trò …".
2. **Demo thao tác (4–5 phút):** làm theo kịch bản phần mình bên dưới. **Vừa bấm vừa nói quy tắc nghiệp vụ** (BR-…), không đọc chữ trên màn hình.
3. **Giải thích BE (1 phút):** bảng chính, quy tắc được chặn ở BE (không chỉ ẩn nút ở FE), và cách phân quyền.
4. **Nói thẳng phần chưa làm (15 giây)**, theo bảng "Chưa đáp ứng" ở cuối. Không nói là đã làm khi chưa làm.

Mẹo: trước mỗi bước, nói **ai làm, làm gì, hệ thống phản ứng thế nào**. Sau mỗi thao tác, chỉ vào ô **"Bước tiếp theo"** để nói việc được chuyển cho ai.

---

## 1. Dũng — Flow 1 · Quản lý ngựa + cổng chủ ngựa (MF-01, MF-02 cân nặng, MF-15) · Bộ D

| # | Tài khoản | Thao tác | Nói gì |
|---|---|---|---|
| 1 | `clubmanager` | **Ngựa** `/horses`: tab Tất cả / Nháp / Hoạt động / Ngừng hoạt động, mỗi tab có số đếm; tìm kiếm; lọc khả năng tập luyện | Vòng đời ngựa tách riêng khỏi sức khỏe và khả năng tập luyện |
| 2 | | **Thêm ngựa**, tên **"Demo Dũng"** (đừng dùng "Demo Flow 1", tên này đã có sẵn trong DB): ngày sinh, giới tính, giống, thế mạnh, **ảnh** (JPEG/PNG/WebP ≤ 5 MB) → **Lưu nháp & tiếp tục** | **BR-01: mã ngựa do hệ thống cấp** (ô chỉ đọc, ghi "Mã dự kiến"), nên không thể trùng; BE còn có ràng buộc UNIQUE. *Không còn thao tác "nhập trùng mã" như checklist cũ* |
| 3 | | **Phả hệ & quyền sở hữu:** cha, mẹ, chủ ngựa → **Xếp ô chuồng** (chọn ô "Còn trống") → **Lưu & tiếp tục** | Trang tự chuyển sang **Chờ kích hoạt**. BR-03/04: mỗi ô chỉ 1 ngựa, ô đã có ngựa không chọn được |
| 4 | | **Chờ kích hoạt**: danh sách điều kiện, bước tiếp theo giao Veterinarian | Ngựa tự kích hoạt khi đủ điều kiện bắt buộc |
| 5 | `veterinarian` | Tổng quan → **Ngựa mới đến** → **Ghi khám ban đầu**: cân nặng, thể trạng, chọn **Khỏe** → Lưu | **BR-05/07: ngựa tự chuyển sang Hoạt động**. Khung bên phải hiện "Bắt buộc" / "Khuyến nghị · không bắt buộc". Ngựa vừa tạo bị lỗi thì dùng ngựa dự phòng **D-chờ-khám** |
| 6 | `veterinarian` | Hồ sơ **D-hoạt-động** → khung **Lịch sử cân nặng** → ghi thêm 1 lần cân → biểu đồ cập nhật | MF-02: theo dõi cân nặng theo thời gian, có phân trang |
| 7 | `clubmanager` | Chi tiết ngựa: tab Tổng quan, Phả hệ & quyền sở hữu, Huấn luyện & thành tích; **Sửa nhận dạng** | |
| 8 | `headtrainer` | **D-hoạt-động** → đánh giá sẵn sàng | Chỉ là khuyến nghị, không chặn kích hoạt |
| 9 | `horseowner1` | **Ngựa của tôi**: chỉ thấy ngựa mình sở hữu → mở **D-hoạt-động** → tab **Sức khỏe & mức sẵn sàng**: tóm tắt + biểu đồ cân nặng | Q-04: chủ ngựa chỉ xem được ngựa của mình. Không thấy **D-của-chủ-2** (của horseowner2); mở bằng URL thì hiện "Không có quyền truy cập" |

**BE (1 phút):** các bảng `horse`, `horse_ownership`, `stable_box` + `stable_assignment`, `baseline_examination`, `horse_weight_record`. Kích hoạt tự động khi đủ các điều kiện bắt buộc. Quyền của chủ ngựa được chặn theo từng dòng dữ liệu ngay ở BE.

---

## 2. Hoàng — Flow 2 · Lập kế hoạch huấn luyện (MF-04, MF-05 lịch) · Bộ H

| # | Tài khoản | Thao tác | Nói gì |
|---|---|---|---|
| 1 | `headtrainer` | **Mẫu chương trình**: xem, tạo, **lưu phiên bản mới**, nhân bản, lưu trữ | DEC-005: mẫu dùng chung khác với chương trình riêng của từng ngựa. Sửa mẫu không làm đổi chương trình đã tạo |
| 2 | | **Thư viện bài tập** | |
| 3 | | **Tạo chương trình** cho **H-chưa-có-chương-trình**: wizard nhiều bước, chọn mẫu dạng thẻ, Trainer có nhãn **Phù hợp / Không khuyến nghị** kèm lý do → Tạo | Hệ thống chỉ gợi ý, Head Trainer quyết định. Ngựa vừa được gỡ hạn chế nên đây là **chương trình phục hồi** (BR-17/18) |
| 4 | | Tạo chương trình cho **H-khóa** → **bị từ chối** kèm lý do | BR-13: ngựa bị Khóa thì không lập chương trình được, BE chặn (HTTP 400) |
| 5 | `trainer1` | **Chương trình được giao** → **H-có-chương-trình** → tạo buổi tập (bài tập, Work Rider, Groom) → **Lịch tập** | Trainer chỉ chỉnh ở mức buổi tập |
| 6 | `trainer1` | Tạo buổi cho **H-hạn-chế** có bài "Breeze nước rút" → **bị chặn**, phải xóa hoặc thay bài | BR-15: hạn chế y tế chặn từng bài tập, không cho vượt qua |
| 7 | `headtrainer` | **Yêu cầu điều chỉnh**: duyệt 1 đề xuất, từ chối 1 đề xuất | Thay đổi chiến lược phải qua Head Trainer (PENDING → APPROVED / REJECTED) |

**BE (1 phút):** `program_template` có phiên bản; `horse_training_program` ghi `sourceTemplateId` và `sourceTemplateVersion`; `adjustment_request`; quy tắc cường độ theo giai đoạn và theo hạn chế của Vet.
**Chưa làm:** MF-05 lượt chạy thử, đang NEEDS CLARIFICATION.

---

## 3. Tiến — Flow 2 · Thực hiện buổi tập, realtime, đánh giá (MF-05, MF-06, MF-07, MF-12) · Bộ T

Các buổi của bộ T mở trong khung **4 giờ** kể từ lúc Long chạy script, nên làm lúc nào trong buổi review cũng được.

| # | Tài khoản | Thao tác | Nói gì |
|---|---|---|---|
| 1 | `groom` | **Hôm nay** `/groom/today` → **T-tập-đủ** → **Bắt đầu kiểm tra trước buổi tập** → 5 mục **Bình thường** → Hoàn tất | BR-21: có kiểm tra đạt thì buổi mới bắt đầu được; mỗi buổi chỉ kiểm tra 1 lần, trong vòng 12 giờ trước giờ tập |
| 2 | `groom` | **T-kiểm-tra-bất-thường** → 1 mục **Bất thường** → buổi **bị chặn** → **Báo cáo sự cố** → biên nhận | BR-22/23 (sự cố A1). Work Rider bấm bắt đầu sẽ bị BE từ chối (409) |
| 3 | `workrider1` | **Lịch phân công** → buổi **T-tập-đủ** → **Bắt đầu** → **Thêm số đo** (nhịp tim, tốc độ, quãng đường) | Chỉ bắt đầu được trong khung giờ đã lên lịch |
| 4 | `trainer1` (cửa sổ thứ 2) | **Giám sát trực tiếp** → buổi T-tập-đủ: số đo hiện ngay. Work Rider nhập **nhịp tim trên 180** (ví dụ 205) → **cảnh báo CRITICAL** → Xác nhận / Tạm dừng / Tiếp tục | MF-07, WebSocket/STOMP. Ngựa T-tập-đủ có **ngưỡng riêng 180 bpm do Vet đặt** (Q-07: chỉ Vet được đặt; Trainer thử đặt sẽ bị 403). Nhập dưới 180 thì không có cảnh báo |
| 5 | `trainer1` | **Dừng vì lý do sức khỏe** → chọn loại sự cố và mức độ → buổi Bị gián đoạn + **tạo sự cố cho Vet** | **BR-25/23 (mới, FE PR #41)**. Dừng thường thì không tạo sự cố |
| 6 | `workrider1` | (Nếu chưa dừng ở bước 5) **Kết thúc** → **Phản hồi người cưỡi** → bước tiếp theo giao Trainer; **Lịch sử cưỡi** | |
| 7 | `workrider2` | **T-báo-vấn-đề** → Bắt đầu → **Báo cáo vấn đề**: **Khập khiễng**, mức Cao → buổi **Bị gián đoạn** | DEC-013: loại sự cố khi đang cưỡi (sự cố A2) |
| 8 | `trainer1` | Trong màn buổi tập → **Báo sự cố** → biên nhận | **MF-12 bước 3.1 (mới)**: Trainer cũng báo được sự cố |
| 9 | `trainer1` | **Đánh giá** buổi đã hoàn thành (Đạt / Đạt một phần / Không đạt) → **Thành tích** (biểu đồ tiến độ) | |

**BE (1 phút):** `pre_session_check`, `training_session` (SCHEDULED → IN_PROGRESS → COMPLETED / INTERRUPTED), số đo, ngưỡng cảnh báo, đẩy realtime qua WebSocket/STOMP, chống trùng lịch Work Rider. Thiết bị IoT thật nằm ngoài phạm vi; có simulator dùng chung pipeline.

---

## 4. Đức — Flow 3 · Y tế và xử lý sự cố (MF-02, MF-03, MF-08, MF-12) · Bộ Y

| # | Tài khoản | Thao tác | Nói gì |
|---|---|---|---|
| 1 | `veterinarian` | **Sự cố**: ô lọc nhanh, cột **Người báo · Nguồn**, nút Tiếp nhận | DEC-013: Vet biết ai báo và báo từ đâu |
| 2 | | **Đóng ngay**: sự cố Bỏ ăn của **Y-nhẹ-và-tối-thiểu** → Đóng sự cố (hộp xác nhận) | Sự cố nhẹ không cần mở ca |
| 3 | | **Quy trình tối thiểu**: sự cố Vấn đề móng/chân của **Y-nhẹ-và-tối-thiểu** → **Mở ca** → **Khám** (Khỏe) → **Đóng ca** → sự cố **tự đóng theo** | DEC-014: tối thiểu là mở ca → khám 1 lần → đóng ca |
| 4 | | **Quy trình đầy đủ**: sự cố **Y-đầy-đủ** → Mở ca → Khám (Chấn thương) → **chấm vị trí chấn thương trên mô hình 3D** → **Phác đồ** (giao việc cho Groom) → **Hạn chế: Khóa** | MF-03 (Three.js). Khóa thì không tạo được buổi tập hay chương trình |
| 5 | | **Gắn vào ca đang mở**: sự cố của **Y-ca-đang-mở** → chọn ca của ngựa này | Không mở ca trùng |
| 6 | `groom` | **Công việc điều trị** → việc của **Y-ca-đang-mở** → Hoàn thành | Ghi giờ và người làm |
| 7 | `veterinarian` | Ca **Y-ca-sẵn-sàng-đóng** → **Tái khám** Đã hồi phục + Khỏe → **Gỡ hạn chế** → **Đóng ca** theo danh sách điều kiện | Đóng ca không tự gỡ khóa; gỡ hạn chế là quyết định riêng của Vet |
| 8 | | Danh sách ca, **Tạo ca trực tiếp**, **Sức khỏe đàn ngựa** | |
| 9 | `headtrainer` | **Tóm tắt y tế** | Q-04: Head Trainer chỉ xem tóm tắt. Đã kiểm tra API: phần lý do, khám, chấn thương, phác đồ, tái khám đều bị ẩn với Head Trainer |

**BE (1 phút):** `incident` (REPORTED → UNDER_REVIEW → RESOLVED), `medical_case`, `veterinary_examination`, `injury` (có toạ độ 3D), `treatment_plan` + việc cho Groom, `medical_restriction` (AVAILABLE / LIMITED / LOCKED). Sức khỏe và khả năng tập luyện là 2 trạng thái tách riêng.
**Chưa làm:**
- BR-28 (ảnh sự cố).
- BR-31: phác đồ chưa thông báo cho Trainer; Groom thì đã nhận việc.

---

## 5. Long — Flow 4 + Quản trị (MF-10, MF-11, MF-13, MF-17, MF-18, MF-20) · Bộ L

| # | Tài khoản | Thao tác | Nói gì |
|---|---|---|---|
| 1 | `clubmanager` | **Tổng quan** → khối **Chăm sóc hôm nay**: tổng, còn lại, **quá giờ**, đã xong; danh sách việc quá giờ; vật tư dưới ngưỡng | Bước 4.4 (BR-36): tổng hợp tình trạng |
| 2 | | **Chăm sóc & dinh dưỡng** → **L-chăm-sóc**: kế hoạch Chăm sóc + Dinh dưỡng → thêm 1 việc → ô **Bước tiếp theo** | BR-33: kế hoạch tự sinh việc hằng ngày cho Groom |
| 3 | | **Chăm sóc hằng ngày**: việc **Quá giờ** nằm trên cùng + cảnh báo; lọc **Groom phụ trách**; **Thao tác** → đổi Groom / hủy việc có lý do → **Lịch sử** | |
| 4 | `groom` | Tổng quan → **Mở chăm sóc hằng ngày** → gõ nhãn vào ô tìm → **Hoàn thành** việc quá giờ và việc cần làm; việc 23:30 **Chưa đến giờ** không có nút Hoàn thành | BR-34: ghi giờ và người làm. BE cũng chặn làm việc trước giờ (409) |
| 5 | `groom` | **Báo bất thường** tại chuồng cho L-chăm-sóc → biên nhận | BR-35: chuyển sang Flow 3 (sự cố A4) |
| 6 | `clubmanager` | **Kho**: nhập/xuất thực tế; `groom` gửi **yêu cầu bổ sung** | Kho không tự trừ khi cho ăn, đúng quy định đã duyệt |
| 7 | | **Đăng ký** `/register` (không có ô chọn vai trò) → **Yêu cầu đăng ký**: duyệt **L-đăng-ký-1** (gán đúng 1 vai trò), từ chối **L-đăng-ký-2** | DEC-008 |
| 8 | | **Nhân sự**: tạo tài khoản kèm mật khẩu ban đầu (DEC-012), chuyển Hoạt động / Ngừng; **Vai trò & quyền hạn**; **Tài khoản**; **Thông báo** | 7 vai trò (DEC-004) |
| 9 | `trainer1` | Gõ URL `/admin/users` → **Không có quyền truy cập** | Phân quyền ở cả FE (route guard) và BE (Spring Security, API trả 403) |
| 10 | `clubmanager` | **Nhật ký hệ thống**: các thao tác vừa demo | MF-20, chỉ xem |

**Chưa làm / tạm hoãn:** MF-09, MF-14, MF-16, MF-19 tạm hoãn theo DEC-007. Trang Tổng quan theo vai trò, Sức khỏe đàn ngựa và Thành tích là các phần báo cáo **có** làm.

---

## Các màn "báo cáo / thống kê" có trong app

Nếu thầy hỏi phần báo cáo, demo các màn sau và nói rõ MF-19 (báo cáo quản lý đầy đủ) đang tạm hoãn:

| Màn | Ai trình bày | Nội dung |
|---|---|---|
| Tổng quan Club Manager | Long | Ngựa theo vòng đời và khả năng tập luyện, ô chuồng, sự cố chưa xong, **chăm sóc hôm nay**, hoạt động gần đây |
| Sức khỏe đàn ngựa `/medical/herd-health` | Đức | Phân bố tình trạng sức khỏe, ngựa cần chú ý |
| Thành tích `/training/performance` | Tiến | Biểu đồ tiến độ theo buổi tập và đánh giá |
| Biểu đồ cân nặng (hồ sơ ngựa, cổng chủ ngựa) | Dũng | Cân nặng theo thời gian |
| Nhật ký hệ thống | Long | Truy vết thao tác |

## Chưa đáp ứng (nói thẳng nếu bị hỏi)

| Yêu cầu | Hiện trạng | Người trả lời |
|---|---|---|
| BR-28 ảnh sự cố | Chưa làm | Đức |
| BR-31 phác đồ báo cho Trainer | Groom nhận việc; thông báo cho Trainer chưa làm | Đức |
| Q-07 ngưỡng **mặc định toàn hệ thống** | Chưa cấu hình giá trị (NEEDS CLARIFICATION; Figma ghi 180 bpm là giá trị prototype). Ngưỡng riêng theo ngựa do Vet đặt thì đã chạy, buổi demo dùng cách này | Đức / Tiến |
| MF-05 lượt chạy thử | NEEDS CLARIFICATION | Hoàng |
| MF-15 video chạy thử, lịch sử thi đấu | Q-12 và MF-14 tạm hoãn | Dũng |
| BR-44 "5 vai trò" | Mở rộng thành 7 vai trò theo DEC-004 | Long |
| MF-09/14/16/19 | Tạm hoãn theo DEC-007 | Long |

**Đã làm thêm từ hôm qua:**
- BR-25/23: dừng vì lý do sức khỏe thì tạo sự cố (Tiến).
- MF-12 bước 3.1: Trainer báo được sự cố (Tiến).
- Biểu đồ cân nặng (Dũng).
