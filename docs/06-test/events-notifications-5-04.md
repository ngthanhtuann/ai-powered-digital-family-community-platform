# [5-04] SCRUM-53: Reminders, Gallery, Notifications, Announcements

## Nguồn đối chiếu

- `docs/01-srs/use-cases/community-events.md`: BR-COM-01..03, 08..12; BR-EVT-04..05.
- `docs/04-api/openapi/community-events.yaml`: `/announcements`, `/notifications`, `/events/{id}/media`.
- Phụ lục Jira [5-04], cập nhật 09/10/2026 và 02/11/2026 (TV1).

## Trường hợp cần kiểm thử

| Nhóm | Trường hợp | Kết quả mong đợi |
|---|---|---|
| Nhắc 24 giờ | `start_at = nowUTC + 24h`, RSVP `GOING` | Tạo `EVENT_REMINDER_24H` |
| Nhắc 1 giờ | `start_at = nowUTC + 1h`, RSVP `MAYBE` | Tạo `EVENT_REMINDER_1H` |
| RSVP không hợp lệ | `NOT_GOING`, RSVP xóa mềm | Không tạo thông báo |
| Thành viên | Chưa xác minh, rời gia đình, không còn quyền xem chi | Không tạo thông báo |
| Sự kiện | Đã bắt đầu, xóa mềm, bị ẩn | Không gửi |
| Lặp job | Chạy hai lần cùng mốc | Không trùng `(user_id, ref_id, type)` |
| Múi giờ | Cùng `Instant` nhưng FE hiển thị tại giờ Việt Nam | Job so UTC, FE dùng giờ địa phương |
| Thông báo | Truy vấn `isRead=false`, đánh dấu đọc | `meta.total` phản ánh số chưa đọc; chỉ chủ tài khoản cập nhật được |
| IDOR | Người A PATCH thông báo của B | 404, không đổi bản ghi B |
| Gallery | Upload JPG/PNG/WebP <=5 MB | 201, media có `owner_type=EVENT` |
| Gallery | PDF, định dạng khác | 415 `FILE_002` |
| Gallery | Ảnh >5 MB | 413 `FILE_001` |
| Gallery | User khác gia đình/chi gọi media content | 403, không lộ bytes |
| Announcement | MEMBER POST/PATCH/DELETE | 403 `PERM_001` |
| Announcement | BRANCH_ADMIN đúng phạm vi, SYSTEM_ADMIN chọn family | Tạo/sửa/gỡ ghim, xóa mềm (HTTP 200) |
| Announcement | `is_pinned=true` và không ghim | Ghim lên đầu danh sách |
| Kiểm duyệt | Announcement `hidden_at` khác NULL | Không xuất hiện ở bảng tin; báo cáo có target ANNOUNCEMENT |
| Kiểm duyệt | Ẩn nội dung, nhiều người báo cáo | Chủ nội dung có `CONTENT_HIDDEN`, mỗi reporter có `REPORT_HIDDEN` |
| Kiểm duyệt | Bỏ qua báo cáo, nhiều người báo cáo | Mỗi reporter có `REPORT_DISMISSED`, chủ nội dung không nhận thông báo ẩn |
| Kiểm duyệt | Khôi phục nội dung ẩn nhầm | Chủ nội dung có `CONTENT_RESTORED`, mỗi reporter có `REPORT_RESTORED` |
| Kiểm duyệt | Báo cáo riêng của từng người | `ref_type=CONTENT_REPORT`, `ref_id` là report UUID; không trùng thông báo |

## Trạng thái xác minh

- Đã thêm unit tests `EventReminderSchedulerTest` và `NotificationServiceTest`.
- Chưa chạy Maven/Jest/DB integration trong môi trường tạo bản vá vì không có Maven và dependencies frontend.
- Quy ước `notification.type` cho kiểm duyệt được bổ sung trong SCRUM-53 theo yêu cầu triển khai của TV3: `CONTENT_HIDDEN`, `CONTENT_RESTORED`, `REPORT_HIDDEN`, `REPORT_DISMISSED`, `REPORT_RESTORED`. Đây là quyết định bổ sung (không có trong tài liệu TV1 ngày 02/11), cần reviewer TV1 xác nhận khi PR review.
