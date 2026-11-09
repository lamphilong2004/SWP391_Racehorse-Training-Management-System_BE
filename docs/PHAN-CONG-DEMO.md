# Phân công demo — Racehorse Ops

**Bắt đầu thẳng vào Flow 1, không phần giới thiệu.** Ai làm phần nào thì demo phần đó
(sheet 2_Project_Team + commit trên main: FE `0a315f6`, BE `8e45992`).

## Nguyên tắc độc lập

- Mỗi phần có **bộ dữ liệu riêng chuẩn bị sẵn** (mục cuối). Không phần nào dùng ngựa, buổi tập hay sự cố
  do người demo trước tạo ra. Một người bỏ qua hoặc bị lỗi, người sau vẫn chạy bình thường.
- Mỗi người chỉ dùng **tài khoản và ngựa của phần mình** (cột "Dữ liệu" bên dưới), không sửa dữ liệu của phần khác.
- Thứ tự gợi ý: **Dũng → Hoàng → Tiến → Đức → Long**. Đổi thứ tự cũng được, vì không phần nào phụ thuộc phần nào.
- Tick `[x]` khi đã tập dượt xong.

| Thứ tự | Người | Phần | Tính năng | Dữ liệu riêng |
|---|---|---|---|---|
| 1 | Dũng | Flow 1 · Quản lý ngựa, cổng chủ ngựa | MF-01, MF-15 | Bộ D |
| 2 | Hoàng | Flow 2 · Lập kế hoạch huấn luyện | MF-04, MF-05 (lịch) | Bộ H |
| 3 | Tiến | Flow 2 · Thực hiện buổi tập, realtime, đánh giá | MF-05 (thực hiện), MF-06, MF-07 | Bộ T |
| 4 | Đức | Flow 3 · Y tế và xử lý sự cố | MF-02, MF-03, MF-08, MF-12 | Bộ Y |
| 5 | Long | Flow 4 · Chăm sóc, dinh dưỡng, kho + Quản trị hệ thống | MF-10, MF-11, MF-13, MF-17, MF-18, MF-20 | Bộ L |

---

## 1. Dũng — Flow 1 · Quản lý ngựa (Bộ D)

- [ ] `clubmanager` → `/horses`: tab vòng đời có số đếm, tìm kiếm, lọc khả năng tập luyện
- [ ] **Thêm ngựa** `/horses/new` (ngựa mới tên **"Demo Flow 1"**): tên, giống, giới tính, ngày sinh, xuất xứ, số vi mạch / số đăng ký, thế mạnh khai báo → lưu Nháp; thử trùng mã để thấy bị từ chối (BR-01)
- [ ] Tải ảnh ngựa (JPEG/PNG/WebP ≤ 5 MB)
- [ ] **Hoàn tất hồ sơ** `/horses/:id/edit`: phả hệ (cha/mẹ), chủ sở hữu + tỉ lệ, xếp ô chuồng trống (BR-03, BR-04)
- [ ] **Chờ kích hoạt** `/horses/pending-activation`: danh sách điều kiện, bước tiếp theo giao Vet
- [ ] `veterinarian` → Tổng quan → **Ngựa mới đến** → **Ghi khám ban đầu** (cân nặng, tình trạng Khỏe) → ngựa **tự chuyển Hoạt động** (BR-05, BR-07). Nếu ngựa vừa tạo bị lỗi, dùng ngựa dự phòng **D-chờ-khám**
- [ ] Chi tiết ngựa: tab Tổng quan, Phả hệ & quyền sở hữu, Huấn luyện & thành tích; sửa nhận dạng `/horses/:id/identity`
- [ ] `headtrainer`: đánh giá sẵn sàng của **D-hoạt-động** (khuyến nghị, không chặn kích hoạt)
- [ ] **Cổng chủ ngựa** `horseowner1` → `/owner/horses`: chỉ thấy ngựa của mình, mở **D-hoạt-động** xem sức khỏe tóm tắt; không thấy **D-của-chủ-2** (của horseowner2)
- [ ] Giải thích BE: ERD, bảng ngựa / sở hữu / ô chuồng, kích hoạt tự động khi đủ 5 điều kiện

## 2. Hoàng — Flow 2 · Lập kế hoạch huấn luyện (Bộ H)

- [ ] `headtrainer` → **Mẫu chương trình** `/training/templates`: xem, tạo, lưu phiên bản mới, nhân bản, lưu trữ (DEC-005)
- [ ] **Thư viện bài tập** `/training/exercises`
- [ ] **Tạo chương trình** `/training/programs/new` cho ngựa **H-chưa-có-chương-trình**: wizard 7 bước, chọn mẫu dạng thẻ, Trainer **Phù hợp / Không khuyến nghị** → tạo (bước tiếp theo giao Trainer)
- [ ] Danh sách / chi tiết chương trình `/training/programs`
- [ ] `trainer1` → **Chương trình được giao** → **tạo buổi tập** cho ngựa **H-có-chương-trình** (bài tập, Work Rider, gán Groom) → **Lịch tập** `/training/calendar`
- [ ] **Đề xuất điều chỉnh** `/training/adjustment-requests`: `headtrainer` duyệt **H-đề-xuất-1**, từ chối **H-đề-xuất-2**, áp dụng (bước tiếp theo giao Trainer)
- [ ] **Chặn do y tế:** tạo chương trình cho **H-khóa** → bị từ chối kèm lý do (BR-13); tạo buổi cho **H-hạn-chế** có bài bị cấm → phải xóa hoặc thay bài (BR-15)
- [ ] Giải thích: **H-chưa-có-chương-trình** vừa được gỡ hạn chế, nên chương trình vừa tạo là **chương trình phục hồi** cường độ thấp (BR-17, BR-18)

## 3. Tiến — Flow 2 · Thực hiện buổi tập và realtime (Bộ T)

Mỗi buổi tập trong Bộ T có khung giờ phủ cả buổi demo và Work Rider riêng, nên làm lúc nào cũng được.

- [ ] `groom` → `/groom/today` → buổi **T-tập-đủ** → 5 mục **Bình thường** → bước tiếp theo giao Work Rider
- [ ] **Sự cố A1:** buổi **T-kiểm-tra-bất-thường** → có mục **Bất thường** → buổi bị chặn → **Báo cáo sự cố** (nội dung điền sẵn) → biên nhận
- [ ] `workrider1` → `/rider` → buổi **T-tập-đủ** → Bắt đầu → **Thêm số đo** (nhịp tim, tốc độ, quãng đường) → Kết thúc → **Phản hồi** (bước tiếp theo giao Trainer); `/rider/history`
- [ ] **Sự cố A2:** `workrider2` → buổi **T-báo-vấn-đề** → Bắt đầu → **Báo cáo vấn đề** (Khập khiễng / Chấn thương…) → buổi **Bị gián đoạn**
- [ ] **Giám sát realtime** `/training/monitoring` (`trainer1`, mở buổi **T-tập-đủ** trong lúc `workrider1` đang cưỡi): số đo, cảnh báo ngưỡng → Xác nhận cảnh báo, Tạm dừng / Tiếp tục, Dừng có lý do
- [ ] **Đánh giá** buổi **T-đã-hoàn-thành** `/training/sessions/:id/evaluation`; **Thành tích** `/training/performance` (biểu đồ tiến độ)
- [ ] Giải thích BE: khung kiểm tra 12 giờ, chỉ bắt đầu trong khung giờ, chống trùng lịch Work Rider, mỗi buổi chỉ kiểm tra 1 lần, WebSocket/STOMP

## 4. Đức — Flow 3 · Y tế và xử lý sự cố (Bộ Y)

Dùng các sự cố có sẵn trong Bộ Y, không cần chờ sự cố của Tiến hay Long.

- [ ] `veterinarian` → **Sự cố** `/incidents`: ô lọc nhanh, cột Người báo · Nguồn, nút Tiếp nhận
- [ ] **Đóng ngay:** sự cố Bỏ ăn của **Y-nhẹ-và-tối-thiểu** → Đóng sự cố (hộp xác nhận)
- [ ] **Tối thiểu:** sự cố Vấn đề móng/chân của **Y-nhẹ-và-tối-thiểu** → Mở ca → Khám (Khỏe) → **Đóng ca**, sự cố tự đóng (DEC-014)
- [ ] **Đầy đủ:** sự cố **Y-đầy-đủ** → Mở ca → Khám (Chấn thương) → **chấn thương trên mô hình 3D** → **Phác đồ** (giao việc Groom) → **Hạn chế tập luyện**: đặt **Khóa**; ngưỡng cảnh báo riêng
- [ ] **Gắn vào ca đang mở:** sự cố Mới báo của **Y-ca-đang-mở** → chọn ca của ngựa này từ danh sách
- [ ] `groom` → **Công việc chăm sóc & cho thuốc** `/care/tasks`: tab Hôm nay, việc của ca **Y-ca-đang-mở** → hoàn thành
- [ ] Ca **Y-ca-sẵn-sàng-đóng** (đã có phác đồ, việc Groom đã xong, đang Khóa): **Tái khám** Đã hồi phục + Khỏe → **Gỡ hạn chế** → **Đóng ca** theo danh sách điều kiện
- [ ] `/medical` danh sách ca, `/medical/cases/new` tạo ca trực tiếp, **Sức khỏe đàn ngựa** `/medical/herd-health`
- [ ] `headtrainer` → **Tóm tắt y tế** `/medical/summary`: chỉ tóm tắt, không thấy hồ sơ lâm sàng
- [ ] Giải thích DEC-013 (loại sự cố khi cưỡi) và DEC-014 (quy trình tối thiểu)

## 5. Long — Flow 4 + Quản trị hệ thống (Bộ L)

- [ ] `clubmanager` → **Tổng quan**: khối **Chăm sóc hôm nay** (tổng việc, còn lại, **quá giờ**, đã xong; danh sách việc quá giờ; vật tư dưới ngưỡng)
- [ ] `/stable` → chọn **L-chăm-sóc**: xem kế hoạch **Chăm sóc** và **Dinh dưỡng** có sẵn; **Tạo kế hoạch chăm sóc** thêm 1 việc mới (ví dụ Chăm sóc chân · 16:00) → ô **Bước tiếp theo**; `headtrainer` mở **Dinh dưỡng** của cùng ngựa (Head Trainer chỉ xem kế hoạch chăm sóc)
- [ ] **Chăm sóc hằng ngày** `/care/daily` (Club Manager): việc **Quá giờ** nằm trên cùng kèm cảnh báo; lọc **Groom phụ trách**; **Thao tác** → đổi Groom / hủy việc có lý do → **Lịch sử**
- [ ] `groom` → **Tổng quan** (khối Chăm sóc hôm nay) → **Mở chăm sóc hằng ngày** → tìm `L-`: **Hoàn thành** việc quá giờ và việc cần làm (ghi giờ + người làm); việc 23:30 **Chưa đến giờ** không có nút Hoàn thành
- [ ] **Sự cố A4:** Groom **Báo bất thường** tại chuồng cho **L-chăm-sóc** → biên nhận
- [ ] **Kho** `/inventory`: nhập / xuất kho; Groom gửi yêu cầu bổ sung
- [ ] **Phân quyền:** đăng nhập vài vai trò, mỗi vai trò có Tổng quan và menu riêng; vào URL không có quyền → bị chặn (FE và BE)
- [ ] **Đăng ký:** `/register` (không chọn vai trò) → `clubmanager` → `/admin/registrations`: duyệt **L-đăng-ký-1** (gán 1 vai trò), từ chối **L-đăng-ký-2**
- [ ] **Nhân sự** `/admin/users`: tạo tài khoản kèm mật khẩu ban đầu (DEC-012), sửa, chuyển Hoạt động / Ngừng; `/admin/roles`; `/account` đổi mật khẩu; thông báo `/notifications`
- [ ] **Nhật ký hệ thống** `/admin/audit-log`: xem các thao tác vừa demo
- [ ] Trả lời phạm vi: MF-09, MF-14, MF-16, MF-19 tạm hoãn theo DEC-007

---

## Bộ dữ liệu độc lập

Tạo bằng script trên BE main: `scripts/demo/seed-demo-data.mjs` (hướng dẫn `docs/DEMO-DATA.md`).
Long chạy trên máy demo **khoảng 15 đến 30 phút trước giờ demo**; thành viên chạy trên máy mình để tập.
Mỗi lần chạy tạo bộ ngựa mới có nhãn thời gian (ví dụ `H-khóa 1109-1738`), dùng đúng bộ có nhãn mới nhất.
Bảng SUMMARY cuối script liệt kê ngựa, mã buổi tập và số ca của từng bộ.

| Bộ | Người | Dữ liệu |
|---|---|---|
| D | Dũng | D-chờ-khám (nháp, chỉ chờ Vet); D-hoạt-động (horseowner1); D-của-chủ-2 (horseowner2) |
| H | Hoàng | H-chưa-có-chương-trình (hạn chế đã gỡ); H-có-chương-trình (trainer1, 2 đề xuất chờ duyệt); H-khóa; H-hạn-chế (cấm Breeze nước rút) |
| T | Tiến | T-tập-đủ (chưa kiểm tra, workrider1); T-kiểm-tra-bất-thường (chưa kiểm tra); T-báo-vấn-đề (đã kiểm tra đạt, workrider2); 1 buổi đã hoàn thành chờ đánh giá |
| Y | Đức | Y-nhẹ-và-tối-thiểu (2 sự cố); Y-đầy-đủ (sự cố mức cao); Y-ca-đang-mở (ca mở + 1 sự cố để gắn); Y-ca-sẵn-sàng-đóng (đã xong việc Groom, đang Khóa) |
| L | Long | L-chăm-sóc (1 việc quá giờ, 1 việc cần làm, 1 việc chưa đến giờ, khẩu phần); vật tư Cỏ khô demo; 2 yêu cầu đăng ký chờ duyệt |

## Đối chiếu yêu cầu: phần app CHƯA đáp ứng (nói thẳng nếu thầy hỏi)

| Yêu cầu gốc | Hiện trạng | Người trả lời |
|---|---|---|
| **BR-28 / MF-12:** báo sự cố kèm **ảnh** | **Chưa làm.** Form báo sự cố chỉ có loại, mức độ, mô tả | Đức |
| **BR-25 + BR-23:** cảnh báo realtime vượt ngưỡng → dừng và tạo sự cố | BE có; **UI nút Dừng chưa tạo sự cố** | Tiến |
| **MF-12 bước 3.1:** Trainer / Head Trainer báo sự cố | BE có; **UI chỉ Groom và Work Rider báo được** | Tiến |
| **MF-02 / MF-15:** theo dõi **cân nặng theo thời gian** | Có cân nặng lúc khám ban đầu; **chưa có biểu đồ cân nặng** | Dũng |
| **MF-05:** quản lý **lượt chạy thử** | **Chưa làm** riêng | Hoàng |
| **MF-15:** video chạy thử, lịch sử thi đấu | Video: NEEDS CLARIFICATION (Q-12); thi đấu phụ thuộc MF-14 tạm hoãn | Dũng |
| **BR-31:** phác đồ gửi tới cả Groom **và Trainer** | Groom nhận việc; **chưa kiểm chứng** Trainer có được báo | Đức |
| **BR-44:** đúng 5 vai trò | Đã mở rộng thành **7 vai trò theo DEC-004** | Long |

Tạm hoãn có quyết định (DEC-007), không demo: MF-09 Lịch phòng bệnh, MF-14 Cuộc đua, MF-16 Chi phí,
MF-19 Báo cáo quản lý (trang Tổng quan theo vai trò và Sức khỏe đàn ngựa là phần có làm).

## Câu hỏi dự phòng

| Câu hỏi | Người trả lời |
|---|---|
| Vì sao "Đang theo dõi" vẫn Sẵn sàng tập? | Đức: sức khỏe và khả năng tập luyện là 2 trạng thái, chỉ hạn chế của Vet mới chặn |
| Vì sao buổi tập bị chặn không hủy được? | Tiến: mỗi buổi chỉ kiểm tra 1 lần, Trainer tạo buổi mới |
| Đóng ca có gỡ khóa không? | Đức: không, gỡ hạn chế là quyết định riêng |
| Hoàn thành việc cho ăn có trừ kho không? | Long: không, kho nhập/xuất tay theo quy định đã duyệt |
| Owner thấy được gì? | Dũng: chỉ ngựa mình sở hữu, sức khỏe dạng tóm tắt |
| Phân quyền làm ở đâu? | Long: cả FE (route guard) và BE (Spring Security + kiểm tra phạm vi) |
