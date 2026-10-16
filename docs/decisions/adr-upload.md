# ADR: Quyết định lưu trữ và tải tệp

- **Task Jira:** SCRUM-31
- **Người thực hiện:** TV3 (PiLo257)
- **Trạng thái:** Quyết định kiến trúc cho chức năng tải tệp
- **Ngày chốt:** 09/10/2026

## 1. Bối cảnh

FamilyConnect cần lưu trữ và tải ảnh trong Community, đồng thời hỗ trợ tài liệu PDF cho chức năng Heritage. Hai phương án được xem xét là lưu tệp trên Docker named volume và sử dụng MinIO tương thích S3.

Việc lựa chọn cần phù hợp với kiến trúc hiện tại, dễ triển khai trong môi trường Docker và bảo đảm tệp chỉ được truy cập khi người dùng có quyền.

## 2. Các phương án

### Phương án A: Docker named volume

- Lưu tệp trên volume do Docker quản lý.
- Dễ triển khai cùng hệ thống hiện tại.
- Không cần vận hành thêm dịch vụ object storage.
- Phù hợp với phạm vi triển khai hiện tại của dự án.

### Phương án B: MinIO (tương thích S3)

- Lưu tệp trong object storage.
- Phù hợp khi cần mở rộng hệ thống lưu trữ độc lập với backend.
- Cần cấu hình và vận hành thêm dịch vụ MinIO.

## 3. Quyết định

**Chọn Docker named volume làm phương án lưu trữ tệp cho phạm vi triển khai hiện tại.**

MinIO chưa được sử dụng trong phạm vi này. Có thể xem xét lại khi dự án cần mở rộng lưu trữ hoặc tách riêng dịch vụ file storage.

Ứng dụng phải sử dụng UUID làm tên tệp lưu trữ, không sử dụng trực tiếp tên do người dùng cung cấp. Tệp phải được truy cập thông qua API có kiểm tra quyền, không công khai đường dẫn lưu trữ.

## 4. Định dạng và giới hạn tệp

Áp dụng `docs/04-api/conventions.md`, mục 7.3:

| Trường hợp | Định dạng | Giới hạn mỗi tệp |
|---|---|---:|
| Tệp ảnh thông thường | JPG, PNG, WEBP | 5 MB |
| Tài liệu Heritage với `owner_type = HERITAGE` | PDF | 10 MB |

- Request tải tệp sử dụng `multipart/form-data`, tên trường là `file`.
- Cấu hình `max-request-size` của máy chủ là 11 MB để chừa dung lượng cho phần đầu multipart.
- Tệp vượt giới hạn trả HTTP `413` với mã `FILE_001`.
- Định dạng không được hỗ trợ trả HTTP `415` với mã `FILE_002`.
- Response lỗi tuân theo định dạng chung trong `conventions.md`, mục 2.2.

## 5. Chính sách xử lý tệp mồ côi

Tệp mồ côi là tệp còn tồn tại trong vùng lưu trữ nhưng không còn bản ghi dữ liệu tham chiếu đến tệp đó.

Quyết định: **không xóa tệp mồ côi ngay lập tức trong luồng request thông thường**. Việc dọn dẹp phải được thực hiện bằng tác vụ kiểm tra riêng, đối chiếu các tệp trong volume với bản ghi dữ liệu trước khi xóa.

Tác vụ dọn dẹp phải có khoảng thời gian chờ trước khi xóa, nhằm tránh xóa nhầm tệp vừa tải lên nhưng chưa hoàn tất việc tạo bản ghi. Chỉ xóa những tệp đã được xác định không còn được tham chiếu và đã vượt thời gian chờ theo cấu hình. Thời gian chờ cụ thể cần được thống nhất trước khi triển khai tác vụ dọn dẹp.

## 6. Bảo mật

- Tên tệp lưu trữ được tạo bằng UUID.
- Không sử dụng tên tệp do người dùng cung cấp làm đường dẫn lưu trữ.
- Kiểm tra loại tệp theo nội dung thực tế kết hợp với phần mở rộng, không chỉ dựa vào `Content-Type` do client gửi.
- Ngăn chặn path traversal khi xử lý đường dẫn.
- Kiểm tra quyền truy cập trước khi trả nội dung tệp.
- Không trả `storageKey` trong response API công khai.
- Endpoint tải nội dung sử dụng ID tài nguyên và phải kiểm tra quyền, theo quy ước API của dự án.

## 7. Kết quả demo và kiểm thử

**Trạng thái: Chưa xác minh đầy đủ bằng bằng chứng trong tài liệu này.**

Trước khi hoàn tất, cần ghi lại kết quả thực tế của các kiểm tra sau:

| Kiểm tra | Kết quả |
|---|---|
| Upload ảnh hợp lệ | Chưa xác minh |
| Upload PDF hợp lệ cho Heritage | Chưa xác minh |
| Từ chối tệp vượt giới hạn | Chưa xác minh |
| Từ chối định dạng không được hỗ trợ | Chưa xác minh |
| Tên tệp lưu trữ sử dụng UUID | Chưa xác minh |
| Kiểm tra quyền khi tải tệp | Chưa xác minh |
| Kiểm tra các trường hợp lỗi | Chưa xác minh |

Chỉ cập nhật trạng thái thành PASS khi có kết quả chạy thực tế hoặc bằng chứng kiểm thử tương ứng. Nếu một kiểm tra chưa được thực hiện, giữ nguyên trạng thái chưa xác minh.

## 8. Phạm vi của quyết định

Tài liệu này ghi lại quyết định kiến trúc cho SCRUM-31. Việc triển khai chức năng upload/download, API, migration cơ sở dữ liệu và kiểm thử thuộc phạm vi công việc triển khai tương ứng, không được gộp vào PR chỉ cập nhật ADR.

## 9. Tài liệu tham chiếu

- `docs/04-api/conventions.md`, mục 2.2: Response lỗi.
- `docs/04-api/conventions.md`, mục 3: Mã lỗi.
- `docs/04-api/conventions.md`, mục 7.3: Quy ước tải tệp.
