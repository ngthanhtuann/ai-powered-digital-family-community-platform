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
