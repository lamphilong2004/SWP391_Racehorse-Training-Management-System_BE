# Luồng demo 11/11 — từ đầu đến cuối (09:30–12:00)

**Nguyên tắc:**
- 2 con ngựa Dũng tạo mới sẽ đi tiếp qua các phần sau:
  - **Ngựa mới 1** → Flow 2 (Hoàng lập chương trình).
  - **Ngựa mới 2** → Flow 3 (Đức xử lý sự cố) → Flow 4 (Long lập kế hoạch chăm sóc).
- Những trường hợp cần giờ cố định hoặc trạng thái làm sẵn thì dùng **bộ dữ liệu `1111-0120`** đã tạo tối 10/11. Tên ngựa có đuôi `1111-0120`.
- Mật khẩu chung của mọi tài khoản: Long gửi riêng. Email có dạng `<tài khoản>@gmail.com`.
- Ảnh cho 2 con ngựa mới: thư mục `ANH-NGUA-CHO-MAI/` (`ngua-moi-1.jpg`, `ngua-moi-2.jpg`).

Ký hiệu cột **Kết quả**: ✅ = điều phải thấy trên màn hình; ⛔ = trường hợp bị chặn đúng quy tắc.

---

## PHẦN 1 — Dũng · Flow 1 Quản lý ngựa (MF-01, MF-02, MF-15) · khoảng 15 phút

| # | Tài khoản | Màn hình | Thao tác | Kết quả |
|---|---|---|---|---|
| 1.1 | clubmanager | **Ngựa** | Xem các tab Tất cả / Nháp / Hoạt động / Ngừng hoạt động; gõ tìm "Y-"; lọc theo Khả năng tập luyện | ✅ Các tab có số đếm; mỗi ngựa có ảnh riêng |
| 1.2 | clubmanager | **Thêm ngựa** | Tạo **Ngựa mới 1**: tên "Ngựa mới 1", ngày sinh, giới tính, giống, thế mạnh Nước rút; **Thêm ảnh** `ngua-moi-1.jpg` → **Lưu nháp & tiếp tục** | ✅ Ô **Mã ngựa chỉ đọc** ("Mã dự kiến") → nói **BR-01: mã do hệ thống cấp nên không thể trùng** |
| 1.3 | clubmanager | Phả hệ & quyền sở hữu | Ngựa cha, ngựa mẹ; Chủ ngựa **horseowner1**; **Xếp ô chuồng** → chọn ô "Còn trống" → **Xác nhận xếp ô** → **Lưu & tiếp tục** | ✅ Tự chuyển sang **Chờ kích hoạt**; ⛔ ô đã có ngựa không chọn được (BR-03/04) |
| 1.4 | clubmanager | Thêm ngựa | Tạo **Ngựa mới 2**, giống bước 1.2–1.3, ảnh `ngua-moi-2.jpg`, chủ **horseowner1** | ✅ Ngựa mới 2 cũng nằm trong **Chờ kích hoạt** |
| 1.5 | clubmanager | **Chờ kích hoạt** | Xem danh sách điều kiện của 2 ngựa | ✅ Còn thiếu "Khám ban đầu của Veterinarian"; ô **Bước tiếp theo** giao Vet |
| 1.6 | veterinarian | **Tổng quan** → Ngựa mới đến | Bấm **Ghi khám ban đầu** cho **Ngựa mới 1**: cân nặng 470, thể trạng, chọn **Khỏe** → **Lưu kết quả khám** | ✅ Ngựa **tự chuyển Hoạt động** (BR-05/07); khung bên phải hiện "Bắt buộc / Khuyến nghị · không bắt buộc" |
| 1.7 | veterinarian | Ghi khám ban đầu | Làm tương tự cho **Ngựa mới 2** (cân nặng 465, **Khỏe**) | ✅ Hoạt động |
| 1.8 | veterinarian | Hồ sơ **Ngựa mới 2** → **Lịch sử cân nặng** | Ghi thêm 1 lần cân (472) | ✅ Biểu đồ cân nặng có 2 điểm (MF-02) |
| 1.9 | clubmanager | Chi tiết **Ngựa mới 1** | Mở các tab Tổng quan / Phả hệ & quyền sở hữu / Huấn luyện & thành tích; mở **Sửa nhận dạng** | ✅ Đủ thông tin, ảnh, ô chuồng |
| 1.10 | headtrainer | Chi tiết **Hoàng Kim** | **Ghi đánh giá sẵn sàng** | ✅ Chỉ là khuyến nghị, không chặn kích hoạt |
| 1.11 | horseowner1 | **Ngựa của tôi** | Mở **Ngựa mới 2** → tab **Sức khỏe & mức sẵn sàng** | ✅ Chỉ thấy ngựa của mình; thấy tóm tắt sức khỏe và biểu đồ cân nặng (MF-15) |
| 1.12 | horseowner1 | Gõ URL ngựa **Ngọc Trai** | — | ⛔ "Không có quyền truy cập" (Q-04: chủ ngựa chỉ xem ngựa mình) |
| 1.13 | veterinarian | **Phong Vân** | (Dự phòng) dùng nếu ngựa mới tạo bị lỗi | — |

**Nói về BE:** các bảng `horse`, `horse_ownership`, `horse_stable_assignment`, `stable_box`, `baseline`/`veterinary_examination`, `weight_record`, `horse_photo`. Kích hoạt tự động khi đủ điều kiện bắt buộc. Chủ ngựa bị chặn theo từng dòng dữ liệu ngay ở BE.

---

## PHẦN 2 — Hoàng · Flow 2 Lập kế hoạch huấn luyện (MF-04, MF-05 lịch) · khoảng 15 phút

| # | Tài khoản | Màn hình | Thao tác | Kết quả |
|---|---|---|---|---|
| 2.1 | headtrainer | **Mẫu chương trình** | Xem 1 mẫu → **Lưu phiên bản mới** → **Nhân bản** → **Lưu trữ** bản nhân bản | ✅ DEC-005: sửa mẫu không làm đổi chương trình đã tạo |
| 2.2 | headtrainer | **Thư viện bài tập** | Xem danh sách | ✅ |
| 2.3 | headtrainer | **Tạo chương trình** | Wizard 7 bước cho **Ngựa mới 1** (vừa kích hoạt ở phần 1): chọn mẫu dạng thẻ → giai đoạn → chọn Trainer **trainer1** (nhãn **Phù hợp** kèm lý do) → Tạo | ✅ Chương trình tạo xong, ô **Bước tiếp theo** giao Trainer |
| 2.4 | headtrainer | Tạo chương trình cho **Mặc Vũ** | — | ⛔ Bị từ chối kèm lý do (BR-13: ngựa đang Khóa) |
| 2.5 | headtrainer | Tạo chương trình cho **Thanh Long** (vừa gỡ hạn chế) | Chỉ mở bước đầu để xem gợi ý | ✅ Nói BR-17/18: ngựa vừa hồi phục thì làm chương trình phục hồi cường độ thấp |
| 2.6 | trainer1 | **Chương trình được giao** | Thấy chương trình của **Ngựa mới 1**; mở **Kim Sư** → **Tạo buổi tập** (bài tập, Work Rider, Groom) | ✅ Work Rider đã có lịch trùng giờ thì bị khóa lựa chọn; buổi hiện ở **Lịch tập** |
| 2.7 | trainer1 | Tạo buổi tập cho **Bão Cát**, chọn bài **Breeze nước rút** | — | ⛔ Bị chặn, phải xóa hoặc thay bài (BR-15: hạn chế y tế) |
| 2.8 | headtrainer | **Yêu cầu điều chỉnh** | **Kim Sư**: duyệt 1 đề xuất, từ chối 1 đề xuất (có lý do) | ✅ PENDING → APPROVED / REJECTED; bước tiếp theo giao Trainer |

**Chưa làm:** MF-05 lượt chạy thử (NEEDS CLARIFICATION).

---

## PHẦN 3 — Tiến · Flow 2 Thực hiện buổi tập, realtime, đánh giá (MF-05, MF-06, MF-07, MF-12) · khoảng 20 phút

Các buổi dùng ở phần này mở **09:30–12:00**. Nên mở 2 cửa sổ: cửa sổ thường cho Groom/Rider, cửa sổ ẩn danh cho trainer1.

| # | Tài khoản | Màn hình | Thao tác | Kết quả |
|---|---|---|---|---|
| 3.1 | groom | **Hôm nay** | Buổi **Xích Thố** → **Bắt đầu kiểm tra trước buổi tập** → 5 mục **Bình thường** → Hoàn tất | ✅ Tất cả bình thường; bước tiếp theo giao Work Rider (BR-21) |
| 3.2 | groom | Hôm nay | Buổi **Lôi Phong** → mục Chân & móng chọn **Bất thường** (ghi quan sát) → **Báo cáo sự cố** (nội dung điền sẵn) → Gửi | ✅ Biên nhận mã INC-…; ⛔ buổi bị chặn (BR-22/23 · **sự cố A1**) |
| 3.3 | workrider3 | **Lịch phân công** | Thử bắt đầu buổi **Lôi Phong** | ⛔ Không bắt đầu được vì kiểm tra bất thường |
| 3.4 | workrider1 | Lịch phân công | Buổi **Xích Thố** → **Bắt đầu** → **Thêm số đo**: nhịp tim 150, tốc độ 35, quãng đường 500 | ✅ Đang tập |
| 3.5 | trainer1 (cửa sổ 2) | **Giám sát trực tiếp** | Mở buổi Xích Thố | ✅ Số đo hiện ngay (WebSocket/STOMP) |
| 3.6 | workrider1 | Buổi đang tập | Thêm số đo **nhịp tim 205** | ✅ Màn giám sát hiện **cảnh báo CRITICAL** (ngưỡng 180 do Vet đặt cho ngựa này, Q-07) |
| 3.7 | trainer1 | Giám sát trực tiếp | **Xác nhận** cảnh báo → **Tạm dừng** → **Tiếp tục** → **Dừng vì lý do sức khỏe** (chọn loại và mức độ) | ✅ Buổi **Bị gián đoạn** + **tạo sự cố cho Vet** (BR-25/23) |
| 3.8 | workrider2 | Lịch phân công | Buổi **Ánh Dương** (Groom đã kiểm tra đạt) → **Bắt đầu** → **Báo cáo vấn đề**: **Khập khiễng**, mức Cao | ✅ Buổi **Bị gián đoạn**, có sự cố (DEC-013 · **sự cố A2**) |
| 3.9 | trainer1 | Mở 1 buổi tập bất kỳ → **Báo sự cố** | Gửi 1 sự cố | ✅ Biên nhận (MF-12 bước 3.1: Trainer báo được sự cố) |
| 3.10 | trainer1 | **Đánh giá** buổi **Xích Thố (buổi đã hoàn thành)** | Chọn **Đạt một phần**, tóm tắt, khuyến nghị → Lưu | ✅ Hiện ở **Thành tích** (biểu đồ tiến độ) |
| 3.11 | workrider1 | **Lịch sử cưỡi** | Xem | ✅ Có buổi đã hoàn thành |

**Nói về BE:** kiểm tra trước buổi tập chỉ hợp lệ trong 12 giờ trước giờ bắt đầu; mỗi buổi chỉ kiểm tra 1 lần; chỉ bắt đầu được trong khung giờ; chống trùng lịch Work Rider. Không dùng thiết bị IoT thật; có simulator dùng chung pipeline.

---

## PHẦN 4 — Đức · Flow 3 Y tế và xử lý sự cố (MF-02, MF-03, MF-08, MF-12) · khoảng 20 phút

Màn **Sự cố** lúc này có đủ các nguồn báo: Groom tại chuồng, **Groom từ phiếu kiểm tra trước buổi tập**, **Work Rider khi đang cưỡi**, cùng các sự cố vừa tạo ở phần 3.

| # | Tài khoản | Màn hình | Thao tác | Kết quả |
|---|---|---|---|---|
| 4.0 | groom | **Sự cố** → **Báo bất thường tại chuồng** | **Ngựa mới 2**: loại **Bỏ ăn**, mức **Thấp**, mô tả → **Gửi sự cố** | ✅ Biên nhận (sự cố chuồng nối sang Flow 3, BR-35) |
| 4.1 | veterinarian | **Sự cố** | Xem ô lọc nhanh; cột **Người báo · Nguồn**: Groom / Kiểm tra trước buổi tập / Work Rider khi đang cưỡi / Trainer | ✅ DEC-013: Vet biết ai báo và báo từ đâu |
| 4.2 | veterinarian | Sự cố **Bỏ ăn** của **Sương Mai** | **Đóng sự cố** → hộp xác nhận | ✅ Đóng ngay, không cần mở ca |
| 4.3 | veterinarian | Sự cố của **Ngựa mới 2** (từ bước 4.0) | **Mở ca y tế** → tab **Khám**: triệu chứng "Ăn lại bình thường sau 1 bữa", Tình trạng **Khỏe** → **Lưu lần khám** → **Đóng ca** → **Xác nhận đóng ca** | ✅ **Quy trình tối thiểu** (DEC-014): ca đóng, sự cố **tự đóng theo** |
| 4.4 | veterinarian | Sự cố **Khập khiễng** của **Phi Vân** | **Mở ca y tế** → **Khám**: tình trạng **Chấn thương** → tab **Chấn thương**: **Vị trí** "Khác — nhập thêm" → "Chân sau trái – hụt nhịp", Mức độ, Tình trạng hồi phục → **Lưu chấn thương** | ✅ Chấn thương **khai báo bằng chữ** (DEC-015, không dùng 3D) |
| 4.5 | veterinarian | Tab **Điều trị** → **Mở trình soạn phác đồ** | Hướng dẫn thuốc; **Thêm công việc cho Groom**: chọn **Groom phụ trách theo tên**, hướng dẫn, ngày mặc định hôm nay → **Lưu phác đồ điều trị** | ✅ Việc được giao cho Groom (BR-32) |
| 4.6 | veterinarian | Ca → **Quản lý khả năng tập luyện & hạn chế** | Chọn **Khóa**, lý do (thời điểm hiệu lực tự điền "bây giờ") → **Lưu** | ✅ Ngựa bị Khóa: không tạo được buổi hay chương trình (BR-13/14) |
| 4.7 | veterinarian | Sự cố từ phiếu kiểm tra của **Liên Hoa** | Xem nguồn "Kiểm tra trước buổi tập" → **Mở ca** hoặc **Đóng sự cố** | ✅ Sự cố A1 có đủ ngữ cảnh buổi tập |
| 4.8 | veterinarian | Sự cố còn lại của **Tuyết Sơn** | **Gắn vào ca đang mở**: chọn ca của ngựa này → **Gắn vào ca** | ✅ Không mở ca trùng |
| 4.9 | groom | **Công việc điều trị** | Ô lọc **Ngựa** = **Tuyết Sơn** → tick việc **Chườm lạnh** | ✅ Đã hoàn thành, ghi giờ và người làm |
| 4.10 | veterinarian | Ca của **Thiên Mã** | Tab **Tái khám**: **Đã hồi phục**, kết quả, **Khỏe** → **Lưu lần tái khám** → **Hạn chế tập luyện**: **Gỡ hạn chế** → quay lại ca → **Đóng ca** | ✅ Ca đóng; ngựa Khỏe / Sẵn sàng. Nói: đóng ca không tự gỡ khóa, gỡ là quyết định riêng của Vet |
| 4.11 | veterinarian | **Ca y tế** → **Tạo ca y tế**; **Sức khỏe đàn ngựa** | Xem | ✅ |
| 4.12 | headtrainer | **Tóm tắt y tế** | Xem | ✅ Chỉ thấy tóm tắt; không thấy chẩn đoán, phác đồ, chấn thương (Q-04) |

**Chưa làm:** BR-28 (ảnh sự cố). BR-31: Groom đã nhận việc, nhưng phác đồ chưa thông báo cho Trainer.

---

## PHẦN 5 — Long · Flow 4 Chăm sóc + Quản trị (MF-10, MF-11, MF-13, MF-17, MF-18, MF-20) · khoảng 20 phút

| # | Tài khoản | Màn hình | Thao tác | Kết quả |
|---|---|---|---|---|
| 5.1 | clubmanager | **Tổng quan** | Khối **Chăm sóc hôm nay** | ✅ Tổng / còn lại / **quá giờ** / đã xong; danh sách việc trễ; vật tư dưới ngưỡng (BR-36) |
| 5.2 | clubmanager | **Chăm sóc & dinh dưỡng** | Chọn **Ngựa mới 2** → **Tạo kế hoạch chăm sóc**: Groom **Trần Văn Hậu**, việc Vệ sinh chuồng giờ gần nhất sắp tới, hướng dẫn → **Lưu kế hoạch & giao việc** | ✅ Toast **Đã lưu kế hoạch** góc phải dưới; việc mới hiện trong Chăm sóc hằng ngày của Groom (BR-33) |
| 5.3 | clubmanager / headtrainer | Chọn **An Khang** → loại **Dinh dưỡng** | Xem khẩu phần Cỏ khô 4 kg, Thức ăn hạt 2 kg | ✅ MF-11 (Head Trainer chỉ xem kế hoạch chăm sóc, sửa được dinh dưỡng) |
| 5.4 | clubmanager | **Chăm sóc hằng ngày** | Việc **Quá giờ** của An Khang nằm trên cùng kèm cảnh báo → lọc **Groom phụ trách** → **Thao tác** → **Đổi Groom** (có lý do) → **Lịch sử** | ✅ Lịch sử ghi người đổi, lý do, thời điểm |
| 5.5 | groom | **Tổng quan** → **Mở chăm sóc hằng ngày** | Gõ "An Khang" → **Hoàn thành** việc **08:00 (Quá giờ)** và việc **11:00 (Cần làm)**; việc **23:30 Chưa đến giờ** không có nút Hoàn thành | ✅ Ghi giờ và người làm (BR-34); ⛔ việc chưa đến giờ bị chặn |
| 5.6 | groom | Dòng việc **An Khang** → **Báo bất thường** | Chọn loại, mức → **Gửi sự cố** | ✅ Sự cố sang Vet (BR-35 · **sự cố A4**) |
| 5.7 | clubmanager | **Kho** | **Cỏ khô Timothy** → **Sổ kho / thao tác** → nhập +10 có lý do | ✅ Tồn kho tăng, có lịch sử |
| 5.8 | groom | **Kho & yêu cầu bổ sung** | Gửi yêu cầu bổ sung 20 kg | ✅ Club Manager thấy yêu cầu. Nói: cho ăn không tự trừ kho |
| 5.9 | (không đăng nhập) | **/register** | Xem form | ✅ Chỉ có họ tên / email / mật khẩu, **không có ô chọn vai trò** (DEC-008) |
| 5.10 | clubmanager | **Yêu cầu đăng ký** | **Duyệt** `lan.bui.11110120@gmail.com` (gán **đúng 1 vai trò**, ví dụ Groom); **Từ chối** `viet.phan.11110120…` (có lý do) | ✅ |
| 5.11 | clubmanager | **Nhân sự & người dùng** | Tạo tài khoản kèm mật khẩu ban đầu (DEC-012); chuyển Hoạt động / Ngừng; **Vai trò & quyền hạn**; **Tài khoản**; **Thông báo** | ✅ 7 vai trò (DEC-004) |
| 5.12 | trainer1 | Gõ URL `/admin/users` | — | ⛔ "Không có quyền truy cập" (phân quyền ở cả FE và BE) |
| 5.13 | clubmanager | **Nhật ký hệ thống** | Xem các thao tác vừa demo | ✅ MF-20 |

**Tạm hoãn theo DEC-007:** MF-09, MF-14, MF-16, MF-19. Tổng quan theo vai trò, Sức khỏe đàn ngựa và Thành tích là các phần báo cáo **có** làm.

---

## Đối chiếu: đã phủ đủ các trường hợp

| Trường hợp | Bước |
|---|---|
| Tạo ngựa → hoàn tất hồ sơ → Vet khám → tự kích hoạt | 1.2–1.7 |
| Mã ngựa không trùng (BR-01); ô chuồng không trùng (BR-03/04) | 1.2, 1.3 |
| Cân nặng theo thời gian; cổng chủ ngựa, chặn ngựa người khác | 1.8, 1.11, 1.12 |
| Mẫu chương trình có phiên bản; wizard; gợi ý Trainer | 2.1, 2.3 |
| Chặn vì Khóa (BR-13); chặn bài cấm (BR-15); chương trình phục hồi | 2.4, 2.7, 2.5 |
| Đề xuất điều chỉnh: duyệt và từ chối | 2.8 |
| Groom kiểm tra Bình thường / Bất thường → sự cố A1 | 3.1, 3.2, 3.3 |
| Rider tập đủ, có số đo; realtime; cảnh báo vượt ngưỡng; dừng vì sức khỏe | 3.4–3.7 |
| Rider báo vấn đề khi đang cưỡi → sự cố A2 | 3.8 |
| Trainer báo sự cố; đánh giá; thành tích | 3.9, 3.10 |
| Groom báo tại chuồng → sự cố A4 | 4.0, 5.6 |
| Vet đóng ngay / quy trình tối thiểu / quy trình đầy đủ (chấn thương chữ, phác đồ, Khóa) | 4.2, 4.3, 4.4–4.6 |
| Gắn sự cố vào ca đang mở; Groom làm việc điều trị; tái khám → gỡ hạn chế → đóng ca | 4.8, 4.9, 4.10 |
| Head Trainer chỉ xem tóm tắt y tế | 4.12 |
| Kế hoạch chăm sóc / dinh dưỡng; quá giờ; đổi Groom; Groom hoàn thành; chặn việc chưa đến giờ | 5.2–5.5 |
| Kho, yêu cầu bổ sung; đăng ký và duyệt; nhân sự; phân quyền; nhật ký | 5.7–5.13 |
