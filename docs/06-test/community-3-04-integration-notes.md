# [3-04] Ghi chú bàn giao BE + FE (TV3 → TV1)

## Phạm vi đã viết trên bản ZIP cập nhật

- Flyway: bảng `post`, `comment`, `reaction`; `type POST/NEWS`, xóa mềm,
  `TIMESTAMPTZ`, FK nội bộ, chỉ mục và unique partial reaction.
- Backend: API CRUD post, phân trang/lọc NEWS, comment, reaction/upsert.
  Quy tắc đã có: MEMBER không đăng NEWS, tác giả sửa, quản trị có thể gỡ theo
  cổng kiểm tra phạm vi, trả envelope 200/201/4xx, `size <= 100`.
- Frontend: route `/community`, tab Tất cả/Tin tức gia đình, tạo/sửa/xóa
  bài và bình luận, optimistic reaction, nút Xem thêm, loading/empty/error.
  FE dùng `src/lib/api.ts` và kiểu đã sinh từ YAML (không sửa file generated).
- Kiểm thử: JUnit/Mockito ở `backend/src/test`, MockMvc controller test và
  test case thủ công `docs/06-test/community-posts-3-04.md`.

## Phần chưa thể tích hợp vì các task khác chưa có trong bản ZIP

1. **SCRUM-35 Auth**: cung cấp JWT principal, `MembershipQuery` và vai trò.
2. **SCRUM-37 Genealogy**: kiểm tra family/branch, tổ tiên/chi con theo BR-GEN-14.
3. **SCRUM-57**: cung cấp `AuditLogger` cho việc gỡ nội dung của người khác.
4. **AI**: kết nối `AiGateway` theo BR-COM-11.

Các điểm này được đưa qua `CommunityPlatformPort`. Chỉ khi thành viên phụ
trách triển khai adapter đọc chính dữ liệu đăng nhập đã xác minh và quyền
genealogy, API mới được bật. **Không có adapter giả, không nhận userId/role
qua query hoặc header do client gửi.** Trong bản độc lập này, controller
được điều kiện hóa bằng `@ConditionalOnBean(CommunityPlatformPort.class)`,
để không mở API vô bảo vệ khi `SecurityConfig` hiện còn `permitAll()`.

Adapter cần thực hiện đúng giao diện Java trong `auth.md` mục 6.3; khi
module đã xuất hiện, phải rà soát cùng TV1/TV2 để không nhân đôi code
Auth/Genealogy. Chưa xác nhận điều kiện Done cho Jira.

## Kiểm tra trước khi PR

- File nguồn được cung cấp có migration `V1` và bản build cũ ghi
  `V2__create_media_table.sql`, nhưng **thiếu file nguồn V2**. Migration
  Community tạm dùng `V3`; cần TV1 xác nhận thứ tự Flyway và bổ sung
  V2 chính thức trước khi áp dụng DB. Không đổi migration đã chạy.
- Trước merge, chạy `mvn test` tại `backend`, `npm ci` và
  `npm run build` tại `frontend` trên máy đã cài công cụ, sau đó test
  end-to-end với hai gia đình khác nhau.
- Bản triển khai này **chưa chạy Maven, PostgreSQL hoặc test giao diện trên
  môi trường tích hợp**. Các test case được đánh dấu `Chưa chạy`.
- Các thay đổi phải nằm trên nhánh task code riêng, mở PR vào `develop`,
  không gộp các file của task khác.
