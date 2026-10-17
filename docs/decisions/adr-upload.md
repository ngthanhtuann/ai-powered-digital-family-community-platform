# ADR: Lựa chọn phương án lưu trữ file upload

- **Task:** SCRUM-31
- **Trạng thái:** Đề xuất cho spike, chờ xác nhận sau khi kiểm thử
- **Phạm vi:** Lưu trữ file upload và cấu hình triển khai liên quan

## 1. Bối cảnh

FamilyConnect cần lưu trữ các file được tải lên. Phương án lưu trữ cần phù hợp với cấu trúc Docker Compose hiện tại, hỗ trợ lưu file lâu dài qua các lần tạo lại container và có thể tích hợp với API upload.

Hai phương án được xem xét:

- Docker named volume lưu file trên máy chủ triển khai.
- MinIO cung cấp lưu trữ đối tượng tương thích S3.

## 2. Các phương án

### Phương án A: Docker named volume

**Ưu điểm**
- Cấu hình đơn giản trong Docker Compose.
- Không cần triển khai thêm dịch vụ lưu trữ đối tượng.
- Dữ liệu tồn tại qua việc tạo lại container nếu volume không bị xóa.
- Phù hợp để thử nghiệm trong môi trường triển khai đơn máy.

**Hạn chế**
- Gắn với hạ tầng lưu trữ của máy chủ triển khai.
- Cần tự quản lý đường dẫn file, quyền truy cập, sao lưu và dọn file không còn được tham chiếu.
- Không cung cấp sẵn giao diện lưu trữ đối tượng S3.

### Phương án B: MinIO

**Ưu điểm**
- Cung cấp API lưu trữ đối tượng tương thích S3.
- Tách lưu trữ file khỏi vòng đời container backend.
- Có thể thuận tiện hơn khi cần mở rộng hoặc chuyển đổi hạ tầng lưu trữ.

**Hạn chế**
- Cần triển khai và cấu hình thêm dịch vụ.
- Phát sinh cấu hình kết nối, thông tin xác thực, quản lý bucket và sao lưu.
- Cần kiểm thử tích hợp giữa backend và MinIO.

## 3. Quyết định đề xuất

Đề xuất dùng **Docker named volume** cho bản spike hiện tại vì cách này ít thay đổi cấu trúc triển khai và phù hợp để kiểm chứng luồng upload trên Docker Compose.

Đây mới là đề xuất cho spike, chưa phải quyết định cuối cùng. Cần kiểm thử upload thực tế trước khi chốt. MinIO được giữ làm phương án thay thế nếu yêu cầu triển khai hoặc mở rộng cần lưu trữ đối tượng.

## 4. Yêu cầu kiểm thử

- File được lưu bền vững khi container backend được tạo lại.
- Tên lưu trữ nội bộ được tạo bằng UUID, không dùng tên file gốc làm tên lưu trữ.
- Kiểm tra loại file bằng magic bytes, không chỉ dựa vào phần mở rộng.
- Ảnh JPG, PNG và WEBP có giới hạn 5 MB mỗi file.
- PDF chỉ được chấp nhận cho `ownerType=HERITAGE`, giới hạn 10 MB mỗi file, sau khi xác nhận quy tắc này.
- Tổng request upload không vượt quá 10 MB.
- Kiểm tra giới hạn ở tầng ứng dụng và Nginx nếu Nginx được sử dụng trong cấu hình triển khai.
- Không công khai đường dẫn lưu trữ nội bộ hoặc `storage_key` trong API response.
- Việc truy cập file riêng tư phải được kiểm tra quyền trước khi trả nội dung file.
- Ghi nhận cách xử lý file không còn được bản ghi dữ liệu tham chiếu; không tự xóa file nếu chính sách chưa được xác nhận.

## 5. Các điểm cần xác nhận

- Quy tắc ngoại lệ PDF cho `HERITAGE`: tài liệu hiện có thông tin chưa thống nhất về trạng thái xác nhận.
- Chính sách dọn file không còn được tham chiếu.
- Cấu hình giới hạn request ở Nginx và sự phối hợp với người phụ trách cấu hình đó.

## 6. Kết quả spike

Chỉ chuyển trạng thái sang hoàn tất sau khi demo upload chạy được, các kiểm thử liên quan có kết quả và phương án lưu trữ được xác nhận.
