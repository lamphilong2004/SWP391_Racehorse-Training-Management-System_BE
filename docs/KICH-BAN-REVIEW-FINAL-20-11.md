# Kịch bản review final 20/11 (09:20–13:00) — dữ liệu cố định

Hôm nay tập và mai review dùng **đúng cùng một bộ dữ liệu** và **đúng cùng các giá trị nhập** dưới đây.
Chỉ có ngày giờ khác nhau. Không tự đổi tên, số liệu hay câu chữ khi nhập.

## 0. Chuẩn bị trên máy Long

| Khi nào | Làm gì |
|---|---|
| Trước mỗi lần tập | Bật SQL Server → chạy `START-RACEHORSE-LOCAL.cmd` → chạy **`TAP-DOT-DU-LIEU.cmd`** (khoảng 3–8 phút). Dữ liệu về y nguyên, buổi tập mở từ khoảng 5 phút sau đó trong 4 giờ. |
| Giữa 2 lần tập | Chạy lại `TAP-DOT-DU-LIEU.cmd`. Hai lần chạy nên cách nhau ít nhất 15 phút (giới hạn đăng ký 5 lần/15 phút). |
| **Sáng 20/11, 07:30–09:00** | Bật SQL Server → `START-RACEHORSE-LOCAL.cmd` → **`CHUAN-BI-REVIEW-20-11.cmd`**. Dữ liệu y hệt lúc tập, buổi tập 09:20–13:00. **Sau đó không chạy lại nữa.** |

- Mở http://localhost:5173 bằng **Microsoft Edge**. Mật khẩu mọi tài khoản: `123456789a`.
- Mỗi người dùng **1 cửa sổ InPrivate riêng** (Ctrl+Shift+N) để đổi tài khoản không bị dính phiên đăng nhập.
- Ảnh cho 2 ngựa mới: thư mục `ANH-NGUA-CHO-MAI\` (`ngua-moi-1.jpg`, `ngua-moi-2.jpg`).

### Tài khoản

| Vai trò | Email | Tên hiển thị |
|---|---|---|
| Club Manager | clubmanager@gmail.com | Lâm Quốc Bảo |
| Head Trainer | headtrainer@gmail.com | Nguyễn Hoàng Phúc |
| Trainer | trainer1@gmail.com | Lê Minh Quân |
| Veterinarian | veterinarian@gmail.com | Phạm Thanh Tâm |
| Groom | groom@gmail.com | Trần Văn Hậu |
| Groom (thay ca) | groom1@gmail.com | Lý Văn Tài |
| Work Rider | workrider1@gmail.com · workrider2@gmail.com · workrider3@gmail.com · workrider@gmail.com | Phạm Đức Anh · Võ Thanh Tùng · Đặng Khánh Linh · Huỳnh Gia Bảo |
| Horse Owner | horseowner1@gmail.com · horseowner2@gmail.com | Nguyễn Minh Khang · Trần Bảo Ngọc |

### Dữ liệu có sẵn sau khi chạy lệnh chuẩn bị (lần nào cũng giống nhau)

| Mã | Ngựa | Dành cho | Trạng thái sẵn có |
|---|---|---|---|
| RH-001 | Phong Vân | Dũng (dự phòng) | Nháp, chờ Vet khám ban đầu |
| RH-002 | Hoàng Kim | Dũng | Hoạt động, chủ Nguyễn Minh Khang |
| RH-003 | Ngọc Trai | Dũng | Hoạt động, chủ Trần Bảo Ngọc (Khang không xem được) |
| RH-004 | Thanh Long | Hoàng | Chưa có chương trình |
| RH-005 | Kim Sư | Hoàng, Tiến | Có chương trình giao Lê Minh Quân, 2 đề xuất điều chỉnh chờ duyệt |
| RH-006 | Mặc Vũ | Hoàng | Đang Khóa |
| RH-007 | Bão Cát | Hoàng | Hạn chế, cấm "Breeze nước rút" |
| RH-008 | Xích Thố | Tiến | Buổi tập trong khung giờ, workrider1, chưa kiểm tra, ngưỡng nhịp tim 180; thêm 1 buổi đã hoàn thành chờ đánh giá |
| RH-009 | Lôi Phong | Tiến | Buổi tập, workrider3, chưa kiểm tra |
| RH-010 | Ánh Dương | Tiến | Buổi tập, workrider2, **đã kiểm tra đạt** |
| RH-011 | Sương Mai | Đức | 2 sự cố Mới báo: Bỏ ăn, Vấn đề móng |
| RH-012 | Hắc Phong | Đức | Sự cố Khập khiễng mức cao |
| RH-013 | Tuyết Sơn | Đức | Ca **#1** đang mở + 1 sự cố mới |
| RH-014 | Thiên Mã | Đức | Ca **#2**, đang Khóa, việc Groom đã xong |
| RH-015 | Liên Hoa | Đức | Sự cố từ phiếu kiểm tra trước buổi tập |
| RH-016 | Phi Vân | Đức | Sự cố Work Rider báo khi đang cưỡi |
| RH-017 | An Khang | Long | Việc chăm sóc **quá giờ** (07:50 khi review), **cần làm** (10:50), 23:30 chưa đến giờ |
| — | Kho "Cỏ khô Timothy" | Long | 200 kg, ngưỡng 50 kg |
| — | Bùi Thị Lan `lan.bui.g2@gmail.com`, Phan Quốc Việt `viet.phan.g2@gmail.com` | Long | Chờ duyệt đăng ký |

Mã buổi tập (TS…) và mã sự cố (INC-…) do hệ thống tự sinh nên mỗi lần chạy khác nhau. Luôn tìm theo **tên ngựa**.

---

## PHẦN 1 — Dũng · Flow 1 Quản lý hồ sơ ngựa · khoảng 15 phút

### 1.1 Club Manager tạo ngựa mới 1 — `clubmanager@gmail.com`
Ngựa → **Thêm ngựa**, nhập đúng:

| Trường | Giá trị |
|---|---|
| Tên ngựa | **Lam Kiếm** |
| Giống ngựa | Thoroughbred |
| Giới tính | Ngựa thiến (Gelding) |
| Ngày sinh | 12/04/2022 |
| Quốc gia xuất xứ | Việt Nam |
| Số vi mạch / số đăng ký | VN-2022-0412 |
| Thế mạnh | Nước rút |
| Thêm ảnh | `ANH-NGUA-CHO-MAI\ngua-moi-1.jpg` |

→ **Lưu nháp & tiếp tục**. Nói: "Mã ngựa do hệ thống cấp (BR-01) nên không thể trùng" — mã dự kiến là **RH-018**.

Bước 2 — Phả hệ & sở hữu:

| Trường | Giá trị |
|---|---|
| Ngựa cha | Frankel (nhập tay) |
| Ngựa mẹ | Lam Sơn Queen (nhập tay) |
| Chủ ngựa | Nguyễn Minh Khang |
| Xếp ô chuồng | **C-90** (thử bấm 1 ô đang có ngựa trước để cho thấy bị chặn — BR-03/04) |

→ **Lưu & tiếp tục** → ngựa vào **Chờ kích hoạt**.

### 1.2 Club Manager tạo ngựa mới 2
Lặp lại 1.1 với:

| Trường | Giá trị |
|---|---|
| Tên ngựa | **Hồng Ngọc** |
| Giống ngựa | Thoroughbred |
| Giới tính | Ngựa cái (Mare) |
| Ngày sinh | 08/09/2021 |
| Quốc gia xuất xứ | Úc |
| Số vi mạch / số đăng ký | AU-2021-0908 |
| Thế mạnh | Sức bền |
| Ảnh | `ngua-moi-2.jpg` |
| Ngựa cha / mẹ | Snitzel / Ruby Rose |
| Chủ ngựa | Nguyễn Minh Khang |
| Ô chuồng | **C-91** |

Mã dự kiến **RH-019**. Mở **Chờ kích hoạt**: cả 2 ngựa còn thiếu "Khám ban đầu của Veterinarian".

### 1.3 Veterinarian khám ban đầu — `veterinarian@gmail.com`
Tổng quan → Ngựa mới đến → **Ghi khám ban đầu**:

| Ngựa | Cân nặng (kg) | Thể trạng hiện tại | Tình trạng | Ghi chú của Veterinarian |
|---|---|---|---|---|
| Lam Kiếm | 468 | Tốt, dáng đi đều | Khỏe | Đủ điều kiện bắt đầu tập |
| Hồng Ngọc | 455 | Tốt | Khỏe | Theo dõi khẩu phần tuần đầu |

→ **Lưu kết quả khám** → ngựa **tự chuyển Hoạt động** (BR-05/07).
Hồ sơ **Hồng Ngọc** → Lịch sử cân nặng → ghi thêm **458 kg** → biểu đồ có 2 điểm (MF-02).

### 1.4 Head Trainer đánh giá sẵn sàng — `headtrainer@gmail.com`
Ngựa → **Hoàng Kim** → **Ghi đánh giá sẵn sàng**: mức **Sẵn sàng**, nhận xét "Thể lực tốt, có thể vào chương trình tốc độ". Nói: chỉ là khuyến nghị, không chặn kích hoạt.

### 1.5 Horse Owner — `horseowner1@gmail.com`
**Ngựa của tôi**: thấy Hoàng Kim, Lam Kiếm, Hồng Ngọc → mở **Hồng Ngọc** → Sức khỏe & mức sẵn sàng (biểu đồ cân nặng 455 → 458).
Gõ thẳng URL hồ sơ **Ngọc Trai** (`/horses/3`) → ⛔ "Không có quyền truy cập" (Q-04).

---

## PHẦN 2 — Hoàng · Flow 2 Lập kế hoạch huấn luyện · khoảng 15 phút

### 2.1 Thư viện mẫu — `headtrainer@gmail.com`
Mẫu chương trình → mở **"Tốc độ · Tăng tốc có kiểm soát"**:
- **Lưu phiên bản mới**: sửa "Thời lượng khuyến nghị" thành **6 tuần** → lưu → thành phiên bản 2. Chọn lại phiên bản 1 để cho thấy bản cũ chỉ xem.
- **Sao chép** → tên "Tốc độ · Tăng tốc có kiểm soát copy" → mở bản sao → **Lưu trữ**.
- Nói (DEC-005): sửa mẫu không làm đổi chương trình đã tạo từ mẫu.

### 2.2 Tạo chương trình cho Lam Kiếm
**+ Tạo chương trình huấn luyện** (wizard 7 bước):

| Bước | Nhập / chọn |
|---|---|
| 1. Ngựa | Gõ tìm **Lam Kiếm** → chọn |
| 2. Hồ sơ | Xem cân nặng 468, khả năng tập Sẵn sàng |
| 3. Mẫu | Chọn **"Nước rút · Chuẩn bị cự ly ngắn (dưới 1.400 m)"** (nhãn Phù hợp, có lý do vì ngựa có thế mạnh Nước rút) |
| 4. Tùy chỉnh | Tên chương trình: **Lam Kiếm · Nước rút 1.200 m**; Mục tiêu huấn luyện: **Chuẩn bị cự ly 1.200 m**; giữ các giai đoạn của mẫu |
| 5. Khối lượng | Giữ mặc định của mẫu |
| 6. Trainer | **Lê Minh Quân**; Lý do phân công: **Chuyên tốc độ, đang trống lịch** |
| 7. Xem lại | **Tạo chương trình** → toast thành công góc phải dưới |

### 2.3 Các trường hợp bị chặn
- Tạo chương trình cho **Mặc Vũ** → ⛔ bị từ chối kèm lý do (BR-13: đang Khóa).
- Mở bước 1 với **Thanh Long**: nói BR-17/18 (ngựa vừa hồi phục thì chọn chương trình cường độ thấp).

### 2.4 Trainer tạo buổi tập — `trainer1@gmail.com`
Chương trình được giao → **Kim Sư** → **Tạo buổi tập**:

| Trường | Giá trị |
|---|---|
| Ngày | hôm nay (tự điền) |
| Giờ bắt đầu | **12:00** (khi tập: giờ chẵn gần nhất **sau** giờ hiện tại) |
| Thời lượng dự kiến | 60 (tự điền) |
| Trọng tâm / cường độ | giữ theo giai đoạn (tự điền) |
| Bài tập | **Khởi động đi bộ**, **Nước kiệu nhẹ** |
| Work Rider | **Huỳnh Gia Bảo** (thử chọn Phạm Đức Anh trước để thấy bị khóa vì trùng lịch) |

→ **Lưu & thông báo**.

Thử tạo buổi cho **Bão Cát** và thêm bài **Breeze nước rút** → ⛔ bị chặn, phải xóa hoặc thay bài (BR-15).

### 2.5 Duyệt đề xuất điều chỉnh — `headtrainer@gmail.com`
Yêu cầu điều chỉnh → 2 đề xuất của **Kim Sư**:
- Đề xuất "đổi mục tiêu giai đoạn" → **Duyệt**.
- Đề xuất "kéo dài thời gian" → **Từ chối**, lý do: **Giữ nguyên lịch giai đoạn hiện tại**.

---

## PHẦN 3 — Tiến · Flow 2 Thực hiện buổi tập, realtime, đánh giá · khoảng 20 phút

Mở 2 cửa sổ InPrivate: cửa sổ A cho Groom/Work Rider, cửa sổ B cho trainer1.

| # | Tài khoản | Thao tác (giá trị cố định) | Kết quả |
|---|---|---|---|
| 3.1 | groom | **Hôm nay** → buổi **Xích Thố** → Bắt đầu kiểm tra → 5 mục **Bình thường** → Hoàn tất | ✅ Rider được bắt đầu (BR-21) |
| 3.2 | groom | Buổi **Lôi Phong** → mục **Chân & móng: Bất thường**, quan sát: **Móng trước phải nóng, ấn đau** → Báo cáo sự cố (điền sẵn) → Gửi | ✅ Biên nhận INC-…; ⛔ buổi bị chặn (BR-22/23) |
| 3.3 | workrider3 | Lịch phân công → **Lôi Phong** → Bắt đầu | ⛔ Bị chặn vì kiểm tra bất thường |
| 3.4 | workrider1 | **Xích Thố** → Bắt đầu → Thêm số đo: nhịp tim **150**, tốc độ **35**, quãng đường **500** | ✅ Đang tập |
| 3.5 | trainer1 (B) | **Giám sát trực tiếp** → buổi Xích Thố | ✅ Số đo hiện ngay (STOMP) |
| 3.6 | workrider1 | Thêm số đo: nhịp tim **205**, tốc độ **38**, quãng đường **900** | ✅ Cảnh báo **CRITICAL** (ngưỡng 180, Q-07) |
| 3.7 | trainer1 | **Xác nhận** → **Tạm dừng** → **Tiếp tục** → **Dừng vì lý do sức khỏe**: loại **Kiệt sức**, mức **Cao**, mô tả **Nhịp tim vượt 200** | ✅ Buổi Bị gián đoạn + sự cố cho Vet (BR-25) |
| 3.8 | workrider2 | **Ánh Dương** → Bắt đầu → **Báo cáo vấn đề**: **Khập khiễng**, mức **Cao**, mô tả **Hụt nhịp chân sau trái ở vòng 2** | ✅ Bị gián đoạn, có sự cố (DEC-013) |
| 3.9 | trainer1 | **Đánh giá** buổi **Xích Thố (đã hoàn thành)**: **Đạt một phần**; tóm tắt **Đạt tốc độ mục tiêu, sức bền chưa đủ**; khuyến nghị **Giữ cường độ, thêm 1 buổi canter** → Lưu | ✅ Hiện ở Thành tích |
| 3.10 | workrider1 | **Lịch sử cưỡi** | ✅ Có buổi đã hoàn thành |

---

## PHẦN 4 — Đức · Flow 3 Y tế và sự cố · khoảng 20 phút

| # | Tài khoản | Thao tác (giá trị cố định) | Kết quả |
|---|---|---|---|
| 4.0 | groom | Sự cố → **Báo bất thường tại chuồng**: ngựa **Hồng Ngọc**, loại **Bỏ ăn**, mức **Thấp**, mô tả **Ăn ít hơn nửa khẩu phần buổi sáng** → Gửi | ✅ Biên nhận |
| 4.1 | veterinarian | **Sự cố**: chỉ cột Người báo · Nguồn (Groom tại chuồng / kiểm tra trước buổi tập / Work Rider khi cưỡi / Trainer) | ✅ DEC-013 |
| 4.2 | veterinarian | Sự cố **Bỏ ăn** của **Sương Mai** → **Đóng sự cố** → xác nhận | ✅ Đóng ngay |
| 4.3 | veterinarian | Sự cố của **Hồng Ngọc** (4.0) → **Mở ca y tế** → Khám: triệu chứng **Ăn lại bình thường sau 1 bữa**, tình trạng **Khỏe** → Lưu → **Đóng ca** | ✅ Quy trình tối thiểu (DEC-014), sự cố tự đóng |
| 4.4 | veterinarian | Sự cố **Khập khiễng** của **Phi Vân** → Mở ca → Khám: **Chấn thương** → tab Chấn thương: Vị trí **Chân sau trái – hụt nhịp**, mức độ **Trung bình**, hồi phục **Chưa hồi phục** → Lưu | ✅ Chấn thương khai báo bằng chữ (DEC-015) |
| 4.5 | veterinarian | Tab **Điều trị** → Điều trị và thuốc: **Chườm lạnh 15 phút, 2 lần/ngày**; Nghỉ ngơi: **Nghỉ tập 7 ngày**; Ngày tái khám: **7 ngày sau**; Thêm công việc cho Groom: **Trần Văn Hậu**, hướng dẫn **Chườm lạnh chân sau trái 15 phút** → Lưu phác đồ | ✅ Giao Groom theo tên (BR-32) |
| 4.6 | veterinarian | **Quản lý khả năng tập luyện & hạn chế** → **Khóa**, lý do **Chấn thương chân sau trái, theo dõi 7 ngày** → Lưu | ✅ Phi Vân Khóa (BR-13/14) |
| 4.7 | veterinarian | Sự cố của **Liên Hoa** (từ phiếu kiểm tra) → xem nguồn → **Đóng sự cố** | ✅ |
| 4.8 | veterinarian | Sự cố còn lại của **Tuyết Sơn** → **Gắn vào ca đang mở** → ca **#1** | ✅ Không mở ca trùng |
| 4.9 | groom | **Công việc điều trị của tôi** → lọc **Tuyết Sơn** → tick việc **Chườm lạnh** | ✅ Ghi giờ và người làm |
| 4.10 | veterinarian | Ca **#2 Thiên Mã** → Tái khám: **Đã hồi phục**, kết quả **Đi đều, hết sưng**, **Khỏe** → Lưu → Hạn chế tập luyện → **Gỡ hạn chế** → **Đóng ca** | ✅ Khỏe / Sẵn sàng. Nói: đóng ca không tự gỡ khóa |
| 4.11 | headtrainer | **Tóm tắt y tế** → Phi Vân | ✅ Chỉ tóm tắt, không thấy chẩn đoán/phác đồ (Q-04) |

---

## PHẦN 5 — Long · Flow 4 Chăm sóc + Quản trị · khoảng 20 phút

| # | Tài khoản | Thao tác (giá trị cố định) | Kết quả |
|---|---|---|---|
| 5.1 | clubmanager | **Tổng quan** → khối Chăm sóc hôm nay | ✅ Có việc quá giờ |
| 5.2 | clubmanager | **Chăm sóc & dinh dưỡng** → ngựa **Hồng Ngọc** → Tạo kế hoạch chăm sóc: Groom **Trần Văn Hậu**; Việc 1 **Vệ sinh chuồng**, khung giờ **17:00**, hướng dẫn **Dọn và thay rơm ô chuồng** → **Lưu kế hoạch & giao việc** | ✅ Toast; việc mới trong Chăm sóc hằng ngày (BR-33) |
| 5.3 | clubmanager | Ngựa **An Khang** → loại **Dinh dưỡng** | ✅ Cỏ khô 4 kg, Thức ăn hạt 2 kg (MF-11) |
| 5.4 | clubmanager | **Chăm sóc hằng ngày** → việc **quá giờ** của An Khang trên cùng → Thao tác → **Đổi Groom**: **Lý Văn Tài**, lý do **Đổi ca trực** → **Lịch sử** | ✅ Ghi người đổi, lý do, thời điểm |
| 5.5 | groom | Chăm sóc hằng ngày → gõ **An Khang** → **Hoàn thành** việc quá giờ còn của Trần Văn Hậu và việc **cần làm**; việc **23:30** không có nút Hoàn thành | ✅ BR-34 |
| 5.6 | groom | Dòng **An Khang** → **Báo bất thường**: loại **Bỏ ăn**, mức **Thấp**, mô tả **Còn thừa nửa máng cỏ** → Gửi | ✅ Sang Vet (BR-35) |
| 5.7 | clubmanager | **Kho** → **Cỏ khô Timothy** → Sổ kho / thao tác → biến động **+10**, lý do **Nhập lô cỏ khô tháng 11** | ✅ 200 → 210 kg |
| 5.8 | groom | Kho → yêu cầu bổ sung **Cỏ khô Timothy 20 kg**, lý do **Dự trù tuần tới** | ✅ Không tự cộng kho (BR-36) |
| 5.9 | (chưa đăng nhập) | Mở **/register** | ✅ Không có ô chọn vai trò (DEC-008) |
| 5.10 | clubmanager | **Yêu cầu đăng ký**: **Duyệt** Bùi Thị Lan → vai trò **Groom**; **Từ chối** Phan Quốc Việt, lý do **Không thuộc nhân sự câu lạc bộ** | ✅ |
| 5.11 | clubmanager | **Nhân sự & người dùng** → Tạo: **Trịnh Văn Nam**, `trinh.nam.g2@gmail.com`, vai trò **Groom**, mật khẩu ban đầu `123456789a` | ✅ DEC-012 |
| 5.12 | trainer1 | Gõ URL `/admin/users` | ⛔ Không có quyền truy cập |
| 5.13 | clubmanager | **Nhật ký hệ thống** | ✅ Thấy các thao tác vừa làm (MF-20) |

---

## Sau mỗi lần tập
Chạy lại `TAP-DOT-DU-LIEU.cmd` để dữ liệu về y nguyên. Sáng 20/11 chạy `CHUAN-BI-REVIEW-20-11.cmd` một lần duy nhất.
