# [6-10] Mobile: Bảng tin, Sự kiện, Thông báo — Test thủ công

**Người làm:** TV3 (PiLo257)

**Reviewer:** TV1

**Nguồn:** `docs/02-design/mobile.md`, `docs/04-api/openapi/community-events.yaml`, `docs/06-test/test-plan.md`, `docs/06-test/test-case-template.md`.

**Trạng thái:** Đã viết giao diện và service mobile. Chưa kiểm thử trên Android/iOS thật; cột kết quả thực tế để trống theo mẫu.

| ID | Module | Mô tả | Tiền điều kiện | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Trạng thái | Mã FR |
|---|---|---|---|---|---|---|---|---|
| TC-UI-006 | Mobile | Điều hướng drawer | MEMBER đã xác minh | 1. Đăng nhập trên Android/iOS. 2. Mở drawer. 3. Chọn Bảng tin, Sự kiện. | Có đúng hai mục, mở đúng màn, không dùng tab bar. | | Chưa chạy | FR-COM-01, FR-EVT-01 |
| TC-UI-007 | Community | Bảng tin phân trang | Gia đình có hơn 20 bài | 1. Mở Bảng tin. 2. Cuộn tới cuối danh sách. | Tải trang kế tiếp (`page` tăng), không mất bài cũ và không lặp ID. | | Chưa chạy | FR-COM-01 |
| TC-UI-008 | Community | Kéo để làm mới | Gia đình có bài mới từ web | 1. Mở Bảng tin trên mobile. 2. Đăng bài trên web. 3. Kéo xuống làm mới mobile. | Bài mới xuất hiện, nội dung và số liệu khớp web. | | Chưa chạy | FR-COM-01 |
| TC-UI-009 | Community | Xem chi tiết/bình luận | MEMBER A đã xác minh, có bài đăng | 1. Chọn bài. 2. Viết bình luận. 3. Mở lại trên web. | API `POST /posts/{id}/comments` trả 201; bình luận có trên cả hai nền tảng. | | Chưa chạy | FR-COM-02 |
| TC-UI-010 | Community | Reaction và bỏ reaction | MEMBER A đã xác minh, có bài | 1. Chọn Thích. 2. Chọn Yêu thích. 3. Chạm lại Yêu thích. | Mỗi lần chỉ một reaction, bỏ reaction đưa `myReaction` về null; số lượng khớp web. | | Chưa chạy | FR-COM-02 |
| TC-UI-011 | Community | Tạo bài không ảnh | MEMBER A đã xác minh | 1. Chọn Tạo bài. 2. Nhập nội dung. 3. Bấm Đăng bài. | `POST /posts` trả 201; mở chi tiết bài; web thấy cùng bài. | | Chưa chạy | FR-COM-01 |
| TC-UI-012 | Community | Chọn ảnh thư viện | Máy có JPG/PNG/WebP <= 5 MB | 1. Tạo bài. 2. Chọn ảnh từ thư viện. 3. Đăng. | `POST /posts/{id}/media` trả 201; hiển thị % tiến trình; ảnh xem được sau khi mở bài bằng JWT. | | Chưa chạy | FR-COM-05 |
| TC-UI-013 | Community | Chụp ảnh camera | Điện thoại có camera, đã cấp quyền | 1. Tạo bài. 2. Chụp ảnh. 3. Đăng. | Ảnh gắn vào bài, xem được trên mobile và web. | | Chưa chạy | FR-COM-05 |
| TC-UI-014 | Community | Từ chối quyền camera | Chưa cấp quyền camera | 1. Từ chối quyền. 2. Bấm Chụp ảnh. | Có thông báo quyền camera; app không bị crash. | | Chưa chạy | FR-COM-05 |
| TC-UI-015 | Community | Tệp không hợp lệ | Có ảnh > 5 MB hoặc HEIC | 1. Chọn ảnh. 2. Quan sát kết quả. | Không tải loại/kích thước không hỗ trợ; hiện lý do và cho chọn ảnh khác. | | Chưa chạy | FR-COM-05 |
| TC-UI-016 | Community | Tải ảnh gián đoạn | Mạng có thể tắt trong lúc upload | 1. Tạo bài kèm hai ảnh. 2. Ngắt mạng lúc tải. 3. Bật lại và bấm tải tiếp. | Hiện lỗi, giữ ID bài; chỉ tải tiếp ảnh thiếu, không tạo trùng bài. | | Chưa chạy | FR-COM-05 |
| TC-UI-017 | Events | Danh sách sắp tới/đã qua | Có sự kiện ở cả hai mốc | 1. Mở Sự kiện. 2. Chuyển hai bộ lọc. | Lọc theo `from`/`to` tại thời điểm thực; sắp xếp theo `startAt`, cuộn để tải thêm. | | Chưa chạy | FR-EVT-01 |
| TC-UI-018 | Events | Chi tiết và RSVP | MEMBER A đã xác minh, sự kiện chưa bắt đầu | 1. Mở sự kiện. 2. Chọn Tham dự/Không/Có thể. | `PUT /events/{id}/rsvp` thành công; `myRsvpStatus`, số người và web đồng bộ. | | Chưa chạy | FR-EVT-03 |
| TC-UI-019 | Events | Sự kiện đã bắt đầu | Có sự kiện bắt đầu trong quá khứ | 1. Mở chi tiết. 2. Kiểm tra nút RSVP. | Không cho đổi RSVP; không gửi thao tác mới tới API. | | Chưa chạy | FR-EVT-03 |
| TC-UI-020 | Events | Người tham dự | Sự kiện có nhiều RSVP | 1. Mở chi tiết. 2. Xem danh sách và tải thêm. | Có tên và trạng thái, không hiện thông tin liên hệ; số liệu khớp web. | | Chưa chạy | FR-EVT-03 |
| TC-UI-021 | Events | Gallery sự kiện | Sự kiện có ảnh | 1. Mở chi tiết. 2. Xem gallery. 3. Tải thêm ảnh nếu có. | Chỉ hiển thị ảnh được phép xem qua `GET /media/{id}/content` kèm JWT. | | Chưa chạy | FR-EVT-04 |
| TC-UI-022 | Notifications | Chuông và số chưa đọc | Có thông báo chưa đọc | 1. Vào app. 2. Quan sát chuông. 3. Mở danh sách. 4. Đánh dấu một thông báo đã đọc. | Số chưa đọc từ `meta.total` giảm, dấu đỏ thay đổi, web cùng trạng thái. | | Chưa chạy | FR-EVT-05 |
| TC-UI-023 | Notifications | Nhắc sự kiện trong app | Có `EVENT_REMINDER_24H/1H` | 1. Mở chuông. 2. Chọn thông báo sự kiện. | Hiện nội dung nhắc và mở chi tiết sự kiện; không có push notification ngoài app. | | Chưa chạy | FR-EVT-05 |
| TC-UI-024 | Mobile | Trạng thái mạng | Có thể tắt mạng điện thoại | 1. Vào từng màn khi offline. 2. Bật lại mạng. 3. Bấm Thử lại hoặc kéo làm mới. | Có trạng thái lỗi, retry; không crash, không trắng màn. | | Chưa chạy | NFR-01 |
| TC-UI-025 | Mobile | Màn hình nhỏ Android/iOS | Android/iPhone màn nhỏ, chiều rộng khoảng 320–360 px | 1. Mở lần lượt Bảng tin, Tạo bài, Chi tiết bài, Sự kiện, Thông báo. 2. Mở bàn phím và cuộn. | Không tràn ngang; nút, ô nhập, ảnh và phần RSVP thao tác được. | | Chưa chạy | NFR-01 |

## Chuẩn bị chạy test

1. Chạy backend và PostgreSQL với dữ liệu giả; có MEMBER A đã xác minh, gia đình A/B, bài viết và sự kiện thử nghiệm.
2. Trên điện thoại thật và backend cùng Wi-Fi: đặt `EXPO_PUBLIC_API_BASE_URL` bằng địa chỉ LAN của máy chạy backend (xem `mobile/README.md`).
3. Chạy `cd mobile`, `npm install`, `npm start`. Thử Android và iOS (chụp ảnh camera trên thiết bị thật; iOS Simulator không có camera thật).
4. Đăng nhập cùng tài khoản trên mobile và web; đối chiếu ID bài, nội dung, comment/reaction, RSVP, ảnh, số thông báo chưa đọc.
5. Khi test hoàn tất, TV1 điền cột **Kết quả thực tế** và đổi **Trạng thái**; test lỗi tạo Bug theo Test Plan.

## Kết quả kiểm tra mã nguồn

- `npm run typecheck`: **đạt**.
- `npm run lint`: **đạt**.
- `npm run format:check`: **đạt**.
- `npx expo config --type public`: **đạt** (cấu hình Expo đọc được).
- `npx expo export --platform android`: **đạt** (Metro tạo JS bundle Android).
- `npx expo export --platform ios`: **đạt** (Metro tạo JS bundle iOS).
- `npx expo install --check`: chưa xác minh được do proxy hết thời gian chờ; `expo-image-picker@58.0.10` đã cài đúng phiên bản tài liệu Expo SDK 58.
- Chưa chạy ứng dụng trên thiết bị Android/iOS, chưa kiểm chứng dữ liệu với web qua backend sống.
- Không thêm `expo-notifications`; nhắc sự kiện chỉ sử dụng màn Thông báo trong ứng dụng.
