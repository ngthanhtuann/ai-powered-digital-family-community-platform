# ADR: Công nghệ Mobile

> **Người viết:** TV5 (Lê Nhựt) | **Reviewer:** TV3 (PiLo257) | **Task Jira:** SCRUM-84
> **Ngày:** 18/10/2026 (merge PR #37) | **Người quyết định:** TV5 đề xuất, TV1 duyệt
> **Trạng thái:** Đã chọn. Thiết kế chi tiết (điều hướng, kiến trúc, xử lý lỗi) ở [`mobile.md`](../02-design/mobile.md).

## 1. Bối cảnh
Dự án cần ứng dụng mobile chạy trên iOS và Android. Web hiện sử dụng Next.js, vì vậy việc lựa chọn công nghệ có thể tận dụng kiến thức và hệ sinh thái React hiện có. Ứng dụng mobile dự kiến gọi cùng REST API `/api/v1` với web; tài liệu này không giả định rằng mobile sẽ dùng chung giao diện hoặc mã UI với web.

## 2. Quyết định
**Chọn React Native + Expo** để phát triển ứng dụng mobile cho iOS và Android.
Điều hướng dùng **React Navigation** (stack gốc chọn nhánh theo trạng thái đăng nhập, menu drawer trong ứng dụng), cấu trúc `src/` gồm `navigation/`, `screens/`, `components/`, `services/` theo `architecture.md` mục 5 và mục 7, quyết định 2. Không dùng Expo Router. Nếu sau này xuất hiện yêu cầu native đặc biệt mà Expo chưa đáp ứng trực tiếp, nhóm sẽ đánh giá giải pháp phù hợp thay vì mặc định loại bỏ Expo.

## 3. Lý do lựa chọn
- **Phù hợp với hệ sinh thái hiện tại:** React Native dùng React và JavaScript/TypeScript, gần với nền tảng React/Next.js của web.
- **Tái sử dụng kiến thức:** Thành viên đã làm web bằng React có thể chuyển sang mobile dễ hơn so với học một hệ sinh thái hoàn toàn khác.
- **Phát triển đa nền tảng:** Có thể xây dựng ứng dụng cho iOS và Android từ một codebase chính, đồng thời kiểm thử trên cả hai nền tảng.
- **Expo giảm công việc cấu hình ban đầu:** Cung cấp công cụ phát triển, chạy thử và build ứng dụng; có các thư viện tích hợp cho nhu cầu mobile như lưu token an toàn và thông báo.
- **Phù hợp với phạm vi dự án:** Các chức năng như bảng tin, sự kiện, cây gia phả, chat AI, tìm kiếm và danh bạ chủ yếu là giao diện ứng dụng kết nối REST API.
Các lợi ích trên là lý do lựa chọn, không có nghĩa toàn bộ mã giao diện web Next.js có thể dùng trực tiếp trong React Native.

## 4. So sánh ngắn với Flutter
| Tiêu chí | React Native + Expo | Flutter |
|---|---|---|
| Ngôn ngữ/hệ sinh thái | JavaScript hoặc TypeScript, React | Dart, Flutter |
| Phù hợp với web Next.js hiện tại | Gần với hệ React và kiến thức web hiện có | Cần làm quen với Dart và hệ sinh thái Flutter |
| Phát triển iOS/Android | Một codebase chính; Expo hỗ trợ quy trình phát triển | Một codebase chính; Flutter hỗ trợ quy trình phát triển |
| UI | Component React Native và thư viện tương ứng | Widget của Flutter |
| Lựa chọn cho dự án này | **Được chọn** vì phù hợp với hệ React hiện có | Không chọn ở thời điểm này vì cần thêm một hệ sinh thái riêng |
Bảng trên là so sánh ở mức phục vụ quyết định dự án, không phải kết luận rằng một công nghệ luôn tốt hơn công nghệ còn lại.

## 5. Hệ quả của quyết định
### Lợi ích kỳ vọng
- Giảm thời gian làm quen công nghệ cho thành viên đã biết React.
- Có thể tổ chức mobile theo component và quy ước TypeScript.
- Dùng chung REST API `/api/v1` và cùng một hợp đồng OpenAPI với web.

### Chi phí và giới hạn cần lưu ý
- Giao diện web không thể được dùng trực tiếp như giao diện React Native; cần xây dựng màn hình mobile riêng.
- Cần kiểm thử trên thiết bị iOS và Android, đặc biệt với thông báo, quyền truy cập ảnh và lưu token.
- Cần cấu hình môi trường API cho máy phát triển, thiết bị thật và bản build.
- Đăng nhập mobile theo luồng JWT ở `use-cases/auth.md` mục 4.2: access token 15 phút, refresh token 7 ngày gửi trong body `POST /api/v1/auth/refresh`, dùng một lần và xoay vòng; cả hai lưu bằng `expo-secure-store`.
- Kiểu dữ liệu API sinh bằng `openapi-typescript` từ `docs/04-api/openapi/` vào `src/services/generated/`, dùng chung hợp đồng với web (`conventions.md` mục 8).

## 6. Phạm vi áp dụng
Các chức năng dự kiến trên mobile gồm: đăng nhập/đăng ký/hồ sơ, bảng tin và tạo bài, sự kiện và RSVP, thông báo, xem cây gia phả và tra quan hệ, chat AI, tìm kiếm, danh bạ và Heritage.
Các chức năng quản trị nặng như Admin và Dashboard chi tiết tiếp tục ở web theo phạm vi hiện tại của task.
