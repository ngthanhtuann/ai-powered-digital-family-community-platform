# Hướng dẫn cài đặt và triển khai

> Sprint 7-8 (TV4 Huy Quốc, SCRUM-68, SCRUM-77). Yêu cầu hệ thống, cài Docker, file `.env.example`, lệnh `docker compose up`, nạp dữ liệu mẫu, tài khoản demo từng vai trò, backup/restore, High Availability, xử lý sự cố.

Hướng dẫn sao lưu/khôi phục dành cho SCRUM-86: [backup-restore.md](backup-restore.md).

Chế độ High Availability hai backend + Nginx được thực hiện riêng trong
`docker-compose.ha.yml` (SCRUM-96). Báo cáo kiểm thử, giới hạn và cách chạy:
[high-availability-7-11.md](../06-test/high-availability-7-11.md).

## Healthcheck và thứ tự khởi động (SCRUM-68)

Compose chỉ khởi chạy backend sau khi PostgreSQL khỏe; frontend chờ backend khỏe. Docker kiểm tra PostgreSQL bằng `pg_isready`, backend qua `/actuator/health` và frontend qua HTTP trên cổng 3000. Xem trạng thái bằng:

```powershell
docker compose ps
docker inspect --format='{{.State.Health.Status}}' familyconnect-db
docker inspect --format='{{.State.Health.Status}}' familyconnect-backend
docker inspect --format='{{.State.Health.Status}}' familyconnect-frontend
```

Ba container cần có trạng thái `healthy`. Backend có thời gian khởi động ban đầu 60 giây để chạy Flyway; frontend là 30 giây.

## Kiểm tra dữ liệu qua khởi động lại

Thử nghiệm sau tạo một bảng đánh dấu tạm trong PostgreSQL và một tệp đánh dấu riêng trong volume `uploads`. Thay `familyconnect` trong các lệnh PostgreSQL nếu `.env` đặt tên database hoặc user khác. Không chạy `docker compose down -v`: cờ `-v` xóa các volume chứa dữ liệu.

```powershell
$marker = [guid]::NewGuid().ToString()
$table = "docker_persistence_check_$($marker.Replace('-', ''))"
docker compose exec -T db psql -U familyconnect -d familyconnect -c "CREATE TABLE public.$table (marker text PRIMARY KEY); INSERT INTO public.$table(marker) VALUES ('$marker');"
docker compose exec -T backend sh -c "printf '%s' '$marker' > '/var/lib/familyconnect/media/.persistence-check-$marker'"
docker compose down
docker compose up -d
docker compose ps
docker compose exec -T db psql -U familyconnect -d familyconnect -tAc "SELECT marker FROM public.$table WHERE marker = '$marker';"
docker compose exec -T backend sh -c "cat '/var/lib/familyconnect/media/.persistence-check-$marker'"
```

Sau khi khởi động lại, cả hai lệnh kiểm tra cuối phải in ra đúng giá trị `$marker`, và ba dịch vụ phải trở lại `healthy`. Dọn dấu kiểm sau khi xác nhận:

```powershell
docker compose exec -T db psql -U familyconnect -d familyconnect -c "DROP TABLE public.$table;"
docker compose exec -T backend rm "/var/lib/familyconnect/media/.persistence-check-$marker"
```

Đã xác minh ngày 12/11/2026 trên stack validation cô lập: dấu kiểm trong PostgreSQL và `uploads` vẫn tồn tại sau `docker compose down` rồi `docker compose up -d`; cả `db`, `backend` và `frontend` đều trở lại `healthy`. Dấu kiểm tạm đã được xóa sau khi xác nhận.

Compose hiện không đặt Nginx trước backend: trình duyệt gọi API trực tiếp qua cổng 8080. Vì vậy không cần cấu hình Nginx trong phạm vi triển khai này.

## Hướng dẫn triển khai trên máy sạch bằng Docker (SCRUM-77)

### 1. Yêu cầu và chuẩn bị

- **Bắt buộc:** Docker Desktop (đã bao gồm Docker Compose v2) và Git (hoặc tải mã nguồn dạng ZIP).
- **Không cần** cài Java, Node.js hay PostgreSQL trên máy.
- Hướng dẫn này viết cho **Windows + PowerShell**.
- Thời gian thực hiện dự kiến: **dưới 15 phút**. Lần build đầu phải tải image và dependency nên phụ thuộc tốc độ mạng.

### 2. Triển khai từ đầu

1. **Lấy mã nguồn** và vào thư mục gốc dự án:

   ```powershell
   git clone https://github.com/ngthanhtuann/ai-powered-digital-family-community-platform.git
   cd ai-powered-digital-family-community-platform
   ```

2. **Tạo tệp cấu hình môi trường:**

   ```powershell
   Copy-Item .env.example .env
   ```

   Mở `.env` và điền các biến sau:

   | Biến                                                | Bắt buộc | Ghi chú                                                                                            |
   | --------------------------------------------------- | -------- | -------------------------------------------------------------------------------------------------- |
   | `POSTGRES_PASSWORD`                                 | Có       | Thiếu thì Docker Compose báo lỗi ngay.                                                             |
   | `JWT_SECRET`                                        | Có       | Thiếu thì backend dừng khi khởi động. Cách tạo ở bên dưới.                                         |
   | `POSTGRES_DB`, `POSTGRES_USER`                      | Không    | Mặc định là `familyconnect`.                                                                       |
   | `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Nên điền | Tạo tài khoản SYSTEM_ADMIN đầu tiên. Để trống thì hệ thống không có quản trị viên.                 |
   | `GEMINI_API_KEY`                                    | Không    | Để trống thì backend vẫn chạy nhưng bỏ qua lập chỉ mục, nên Chatbot và tóm tắt AI không hoạt động. |

   Tạo `JWT_SECRET` bằng PowerShell (không cần openssl):

   ```powershell
   [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))
   ```

   > Tệp `.env` chứa mật khẩu và khóa bí mật. **Không commit** tệp này lên Git.

3. **Cảnh báo về Gemini API:**

   > **KHÔNG dùng dữ liệu thật với gói miễn phí Gemini.** Gói miễn phí có thể lưu và dùng dữ liệu để huấn luyện mô hình. Chỉ dùng dữ liệu giả (mock data) khi chạy thử với Gemini API key trong môi trường phát triển hoặc demo.

4. **Khởi chạy hệ thống:**

   ```powershell
   docker compose --env-file .env up -d --build
   ```

   Lần build đầu mất nhiều thời gian hơn các lần sau.

5. **Kiểm tra trạng thái:**

   ```powershell
   docker compose ps
   ```

   Cả ba container `familyconnect-db`, `familyconnect-backend`, `familyconnect-frontend` phải ở trạng thái `healthy` (có thể mất 1 đến 2 phút sau khi chạy).

6. **Mở ứng dụng:**
   - Giao diện web: http://localhost:3000
   - API backend: http://localhost:8080
   - Swagger: http://localhost:8080/swagger-ui.html

### 3. Nạp dữ liệu mẫu và kiểm tra luồng demo

1. **Chạy script seed:**

   ```powershell
   powershell -ExecutionPolicy Bypass -File docker\seed-demo.ps1
   ```

2. **Tài khoản demo** (mật khẩu chung `Demo@2026`):

   | Vai trò                          | Email                                                                            |
   | -------------------------------- | -------------------------------------------------------------------------------- |
   | MEMBER đã xác minh               | `ha@demo.familyconnect.vn` (Nguyễn Thị Hà, Chi 3)                                |
   | MEMBER chưa xác minh (chờ duyệt) | `tu@demo.familyconnect.vn`                                                       |
   | BRANCH_ADMIN (Trưởng chi)        | `hung@demo.familyconnect.vn` (Trưởng Chi 3)                                      |
   | BRANCH_ADMIN (Trưởng họ)         | `phuc@demo.familyconnect.vn` (người tạo dòng họ, quản lý cả họ)                  |
   | SYSTEM_ADMIN                     | Email và mật khẩu bạn đặt ở `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` |

3. **Checklist kiểm tra luồng chính:**
   - [ ] Đăng nhập và xem cây gia phả (5 đời, 53 người).
   - [ ] Đăng bài và RSVP sự kiện.
   - [ ] Tải ảnh hợp lệ, tối đa 5 MB.
   - [ ] Tải tài liệu Heritage dạng PDF, tối đa 10 MB.
   - [ ] Dashboard: tài khoản MEMBER thường bị từ chối truy cập; tài khoản quản trị truy cập được.
   - [ ] Xuất dữ liệu CSV.
   - [ ] Chatbot trả lời (cần `GEMINI_API_KEY`, chỉ dùng dữ liệu giả).
   - [ ] Giao diện hiển thị đúng ở 360px, 768px, 1440px theo `design-system.md`.

### 4. Xử lý sự cố

| Triệu chứng                                       | Cách xử lý                                                                    |
| ------------------------------------------------- | ----------------------------------------------------------------------------- |
| `docker compose up` báo thiếu `POSTGRES_PASSWORD` | Chưa tạo `.env` hoặc chưa điền biến này. Làm lại bước 2.                      |
| Backend dừng ngay khi khởi động                   | Thiếu `JWT_SECRET`. Xem log: `docker compose logs backend`.                   |
| Cổng 3000 hoặc 8080 đã bị chiếm                   | Tắt chương trình đang dùng cổng đó, hoặc đổi cổng trong `docker-compose.yml`. |
| Container không lên `healthy`                     | Chạy `docker compose logs <tên dịch vụ>` để xem lỗi, chờ thêm 1 đến 2 phút.   |
| Script seed bị chặn bởi ExecutionPolicy           | Dùng đúng lệnh có `-ExecutionPolicy Bypass` ở mục 3.                          |
| Chatbot / tóm tắt AI không phản hồi               | Kiểm tra `GEMINI_API_KEY` đã điền chưa, rồi `docker compose logs backend`.    |
| Muốn làm lại từ đầu                               | `docker compose down -v`                                                      |
