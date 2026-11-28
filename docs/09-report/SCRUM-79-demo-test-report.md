# KỊCH BẢN DEMO VÀ BÁO CÁO KIỂM THỬ - FAMILYCONNECT (MÃ KẾ HOẠCH: 8-09 / SCRUM-79)

**Dự án:** AI-Powered Digital Family Community Platform (FamilyConnect)  
**Mã kế hoạch / Task:** 8-09 (SCRUM-79: Chạy thử demo toàn nhóm, kiểm thử máy sạch, hoàn thiện báo cáo và chuẩn bị bảo vệ)  

---

## 1. MỤC TIÊU & PHẠM VI KIỂM THỬ (TEST SCOPE)
- **Mục tiêu:** Kiểm tra toàn bộ hệ thống trên môi trường sạch (Clean Environment / Docker Deployment), đảm bảo các tính năng chính chạy trơn tru, không có lỗi Critical hoặc Major.
- **Phạm vi kiểm thử:**
  1. Module Xác thực & Phân quyền (Auth & RBAC Matrix).
  2. Module Danh bạ & Tìm kiếm thành viên gia đình (`Directory & Search`).
  3. Module Cây gia phả & Quan hệ họ hàng (`Genealogy & Kinship`).
  4. Module Cộng đồng & Đăng bài, chọn phạm vi bài đăng (`Community & Post Scope`).
  5. Module Trợ lý AI, Tóm tắt & Tìm kiếm ngữ nghĩa (`AI Chat & Semantic Search`).

---

## 2. KỊCH BẢN DEMO TRÊN MÁY SẠCH (DEPLOYMENT & DEMO SCRIPT)

### Bước 1: Khởi chạy môi trường sạch (Clean Deployment via Docker)
1. Clone mã nguồn mới nhất từ kho lưu trữ của nhóm.
2. Cấu hình file `.env` chuẩn (Database PostgreSQL, JWT Secret, AI API Keys).
3. Chạy lệnh dựng hệ thống bằng Docker Compose:
   ```bash
   docker compose down -v
   docker compose --env-file .env up -d --build
   ```
4. Kiểm tra logs backend và frontend để đảm bảo các service (Spring Boot API, PostgreSQL, frontend Next.js) khởi động thành công không có lỗi kết nối.

### Bước 2: Kịch bản Kiểm thử & Demo Chức năng
- **Scenarios 1: Đăng nhập & Phân quyền (RBAC)**
  - Đăng nhập bằng tài khoản Quản trị viên (Admin) và tài khoản Thành viên thường.
  - *Kiểm tra:* Xác thực JWT token hoạt động chính xác, phân quyền truy cập các endpoint quản trị (`/api/v1/admin/**`) trả 403 với tài khoản thường.
- **Scenarios 2: Tìm kiếm Danh bạ & Lọc hồ sơ (`DirectoryModule`)**
  - Truy cập trang Danh bạ thành viên gia đình.
  - Nhập từ khóa tìm kiếm, lọc theo chi/nhánh hoặc quan hệ.
  - *Kiểm tra:* Kết quả trả về chính xác, phân trang mượt mà, không xảy ra lỗi N+1 query hoặc timeout.
- **Scenarios 3: Cây gia phả & Quan hệ họ hàng (`GenealogyModule`)**
  - Xem sơ đồ cây gia phả, thêm thành viên mới, kiểm tra tính toán quan hệ họ hàng (Kinship calculation).
- **Scenarios 4: Đăng bài viết cộng đồng & Chọn phạm vi (`CommunityModule`)**
  - Tạo bài đăng mới, chọn phạm vi hiển thị (Toàn tộc / Chi cụ thể).
  - *Kiểm tra:* Nội dung được lưu trữ an toàn, kiểm duyệt nội dung (Content Moderation) hoạt động tốt.
- **Scenarios 5: Trợ lý AI & Tóm tắt thông minh (`AIModule`)**
  - Sử dụng tính năng trò chuyện với AI và tóm tắt sự kiện gia tộc qua `AiSummaryController`.

---

## 3. BẢNG GHI NHẬN LỖI (BUG TRACKING & RESOLUTION)

| ID Lỗi | Mô tả lỗi phát hiện | Mức độ | Trạng thái | Hướng giải quyết / Khắc phục |
|--------|---------------------|--------|------------|-------------------------------|
| BUG-FC-01 | Lỗi kết nối database sau khi `docker compose down -v` do khởi động PostgreSQL chậm hơn Spring Boot. | **Major** | Đã khắc phục | Cấu hình thêm `healthcheck` và `depends_on` trong `docker-compose.yml` để backend chờ DB sẵn sàng hoàn toàn. |
| BUG-FC-02 | Danh bạ hiện "Chưa có thông tin quan hệ" cho mọi người: API `GET /directory/members` luôn trả `relationship = null`. | **Major** | Đã khắc phục | Tính quan hệ với người xem bằng `relationshipLabels` (PR #164, SCRUM-126). |
| BUG-FC-03 | Bấm "Xem bài viết" trong thông báo không mở được bài cũ nằm ngoài trang đầu bảng tin. | **Minor** | Đã khắc phục | Mở `/community?item=<id>`, tải riêng bài và ghim lên đầu (PR #164, SCRUM-126). |

BUG-FC-02 và BUG-FC-03 phát hiện khi chạy thử demo lần 1 ngày 19/11/2026 (`demo-script.md` mục 6). BUG-FC-01: healthcheck và `condition: service_healthy` có trong `docker-compose.yml` từ SCRUM-68.

---

## 4. NỘI DUNG THUYẾT TRÌNH BẢO VỆ (3 - 4 PHÚT)

- **Mở đầu (0 - 1 phút):** Chào thầy cô và các bạn, đại diện nhóm xin trình bày kết quả kiểm thử và kịch bản demo cho nền tảng **FamilyConnect** (Mã kế hoạch 8-09). Hệ thống hướng tới xây dựng một mạng xã hội gia tộc thông minh ứng dụng AI.
- **Kiến trúc & Triển khai (1 - 2.5 phút):**
  - Hệ thống được docker hóa toàn diện (Spring Boot Backend, Next.js Frontend, PostgreSQL); Mobile dùng Expo.
  - Tích hợp các module cốt lõi: Quản lý danh bạ thành viên, Cây gia phả, Đăng bài viết phân quyền theo chi, và Trợ lý AI tìm kiếm ngữ nghĩa.
- **Kết quả kiểm thử & Demo (2.5 - 4 phút):**
  - Chạy thử lần 1 (19/11/2026) phát hiện 2 lỗi, đã sửa ở PR #164. Lần chạy thử toàn nhóm thứ 2: chưa xác minh (ghi kết quả vào `demo-script.md` mục 6).
  - Sẵn sàng bảo vệ đồ án trước hội đồng.
