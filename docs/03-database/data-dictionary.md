# Từ điển dữ liệu

> **Người tổng hợp:** TV2 (Nguyễn Minh Trí) | **Reviewer:** TV5 (Lê Nhựt) | **Task Jira:** SCRUM-28 (2-03) | **Phiên bản:** 0.4 (khớp ERD v0.5, 30 bảng)
> Nguồn: `genealogy.md` (TV2, bản 14/10), `community-events.md` (TV3, đã merge, có `content_report`), `heritage-directory-dashboard.md` (TV4), `auth.md` mục 5 (TV1, đã merge PR #31; quy tắc cột người thực hiện theo PR #42), `ai.md` mục 5 (TV5, đã merge PR #35).
> Mỗi bảng một khối. Quy ước: khóa chính UUID; `TIMESTAMPTZ`; xóa mềm `deleted_at`; khóa ngoại chỉ trong cùng module; cột "tham chiếu logic" chỉ lưu UUID có index, service kiểm tra.

## Auth và quản trị (auth, admin)

Người sở hữu: **TV1 Tuấn**

### `users`

**Mục đích:** Tài khoản và hồ sơ cá nhân (mật khẩu BCrypt).  **Người sở hữu:** TV1 Tuấn.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| email | VARCHAR(255) | NOT NULL | Chữ thường; UNIQUE WHERE deleted_at IS NULL |
| password_hash | VARCHAR(100) | NOT NULL | Hash BCrypt, không trả ra API |
| full_name | VARCHAR(150) | NOT NULL | Họ tên |
| phone | VARCHAR(20) | NULL |  |
| date_of_birth | DATE | NOT NULL | Tuổi tối thiểu 16 kiểm ở service (BR-AUTH-03) |
| status | VARCHAR(20) | NOT NULL DEFAULT ACTIVE, CHECK | ACTIVE, LOCKED |
| lock_reason | VARCHAR(255) | NULL | Bắt buộc khi LOCKED (service) |
| last_login_at | TIMESTAMPTZ | NULL | Đăng nhập thành công gần nhất |
| created_by | UUID | NULL | NULL khi tự đăng ký; chỉ lưu UUID, không FK (cột người thực hiện, `auth.md` mục 5) |
| updated_by | UUID | NULL | Chỉ lưu UUID, không FK (cột người thực hiện) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** Chỉ mục (status).

### `role`

**Mục đích:** Danh mục vai trò.  **Người sở hữu:** TV1 Tuấn.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| code | VARCHAR(30) | NOT NULL, UNIQUE | GUEST, MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN |
| name | VARCHAR(100) | NOT NULL | Tên hiển thị |
| description | VARCHAR(255) | NULL |  |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** Dữ liệu khởi tạo 4 dòng (Flyway). GUEST không gán vào user_role; BRANCH_ADMIN nằm ở member_verification.role.

### `user_role`

**Mục đích:** Vai trò toàn hệ thống của người dùng (MEMBER, SYSTEM_ADMIN).  **Người sở hữu:** TV1 Tuấn.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| user_id | UUID | NOT NULL, FK | Người dùng → `users.id` (FK, cùng module) |
| role_id | UUID | NOT NULL, FK | Vai trò → `role.id` (FK, cùng module) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** UNIQUE (user_id, role_id) WHERE deleted_at IS NULL.

### `refresh_token`

**Mục đích:** Refresh token (chỉ lưu hash).  **Người sở hữu:** TV1 Tuấn.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| user_id | UUID | NOT NULL, FK | Người dùng → `users.id` (FK, cùng module) |
| token_hash | VARCHAR(64) | NOT NULL, UNIQUE | SHA-256 dạng hex |
| expires_at | TIMESTAMPTZ | NOT NULL | Hết hạn |
| revoked_at | TIMESTAMPTZ | NULL | Đã dùng, đăng xuất hoặc thu hồi |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

### `member_verification`

**Mục đích:** Yêu cầu tham gia gia đình và tình trạng thành viên.  **Người sở hữu:** TV1 Tuấn.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| user_id | UUID | NOT NULL, FK | Người xin tham gia hoặc thành viên → `users.id` (FK, cùng module) |
| family_id | UUID | NOT NULL | Gia đình, không FK → `family` (tham chiếu logic) |
| branch_id | UUID | NULL | Chi, không FK; BRANCH_ADMIN: NULL = cả gia đình → `branch` (tham chiếu logic) |
| role | VARCHAR(20) | NOT NULL DEFAULT MEMBER, CHECK | MEMBER, BRANCH_ADMIN |
| status | VARCHAR(20) | NOT NULL DEFAULT PENDING, CHECK | PENDING, APPROVED, REJECTED, CANCELLED, REMOVED |
| message | VARCHAR(255) | NULL | Lời nhắn của người xin |
| reject_reason | VARCHAR(255) | NULL | Lý do từ chối |
| reviewed_by | UUID | NULL | Người duyệt; chỉ lưu UUID, không FK (cột người thực hiện) |
| reviewed_at | TIMESTAMPTZ | NULL |  |
| created_by | UUID | NULL | Không FK → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Không FK → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** UNIQUE (user_id) WHERE status IN (PENDING, APPROVED) AND deleted_at IS NULL (mỗi người một gia đình, BR-AUTH-08).

### `audit_log`

**Mục đích:** Nhật ký kiểm toán (chỉ thêm, không sửa).  **Người sở hữu:** TV1 Tuấn.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| actor_user_id | UUID | NULL | Người thực hiện, không FK; NULL khi chưa xác định → `users` (tham chiếu logic) |
| actor_role | VARCHAR(20) | NULL | Vai trò hiệu lực |
| action | VARCHAR(60) | NOT NULL | Mã hành động (mục 6.4 auth.md) |
| target_type | VARCHAR(40) | NULL | Loại đối tượng |
| target_id | UUID | NULL | Đối tượng, tùy target_type, không FK |
| family_id | UUID | NULL | Gia đình liên quan → `family` (tham chiếu logic) |
| ip_address | VARCHAR(45) | NULL |  |
| detail | JSONB | NULL | Thông tin bổ sung không nhạy cảm |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời điểm thao tác |

**Ràng buộc bổ sung:** Ngoại lệ quy ước: chỉ có id và created_at (bản ghi chỉ thêm). Chỉ mục (created_at DESC), (actor_user_id, created_at DESC), (action, created_at DESC).

### `system_config`

**Mục đích:** Tham số cấu hình hệ thống.  **Người sở hữu:** TV1 Tuấn.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| config_key | VARCHAR(100) | NOT NULL | UNIQUE WHERE deleted_at IS NULL |
| config_value | VARCHAR(500) | NOT NULL |  |
| value_type | VARCHAR(20) | NOT NULL, CHECK | INTEGER, BOOLEAN, STRING |
| config_group | VARCHAR(50) | NOT NULL | Nhóm hiển thị |
| default_value | VARCHAR(500) | NOT NULL | Giá trị mặc định |
| description | VARCHAR(255) | NOT NULL |  |
| created_by | UUID | NULL | Không FK → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người sửa; chỉ lưu UUID, không FK (cột người thực hiện) |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

## Gia phả (genealogy)

Người sở hữu: **TV2 Trí**

### `family`

**Mục đích:** Gia đình / dòng họ.  **Người sở hữu:** TV2 Trí.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| name | VARCHAR(150) | NOT NULL | Tên gia đình |
| description | TEXT |  | Mô tả |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

### `branch`

**Mục đích:** Chi họ.  **Người sở hữu:** TV2 Trí.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL, FK | Gia đình → `family.id` (FK, cùng module) |
| name | VARCHAR(150) | NOT NULL | Tên chi |
| parent_branch_id | UUID | NULL, FK | Chi cha → `branch.id` (FK, cùng module) |
| origin_person_id | UUID | NULL, FK | Người tổ của chi → `person.id` (FK, cùng module) |
| description | TEXT |  | Mô tả |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

### `person`

**Mục đích:** Thành viên (người).  **Người sở hữu:** TV2 Trí.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL, FK | Gia đình → `family.id` (FK, cùng module) |
| branch_id | UUID | NULL, FK | Chi họ → `branch.id` (FK, cùng module) |
| full_name | VARCHAR(150) | NOT NULL | Họ tên |
| gender | VARCHAR(10) | NOT NULL, CHECK | MALE, FEMALE, OTHER |
| birth_date | DATE | NULL | Người còn sống bắt buộc có (BR-GEN-17) |
| birth_year | SMALLINT | NULL | Chỉ cho người đã mất không rõ ngày sinh |
| birthplace | VARCHAR(255) |  |  |
| current_province | VARCHAR(100) |  | Tỉnh/thành nơi ở hiện tại (lọc FR-DIR-04, thống kê dashboard) |
| current_address | VARCHAR(255) |  | Địa chỉ nơi ở hiện tại |
| is_deceased | BOOLEAN | NOT NULL DEFAULT FALSE |  |
| death_date | DATE | NULL, CHECK (death_date >= birth_date) |  |
| burial_place | VARCHAR(255) | NULL | Nơi an táng |
| avatar_media_id | UUID | NULL | Ảnh đại diện, tham chiếu media → `media` (tham chiếu logic) |
| user_id | UUID | NULL, UNIQUE (WHERE user_id IS NOT NULL AND deleted_at IS NULL) | Tài khoản nối với thành viên → `users` (tham chiếu logic) |
| note | TEXT |  | Ghi chú |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

### `parent_child`

**Mục đích:** Quan hệ cha/mẹ - con.  **Người sở hữu:** TV2 Trí.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| parent_id | UUID | NOT NULL, FK | Cha hoặc mẹ → `person.id` (FK, cùng module) |
| child_id | UUID | NOT NULL, FK | Con → `person.id` (FK, cùng module) |
| parent_role | VARCHAR(10) | NOT NULL, CHECK | FATHER, MOTHER |
| kind | VARCHAR(12) | NOT NULL DEFAULT BIOLOGICAL, CHECK | BIOLOGICAL, ADOPTED, STEP |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** CHECK (parent_id <> child_id); UNIQUE (parent_id, child_id) WHERE deleted_at IS NULL; UNIQUE (child_id, parent_role) WHERE kind = 'BIOLOGICAL' AND deleted_at IS NULL.

### `marriage`

**Mục đích:** Hôn nhân.  **Người sở hữu:** TV2 Trí.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| person1_id | UUID | NOT NULL, FK | Người 1 → `person.id` (FK, cùng module) |
| person2_id | UUID | NOT NULL, FK | Người 2 → `person.id` (FK, cùng module) |
| status | VARCHAR(10) | NOT NULL, CHECK | MARRIED, DIVORCED, WIDOWED |
| start_date | DATE | NULL | Ngày kết hôn |
| end_date | DATE | NULL | Ngày ly hôn hoặc góa |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** CHECK (person1_id <> person2_id).

## Cộng đồng (community)

Người sở hữu: **TV3 PiLo257**

### `post`

**Mục đích:** Bài đăng (POST hoặc NEWS).  **Người sở hữu:** TV3 PiLo257.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL | Gia đình sở hữu → `family` (tham chiếu logic) |
| branch_id | UUID | NULL | NULL = cả gia đình → `branch` (tham chiếu logic) |
| content | TEXT | NOT NULL | Nội dung |
| type | VARCHAR(20) | NOT NULL, CHECK | POST, NEWS |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |
| hidden_at | TIMESTAMPTZ | NULL | Bị quản trị ẩn (kiểm duyệt), khác `deleted_at` |

### `comment`

**Mục đích:** Bình luận.  **Người sở hữu:** TV3 PiLo257.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| post_id | UUID | NOT NULL, FK | Bài đăng → `post.id` (FK, cùng module) |
| content | TEXT | NOT NULL | Nội dung |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |
| hidden_at | TIMESTAMPTZ | NULL | Bị quản trị ẩn (kiểm duyệt), khác `deleted_at` |

### `reaction`

**Mục đích:** Reaction.  **Người sở hữu:** TV3 PiLo257.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| post_id | UUID | NOT NULL, FK | Bài đăng → `post.id` (FK, cùng module) |
| type | VARCHAR(20) | NOT NULL, CHECK | LIKE, LOVE, HAHA, SAD |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** UNIQUE (post_id, created_by) WHERE deleted_at IS NULL.

### `media`

**Mục đích:** Ảnh và tệp tải lên (dùng chung cho cả hệ thống).  **Người sở hữu:** TV3 PiLo257.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| owner_type | VARCHAR(20) | NOT NULL, CHECK | POST, EVENT, HERITAGE, PERSON |
| owner_id | UUID | NOT NULL | Tài nguyên sở hữu (theo owner_type) → `post` (tham chiếu logic) |
| family_id | UUID | NOT NULL | Gia đình, dùng lọc quyền → `family` (tham chiếu logic) |
| file_size | BIGINT | NOT NULL | Byte |
| file_name | VARCHAR(255) | NOT NULL | Tên gốc đã làm sạch |
| storage_key | VARCHAR(255) | NOT NULL, UNIQUE | Khóa lưu trữ nội bộ |
| caption | VARCHAR(255) | NULL | Chú thích |
| file_type | VARCHAR(100) | NOT NULL | MIME |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

### `announcement`

**Mục đích:** Thông báo ghim.  **Người sở hữu:** TV3 PiLo257.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL | Gia đình → `family` (tham chiếu logic) |
| branch_id | UUID | NULL | NULL = cả gia đình → `branch` (tham chiếu logic) |
| is_pinned | BOOLEAN | NOT NULL DEFAULT FALSE | Ghim |
| title | VARCHAR(255) | NOT NULL |  |
| content | TEXT | NOT NULL |  |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |
| hidden_at | TIMESTAMPTZ | NULL | Bị quản trị ẩn (kiểm duyệt), khác `deleted_at` |

### `content_report`

**Mục đích:** Báo cáo nội dung vi phạm (BR-COM-12).  **Người sở hữu:** TV3 PiLo257.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL | Gia đình của nội dung → `family` (tham chiếu logic) |
| target_type | VARCHAR(20) | NOT NULL, CHECK | POST, COMMENT, EVENT, ANNOUNCEMENT |
| target_id | UUID | NOT NULL | Nội dung bị báo cáo, theo `target_type` (tham chiếu logic, không FK) |
| reporter_id | UUID | NOT NULL | Người báo cáo → `users` (tham chiếu logic) |
| reason | VARCHAR(255) | NOT NULL | Lý do |
| content_snippet | VARCHAR(200) | NOT NULL | Trích đoạn nội dung lúc báo cáo |
| status | VARCHAR(20) | NOT NULL, CHECK, DEFAULT 'PENDING' | PENDING, HIDDEN, DISMISSED |
| created_by | UUID | NOT NULL | Người tạo → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

Ràng buộc và chỉ mục: `UNIQUE (target_type, target_id, reporter_id) WHERE deleted_at IS NULL`; `(status, created_at)`.

### `notification`

**Mục đích:** Thông báo gửi từng người dùng.  **Người sở hữu:** TV3 PiLo257.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| user_id | UUID | NOT NULL | Người nhận → `users` (tham chiếu logic) |
| ref_type | VARCHAR(30) | NULL | EVENT, ANNOUNCEMENT |
| ref_id | UUID | NULL | Tài nguyên tham chiếu |
| type | VARCHAR(50) | NOT NULL, CHECK | EVENT_REMINDER_24H, EVENT_REMINDER_1H, ANNOUNCEMENT |
| title | VARCHAR(255) | NOT NULL |  |
| content | TEXT | NOT NULL |  |
| is_read | BOOLEAN | NOT NULL DEFAULT FALSE |  |
| created_by | UUID | NULL | Nguồn tạo (có thể NULL) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** UNIQUE (user_id, ref_id, type) WHERE deleted_at IS NULL AND ref_id IS NOT NULL. created_by cho phép NULL (thông báo do hệ thống tạo).

## Sự kiện (events)

Người sở hữu: **TV3 PiLo257**

### `event`

**Mục đích:** Sự kiện.  **Người sở hữu:** TV3 PiLo257.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL | Gia đình tổ chức → `family` (tham chiếu logic) |
| branch_id | UUID | NULL | NULL = cả gia đình → `branch` (tham chiếu logic) |
| title | VARCHAR(255) | NOT NULL | Tên |
| description | TEXT | NULL |  |
| start_at | TIMESTAMPTZ | NOT NULL | Bắt đầu |
| end_at | TIMESTAMPTZ | NULL, CHECK (end_at > start_at) | Kết thúc |
| location | VARCHAR(255) | NULL | Địa điểm |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |
| hidden_at | TIMESTAMPTZ | NULL | Bị quản trị ẩn (kiểm duyệt), khác `deleted_at` |

### `event_participant`

**Mục đích:** Người tham dự và RSVP.  **Người sở hữu:** TV3 PiLo257.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| event_id | UUID | NOT NULL, FK | Sự kiện → `event.id` (FK, cùng module) |
| user_id | UUID | NOT NULL | Người tham dự (tài khoản) → `users` (tham chiếu logic) |
| rsvp_status | VARCHAR(20) | NOT NULL, CHECK | GOING, NOT_GOING, MAYBE |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** UNIQUE (event_id, user_id) WHERE deleted_at IS NULL.

## Di sản, danh bạ (heritage, directory)

Người sở hữu: **TV4 Huy Quốc**

### `heritage_document`

**Mục đích:** Tài liệu lịch sử (kho lưu trữ số).  **Người sở hữu:** TV4 Huy Quốc.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL | Gia đình → `family` (tham chiếu logic) |
| title | VARCHAR(255) | NOT NULL |  |
| description | TEXT |  |  |
| category | VARCHAR(100) |  |  |
| media_id | UUID | NOT NULL | Tệp, tham chiếu media → `media` (tham chiếu logic) |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

### `family_story`

**Mục đích:** Câu chuyện gia đình.  **Người sở hữu:** TV4 Huy Quốc.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL | Gia đình → `family` (tham chiếu logic) |
| title | VARCHAR(255) | NOT NULL |  |
| content | TEXT | NOT NULL |  |
| related_person_id | UUID | NULL | Người liên quan → `person` (tham chiếu logic) |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

### `outstanding_member`

**Mục đích:** Người tiêu biểu.  **Người sở hữu:** TV4 Huy Quốc.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL | Gia đình → `family` (tham chiếu logic) |
| person_id | UUID | NOT NULL | Thành viên → `person` (tham chiếu logic) |
| achievement | TEXT | NOT NULL | Thành tích |
| display_order | INT | NOT NULL DEFAULT 0 | Thứ tự hiển thị |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** UNIQUE (family_id, person_id) WHERE deleted_at IS NULL.

### `profession_profile`

**Mục đích:** Hồ sơ nghề nghiệp (1 dòng mỗi người, từ 16 tuổi).  **Người sở hữu:** TV4 Huy Quốc.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL | Gia đình → `family` (tham chiếu logic) |
| person_id | UUID | NOT NULL | Thành viên → `person` (tham chiếu logic) |
| job_title | VARCHAR(150) | NOT NULL | Chức danh |
| company | VARCHAR(255) |  |  |
| work_location | VARCHAR(255) |  |  |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** UNIQUE (person_id) WHERE deleted_at IS NULL.

### `education_profile`

**Mục đích:** Hồ sơ học vấn (1 dòng mỗi người).  **Người sở hữu:** TV4 Huy Quốc.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL | Gia đình → `family` (tham chiếu logic) |
| person_id | UUID | NOT NULL | Thành viên → `person` (tham chiếu logic) |
| school | VARCHAR(255) | NOT NULL | Trường |
| degree | VARCHAR(100) |  | Bằng cấp |
| graduation_year | INT | CHECK (graduation_year >= 1900) | Năm tốt nghiệp |
| created_by | UUID | NOT NULL | Người tạo, tham chiếu users (logic) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật, tham chiếu users (logic) → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** UNIQUE (person_id) WHERE deleted_at IS NULL.

## AI

Người sở hữu: **TV5 Lê Nhựt**

### `ai_embedding_chunk`

**Mục đích:** Đoạn văn bản + vector (pgvector) để tìm kiếm ngữ nghĩa và RAG.  **Người sở hữu:** TV5 Lê Nhựt.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL, INDEX | Phạm vi gia đình → `family` (tham chiếu logic) |
| branch_id | UUID | NULL | Phạm vi chi của nguồn (`ai.md` BR-13); NULL là toàn gia đình → `branch.id` (tham chiếu logic, không FK) |
| source_type | VARCHAR(40) | NOT NULL, CHECK | `PERSON`, `POST`, `STORY`, `DOCUMENT`, `EVENT` |
| source_id | UUID | NOT NULL, INDEX | ID nguồn của module sở hữu, không FK → `person` hoặc `post` hoặc `family_story` hoặc `heritage_document` (tham chiếu logic); với `EVENT` → `event.id` |
| chunk_no | INTEGER | NOT NULL, CHECK (chunk_no >= 0) | Thứ tự chunk trong nguồn |
| content | TEXT | NOT NULL | Nội dung đã lọc dữ liệu nhạy cảm |
| embedding | VECTOR(768) | NOT NULL | gemini-embedding-001, 768 chiều (ADR; thử 1536 nếu tiếng Việt không đạt) |
| content_hash | VARCHAR(64) | NOT NULL | Phát hiện nội dung thay đổi |
| metadata | JSONB | NOT NULL DEFAULT '{}' | Metadata không nhạy cảm |
| created_by | UUID | NULL | Nguồn tạo (có thể NULL) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** UNIQUE (family_id, source_type, source_id, chunk_no) WHERE deleted_at IS NULL; chỉ mục (family_id, branch_id). Chỉ mục HNSW cosine trên embedding. Cần CREATE EXTENSION vector. created_by cho phép NULL (tác vụ nền).

### `ai_chat_session`

**Mục đích:** Phiên trò chuyện.  **Người sở hữu:** TV5 Lê Nhựt.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL, INDEX | Phạm vi gia đình → `family` (tham chiếu logic) |
| user_id | UUID | NOT NULL, INDEX | Chủ phiên, không FK liên module → `users` (tham chiếu logic) |
| title | VARCHAR(200) | NULL | Tiêu đề phiên |
| status | VARCHAR(20) | NOT NULL DEFAULT ACTIVE, CHECK | ACTIVE, ARCHIVED |
| last_message_at | TIMESTAMPTZ | NULL | Tin nhắn gần nhất |
| created_by | UUID | NULL | Nguồn tạo (có thể NULL) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** Chỉ mục (family_id, user_id, updated_at DESC) và (user_id, deleted_at).

### `ai_chat_message`

**Mục đích:** Tin nhắn trong phiên.  **Người sở hữu:** TV5 Lê Nhựt.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| session_id | UUID | NOT NULL, FK | Phiên (cùng module) → `ai_chat_session.id` (FK, cùng module) |
| role | VARCHAR(20) | NOT NULL, CHECK | USER, ASSISTANT, SYSTEM |
| content | TEXT | NOT NULL | Nội dung (theo chính sách lưu trữ và riêng tư) |
| sources | JSONB | NOT NULL DEFAULT '[]' | Nguồn đã dùng, không chứa nội dung nhạy cảm |
| model_name | VARCHAR(100) | NULL | Model LLM |
| input_tokens | INTEGER | NULL, CHECK (>= 0) | Token đầu vào |
| output_tokens | INTEGER | NULL, CHECK (>= 0) | Token đầu ra |
| latency_ms | INTEGER | NULL, CHECK (>= 0) | Độ trễ tổng |
| created_by | UUID | NULL | Nguồn tạo (có thể NULL) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** Chỉ mục (session_id, created_at).

### `ai_summary_cache`

**Mục đích:** Cache kết quả tóm tắt.  **Người sở hữu:** TV5 Lê Nhựt.  **Trạng thái:** Đầy đủ.

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Khóa chính |
| family_id | UUID | NOT NULL, INDEX | Phạm vi gia đình → `family` (tham chiếu logic) |
| source_type | VARCHAR(40) | NOT NULL | Loại nguồn |
| source_id | UUID | NOT NULL, INDEX | ID nguồn, không FK liên module → `post` hoặc `family_story` hoặc `heritage_document` hoặc `person` (tham chiếu logic) |
| source_version_hash | VARCHAR(64) | NOT NULL | Hash phiên bản nguồn |
| summary | TEXT | NOT NULL | Tóm tắt đã lọc |
| model_name | VARCHAR(100) | NOT NULL | Model tạo tóm tắt |
| expires_at | TIMESTAMPTZ | NOT NULL | Hạn cache |
| created_by | UUID | NULL | Nguồn tạo (có thể NULL) → `users` (tham chiếu logic) |
| updated_by | UUID | NULL | Người cập nhật → `users` (tham chiếu logic) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc bổ sung:** Chỉ mục (family_id, source_type, source_id, source_version_hash) và (expires_at); unique partial khi deleted_at IS NULL.
