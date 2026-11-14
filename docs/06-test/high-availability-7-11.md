# SCRUM-96 [7-11] – High Availability cơ bản

**Người thực hiện:** TV3. **Reviewer:** TV1. **Trạng thái:** Chờ chạy kiểm thử thực tế.

## Phạm vi triển khai

- Compose HA riêng (`docker-compose.ha.yml`) để không thay đổi lệnh chạy một backend hiện hành.
- PostgreSQL pgvector 1 nút; hai backend Spring Boot; Nginx round-robin trên port 8080; Next.js trên 3000.
- Mọi dịch vụ trong HA có `restart: unless-stopped` và healthcheck. Backend đợi DB healthy; Nginx đợi hai backend healthy lúc khởi động; frontend đợi Nginx healthy.
- Hai backend cùng dùng volume PostgreSQL `pgdata`, media `uploads`, và backup `backups`.
- Access token JWT stateless; refresh token trong DB. Backend-secondary đợi primary healthy trước khi khởi động; Flyway dùng khóa migration của PostgreSQL để an toàn khi triển khai lại.
- Rate limit của login, refresh, AI chat/search/summary được kiểm soát chung qua các bảng migration `V22__ha_shared_rate_limits.sql` (mã `RATE_001` giữ nguyên). Cache thống kê dashboard và cấu hình hệ thống trong JVM được tắt trong chế độ HA; cache AI summary đã ở PostgreSQL.
- Chỉ `backend-primary` chạy `EventReminderScheduler`; ràng buộc UNIQUE thông báo trong PostgreSQL giữ chống trùng. Nếu primary ngừng lâu, job chạy bù khi primary phục hồi và sự kiện chưa bắt đầu.
- Gemini lỗi/timeout: API phải trả `AI_001` (503) và backend vẫn healthy, cần đo kiểm thực tế. Nginx không tự retry HTTP 503 để tránh gọi AI lặp lại.
- Backup gồm `pg_dump` (cả `vector`) và `uploads`; script backup/restore đã dùng `flock` trên volume `backups` chung. Sau Flyway V22 cần tạo bản backup tương ứng V22: restore V21 vào DB V22 bị chặn theo chính sách phiên bản migration. KHÔNG restore dữ liệu thật trong lúc 2 backend đang nhận request.

## Cách khởi động chế độ HA

Trong thư mục root repo, tạo `.env` từ `.env.example` và thiết lập bí mật thật trên máy, không commit.

```powershell
docker compose -f docker-compose.ha.yml config -q
docker compose -f docker-compose.ha.yml up -d --build
docker compose -f docker-compose.ha.yml ps
```

Mở `http://localhost:3000` (FE) và `http://localhost:8080/actuator/health` (API).

LƯU Ý: trước khi chuyển từ Compose thường sang Compose HA, dừng Compose thường KHÔNG kèm `-v`. Cả hai file dùng cùng Compose project/volume; không chạy đồng thời. Không chạy `docker compose down -v` vì sẽ xóa DB/ảnh/backup. Giữ nguyên `.env` để JWT đã cấp vẫn hợp lệ.

## Checklist và kết quả cần ghi

**Chưa chạy:** các kết quả bên dưới là ô ghi nhận, không phải số liệu đo đã đạt. Test tích hợp `SharedRateLimitStoreIntegrationTest` kiểm tra hai cửa sổ giới hạn qua PostgreSQL khi chạy `mvn verify`.

| Ca kiểm thử | Cách chạy | Tiêu chí | Kết quả thực tế | Thời gian phục hồi |
|---|---|---|---|---|
| Khởi động | `docker compose -f docker-compose.ha.yml ps` | 5 dịch vụ healthy (DB, 2 BE, nginx, FE) | Chưa đo | Chưa đo |
| Round-robin/failover | Gọi lặp API `/actuator/health` qua Nginx; `docker compose -f docker-compose.ha.yml stop backend-secondary`; gọi tiếp 30 lần | Không có request lỗi khi 1 backend dừng | Chưa đo | Chưa đo |
| Tự phục hồi khi process chết | Trong backend-primary: `kill -9 1` bằng `docker compose exec`; quan sát `docker compose ps`, health và `docker inspect` | Container tự restart, trở về healthy | Chưa đo | Chưa đo |
| Volume | Tạo marker test trong DB và trong `uploads`; `docker compose -f docker-compose.ha.yml down` (KHÔNG `-v`), `up -d`; đọc lại marker | Marker không mất | Chưa đo | Chưa đo |
| Backup/Restore | Tạo backup qua UI/API, restore vào DB kiểm thử theo `docs/07-deploy/backup-restore.md` | Có bảng `vector` và media tương ứng; không chạm DB thật | Chưa đo | Chưa đo |
| Rate limit | Gọi login/refresh hoặc AI thử vượt hạn mức, xen kẽ hai backend | Tổng lượt dùng chung, HTTP 429 `RATE_001` | Chưa đo | Chưa đo |
| Reminder | Tạo sự kiện thử nghiệm, kiểm tra thông báo đúng số lần | Không trùng 1h/24h | Chưa đo | Chưa đo |
| AI lỗi | Thử với Gemini timeout/không cấu hình khóa trên dữ liệu giả | 503 `AI_001`, các API khác vẫn chạy | Chưa đo | Chưa đo |

### Lệnh kiểm thử thủ công (PowerShell)

```powershell
# Hai backend cùng hoạt động trước thử nghiệm
docker compose -f docker-compose.ha.yml ps
1..30 | ForEach-Object { try { (Invoke-WebRequest -UseBasicParsing 'http://localhost:8080/actuator/health' -TimeoutSec 5).StatusCode } catch { 'FAILED' } }

# Tạm dừng một backend (đây là dừng chủ động, không kiểm tra auto-restart)
docker compose -f docker-compose.ha.yml stop backend-secondary
1..30 | ForEach-Object { try { (Invoke-WebRequest -UseBasicParsing 'http://localhost:8080/actuator/health' -TimeoutSec 5).StatusCode } catch { 'FAILED' } }
docker compose -f docker-compose.ha.yml start backend-secondary

# Mô phỏng JVM PID1 crash (restart policy kích hoạt do tiến trình chết)
# Chỉ chạy trên stack TEST, KHÔNG chạy với dữ liệu sản xuất.
$before = [DateTimeOffset]::UtcNow
docker compose -f docker-compose.ha.yml exec -T backend-primary sh -c 'kill -9 1'
docker compose -f docker-compose.ha.yml ps
# Đợi đến khi primary healthy; ghi thời gian phục hồi = UtcNow - $before.
```

Kiểm tra volume bằng marker dùng hướng dẫn trong `docs/07-deploy/deploy.md`, thay mọi lệnh `docker compose` thành `docker compose -f docker-compose.ha.yml` và service `backend` thành `backend-primary`. Sau kiểm thử phải xóa marker. Kiểm thử restore an toàn trên database thử nghiệm theo `docs/07-deploy/backup-restore.md`.

## Giới hạn và hướng nâng cấp

1. **Không HA khi mất DB hoặc Docker host:** DB một nút, không PostgreSQL replication, tất cả container trên một máy. Mất máy hoặc volume vẫn gây downtime/mất dữ liệu; cần backup ra ngoài máy.
2. **Nginx cũng là một điểm lỗi đơn:** container có restart nhưng không có 2 load balancer.
3. **Scheduled job chỉ có một worker:** không tạo trùng, nhưng nếu primary down lâu có thể lỡ mốc nhắc (các sự kiện đã bắt đầu sẽ không được gửi bù); hướng nâng cấp dùng DB distributed lock/ShedLock.
4. **Upstream Nginx tĩnh:** `docker compose ... up --force-recreate` một backend có thể thay IP; nếu gặp 502 sau recreate cần reload/restart Nginx để resolve lại. Docker restart process thông thường không đổi IP.
5. **Restart chỉ xử lý tiến trình dừng:** Docker không tự khởi động lại container chỉ vì healthcheck báo `unhealthy`, và `docker compose stop` là dừng chủ động, không phải bài test auto-restart.
6. **Không bảo đảm mọi POST đang xử lý được chuyển tiếp:** Nginx chỉ retry khi an toàn/chưa gửi request tới upstream; request đang thực thi có thể lỗi khi backend chết và cần người dùng thử lại.
7. **Quy mô học phần:** chưa có high-availability thật ở nhiều máy. Hướng nâng cấp: PostgreSQL primary/replica + failover, shared object storage, Redis rate-limit, worker lease, Kubernetes/Ingress, backup offsite.

**Chưa thể đánh dấu Done trước khi TV3 đo các kết quả và TV1 test chéo.**
