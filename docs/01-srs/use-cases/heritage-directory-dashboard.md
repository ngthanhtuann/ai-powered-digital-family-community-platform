> **Người viết:** TV4 (Huy Quốc) | **Reviewer:** Nguyễn Thanh Tuấn | **Task Jira:** SCRUM-22
> **Trạng thái:** Đã merge vào `develop` (Sprint 1) | **Phiên bản:** 0.2 (đã sửa theo review của TV1)

# TÀI LIỆU ĐẶC TẢ MODULE: HERITAGE, DIRECTORY, DASHBOARD
**Nhánh Git:** docs/SCRUM-22-heritage-directory-dashboard
**Gói backend:** com.familyconnect.modules.heritage, com.familyconnect.modules.directory, com.familyconnect.modules.dashboard

## 1. YÊU CẦU CHỨC NĂNG (FR)
Theo tài liệu SRS mục 3, các yêu cầu chức năng thuộc phạm vi 3 module bao gồm:
*   **Family Directory (Danh bạ):**
    *   FR-DIR-01 + FR-DIR-04: Danh bạ thành viên và Tìm thành viên theo nghề nghiệp, nơi ở, thế hệ (Dùng chung endpoint).
    *   FR-DIR-02: Hồ sơ nghề nghiệp (profession_profile).
    *   FR-DIR-03: Hồ sơ học vấn (education_profile).
*   **Family Heritage (Di sản):**
    *   FR-HER-01: Quản lý tài liệu lịch sử (heritage_document).
    *   FR-HER-02: Đăng tải và đọc câu chuyện gia đình (family_story).
    *   FR-HER-03: Quản lý danh sách người tiêu biểu (outstanding_member).
    *   FR-HER-04: Thư viện ảnh (sử dụng chung bảng media của module community).
    *   FR-HER-05: Kho lưu trữ số.
*   **Dashboard & Reporting (Thống kê):**
    *   FR-DSH-01 đến FR-DSH-04: Thống kê gia đình, hoạt động cộng đồng, sự kiện, nhân khẩu.
    *   FR-DSH-05: Tạo báo cáo CSV (Lưu ý: Xuất PDF thuộc phạm vi Sprint 6-7 ở SCRUM-62, 95).

## 2. SƠ ĐỒ VÀ ĐẶC TẢ USE CASE

### 2.1 Use Case Diagram
![Use Case Diagram](../../images/heritage-directory-dashboard-usecase.drawio.png)

### 2.2 Danh sách Actor & Mối quan hệ
*   **Thành viên (MEMBER):** Người dùng đã đăng nhập và được Trưởng chi xác minh vào gia đình (sở hữu `family_id` hợp lệ).
*   **Trưởng chi (BRANCH_ADMIN):** Kế thừa toàn bộ quyền hạn của MEMBER, bổ sung quyền quản lý tài liệu lịch sử và người tiêu biểu, xem dashboard và xuất dữ liệu tổng hợp trong phạm vi chi họ của mình (hoặc toàn gia đình nếu `branch_id` rỗng).
*   **Quản trị hệ thống (SYSTEM_ADMIN):** Kế thừa toàn bộ quyền hạn của BRANCH_ADMIN (và MEMBER), có quyền quản lý và trích xuất dữ liệu trên toàn bộ gia đình.
*   **Phân quyền theo bảng "Ai được làm gì" của `srs.md`:** MEMBER **không** xem dashboard và không xuất báo cáo; MEMBER chỉ **xem** tài liệu lịch sử, được thêm câu chuyện và ảnh; chỉ BRANCH_ADMIN và SYSTEM_ADMIN thêm/sửa/xóa tài liệu lịch sử và quản lý người tiêu biểu.

### 2.3 Đặc tả chi tiết 8 Use Case

| Tên Use Case | UC-DIR-01: Tìm thành viên theo nghề nghiệp, nơi ở, thế hệ |
| :--- | :--- |
| **Mã UC / FR** | UC-DIR-01 / FR-DIR-01, FR-DIR-04 |
| **Actor** | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN |
| **Tiền điều kiện** | Người dùng đã xác thực, có token hợp lệ và được duyệt vào gia đình. |
| **Luồng chính** | 1. Người dùng truy cập Danh bạ, nhập các tiêu chí tìm kiếm tùy chọn (kết hợp Nghề nghiệp, Nơi ở, Thế hệ).<br>2. Hệ thống kiểm tra xác thực, lấy `family_id`.<br>3. Truy vấn danh sách thành viên thỏa mãn từ module genealogy qua interface.<br>4. Lọc thông tin theo độ tuổi và hiển thị kết quả phân trang. |
| **Luồng lỗi** | - Chưa đăng nhập/token hết hạn: 401 AUTH_002.<br>- Chưa xác minh: 403 PERM_002. |
| **Hậu điều kiện** | Người dùng xem được danh bạ theo chính sách quyền riêng tư. |
| **Quy tắc** | **BR-DIR-01:** Trẻ em dưới 16 tuổi bắt buộc ẩn khỏi kết quả tìm kiếm theo nghề nghiệp/nơi ở. Trong danh bạ chung, ẩn liên hệ, nơi làm việc, trường học; chỉ trả về họ tên, quan hệ, năm sinh. |

| Tên Use Case | UC-DIR-02: Cập nhật hồ sơ nghề nghiệp & học vấn |
| :--- | :--- |
| **Mã UC / FR** | UC-DIR-02 / FR-DIR-02, FR-DIR-03 |
| **Actor** | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN |
| **Tiền điều kiện** | Chỉ sửa hồ sơ của chính mình (`person.user_id` = người đăng nhập) hoặc cha mẹ sửa cho con dưới 16 tuổi (quan hệ `parent_child`). BRANCH_ADMIN sửa cho người trong chi (kể cả người chưa có tài khoản, theo BR-GEN-14). SYSTEM_ADMIN sửa cho người trong toàn gia đình. |
| **Luồng chính** | 1. Người dùng chọn cập nhật thông tin.<br>2. Hệ thống kiểm tra quyền sửa.<br>3. Validate dữ liệu.<br>4. Lưu/ghi đè bản ghi duy nhất của người dùng vào bảng. |
| **Luồng lỗi** | - Dữ liệu không hợp lệ: 400 VALID_001.<br>- Sửa hồ sơ người khác không có quyền: 403 PERM_001.<br>- Chưa xác minh gia đình: 403 PERM_002.<br>- Không tìm thấy thành viên: 404 NOT_FOUND_001. |
| **Hậu điều kiện** | Dữ liệu hồ sơ cá nhân được đồng bộ và lưu trữ. |
| **Quy tắc** | **BR-DIR-02:** Mỗi người chỉ có 1 dòng hồ sơ đang active. Năm tốt nghiệp không vượt quá năm hiện tại + 10. |

| Tên Use Case | UC-HER-01: Quản lý tài liệu lịch sử |
| :--- | :--- |
| **Mã UC / FR** | UC-HER-01 / FR-HER-01, FR-HER-05 |
| **Actor** | BRANCH_ADMIN, SYSTEM_ADMIN (thêm, sửa, xóa); MEMBER (chỉ xem danh sách và chi tiết) |
| **Tiền điều kiện** | File tải lên qua module Community (`POST /api/v1/media`, `ownerType = HERITAGE`), có `media_id` hợp lệ, thuộc cùng `family_id` của người dùng. Tệp tài liệu (`pdf`, tối đa 10 MB) và ảnh (`jpg`, `png`, `webp`, tối đa 5 MB) cùng lưu ở bảng `media` với `owner_type = HERITAGE`; việc cho phép `pdf` đã chốt 9/10/2026 (`conventions.md` mục 7.3, BR-COM-08). |
| **Luồng chính** | 1. Thêm mới: Nhập Tiêu đề, Mô tả, Danh mục và `media_id`. Hệ thống kiểm tra `media_id` tồn tại và `media.family_id` trùng `family_id` của người dùng, sau đó ghi bản ghi vào `heritage_document`.<br>2. Sửa/Xóa: Hệ thống kiểm tra vai trò BRANCH_ADMIN hoặc SYSTEM_ADMIN trong gia đình của người dùng, rồi cập nhật/xóa mềm.<br>3. Báo module AI cập nhật embedding qua interface. |
| **Luồng lỗi** | - MEMBER thêm/sửa/xóa, hoặc dùng `media_id` của gia đình khác: 403 PERM_001.<br>- Chưa xác minh: 403 PERM_002.<br>- Dữ liệu thiếu: 400 VALID_001.<br>- Không tìm thấy tài liệu: 404 NOT_FOUND_001. |
| **Hậu điều kiện** | Tài liệu xuất hiện/được cập nhật/bị xóa trong kho lưu trữ số. |
| **Quy tắc** | **BR-HER-01:** Phân loại theo category. Chỉ BRANCH_ADMIN và SYSTEM_ADMIN được thêm/sửa/xóa tài liệu (theo `srs.md`); MEMBER chỉ xem. Tài liệu thuộc cả gia đình, không gắn chi. |

| Tên Use Case | UC-HER-02: Quản lý câu chuyện gia đình |
| :--- | :--- |
| **Mã UC / FR** | UC-HER-02 / FR-HER-02 |
| **Actor** | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN |
| **Tiền điều kiện** | Đã đăng nhập và xác minh. |
| **Luồng chính** | 1. Thêm mới: Nhập Tiêu đề, Nội dung và (tùy chọn) đối tượng liên quan `related_person_id` (hệ thống kiểm tra qua interface genealogy: người này tồn tại và thuộc cùng `family_id`). Hệ thống lưu vào `family_story` và gán `created_by` là người đăng.<br>2. Sửa/Xóa: Hệ thống kiểm tra quyền (tác giả hoặc Admin) và thực hiện.<br>3. Báo module AI cập nhật embedding qua interface. |
| **Luồng lỗi** | - Cố sửa/xóa chuyện của người khác khi không có quyền: 403 PERM_001.<br>- Chưa xác minh: 403 PERM_002.<br>- Rỗng tiêu đề/nội dung: 400 VALID_001.<br>- Không tìm thấy câu chuyện: 404 NOT_FOUND_001. |
| **Hậu điều kiện** | Câu chuyện hiển thị/cập nhật/bị xóa trên bảng tin di sản. |
| **Quy tắc** | **BR-HER-02:** Tên tác giả hiển thị sẽ tra cứu qua interface bằng ID người đăng. |

| Tên Use Case | UC-HER-03: Thư viện ảnh dòng họ |
| :--- | :--- |
| **Mã UC / FR** | UC-HER-03 / FR-HER-04 |
| **Actor** | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN |
| **Tiền điều kiện** | Đã đăng nhập và xác minh. |
| **Luồng chính** | 1. Gửi yêu cầu GET đọc danh sách ảnh từ module community qua interface với điều kiện `owner_type = 'HERITAGE'` và `family_id`.<br>2. Trả kết quả ảnh phân trang.<br>*(Tải ảnh lên dùng `POST /api/v1/media` của community với `ownerType = HERITAGE`; MEMBER và BRANCH_ADMIN được tải theo `srs.md`. Tệp tài liệu `pdf` cũng dùng `media` với `owner_type = HERITAGE` nhưng thuộc UC-HER-01, không hiện ở thư viện ảnh.)* |
| **Luồng lỗi** | - Chưa xác minh: 403 PERM_002.<br>*(Ghi chú: Việc upload tệp bị quá dung lượng hoặc sai định dạng do module community xử lý)* |
| **Hậu điều kiện** | Người dùng xem được danh sách hình ảnh di sản. |
| **Quy tắc** | **BR-HER-03:** Không có bảng riêng, đọc trực tiếp từ `media` (`owner_type = HERITAGE`, cùng `family_id`). Thư viện ảnh chỉ gồm tệp ảnh (`jpg`, `png`, `webp`) **chưa** được `heritage_document` tham chiếu. Tệp tài liệu (`pdf`) và ảnh đã được `heritage_document` tham chiếu (bản scan tài liệu) thì **không** hiện trong thư viện ảnh, chỉ hiện ở UC-HER-01. |

| Tên Use Case | UC-HER-04: Quản lý người tiêu biểu |
| :--- | :--- |
| **Mã UC / FR** | UC-HER-04 / FR-HER-03 |
| **Actor** | BRANCH_ADMIN, SYSTEM_ADMIN |
| **Tiền điều kiện** | Có quyền quản trị tương ứng với phạm vi gia đình/chi họ (BR-GEN-14). |
| **Luồng chính** | 1. Chọn thành viên, nhập thành tích, thứ tự.<br>2. Kiểm tra qua interface genealogy: `person_id` tồn tại và thuộc cùng `family_id` (với BRANCH_ADMIN: thuộc chi của mình).<br>3. Ghi vào `outstanding_member`. |
| **Luồng lỗi** | - Member thông thường hoặc thao tác ngoài chi của mình: 403 PERM_001.<br>- Chưa xác minh: 403 PERM_002.<br>- Trùng người đã vinh danh: 409 HER_001.<br>- Dữ liệu không hợp lệ: 400 VALID_001.<br>- Không tìm thấy: 404 NOT_FOUND_001. |
| **Hậu điều kiện** | Danh sách người tiêu biểu được cập nhật. |
| **Quy tắc** | **BR-HER-04:** Chỉ quản trị viên thêm/sửa/xóa. Mỗi người chỉ xuất hiện 1 lần. |

| Tên Use Case | UC-DSH-01: Xem Dashboard tổng quan |
| :--- | :--- |
| **Mã UC / FR** | UC-DSH-01 / FR-DSH-01 đến FR-DSH-04 |
| **Actor** | BRANCH_ADMIN, SYSTEM_ADMIN |
| **Tiền điều kiện** | Đã xác minh `family_id`. BRANCH_ADMIN xem trong phạm vi chi họ của mình (hoặc toàn gia đình nếu `branch_id` rỗng). |
| **Luồng chính** | 1. Người dùng xem Dashboard.<br>2. Gọi interface đến genealogy, directory, community, events để tổng hợp số liệu.<br>3. Trả về JSON thống kê. |
| **Luồng lỗi** | - Chưa đăng nhập/token lỗi: 401 AUTH_002.<br>- Chưa xác minh: 403 PERM_002.<br>- MEMBER gọi, hoặc `branchId` ngoài phạm vi / khác gia đình: 403 PERM_001. |
| **Hậu điều kiện** | Biểu đồ Dashboard hiển thị trực quan các chỉ số hiện tại. |
| **Quy tắc** | **BR-DSH-01:** Chỉ BRANCH_ADMIN và SYSTEM_ADMIN xem dashboard (theo `srs.md`); MEMBER không xem. Chỉ đọc qua interface Java. Thống kê nghề nghiệp, nơi ở phải loại trừ trẻ dưới 16 tuổi. |

| Tên Use Case | UC-DSH-02: Xuất báo cáo CSV |
| :--- | :--- |
| **Mã UC / FR** | UC-DSH-02 / FR-DSH-05 |
| **Actor** | BRANCH_ADMIN, SYSTEM_ADMIN |
| **Tiền điều kiện** | Đã xác minh `family_id` và có quyền quản trị. BRANCH_ADMIN xuất trong phạm vi chi họ. |
| **Luồng chính** | 1. Quản trị viên nhấn xuất CSV.<br>2. Lấy dữ liệu danh bạ qua interface genealogy (họ tên, quan hệ, giới tính, thế hệ) và hồ sơ nghề nghiệp qua interface directory.<br>3. Ẩn thông tin liên hệ và địa chỉ chi tiết; với trẻ dưới 16 tuổi chỉ điền `id`, `fullName`, `relationship`, `birthYear`, các cột còn lại để trống.<br>4. Chèn dấu `'` trước các ô chứa `=`, `+`, `-`, `@` để chống Injection.<br>5. Trả file UTF-8 kèm BOM.<br>6. Ghi Audit Log hành động xuất dữ liệu. |
| **Luồng lỗi** | - Sai token: 401 AUTH_002.<br>- Không đủ quyền (MEMBER gọi): 403 PERM_001.<br>- Chưa xác minh: 403 PERM_002. |
| **Hậu điều kiện** | File CSV được tải về thiết bị. Hệ thống lưu vết thao tác (SCRUM-57). |
| **Quy tắc** | **BR-DSH-02:** Danh sách cột (camelCase): `id`, `fullName`, `birthYear`, `gender`, `relationship`, `generation`, `jobTitle`, `workLocation`. Với trẻ dưới 16 tuổi chỉ điền `id`, `fullName`, `relationship`, `birthYear` (theo mục "Trẻ em và người đã mất" của privacy.md). Audit log bắt buộc. |
---
## 3. DANH SÁCH API, LIÊN KẾT MODULE & CÂU HỎI MỞ

### 3.1 Danh sách API
Tiền tố `/api/v1`. Mọi API trả lỗi theo `conventions.md`; danh sách có `page`, `size`, `sort`.

| Method | URL | Use Case | Vai trò | Mã lỗi chính |
| :--- | :--- | :--- | :--- | :--- |
| GET | `/directory/members` | UC-DIR-01 | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_002 |
| GET | `/directory/members/{personId}/profession-profile` | UC-DIR-02 | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN | AUTH_002, PERM_001, PERM_002, NOT_FOUND_001 |
| PUT | `/directory/members/{personId}/profession-profile` | UC-DIR-02 | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_001, PERM_002, NOT_FOUND_001 |
| GET | `/directory/members/{personId}/education-profile` | UC-DIR-02 | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN | AUTH_002, PERM_001, PERM_002, NOT_FOUND_001 |
| PUT | `/directory/members/{personId}/education-profile` | UC-DIR-02 | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_001, PERM_002, NOT_FOUND_001 |
| GET | `/heritage/documents` | UC-HER-01 | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_002 |
| GET | `/heritage/documents/{id}` | UC-HER-01 | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN | AUTH_002, PERM_002, NOT_FOUND_001 |
| POST | `/heritage/documents` | UC-HER-01 | BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_001, PERM_002, NOT_FOUND_001 |
| PATCH, DELETE | `/heritage/documents/{id}` | UC-HER-01 | BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_001, PERM_002, NOT_FOUND_001 |
| GET | `/heritage/stories` | UC-HER-02 | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_002 |
| GET | `/heritage/stories/{id}` | UC-HER-02 | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN | AUTH_002, PERM_002, NOT_FOUND_001 |
| POST | `/heritage/stories` | UC-HER-02 | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_001, PERM_002, NOT_FOUND_001 |
| PATCH, DELETE | `/heritage/stories/{id}` | UC-HER-02 | MEMBER (của mình), BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_001, PERM_002, NOT_FOUND_001 |
| GET | `/heritage/photos` | UC-HER-03 | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_002 |
| GET | `/heritage/outstanding-members` | UC-HER-04 | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_002 |
| POST | `/heritage/outstanding-members` | UC-HER-04 | BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_001, PERM_002, NOT_FOUND_001, HER_001 |
| PATCH, DELETE | `/heritage/outstanding-members/{id}` | UC-HER-04 | BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_001, PERM_002, NOT_FOUND_001 |
| GET | `/dashboard/statistics` | UC-DSH-01 | BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_001, PERM_002 |
| GET | `/dashboard/reports/members` | UC-DSH-02 | BRANCH_ADMIN, SYSTEM_ADMIN | VALID_001, AUTH_002, PERM_001, PERM_002 |

API xóa trả `200` với `{ "success": true, "data": null }`, không dùng `204` (`conventions.md`, mục 1.3).

### 3.2 Liên kết module khác
*   **Genealogy:** Cung cấp nhân khẩu học qua `GenealogyInterface.getDemographics()`.
*   **Community:** Cung cấp hình ảnh qua `CommunityInterface.getHeritageMedia()`. Báo module này khi tải lên ảnh/tệp.
*   **Events:** Cung cấp sự kiện qua `EventInterface.getUpcomingEvents()`.
*   **Directory:** Cung cấp thống kê nghề nghiệp, nơi ở và hồ sơ nghề nghiệp cho dashboard qua `DirectoryInterface`.
*   **AI:** Báo cập nhật embedding qua `AiGateway`.

### 3.3 Bảng mã lỗi riêng (Module Errors)
| Mã lỗi | HTTP Status | Thông điệp |
| :--- | :--- | :--- |
| `HER_001` | 409 Conflict | Thành viên đã tồn tại trong danh sách tiêu biểu. |

### 3.4 Quyết định đã chốt và câu hỏi còn mở
**Đã chốt**
1. **Cách tính thế hệ:** theo `genealogy.md` mục 6.8 (đời 1 là người không có cha mẹ trong gia đình; người vào bằng hôn nhân lấy đời của vợ/chồng; lấy giá trị lớn nhất). Quy tắc lấy đời của vợ/chồng đã chốt 9/10/2026 (`genealogy.md` mục 11, câu 7). Dashboard và danh bạ lấy qua `GenealogyInterface.getDemographics()`, không tự viết truy vấn đệ quy riêng.
2. **Quyền của BRANCH_ADMIN với tài liệu chung:** có, theo `srs.md` ("Có (tất cả)"). Tài liệu lịch sử thuộc cả gia đình, không gắn chi.
3. **Xóa tài liệu (9/10/2026):** xóa mềm `heritage_document` thì **giữ** tệp trong kho và giữ bản ghi `media` để khôi phục được.

**Còn mở (quyết định ở SCRUM-31)**
1. **Dọn tệp mồ côi:** thời điểm và cách xóa vật lý các tệp không còn bản ghi nào dùng được quyết định cùng spike lưu trữ ảnh (SCRUM-31).

## 4. THIẾT KẾ CƠ SỞ DỮ LIỆU
*Quy ước chung: Khóa chính UUID. Các bảng có `id`, `created_at`, `updated_at`, `deleted_at`, `created_by` (NOT NULL), `updated_by` (NULL cho đến lần sửa đầu tiên, giống các module khác). Khóa ngoại chỉ dùng giữa các bảng trong cùng module (`architecture.md` mục 7, quyết định 9). Năm bảng dưới đây chỉ tham chiếu bảng của module khác (`users` của auth; `family`, `person` của genealogy; `media` của community) nên **không khai báo FK**: các cột này lưu UUID thường, có index, và service kiểm tra tồn tại, quyền và `family_id` qua interface Java của module tương ứng.*

```sql
-- 1. Bảng tài liệu lịch sử
-- Mục đích: Lưu trữ thông tin tham chiếu đến các tệp/hình ảnh tài liệu (gia phả, sắc phong) của dòng họ.
CREATE TABLE heritage_document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,                   -- tham chiếu family của module genealogy, kiểm tra ở service
    title VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    media_id UUID NOT NULL,                    -- tham chiếu media của module community, kiểm tra ở service
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE NULL,
    created_by UUID NOT NULL,                  -- tham chiếu users của module auth, kiểm tra ở service
    updated_by UUID NULL                       -- tham chiếu users của module auth, kiểm tra ở service
);
CREATE INDEX idx_heritage_doc_media ON heritage_document (media_id);
CREATE INDEX idx_heritage_doc_family ON heritage_document (family_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_heritage_doc_creator ON heritage_document (created_by);

-- 2. Bảng câu chuyện gia đình
-- Mục đích: Lưu trữ các bài viết hồi ký, câu chuyện tiểu sử liên quan đến dòng họ hoặc cá nhân.
CREATE TABLE family_story (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,                   -- tham chiếu family của module genealogy, kiểm tra ở service
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    related_person_id UUID NULL,               -- tham chiếu person của module genealogy, kiểm tra ở service
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE NULL,
    created_by UUID NOT NULL,                  -- tham chiếu users của module auth, kiểm tra ở service
    updated_by UUID NULL                       -- tham chiếu users của module auth, kiểm tra ở service
);
CREATE INDEX idx_family_story_family ON family_story (family_id, created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_family_story_related ON family_story (related_person_id) WHERE deleted_at IS NULL AND related_person_id IS NOT NULL;
CREATE INDEX idx_family_story_creator ON family_story (created_by);

-- 3. Bảng người tiêu biểu
-- Mục đích: Vinh danh những cá nhân xuất sắc trong dòng họ, quy định thứ tự hiển thị trên danh sách.
CREATE TABLE outstanding_member (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,                   -- tham chiếu family của module genealogy, kiểm tra ở service
    person_id UUID NOT NULL,                   -- tham chiếu person của module genealogy, kiểm tra ở service
    achievement TEXT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE NULL,
    created_by UUID NOT NULL,                  -- tham chiếu users của module auth, kiểm tra ở service
    updated_by UUID NULL                       -- tham chiếu users của module auth, kiểm tra ở service
);
CREATE UNIQUE INDEX uq_outstanding_person ON outstanding_member (family_id, person_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_outstanding_display ON outstanding_member (family_id, display_order);
CREATE INDEX idx_outstanding_person ON outstanding_member (person_id);
CREATE INDEX idx_outstanding_creator ON outstanding_member (created_by);

-- 4. Bảng hồ sơ nghề nghiệp
-- Mục đích: Lưu trữ 1 dòng hồ sơ công việc/công ty hiện tại của một cá nhân từ 16 tuổi trở lên.
CREATE TABLE profession_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,                   -- tham chiếu family của module genealogy, kiểm tra ở service
    person_id UUID NOT NULL,                   -- tham chiếu person của module genealogy, kiểm tra ở service
    job_title VARCHAR(150) NOT NULL,
    company VARCHAR(255),
    work_location VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE NULL,
    created_by UUID NOT NULL,                  -- tham chiếu users của module auth, kiểm tra ở service
    updated_by UUID NULL                       -- tham chiếu users của module auth, kiểm tra ở service
);
CREATE UNIQUE INDEX uq_prof_person ON profession_profile (person_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_prof_family ON profession_profile (family_id);
CREATE INDEX idx_prof_creator ON profession_profile (created_by);

-- 5. Bảng hồ sơ học vấn
-- Mục đích: Lưu trữ 1 dòng bằng cấp/trường học cao nhất hoặc hiện tại của một cá nhân.
CREATE TABLE education_profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,                   -- tham chiếu family của module genealogy, kiểm tra ở service
    person_id UUID NOT NULL,                   -- tham chiếu person của module genealogy, kiểm tra ở service
    school VARCHAR(255) NOT NULL,
    degree VARCHAR(100),
    graduation_year INT CHECK (graduation_year >= 1900),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE NULL,
    created_by UUID NOT NULL,                  -- tham chiếu users của module auth, kiểm tra ở service
    updated_by UUID NULL                       -- tham chiếu users của module auth, kiểm tra ở service
);
CREATE UNIQUE INDEX uq_edu_person ON education_profile (person_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_edu_family ON education_profile (family_id);
CREATE INDEX idx_edu_creator ON education_profile (created_by);
```
## 5. CHỈ SỐ DASHBOARD & CÂU TRUY VẤN SQL THAM KHẢO
> Các câu SQL dưới đây mô tả logic. Khi triển khai, mỗi module chỉ truy vấn bảng của mình; dữ liệu của module khác lấy qua interface Java (không join chéo module). BRANCH_ADMIN chỉ xem trong phạm vi chi (thêm điều kiện `branch_id` khi có `branchId`).

| Chỉ số | FR | Nguồn dữ liệu (Interface) | Vai trò xem được |
| :--- | :--- | :--- | :--- |
| Tổng thành viên | FR-DSH-01 | `GenealogyInterface.getTotalMembers()` | BRANCH_ADMIN, SYSTEM_ADMIN |
| Theo thế hệ | FR-DSH-04 | `GenealogyInterface.getDemographics()` | BRANCH_ADMIN, SYSTEM_ADMIN |
| Theo giới tính | FR-DSH-04 | `GenealogyInterface.getDemographics()` | BRANCH_ADMIN, SYSTEM_ADMIN |
| Nghề nghiệp | FR-DSH-04 | `DirectoryInterface.getJobStats()` | BRANCH_ADMIN, SYSTEM_ADMIN |
| Nơi ở | FR-DSH-04 | `DirectoryInterface.getLocationStats()` | BRANCH_ADMIN, SYSTEM_ADMIN |
| Bài đăng theo tuần | FR-DSH-02 | `CommunityInterface.getWeeklyPosts()` | BRANCH_ADMIN, SYSTEM_ADMIN |
| Sự kiện sắp tới | FR-DSH-03 | `EventInterface.getUpcomingEvents()` | BRANCH_ADMIN, SYSTEM_ADMIN |
### 5.1 Tổng thành viên (bao gồm cả người đã mất)
```sql
SELECT COUNT(*) AS total_members
FROM person
WHERE family_id = :familyId AND deleted_at IS NULL;
```
### 5.2 Thống kê theo thế hệ
Lấy từ `GenealogyInterface.getDemographics()`; cách tính thế hệ xem `genealogy.md` mục 6.8 (không lặp lại truy vấn ở đây để hai tài liệu không lệch nhau).

### 5.3 Thống kê giới tính
```sql
SELECT gender, COUNT(*) AS count
FROM person
WHERE family_id = :familyId AND deleted_at IS NULL
GROUP BY gender;
```
### 5.4 Thống kê nghề nghiệp (loại trừ trẻ dưới 16 tuổi và người đã mất)
> Câu dưới chỉ mô tả điều kiện lọc. Khi triển khai không join `profession_profile` với `person` (khác module): module directory lấy danh sách `person_id` hợp lệ (còn sống, từ 16 tuổi, đúng `family_id` và `branchId`) từ `GenealogyInterface`, rồi đếm trên `profession_profile` với `person_id = ANY(:personIds)`.

```sql
SELECT pf.job_title, COUNT(*) AS count
FROM profession_profile pf
JOIN person p ON p.id = pf.person_id
WHERE pf.family_id = :familyId 
  AND pf.deleted_at IS NULL 
  AND p.deleted_at IS NULL
  AND p.is_deceased = FALSE
  AND p.birth_date <= CURRENT_DATE - INTERVAL '16 years'
  AND (:branchId IS NULL OR p.branch_id = :branchId)
GROUP BY pf.job_title
ORDER BY count DESC;
```
### 5.5 Thống kê nơi ở (loại trừ trẻ dưới 16 tuổi và người đã mất)
```sql
SELECT p.current_province AS location, COUNT(*) AS count
FROM person p
WHERE p.family_id = :familyId 
  AND p.deleted_at IS NULL 
  AND p.current_province IS NOT NULL
  AND p.is_deceased = FALSE
  AND p.birth_date <= CURRENT_DATE - INTERVAL '16 years'
  AND (:branchId IS NULL OR p.branch_id = :branchId)
GROUP BY p.current_province
ORDER BY count DESC;
```
### 5.6 Bài đăng 8 tuần gần nhất (xếp tuần cũ đến mới)
```sql
WITH date_series AS (
    SELECT generate_series(
        DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh') - INTERVAL '7 weeks',
        DATE_TRUNC('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Ho_Chi_Minh'),
        '1 week'::interval
    ) AS week_start
)
SELECT ds.week_start, COUNT(p.id) AS post_count
FROM date_series ds
LEFT JOIN post p ON DATE_TRUNC('week', p.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh') = ds.week_start
    AND p.family_id = :familyId AND p.deleted_at IS NULL
GROUP BY ds.week_start
ORDER BY ds.week_start ASC;
```
### 5.7 5 sự kiện sắp tới
```sql
SELECT id, title, start_at
FROM event
WHERE family_id = :familyId 
  AND deleted_at IS NULL 
  AND start_at >= CURRENT_TIMESTAMP
ORDER BY start_at ASC
LIMIT 5;

```
