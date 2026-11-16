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

### 1. Yêu cầu hệ thống và chuẩn bị
- Máy tính chỉ cần cài đặt **Docker** và **Docker Compose**. Không cần cài đặt Java, Node.js hay PostgreSQL thủ công trên máy.
- Thời gian thực hiện dự kiến: dưới 15 phút.

### 2. Các bước triển khai từ đầu
1. **Lấy mã nguồn từ kho lưu trữ** và chuyển đến thư mục gốc của dự án.
2. **Tạo tệp cấu hình môi trường**: Sao chép tệp `.env.example` thành `.env` và điền đầy đủ các thông tin bắt buộc:
   ```powershell
   Copy-Item .env.example .env
   ```
   - Cấu hình các biến `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`.
3. **Cảnh báo bắt buộc về Gemini API**:
   > **LƯU Ý QUAN TRỌNG:** **KHÔNG dùng dữ liệu thật với gói miễn phí Gemini**. Gói miễn phí có thể lưu trữ và sử dụng dữ liệu cho việc huấn luyện mô hình. Chỉ sử dụng dữ liệu giả (mock data) khi chạy thử nghiệm với Gemini API key trong môi trường phát triển/demo.
4. **Khởi chạy hệ thống bằng Docker Compose**:
   ```powershell
   docker compose up -d --build
   ```
   - Kiểm tra trạng thái các container (cả `familyconnect-db`, `familyconnect-backend`, và `familyconnect-frontend` phải ở trạng thái `healthy`):
     ```powershell
     docker compose ps
     ```

### 3. Nạp dữ liệu mẫu (Seed Data) và Kiểm tra luồng demo
1. **Chạy script nạp dữ liệu mẫu**:
   ```powershell
   powershell -ExecutionPolicy Bypass -File docker\seed-demo.ps1
   ```
2. **Đăng nhập và kiểm tra các vai trò demo**:
   - **MEMBER đã xác minh**: `hung@demo.familyconnect.vn` / `Demo@2026` (hoặc `phuc@demo.familyconnect.vn`).
   - **MEMBER chưa xác minh / chờ duyệt**: `tu@demo.familyconnect.vn` / `Demo@2026`.
   - **BRANCH_ADMIN**: Trưởng chi / Trưởng họ có phân quyền quản lý chi.
   - **SYSTEM_ADMIN**: Quản trị hệ thống cấp cao.
3. **Kiểm tra các luồng nghiệp vụ chính**:
   - **Đăng nhập & Xem cây gia phả**: Kiểm tra hiển thị sơ đồ cây 5 đời.
   - **Đăng bài & RSVP sự kiện**: Kiểm tra tương tác cộng đồng và phản hồi tham dự.
   - **Upload tệp (Kiểm tra giới hạn dung lượng)**:
     - Tải lên ảnh định dạng hợp lệ với dung lượng **tối đa 5 MB**.
     - Tải lên tài liệu Heritage định dạng PDF với dung lượng **tối đa 10 MB**.
   - **Dashboard & Báo cáo**: Kiểm tra phân quyền truy cập Dashboard (tài khoản MEMBER thông thường phải bị từ chối truy cập), xuất dữ liệu CSV, và tính năng Chatbot (với dữ liệu giả).
   - **Responsive Design**: Kiểm tra giao diện trên 3 cỡ màn hình chuẩn **360px, 768px, 1440px** theo `design-system.md`.

