# ADR: Quyết định lưu trữ và tải tệp

- **Ngày:** 17/10/2026
- **Người quyết định:** TV1
- **Trạng thái:** Đã chọn

## 1. Bối cảnh

FamilyConnect cần lưu trữ và tải ảnh cho Community, đồng thời hỗ trợ tệp PDF cho Heritage. Hai phương án được xem xét là lưu tệp trên Docker named volume và sử dụng MinIO tương thích S3.

Giải pháp phải phù hợp với kiến trúc Docker hiện tại, tuân theo quy ước upload của dự án, không công khai trực tiếp tệp và có thể sao lưu cùng dữ liệu hệ thống.

## 2. Các lựa chọn

| Lựa chọn | Ưu | Nhược |
|---|---|---|
| Docker named volume | Đơn giản, dễ triển khai cùng Docker Compose hiện tại; không cần thêm dịch vụ; khớp kiến trúc hiện tại của dự án | Phụ thuộc vào một máy chủ Docker; phải tự sao lưu volume |
| MinIO (S3 compatible) | Tách lưu trữ khỏi backend; phù hợp hơn khi cần mở rộng hoặc nhiều máy chủ | Phải cấu hình và vận hành thêm dịch vụ MinIO; tăng độ phức tạp so với nhu cầu hiện tại |

## 3. Quyết định và lý do

Chọn **Docker named volume** làm phương án lưu trữ tệp trong phạm vi hiện tại của FamilyConnect.

Volume sử dụng tên:

`uploads`

Backend đọc đường dẫn lưu trữ từ biến môi trường:

`MEDIA_STORAGE_PATH`

Trong môi trường Docker hiện tại, đường dẫn mount của backend là:

`/var/lib/familyconnect/media`

Docker Compose sử dụng volume `uploads` mount vào đường dẫn trên và đặt:

`MEDIA_STORAGE_PATH=/var/lib/familyconnect/media`

Giải pháp này được chọn vì phù hợp với kiến trúc Docker hiện tại, đơn giản hơn MinIO và không yêu cầu vận hành thêm một dịch vụ object storage.

MinIO chưa được sử dụng trong phạm vi hiện tại và có thể được xem xét lại khi hệ thống cần lưu trữ phân tán hoặc triển khai trên nhiều máy chủ.

## 4. Hệ quả

- Volume `uploads` phải được duy trì khi container backend được tạo lại.
- Volume `uploads` phải được sao lưu cùng thời điểm với cơ sở dữ liệu PostgreSQL.
- Giải pháp hiện tại phụ thuộc vào một Docker host.
- Hệ thống triển khai phải quản lý việc sao lưu và khôi phục volume `uploads`.

## 5. Chính sách lưu trữ, giới hạn và xử lý tệp

### 5.1. Định dạng và giới hạn

Theo `docs/04-api/conventions.md` mục 7.3:

| Trường hợp | Định dạng | Giới hạn mỗi tệp |
|---|---|---:|
| Tệp ảnh thông thường | JPG, PNG, WEBP | 5 MB |
| Tài liệu với `owner_type = HERITAGE` | PDF | 10 MB |

- Request upload sử dụng `multipart/form-data`, trường tệp tên `file`.
- Tổng dung lượng tệp trong một request tối đa 10 MB.
- Spring cấu hình `max-request-size` là **11 MB** để chừa phần đầu multipart.
- Nginx cấu hình `client_max_body_size 11m` để request hợp lệ không bị Nginx chặn trước khi đến backend; phối hợp TV4 khi cấu hình Docker/Nginx.
- Tệp vượt giới hạn trả HTTP `413` với mã `FILE_001`.
- Định dạng không được hỗ trợ trả HTTP `415` với mã `FILE_002`.
- Tên tệp lưu trữ (`storage_key`) được sinh bằng UUID.
- Loại tệp phải được xác định từ **nội dung thực tế của tệp (magic bytes)**, không chỉ dựa vào phần mở rộng hoặc `Content-Type` do client gửi.
- MIME đã xác định được lưu vào cột `media.file_type`.

### 5.2. Truy cập tệp

Tệp không được công khai bằng đường dẫn lưu trữ trực tiếp.

Endpoint phục vụ nội dung tệp:

`GET /api/v1/media/{id}/content`

Endpoint phải kiểm tra quyền trước khi trả nội dung:

- Không đủ quyền: `403 PERM_001`.
- Không tồn tại hoặc bản ghi đã bị xóa mềm: `404 NOT_FOUND_001`.

Không trả `storageKey` trong API công khai để client tự truy cập tệp.

### 5.3. Xử lý tệp mồ côi

Tệp mồ côi là tệp vật lý còn tồn tại trong volume nhưng không còn bản ghi `media` tương ứng.

Không xóa vật lý ngay trong luồng request thông thường. Tác vụ dọn dẹp phải đối chiếu tệp trong volume với dữ liệu trước khi xóa.

- **Thời gian chờ trước khi xóa:** 24 giờ, được đặt thành tham số cấu hình.
- **Tần suất tác vụ dọn:** mỗi ngày 1 lần, ngoài giờ cao điểm.
- Media đã xóa mềm theo BR-COM-10 vẫn **giữ tệp vật lý**, tương tự cách xử lý của Heritage.
- Chỉ xóa tệp vật lý khi **không còn bản ghi `media` nào tương ứng**.

## 6. Sao lưu

Volume `uploads` phải được sao lưu cùng thời điểm với cơ sở dữ liệu PostgreSQL để tránh tình trạng bản ghi database và tệp vật lý không đồng bộ.

Yêu cầu sao lưu và khôi phục volume `uploads` cùng cơ sở dữ liệu phải được cập nhật trong `docs/07-deploy/deploy.md`.

## 7. Kết quả demo và kiểm thử

**Trạng thái: Chưa xác minh đầy đủ bằng demo.**

| Kiểm tra | Kết quả |
|---|---|
| Upload ảnh PNG hợp lệ | PASS |
| Tải nội dung qua `GET /api/v1/media/{id}/content` | PASS |
| Upload PDF hợp lệ cho Heritage | Chưa xác minh |
| Từ chối tệp vượt giới hạn | Chưa xác minh |
| Từ chối định dạng không được hỗ trợ | Chưa xác minh |
| Tên tệp lưu trữ sử dụng UUID | Chưa xác minh |
| Kiểm tra quyền khi tải tệp | Chưa xác minh |
| Tệp không tồn tại / đã xóa mềm | Chưa xác minh |

Kết quả đã xác minh:

- Upload PNG hợp lệ trả về HTTP `201`, có `id` dạng UUID, `contentType` là `image/png` và `contentUrl` sử dụng `/api/v1/media/{id}/content`.
- API upload không trả `storageKey` ra response công khai.
- `GET /api/v1/media/{id}/content` với media vừa upload trả HTTP `200` và `Content-Type: image/png`.

Các trường hợp còn lại chỉ được cập nhật thành `PASS` sau khi chạy demo thực tế và có bằng chứng tương ứng.

## 8. Tài liệu tham chiếu

- `docs/04-api/conventions.md`, mục 7.3.
- `docs/01-srs/use-cases/community-events.md`, BR-COM-08 và BR-COM-10.
- `docs/02-design/architecture.md`.
- `docs/07-deploy/deploy.md`.
- `docs/05-ui/heritage-directory-dashboard.md`, phần quy định giữ tệp khi Heritage sử dụng xóa mềm.
