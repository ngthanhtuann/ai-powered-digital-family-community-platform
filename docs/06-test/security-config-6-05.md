# [6-05] Checklist bảo mật cấu hình

**Người làm:** TV3 (PiLo257)

**Reviewer:** TV1

**Nhánh chuẩn đối chiếu:** `develop` tại `ca54128` (11/11/2026)
**Phạm vi:** Secret, CORS, security headers, request size, rate limit, OWASP nhanh.

> Trạng thái dưới đây phân biệt **đã kiểm tra mã nguồn** và **đã chạy test**. Không ghi PASS khi chưa chạy kiểm chứng trên backend thực tế.

| Hạng mục | Kiểm tra/cấu hình | Trạng thái |
|---|---|---|
| Secret | `JWT_SECRET`, `GEMINI_API_KEY`, `BOOTSTRAP_ADMIN_PASSWORD` lấy từ biến môi trường. `SPRING_DATASOURCE_PASSWORD` không có mật khẩu mặc định; Docker bắt buộc có `POSTGRES_PASSWORD`. `.env` nằm trong `.gitignore`; `.env.example` để trống giá trị. | Đã cấu hình; chờ quét secret toàn lịch sử Git |
| CORS | `CORS_ALLOWED_ORIGINS` chỉ cho origin chính xác của web (phát triển mặc định `http://localhost:3000,http://localhost:3001`); không dùng `*`. Cần đặt domain thật khi triển khai. | Đã cấu hình; chờ chạy test |
| Header bảo mật | Spring Security trả `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy`; HSTS chỉ trên HTTPS. | Đã cấu hình; chờ chạy test HTTPS |
| Giới hạn request | Spring multipart `max-file-size: 10MB`, `max-request-size: 11MB`; ảnh 5 MB, PDF HERITAGE 10 MB; lỗi `413 FILE_001`. | Có sẵn; chờ test upload |
| Reverse proxy | Nginx cần `client_max_body_size 11m` và phải đổi lỗi vượt kích thước thành JSON `413 FILE_001` (không trả HTML). Repo chưa có cấu hình Nginx để sửa. | Chờ cấu hình triển khai (TV4/SCRUM-96) |
| Rate limit login | `POST /api/v1/auth/login`: 5 lần/phút theo cặp IP + email; vượt trả `429 RATE_001`, header `Retry-After`. | Có sẵn; chờ chạy test |
| Rate limit refresh | `POST /api/v1/auth/refresh`: mặc định 20 lần/phút theo IP; chỉnh bằng `AUTH_REFRESH_RATE_LIMIT_PER_MINUTE`; vượt trả `429 RATE_001`, `Retry-After`. Bộ đếm nằm trong RAM từng backend (nhiều replica cần bộ đếm chung). | Đã bổ sung; chờ chạy test |
| Rate limit AI | Chat 20 lần/giờ/người; search 30 lần/giờ/người; summary 10 lần/giờ/người theo cấu hình hiện tại; dùng `RATE_001`. | Có sẵn; chờ chạy test |
| SQL injection | Các truy vấn đã xem dùng JPA/JdbcTemplate với tham số `?` hoặc `:param`; tiếp tục kiểm tra phần `ORDER BY` động theo danh sách trường cho phép. | Rà soát mã nguồn sơ bộ; chưa kiểm thử khai thác |
| XSS | Frontend React/Next.js tự escape text; không thấy `dangerouslySetInnerHTML` khi rà nhanh. Cần test bài đăng/bình luận chứa payload XSS trên trình duyệt. | Rà soát mã nguồn sơ bộ; chưa chạy trình duyệt |
| IDOR/RBAC | Test ma trận `RbacMatrixIntegrationTest` truy cập chéo gia đình, bao gồm `GET /media/{id}/content`, đòi `403 PERM_001` và không có `data`; người chưa xác minh `403 PERM_002`. | Có test; chưa chạy xác nhận |
| Upload | `MediaStorage` kiểm tra magic bytes, so với phần mở rộng, lưu bằng UUID; media chỉ đọc qua endpoint kiểm tra quyền. | Có sẵn; chờ chạy test |

## Cách TV1 kiểm thử

1. Tạo `.env` từ `.env.example`, điền `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET` (ít nhất 32 byte), `CORS_ALLOWED_ORIGINS`. Chỉ dùng dữ liệu giả khi bật Gemini. Không đưa các giá trị này vào PR/log.
2. Chạy `docker compose up -d db`; khi chạy Maven trực tiếp, truyền `SPRING_DATASOURCE_PASSWORD` và các biến kết nối DB tương ứng. Nếu chạy Docker toàn bộ, dùng `docker compose up --build`.
3. Trong `backend`, chạy `mvn test -Dtest=RefreshRateLimiterTest,SecurityConfigTest,AuthApiIntegrationTest,RbacMatrixIntegrationTest` (cần PostgreSQL test và JWT test profile đang có).
4. Gửi request với `Origin` đúng web và một origin ngoài danh sách; chỉ origin được khai báo mới có `Access-Control-Allow-Origin`. Với HTTPS kiểm tra HSTS bằng `curl -I`; HTTP không bắt buộc có HSTS.
5. Gọi `/auth/login` quá 5 lần/phút cho cùng IP + email; gọi `/auth/refresh` quá 20 lần/phút cùng IP, xác minh `429 RATE_001` và `Retry-After`. Gọi chat/search/summary quá hạn mức với user đã xác minh.
6. Thử ảnh trên 5 MB, PDF HERITAGE trên 10 MB, tệp giả định dạng, tổng multipart trên 11 MB; xác minh lỗi `FILE_001`/`FILE_002` đúng quy ước.
7. Với hai gia đình A/B, dùng token thành viên A để gọi tài nguyên, URL, query và body có ID của B (đặc biệt media): không trả dữ liệu của B, phản hồi `PERM_001`; người chưa duyệt nhận `PERM_002` ở endpoint cần xác minh.
8. Thử input SQL injection và chuỗi XSS trong ô tìm kiếm, bài đăng, bình luận; xác minh không thay đổi truy vấn, không chạy script trong giao diện.

**Kết luận hiện tại:** Chưa xác nhận tiêu chí hoàn thành cuối cùng vì bộ test tích hợp, thao tác trình duyệt, Nginx và quét secret lịch sử cần được chạy/đối chiếu trong môi trường kiểm thử của nhóm.
