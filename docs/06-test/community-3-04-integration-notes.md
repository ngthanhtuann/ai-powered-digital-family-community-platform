# [3-04] Ghi chú bàn giao BE + FE (TV3 → TV1)

> Cập nhật 24/10/2026 theo trạng thái trên `develop` (commit `d2f6f7d`), sau khi PR #55 được review và merge.
> Bản trước của ghi chú này viết khi PR còn WIP (chưa có Auth, Genealogy; migration tạm `V3`); các mục đó đã xử lý như bên dưới.

## Phạm vi đã có trên develop

- Flyway `V6__community_post_comment_reaction.sql`: bảng `post`, `comment`, `reaction` khớp `erd.dbml`; `type` POST/NEWS, xóa mềm, `TIMESTAMPTZ`, khóa ngoại trong module, unique partial cho reaction. Đánh số sau `V5` (Gia phả).
- Backend: CRUD bài đăng, phân trang và lọc NEWS, bình luận, reaction (upsert), lỗi theo `ApiException` và `GlobalExceptionHandler` chung.
- `ModulePlatformAdapter` cài `CommunityPlatformPort` bằng interface của module khác: `MembershipQuery` (đăng nhập, xác minh, vai trò), `GenealogyPort.isBranchWithin` (phạm vi chi, BR-COM-03), `AuditLogger` (`CONTENT_DELETED` cho mọi thao tác xóa, BR-COM-10, BR-COM-12), `AiGateway` (cập nhật chỉ mục sau commit, BR-COM-11), `UserDirectory` (`authorName`). Không nhận userId hay vai trò từ client.
- Response có `authorName` và `canDelete` (`community-events.yaml`); đếm bình luận, reaction theo lô cho mỗi trang.
- Frontend `/community`: tab Tất cả/Tin tức gia đình, tạo/sửa/xóa bài và bình luận, reaction, Xem thêm; dùng `Button`, `TextField` của UI kit; mục "Bảng tin" trên menu trỏ tới `/community`.

## Đã kiểm tra

- `mvn -B verify` trên `develop` `d2f6f7d`: 124/124 test qua (gồm `CommunityServiceTest`, `CommunityControllerTest`, `ModulePlatformAdapterTest`).
- Frontend: `npm run lint`, `npx tsc --noEmit`, `npm run build` qua.
- Test case API trong `community-posts-3-04.md`: 15/15 Đạt (chạy 24/10/2026 trên backend thật với PostgreSQL pgvector, dữ liệu giả).

## Còn lại (không thuộc SCRUM-38)

- Các test case giao diện `TC-UI-001` đến `TC-UI-005` chưa chạy lại trong đợt này.
- Ảnh gắn vào bài đăng (`/posts/{id}/media`) thuộc SCRUM-46; báo cáo nội dung và `ContentModerationPort` thuộc SCRUM-50.
- Tư cách thành viên hiện tạo qua tạo gia đình (UC-GEN-01) hoặc dữ liệu kiểm thử; luồng xin vào gia đình `/join-requests` thuộc SCRUM-57.
