# Sao lưu và khôi phục (SCRUM-86, UC-AUTH-11)

Phạm vi: toàn bộ PostgreSQL, bao gồm các bảng vector, và Docker volume `uploads`.
Một bản sao lưu là một file `familyconnect-YYYYMMDD-HHMMSS.tar.gz` chứa
`database.dump` (định dạng custom của `pg_dump`) và thư mục `media/`.
Thư mục `backups` là Docker volume riêng, không đưa lên Git.

## Chuẩn bị

Trong thư mục dự án, tạo `.env` theo hướng dẫn triển khai. Chạy:

```powershell
docker compose up -d --build db backend
```

Container backend cài `postgresql-client-16`, có `pg_dump`, `pg_restore` và
script ở `/app/scripts/`. Không commit bản sao lưu, mật khẩu hoặc file `.env`.
Nên đưa bản sao lưu ra thiết bị lưu trữ độc lập với Docker host vì mất ổ đĩa
hoặc xóa volume `backups` sẽ làm mất cả backup.

## Cách 1: Trên giao diện

1. Đăng nhập tài khoản `SYSTEM_ADMIN`, mở `/admin` → **Sao lưu & Khôi phục**.
2. Chọn **Tạo bản sao lưu**. Danh sách hiển thị tên file, kích thước, thời điểm tạo.
3. Khi cần khôi phục, chọn bản sao lưu, đọc cảnh báo ghi đè và nhập chính xác
   `RESTORE` rồi xác nhận. Các refresh token bị thu hồi sau khi khôi phục;
   người dùng phải đăng nhập lại khi cần làm mới phiên.

API khớp `docs/04-api/openapi/auth.yaml`: `POST /api/v1/admin/backups` (201),
`GET /api/v1/admin/backups?page=0&size=20`, `POST /api/v1/admin/restores`
với `{ "backupId": "familyconnect-YYYYMMDD-HHMMSS", "confirmText": "RESTORE" }`.
Chỉ SYSTEM_ADMIN được truy cập. Tác vụ đồng thời trả `409 ADM_003`.
Audit `BACKUP_CREATED`/`RESTORE_COMPLETED` đi qua `AuditLogger`; trong
`develop` hiện tại, adapter `LoggingAuditLogger` ghi vào log ứng dụng, còn
chuyển sang bảng `audit_log` thuộc SCRUM-57.

## Giới hạn khi khôi phục

- Chỉ khôi phục được bản sao lưu **cùng phiên bản migration** (Flyway) với cơ sở dữ liệu hiện tại.
  Khác phiên bản thì script dừng trước khi đụng vào dữ liệu, API trả `500 SERVER_001` và log ghi
  "Bản sao lưu ở schema Vx, DB hiện tại ở Vy". Lý do: `pg_restore --clean` chỉ dựng lại bảng có trong
  bản sao lưu; bảng của migration mới hơn vẫn còn và Flyway sẽ lỗi ở lần khởi động sau.
- Ảnh trong bản sao lưu được chép vào thư mục tạm trên volume `uploads` trước, rồi mới khôi phục DB và hoán
  đổi bằng `mv`. Lỗi trước bước hoán đổi thì DB và ảnh hiện tại giữ nguyên.
- Sau khi khôi phục nên khởi động lại backend (`docker compose restart backend`) để xóa cache trong bộ nhớ
  (dashboard, hạn mức AI).

## Cách 2: Chạy script bằng PowerShell

```powershell
# Tạo bản sao lưu; dòng kết quả cho biết tên file đầy đủ.
docker compose exec backend /app/scripts/backup.sh

# Xem các archive hiện có.
docker compose exec backend ls -lh /var/lib/familyconnect/backups
```

**Cảnh báo:** lệnh dưới đây ghi đè database hiện tại và toàn bộ volume uploads.
Chỉ chạy khi đã có bản sao lưu được kiểm tra và chủ động chấp nhận mất thay đổi
sau thời điểm backup. Nên đóng các phiên làm việc của người dùng trước khi chạy.
Thay mã ví dụ bằng ID nhận được từ lệnh sao lưu:

```powershell
docker compose exec backend /app/scripts/restore.sh familyconnect-YYYYMMDD-HHMMSS RESTORE
```

Sau khi khôi phục bằng script trực tiếp, cần thu hồi refresh token vì bước này
được API thực hiện *sau* khi chạy script. Đối với hệ thống đang phục vụ người
dùng, ưu tiên khôi phục qua giao diện/API để tự động thực hiện bước thu hồi
và audit log. Chạy script tay nên dành cho bảo trì/khắc phục sự cố.

## Kiểm tra an toàn trên DB và thư mục ảnh *riêng*

Không chạy phép thử xóa dữ liệu trên cơ sở dữ liệu thật. Sau khi tạo backup,
tạo DB kiểm thử trên cùng PostgreSQL server (tên chỉ để kiểm thử, không phải DB
sản xuất). Các lệnh sau giả sử cấu hình mặc định `POSTGRES_USER=familyconnect`;
nếu `.env` đã đổi, thay đúng tên người dùng tương ứng.

```powershell
docker compose exec db createdb -U familyconnect familyconnect_restore_test
docker compose exec backend mkdir -p /tmp/familyconnect-restore-test-media

# Thay ID bằng bản sao lưu vừa tạo. DB và ảnh đích đều là dữ liệu kiểm thử.
docker compose exec -e PGDATABASE=familyconnect_restore_test -e MEDIA_STORAGE_PATH=/tmp/familyconnect-restore-test-media backend /app/scripts/restore.sh familyconnect-YYYYMMDD-HHMMSS RESTORE

# Kiểm tra bảng PostgreSQL đã khôi phục, bao gồm dữ liệu vector nếu có.
docker compose exec db psql -U familyconnect -d familyconnect_restore_test -c "\dt"

# Đối chiếu ảnh/tệp, khi volume uploads không thay đổi từ lúc backup.
docker compose exec backend diff -qr /var/lib/familyconnect/media /tmp/familyconnect-restore-test-media
```

Để kiểm tra chu trình xóa → khôi phục mà không đụng dữ liệu thật: chọn một bản
ghi **trong DB `familyconnect_restore_test`**, lưu lại ID và dữ liệu; xóa bản
ghi kiểm thử đó; chạy lại lệnh restore kiểm thử; so sánh bản ghi và tệp ảnh.
Không xóa dữ liệu trên DB `familyconnect` hoặc volume `uploads` để thử.
Nếu không có dữ liệu mẫu phù hợp, chưa thể xác nhận tiêu chí kiểm thử vòng đời.

Khi đã kiểm tra xong, giữ DB kiểm thử nếu còn cần đối chiếu; việc xóa DB này
chỉ thực hiện sau khi xác nhận đúng tên và không chứa dữ liệu cần giữ.
