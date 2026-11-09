# Việc cần làm trước buổi demo — Racehorse Ops

Cập nhật 09/11/2026. Đọc kèm `docs/PHAN-CONG-DEMO.md` (checklist từng phần) và `docs/DEMO-DATA.md` (cách tạo dữ liệu) trong repo BE.

Demo chạy trên máy Long. Mọi người chỉ tập trên máy mình. Mỗi phần có bộ ngựa riêng, nên phần của người này không ảnh hưởng phần của người khác. Vào demo là bắt đầu luôn từ Flow 1, không cần giới thiệu.

Thứ tự: Dũng (Flow 1) → Hoàng (Flow 2, lập kế hoạch) → Tiến (Flow 2, buổi tập và realtime) → Đức (Flow 3, y tế và sự cố) → Long (Flow 4 và quản trị).

---

## PHẦN A — BẮT BUỘC, AI CŨNG LÀM

1. Pull code mới nhất ở nhánh `main` của cả FE và BE:
   `git pull --ff-only origin main`
2. Tạo DB local theo `docs/TEAM-DATABASE-SETUP.md`. Nhớ bước cài truststore TLS.
3. Thêm 20 ô chuồng demo (chỉ thêm, không sửa hay xóa gì, chạy lại vẫn an toàn):
   `sqlcmd -S localhost -E -l 90 -d <tên DB của bạn> -i scripts/demo/add-demo-stable-boxes.sql`
4. Chạy BE (cổng 8080) và FE, rồi chạy script tạo dữ liệu theo `docs/DEMO-DATA.md`:
   `node scripts/demo/seed-demo-data.mjs`
   Chạy xong phải thấy dòng `DEMO_DATA_PASS tag MMDD-HHMM` và bảng SUMMARY.
   Lưu ý: chạy trước giờ tập 15 đến 30 phút, đừng chạy từ hôm trước. Buổi tập chỉ mở trong 4 giờ, và kiểm tra trước buổi tập chỉ có hiệu lực trong 12 giờ.
5. Tập **đủ checklist phần mình** trong `PHAN-CONG-DEMO.md` ít nhất 1 lần, tick từng ô. Chỉ dùng ngựa có nhãn mới nhất của bộ mình.
6. Học thuộc câu trả lời cho các câu hỏi dự phòng thuộc phần mình (cuối file `PHAN-CONG-DEMO.md`), nhất là các mục "chưa đáp ứng" bên dưới.
7. Gặp lỗi thì gửi vào nhóm kèm ảnh chụp màn hình, các bước đã làm và nhãn bộ dữ liệu. **Không sửa thẳng trên `main`.** Tạo nhánh riêng, mở PR để Long review.

Mật khẩu không ghi trong file này. Ai cần thì hỏi Long riêng. Đừng commit mật khẩu lên git.

---

## PHẦN B — VIỆC LÀM THÊM ĐỂ ĐỦ YÊU CẦU

Mức ưu tiên:
- **CAO**: nên làm trước demo.
- **TRUNG BÌNH**: làm nếu kịp.
- **THẤP**: không cần code, chỉ chuẩn bị câu trả lời.

### Đức — Flow 3 · Y tế và sự cố

1. **[CAO] Báo sự cố kèm ảnh** (BR-28, MF-12 — Tier 1)
   - Hiện tại: form báo sự cố mới chỉ có loại, mức độ và mô tả, chưa có ảnh.
   - Cần làm:
     - BE: API nhận và lưu ảnh đính kèm sự cố.
     - FE: thêm ô chọn ảnh ở form báo sự cố, và hiện ảnh ở trang chi tiết sự cố bên Vet.
   - Khối lượng: khoảng nửa ngày, sửa cả BE và FE.
   - **Chờ Long ghi quyết định (DEC) về cách lưu ảnh trước khi code**, vì việc này đổi API và DB.
2. **[TRUNG BÌNH] Kiểm tra phác đồ có báo cho Trainer không** (BR-31)
   - Hiện tại: Groom đã nhận việc, nhưng chưa ai kiểm tra Trainer có nhận thông báo không.
   - Cần làm: tạo phác đồ cho 1 ca, đăng nhập `trainer1`, xem chuông thông báo. Nếu không có, thêm 1 thông báo ở BE.
   - Khối lượng: kiểm tra mất 15 phút, nếu phải sửa thì khoảng 1 giờ.

### Tiến — Flow 2 · Buổi tập và realtime (Hoàng hỗ trợ phần UI)

1. **[CAO] Nút "Dừng vì lý do sức khỏe" ở màn giám sát realtime phải tạo sự cố** (BR-25, BR-23)
   - Hiện tại: BE đã hỗ trợ, nhưng UI luôn gửi `medicallyRelevant: false`, nên dừng buổi tập không tạo sự cố cho Vet.
   - Cần làm (chỉ sửa FE):
     - Tách thành 2 lựa chọn: "Dừng thường" và "Dừng vì lý do sức khỏe".
     - Khi chọn lý do sức khỏe thì gửi `medicallyRelevant: true`.
     - Kiểm tra Vet thấy sự cố mới trong danh sách.
   - Khối lượng: khoảng 1 đến 2 giờ.
2. **[TRUNG BÌNH] Trainer báo sự cố trên UI** (MF-12 bước 3.1)
   - Hiện tại: BE đã cho Trainer báo sự cố, nhưng UI chỉ hiện nút cho Groom và Work Rider.
   - Cần làm (chỉ sửa FE): thêm nút "Báo sự cố" ở màn buổi tập của Trainer, dùng lại form có sẵn.
   - Khối lượng: khoảng 1 giờ.

### Dũng — Flow 1 · Quản lý ngựa

1. **[TRUNG BÌNH] Ghi cân nặng theo thời gian và vẽ biểu đồ** (MF-02, MF-15)
   - Hiện tại: chỉ có cân nặng lúc khám ban đầu. Trang của chủ ngựa đang ghi "biểu đồ sẽ hiện khi có thêm số đo".
   - Cần làm:
     - Kiểm tra BE đã có API ghi cân nặng nhiều lần chưa (BE đã có bảng lưu cân nặng).
     - FE: thêm form ghi cân nặng cho Vet và biểu đồ cân nặng trên hồ sơ ngựa.
   - Khối lượng: khoảng nửa ngày.
2. **[THẤP] Video chạy thử, lịch sử thi đấu** (MF-15)
   - Không code. Video chạy thử đang là NEEDS CLARIFICATION (Q-12). Lịch sử thi đấu phụ thuộc MF-14, đang tạm hoãn theo DEC-007.
   - Chuẩn bị câu trả lời nếu thầy hỏi.

### Hoàng — Flow 2 · Lập kế hoạch huấn luyện

1. **[CAO] Hỗ trợ Tiến** làm 2 việc UI ở trên (nút dừng realtime, Trainer báo sự cố). Hoặc review PR của Tiến.
2. **[THẤP] Lượt chạy thử** (MF-05)
   - Chưa làm, và yêu cầu gốc chưa mô tả rõ.
   - Không code. Chuẩn bị câu trả lời: "phần này đang chờ làm rõ yêu cầu (NEEDS CLARIFICATION)".

### Long — Flow 4 và quản trị, Leader

1. **[CAO] Ghi quyết định (DEC)** cho phần ảnh sự cố, để Đức có thể bắt đầu.
2. **[CAO] Review và merge PR** của mọi người trước ngày demo. Chốt hạn nộp PR là **trước demo 1 ngày**, sau đó chỉ sửa lỗi.
3. **[BẮT BUỘC] Ngày demo:**
   - Pull `main` mới nhất.
   - Chạy `seed-demo-data.mjs` trên máy Long trước giờ demo 15 đến 30 phút.
   - Gửi nhãn bộ dữ liệu mới nhất vào nhóm để mọi người biết dùng ngựa nào.
4. **[THẤP]** Chuẩn bị câu trả lời cho 2 câu dễ bị hỏi:
   - BR-44 đã được thay bằng DEC-004 (7 vai trò).
   - MF-09, MF-14, MF-16, MF-19 đang tạm hoãn theo DEC-007.

---

## PHẦN C — NẾU KHÔNG KỊP

Chỉ làm 2 việc **CAO** có code:
1. Nút dừng realtime (Tiến và Hoàng): chỉ sửa FE, rủi ro thấp.
2. Ảnh sự cố (Đức): yêu cầu Tier 1, dễ bị hỏi nhất.

Các việc còn lại cứ trả lời thẳng là "chưa làm" hoặc "đang chờ làm rõ yêu cầu", theo bảng "Đối chiếu yêu cầu" trong `PHAN-CONG-DEMO.md`. Đừng nói là đã làm khi chưa làm.

## Quy tắc khi sửa code

- Mỗi việc làm trên 1 nhánh riêng, mở PR vào `main`, ghi rõ đang sửa BR hay MF nào.
- Nếu đổi API thì sửa cả FE và BE trong cùng đợt, rồi cập nhật OpenAPI.
- Không xóa hẳn dữ liệu y tế, huấn luyện hay nhật ký (audit).
- Sửa xong thì chạy lại script tạo dữ liệu trên máy mình và tập lại đủ checklist phần mình.
