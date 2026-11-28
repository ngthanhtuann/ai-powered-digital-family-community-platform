# Kịch bản demo FamilyConnect (SCRUM-72, kế hoạch 8-02)

**Người viết:** TV1 Tuấn | **Reviewer:** TV4 | **Thời lượng:** 12–14 phút (Web 10 phút, Mobile 2 phút, dự phòng 2 phút)

Mọi dữ liệu trong demo là **dữ liệu giả** do `docker/seed-demo.ps1` tạo (họ Nguyễn – Nam Định, 53 người, 5 đời). Không dùng dữ liệu gia đình thật với Gemini gói miễn phí.

## 1. Chuẩn bị (làm trước buổi bảo vệ ít nhất 30 phút)

1. Máy demo đã có Docker Desktop. Ở thư mục gốc repo, bản `develop` mới nhất:

   ```powershell
   git pull
   docker compose --env-file .env up -d --build
   docker compose ps          # db, backend, frontend đều healthy
   powershell -ExecutionPolicy Bypass -File docker\seed-demo.ps1
   ```

   Script tự dừng nếu dữ liệu demo đã có. Muốn nạp lại từ đầu: `docker compose down -v` rồi chạy lại hai lệnh trên (mất toàn bộ dữ liệu cũ).
2. `.env` có `GEMINI_API_KEY` (cho Trợ lý AI) và `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` (tài khoản quản trị hệ thống). Không chiếu nội dung `.env` lên màn hình.
3. Mở sẵn 3 cửa sổ trình duyệt (3 hồ sơ Chrome hoặc 1 thường + 2 ẩn danh) ở `http://localhost:3000`, đã đăng nhập sẵn 3 vai trò ở mục 2.
4. Điện thoại Android đã cài APK (hoặc Expo Go) trỏ tới IP LAN của máy demo, đăng nhập sẵn `ha@demo.familyconnect.vn`.
5. Hỏi thử Trợ lý AI 1 câu để chắc Gemini còn quota (mỗi tài khoản tối đa 20 câu/giờ). Nếu lỗi, chuyển sang mục 5.

## 2. Tài khoản demo

Mật khẩu chung của tài khoản seed: `Demo@2026` (chỉ dùng trên máy demo).

| Vai trò hiển thị | Email | Quyền trong demo |
|---|---|---|
| Thành viên (MEMBER) | `ha@demo.familyconnect.vn` | Nguyễn Thị Hà, Chi 3, đời 4: xem cây, đăng bài, bình luận, hỏi AI |
| Trưởng chi (BRANCH_ADMIN, Chi 3) | `hung@demo.familyconnect.vn` | Nguyễn Văn Hùng: quản lý thành viên Chi 3, duyệt yêu cầu vào chi |
| Trưởng họ (BRANCH_ADMIN, cả họ) | `phuc@demo.familyconnect.vn` | Nguyễn Văn Phúc: người tạo dòng họ, duyệt yêu cầu, xem Dashboard |
| Đang chờ duyệt | `tu@demo.familyconnect.vn` | Đỗ Minh Tú: đã gửi yêu cầu vào Chi 1, chưa được duyệt |
| Quản trị hệ thống (SYSTEM_ADMIN) | theo `BOOTSTRAP_ADMIN_EMAIL` | Quản lý người dùng, kiểm duyệt, nhật ký, sao lưu, cấu hình |

## 3. Luồng demo Web (10 phút)

| Phút | Người trình bày | Tài khoản | Thao tác | Điều cần nói |
|---|---|---|---|---|
| 0:00–1:00 | TV1 | — | Mở trang đăng nhập, giới thiệu bài toán và kiến trúc (1 slide) | Modular monolith Spring Boot + Next.js + Expo, PostgreSQL + pgvector, Gemini |
| 1:00–2:30 | TV1 | `ha` | **Cây gia phả**: ô "Bạn" được tô màu; mở/thu nhánh; chạm một người để xem thẻ | Cây 5 đời, tải lười, 500 người dưới 500 ms (`docs/06-test/performance-6-04.md`) |
| 2:30–3:30 | TV1 | `ha` | **Tra cứu quan hệ** (`/relationship`): Hà với Nguyễn Văn Tổ → "cháu nội"; Hà với Đặng Văn Tài → "dượng"; bấm "Giải thích bằng AI" | Thuật toán tổ tiên chung gần nhất, xưng hô nội/ngoại, dâu/rể, vợ cũ |
| 3:30–5:00 | TV3 | `ha` | **Bảng tin**: đăng bài, chọn "Ai được xem" (Cả dòng họ / Chi của tôi / Con cháu của tôi); thả cảm xúc; bình luận và trả lời bình luận | BR-COM-13 phạm vi người xem; BR-COM-14 trả lời 1 cấp, thông báo cho chủ bài |
| 5:00–5:30 | TV3 | `phuc` | Chuông thông báo → "Xem bài viết": mở đúng bài | Thông báo POST_COMMENTED, COMMENT_REPLIED |
| 5:30–6:30 | TV4 | `ha` | **Danh bạ** → bấm một người → trang cá nhân; "Hồ sơ cá nhân" → "Chỉnh sửa hồ sơ" có nghề nghiệp, học vấn | BR-GEN-20: chỉ chính chủ sửa thông tin của mình |
| 6:30–7:00 | TV4 | `ha` | **Di sản**: mở một câu chuyện và tài liệu PDF | Tệp chỉ lấy qua API có kiểm tra quyền |
| 7:00–8:30 | TV5 | `ha` | **Trợ lý AI** (`/assistant`): "Tôi là ai?", "Cha tôi là ai?", "Giỗ Tổ họ Nguyễn tổ chức ở đâu?", "Quỹ khuyến học hiện có bao nhiêu tiền?" | RAG trên dữ liệu được phép xem; có trích nguồn; thiếu dữ kiện thì trả "Không tìm thấy thông tin." |
| 8:30–9:00 | TV5 | `ha` | **Tìm kiếm** (`/search`): "người làm bác sĩ" | Tìm kiếm ngữ nghĩa bằng embedding |
| 9:00–9:30 | TV4 | `phuc` | **Dashboard** và **Báo cáo** (`/reports`): xuất PDF | Thống kê thành viên, cộng đồng, sự kiện |
| 9:30–10:00 | TV1 | SYSTEM_ADMIN | **Quản trị** (`/admin/nguoi-dung`, `/admin/nhat-ky`, `/admin/sao-luu`): chọn gia đình ở thanh thả xuống | RBAC 4 vai trò; nhật ký kiểm toán; sao lưu/khôi phục |

**Kiểm tra quyền (nói kèm):** `ha` mở `/dashboard` bị từ chối; `tu` đăng nhập chỉ thấy trang "Đang chờ duyệt".

## 4. Luồng demo Mobile (2 phút, TV3)

1. Mở app, menu ☰ → **Cây gia phả**: chạm một người → trang thành viên (thông tin, nghề nghiệp, bài đăng).
2. **Bảng tin** → mở một bài → bình luận, trả lời.
3. **Trợ lý AI** → hỏi "Tôi là ai?".

## 5. Phương án dự phòng

| Sự cố | Cách xử lý |
|---|---|
| Gemini lỗi, hết quota | Nói rõ AI đang lỗi; chuyển sang kết quả đã lưu trong `docs/06-test/ai-quality-scrum-69.md` (40/40 câu) và ảnh chụp; không giả làm câu trả lời trực tiếp |
| Mạng phòng bảo vệ không có | Toàn bộ stack chạy local, chỉ AI cần Internet. Mobile dùng hotspot của máy demo |
| Stack không lên | Chiếu video quay sẵn luồng mục 3 (quay trước buổi bảo vệ, lưu trên máy demo và USB) |
| Điện thoại không kết nối | Bỏ mục 4, chiếu ảnh chụp màn hình mobile trong slide |

## 6. Ghi nhận chạy thử

| Lần | Ngày | Người chạy | Môi trường | Thời gian | Vấn đề ghi nhận |
|---|---|---|---|---|---|
| 1 | 19/11/2026 | TV1 | Stack Docker tạm, `develop` `0e52bb8` + nhánh SCRUM-126, dữ liệu seed | Chưa bấm giờ | Danh bạ hiện "Chưa có thông tin quan hệ" cho mọi người (API luôn trả `relationship = null`); bài cũ mở từ thông báo không hiện. Cả hai đã sửa ở SCRUM-126 |
| 2 | 19/11/2026 | AI (theo yêu cầu TV1) | Stack Docker, `develop` `2dc939f`, dữ liệu seed | 13,2 giây và 1,7 giây (2 lượt liên tiếp, gọi API tự động) | Không có lỗi. 11/11 luồng đạt ở cả 2 lượt: cây 53 người, tra quan hệ Hà–Tổ "cháu nội", bảng tin, sự kiện, danh bạ, di sản, Dashboard chặn MEMBER và cho trưởng họ, duyệt yêu cầu, tài khoản chưa xác minh bị chặn, AI trả lời "Tôi là ai?" |
| 3 | Chưa chạy | Cả nhóm | Máy demo | | |

Lần 1 chỉ chạy các bước Web của mục 3 với tài khoản `ha` (bảng tin, thông báo, trả lời bình luận, chọn người xem, danh bạ, trang cá nhân, form hồ sơ); chưa bấm giờ, chưa chạy Mobile, Dashboard, Quản trị. Lần 2 là kiểm thử khói qua API, không thay thế buổi tập: thời gian ghi ở bảng là thời gian máy gọi API, không phải thời lượng thuyết trình. Lần 3 chạy cả nhóm trên máy demo theo mục 3, bấm giờ và điền bảng.
