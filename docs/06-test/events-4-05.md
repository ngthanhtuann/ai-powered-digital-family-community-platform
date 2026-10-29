# Kiểm thử [4-05] — Events cốt lõi

Tài liệu đối chiếu: `docs/01-srs/use-cases/community-events.md`,
`docs/04-api/openapi/community-events.yaml`, `docs/03-database/erd.dbml`.

## Kiểm thử tự động (BE)

`backend/src/test/java/com/familyconnect/modules/events/service/EventServiceTest.java`

Chạy tại thư mục `backend`:

```bash
mvn -Dtest=EventServiceTest test
```

Các trường hợp: thành viên đã xác minh tạo sự kiện; từ chối thành viên chưa xác minh;
kiểm tra thời điểm kết thúc; từ chối sửa/xóa bởi thành viên không có quyền;
RSVP trước giờ bắt đầu; xác minh trạng thái và số đếm RSVP;
trả `EVT_001` khi RSVP từ thời điểm bắt đầu; từ chối thêm người tham dự trùng.

## Kiểm thử API và FE thủ công (theo `test-case-template.md`)

| ID | Module | Mô tả | Tiền điều kiện | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Trạng thái | Mã FR |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| TC-EVT-001 | Events | Tạo sự kiện | MEMBER đã xác minh | 1. Vào `/events`; 2. Điền tên, giờ, địa điểm, mô tả; 3. Bấm **Tạo sự kiện** | Sự kiện xuất hiện trong danh sách; API tạo trả 201 | | Chưa chạy | FR-EVT-01 |
| TC-EVT-002 | Events | RSVP và thay trạng thái | Hai MEMBER đã xác minh cùng gia đình | 1. Người thứ nhất tạo sự kiện; 2. Người thứ hai chọn **Tham dự** rồi **Có thể** | Chỉ một RSVP hoạt động/người; `going`, `maybe` cập nhật đúng | | Chưa chạy | FR-EVT-02 |
| TC-EVT-003 | Events | Xem người tham dự | Có sự kiện đã RSVP | 1. Mở sự kiện; 2. Bấm **Xem danh sách người tham dự**; 3. Lọc trạng thái | Hiện họ tên và RSVP, không hiện thông tin liên hệ | | Chưa chạy | FR-EVT-03 |
| TC-EVT-004 | Events | Sửa và hủy sự kiện | Người tạo đã xác minh | 1. Sửa sự kiện; 2. Hủy sự kiện | Dữ liệu thay đổi; DELETE trả 200; sự kiện không còn trong danh sách | | Chưa chạy | FR-EVT-01 |
| TC-EVT-005 | Events | Từ chối sửa/xóa trái quyền | MEMBER không phải người tạo | 1. Gọi PATCH/DELETE `/api/v1/events/{id}` | API trả 403 `PERM_001` | | Chưa chạy | FR-EVT-01 |
| TC-EVT-006 | Events | Chặn thành viên chưa xác minh | MEMBER chưa xác minh | 1. Gọi GET `/api/v1/events` | API trả 403 `PERM_002` | | Chưa chạy | FR-EVT-01 |
| TC-EVT-007 | Events | Chặn RSVP sau giờ bắt đầu | Sự kiện đã bắt đầu | 1. Gửi PUT `/api/v1/events/{id}/rsvp` với `GOING` | API trả 409 `EVT_001`, RSVP không đổi | | Chưa chạy | FR-EVT-02 |
| TC-EVT-008 | Events | Phân trang/lọc thời gian | Có sự kiện sắp tới và đã qua | 1. Mở hai tab danh sách; 2. Chọn **Xem thêm** | Đúng thời điểm lọc; phân trang bắt đầu tại `page=0` | | Chưa chạy | FR-EVT-01 |
| TC-EVT-009 | Events | Chặn truy cập khác gia đình | Hai tài khoản ở hai gia đình | 1. Gọi GET `/api/v1/events/{id}` bằng tài khoản khác gia đình | API từ chối 403 `PERM_001` | | Chưa chạy | FR-EVT-01 |

**Trạng thái:** Các trường hợp thủ công chờ chạy trên môi trường có Backend, PostgreSQL và tài khoản thử nghiệm; không tự ghi PASS.

**Phạm vi:** Không kiểm thử nhắc sự kiện hoặc thư viện media sự kiện vì không thuộc [4-05].
