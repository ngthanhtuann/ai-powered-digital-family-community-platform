# Test case [3-04] — Bài đăng, bình luận, reaction (BE + FE)

> Người thực hiện: TV3 | Reviewer/test chéo: TV1. Bám `test-case-template.md`,
> `community-events.md`, `community-events.yaml`, `srs.md` Phụ lục A.
> Kết quả thực tế để trống; chỉ điền `Đạt` khi TV1 thực sự chạy.

| ID | Module | Mô tả | Tiền điều kiện | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Trạng thái | Mã FR |
|---|---|---|---|---|---|---|---|---|
| TC-COM-001 | Community | MEMBER đăng POST | MEMBER A đã xác minh | 1. Gọi `POST /api/v1/posts` với `{content:"Tin gia đình",type:"POST"}` | HTTP 201; `success:true`; `type=POST` | | Chưa chạy | FR-COM-01 |
| TC-COM-002 | Community | MEMBER không được đăng NEWS | MEMBER A đã xác minh | 1. Gọi `POST /api/v1/posts` với `type=NEWS` | 403 `PERM_001` | | Chưa chạy | FR-COM-03 |
| TC-COM-003 | Community | Trưởng chi đăng NEWS | BRANCH_ADMIN A đã xác minh | 1. Gọi `POST /api/v1/posts` với `type=NEWS` | 201; bài xuất hiện tại tab Tin tức gia đình của thành viên có quyền xem | | Chưa chạy | FR-COM-03 |
| TC-COM-004 | Community | Người khác không được sửa bài | MEMBER A1 và A2 đã xác minh, A1 có bài | 1. Dùng token A2 gọi `PATCH /api/v1/posts/{id}` bài A1 | 403 `PERM_001` | | Chưa chạy | FR-COM-01 |
| TC-COM-005 | Community | Trưởng chi gỡ bài trong chi | BRANCH_ADMIN quản lý chi, có bài người khác cùng chi | 1. Gọi `DELETE /api/v1/posts/{id}` | 200 `{success:true,data:null}`; bài bị xóa mềm; có audit log | | Chưa chạy | FR-COM-01 |
| TC-COM-006 | Community | Bình luận trong bài có quyền xem | MEMBER A đã xác minh và có bài | 1. Gọi `POST /api/v1/posts/{id}/comments` với `content` hợp lệ | 201; danh sách bình luận có bản ghi mới | | Chưa chạy | FR-COM-02 |
| TC-COM-007 | Community | Bình luận quá 1000 ký tự | MEMBER A đã xác minh | 1. Gửi bình luận 1001 ký tự | 400 `VALID_001` | | Chưa chạy | FR-COM-02 |
| TC-COM-008 | Community | Mỗi người một reaction đang hoạt động | MEMBER A đã reaction LIKE | 1. Gọi `POST /api/v1/posts/{id}/reactions` với LOVE | 201; `myReaction=LOVE`; chỉ một reaction hoạt động của A trên bài | | Chưa chạy | FR-COM-02 |
| TC-COM-009 | Community | Bỏ reaction | MEMBER A đã reaction | 1. Gọi `DELETE /api/v1/posts/{id}/reactions` | 200 `{success:true,data:null}`; `myReaction=null` | | Chưa chạy | FR-COM-02 |
| TC-COM-010 | Community | Phân trang đúng quy ước | MEMBER A đã xác minh; gia đình A có nhiều bài | 1. Gọi `GET /api/v1/posts?page=0&size=20&sort=createdAt,desc` | 200; có `meta{page,size,total}`, chỉ bài được quyền xem | | Chưa chạy | FR-COM-01 |
| TC-COM-011 | Community | Không cho size quá 100 | MEMBER A đã xác minh | 1. Gọi `GET /api/v1/posts?size=101` | 400 `VALID_001` | | Chưa chạy | FR-COM-01 |
| TC-IDOR-001 | Community | Chống xem bài gia đình khác | MEMBER A đã xác minh, biết UUID bài gia đình B | 1. Gọi `GET /api/v1/posts/{id}` thuộc B | 403 `PERM_001` | | Chưa chạy | FR-COM-01 |
| TC-IDOR-002 | Community | Chống đọc bình luận gia đình khác | MEMBER A đã xác minh, có UUID bài B | 1. Gọi `GET /api/v1/posts/{id}/comments` | 403 `PERM_001` | | Chưa chạy | FR-COM-02 |
| TC-PERM-001 | Community | Người chưa xác minh bị từ chối | MEMBER đang PENDING | 1. Gọi `GET /api/v1/posts` | 403 `PERM_002` | | Chưa chạy | FR-COM-01 |
| TC-PERM-002 | Community | Chưa đăng nhập bị từ chối | Không có JWT | 1. Gọi `GET /api/v1/posts` | 401 `AUTH_002` | | Chưa chạy | FR-COM-01 |
| TC-UI-001 | Community | Hai tab bảng tin | MEMBER A đã xác minh | 1. Mở Bảng tin 2. Chọn Tin tức gia đình | Tab Tất cả gồm POST/NEWS; tab Tin tức chỉ NEWS | | Chưa chạy | FR-COM-03 |
| TC-UI-002 | Community | Nút NEWS theo role | Có MEMBER và BRANCH_ADMIN đã xác minh | 1. Mở form với từng tài khoản | MEMBER chỉ có POST; BRANCH_ADMIN có thêm NEWS | | Chưa chạy | FR-COM-03 |
| TC-UI-003 | Community | Optimistic reaction | MEMBER A đã xác minh | 1. Bấm reaction 2. Giả lập API lỗi | Số lượng đổi ngay; nếu lỗi quay lại trạng thái trước và báo lỗi | | Chưa chạy | FR-COM-02 |
| TC-UI-004 | Community | Tải thêm bài | Có trên 20 bài phù hợp | 1. Vào bảng tin 2. Bấm Xem thêm | Bổ sung trang tiếp theo, không lặp bài | | Chưa chạy | FR-COM-01 |
| TC-UI-005 | Community | Responsive | Backend và Auth/Genealogy đã tích hợp | 1. Kiểm tra bảng tin lần lượt tại 360, 768, 1440 px | Không cuộn ngang; đọc bài, bình luận, reaction và nút Xem thêm dùng được | | Chưa chạy | NFR-01 |

## Phụ thuộc để chạy test tích hợp

- SCRUM-35: JWT, `MembershipQuery` và `member_verification` đã triển khai.
- SCRUM-37: `GenealogyPort.isBranchWithin` và kiểm tra `family_id`/chi.
- SCRUM-57: `AuditLogger`, để kiểm chứng log khi trưởng chi gỡ nội dung.
- Module AI: `AiGateway`, để kiểm tra cập nhật embedding theo BR-COM-11.
- Dữ liệu kiểm thử tuân `test-plan.md` mục 6; không dùng người dùng hoặc gia đình giả trong production.
