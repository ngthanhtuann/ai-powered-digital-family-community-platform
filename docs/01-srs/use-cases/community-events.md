# Use case: Community và Events

> **Người viết:** TV3 (PiLo257) | **Reviewer:** TV1 (Tuấn Nguyễn Thanh) | **Task Jira:** SCRUM-20 | **Hạn nộp review:** Thứ Sáu 9/10
> **Trạng thái:** Đã merge vào `develop` (Sprint 1)

## 1. Phạm vi và Actor

Module Community và Events cung cấp các chức năng phục vụ việc chia sẻ nội dung, tương tác trong gia đình và tổ chức các sự kiện gia đình.

### Actor

| Actor | Vai trò |
|---|---|
| Thành viên (MEMBER) | Tạo bài đăng thường, bình luận, reaction, chia sẻ ảnh, RSVP và tham gia sự kiện sau khi xác minh |
| Trưởng chi (BRANCH_ADMIN) | Quản lý nội dung trong phạm vi chi (gồm gỡ bài đăng, bình luận, sự kiện thuộc chi mình quản lý); đăng tin tức gia đình và thông báo |
| Quản trị hệ thống (SYSTEM_ADMIN) | Kiểm duyệt nội dung trên toàn hệ thống (FR-ADM-02) và xóa nội dung vi phạm |
| Hệ thống | Tạo thông báo và xử lý nhắc nhở sự kiện theo cấu hình đã được nhóm chốt |

> **Theo `srs.md` Phụ lục A:** `BRANCH_ADMIN` được gỡ bài đăng, bình luận, sự kiện của người khác trong chi mình quản lý (kèm các chi con, theo BR-GEN-14); kiểm duyệt nội dung toàn hệ thống (FR-ADM-02, module admin) chỉ `SYSTEM_ADMIN` thực hiện.

### Quy tắc truy cập

- Người dùng phải đăng nhập để sử dụng các chức năng dành cho thành viên.
- Chỉ thành viên đã được xác minh mới được truy cập các nội dung yêu cầu quyền thành viên.
- Quyền tạo, sửa và xóa nội dung được kiểm tra theo vai trò và quyền của người dùng.
- Người dùng chỉ được sửa hoặc xóa nội dung do mình tạo, hoặc nội dung mà vai trò của mình được phép quản lý.
- Mọi truy vấn phải giới hạn theo `family_id` của người đăng nhập; không tin `family_id` do client gửi; riêng `SYSTEM_ADMIN` (không thuộc gia đình nào) truyền `familyId` ở API danh sách, tạo mới và thống kê (`conventions.md` mục 6.2). Người dùng thuộc gia đình A gọi dữ liệu gia đình B phải bị từ chối (chống IDOR).
- `branch_id = NULL` biểu thị phạm vi toàn gia đình; có `branch_id` thì nội dung thuộc phạm vi chi. Nội dung có `branch_id` của chi X hiển thị cho thành viên thuộc chi X và các chi con của X (đi theo `branch.parent_branch_id`, cùng chiều với phạm vi quản lý của `BRANCH_ADMIN` ở BR-GEN-14); thành viên của chi cha hoặc chi khác nhánh không thấy. `BRANCH_ADMIN` quản lý cả gia đình và `SYSTEM_ADMIN` thấy tất cả. (Đã chốt 9/10/2026.)

### Phân trang

Các API trả về danh sách sử dụng phân trang theo `page` và `size`.

Ví dụ:

`?page=0&size=20&sort=createdAt,desc`

Thông tin phân trang được trả về trong trường `meta` theo quy ước API chung của FamilyConnect.

## 2. Use Case Diagram

![Community và Events Use Case Diagram](../../images/community-events-usecase.drawio.png)

File nguồn có thể chỉnh sửa: [`community-events-usecase.drawio`](../../images/community-events-usecase.drawio).

Sơ đồ thể hiện 4 actor theo hệ thống: `MEMBER`, `BRANCH_ADMIN`, `SYSTEM_ADMIN` và `Hệ thống`; các quyền sở hữu sự kiện là điều kiện sở hữu tài nguyên, không phải một role riêng.

## 3. Danh sách use case

| Mã UC | Tên | Actor | Mã FR | Ưu tiên |
|---|---|---|---|---|
| UC-COM-01 | Tạo, sửa, xóa bài đăng | Thành viên | FR-COM-01 | Must |
| UC-COM-02 | Bình luận | Thành viên | FR-COM-02 | Must |
| UC-COM-03 | Thả reaction | Thành viên | FR-COM-02 | Must |
| UC-COM-04 | Chia sẻ tin tức gia đình | Trưởng chi, Quản trị hệ thống | FR-COM-03 | Must |
| UC-COM-05 | Tải và chia sẻ ảnh | Thành viên | FR-COM-04 | Must |
| UC-COM-06 | Đăng thông báo (announcement) | Trưởng chi, Quản trị hệ thống | FR-COM-05 | Must |
| UC-EVT-01 | Tạo sự kiện | Thành viên, Trưởng chi, Quản trị hệ thống | FR-EVT-01 | Must |
| UC-EVT-02 | RSVP (Tham dự / Không / Có thể) | Thành viên | FR-EVT-02 | Must |
| UC-EVT-03 | Xem và quản lý người tham dự | Thành viên (xem); MEMBER (người tạo sự kiện), Trưởng chi, Quản trị hệ thống (thêm, gỡ) | FR-EVT-03 | Must |
| UC-EVT-04 | Thư viện ảnh sự kiện | Thành viên | FR-EVT-04 | Must |
| UC-EVT-05 | Nhắc nhở sự kiện | Hệ thống | FR-EVT-05 | Must |

## 4. Đặc tả từng use case

### UC-COM-01: Tạo, sửa, xóa bài đăng

| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-COM-01 |
| Actor | MEMBER; BRANCH_ADMIN (quản lý nội dung trong phạm vi chi); SYSTEM_ADMIN (kiểm duyệt toàn hệ thống) |
| Mô tả ngắn | Thành viên tạo bài đăng thường; người tạo có thể sửa bài của mình và các vai trò quản trị chỉ xóa trong phạm vi quyền được quy định. |
| Tiền điều kiện | Người dùng đã đăng nhập và là thành viên đã được xác minh. |
| Luồng chính | 1. Thành viên chọn chức năng tạo bài đăng.<br>2. Thành viên nhập nội dung và gửi yêu cầu.<br>3. Hệ thống kiểm tra dữ liệu, family_id và phạm vi branch_id.<br>4. Hệ thống lưu bài đăng và trả dữ liệu.<br>5. Khi cần, người tạo sửa bài của mình bằng PATCH; người tạo, BRANCH_ADMIN trong phạm vi chi hoặc SYSTEM_ADMIN có thể xóa mềm bằng DELETE. |
| Luồng thay thế / ngoại lệ | 2a. Nội dung không hợp lệ, hệ thống trả lỗi.<br>3a. Thành viên chưa xác minh nhận 403 PERM_002; người dùng ngoài gia đình/phạm vi chi bị từ chối.<br>5a. Người không phải người tạo sửa bài hoặc không có quyền xóa nhận 403. |
| Hậu điều kiện | Bài đăng được tạo và có thể hiển thị cho các thành viên được phép xem. |
| Quy tắc nghiệp vụ | BR-COM-05 |

### UC-COM-02: Bình luận

| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-COM-02 |
| Actor | Thành viên |
| Mô tả ngắn | Thành viên thêm bình luận vào bài đăng mà mình được phép xem. |
| Tiền điều kiện | Người dùng đã đăng nhập và đã được xác minh thành viên. Bài đăng tồn tại và người dùng được phép xem. |
| Luồng chính | 1. Thành viên mở bài đăng mình được phép xem và xem danh sách bình luận.<br>2. Thành viên nhập bình luận rồi gửi.<br>3. Hệ thống kiểm tra quyền, dữ liệu và giới hạn độ dài đã được thống nhất.<br>4. Hệ thống lưu và hiển thị bình luận.<br>5. Người tạo bình luận có thể sửa hoặc xóa bình luận của mình; BRANCH_ADMIN (trong phạm vi chi mình quản lý) và SYSTEM_ADMIN (kiểm duyệt) có thể xóa bình luận vi phạm. |
| Luồng thay thế / ngoại lệ | 2a. Nội dung bình luận không hợp lệ hoặc vượt giới hạn được cấu hình, hệ thống trả lỗi.<br>3a. Người dùng không có quyền xem bài đăng hoặc chưa được xác minh (PERM_002), hệ thống từ chối yêu cầu.<br>5a. Người dùng không phải chủ bình luận không thể sửa/xóa bình luận đó, trừ quyền quản lý nội dung trong chi của BRANCH_ADMIN và quyền kiểm duyệt của SYSTEM_ADMIN đã nêu. |
| Hậu điều kiện | Bình luận được lưu vào bài đăng. |
| Quy tắc nghiệp vụ | BR-COM-06 |

### UC-COM-03: Thả reaction

| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-COM-02 |
| Actor | Thành viên |
| Mô tả ngắn | Thành viên thêm hoặc thay đổi reaction trên bài đăng được phép xem. |
| Tiền điều kiện | Người dùng đã đăng nhập, đã được xác minh và có quyền xem bài đăng. |
| Luồng chính | 1. Thành viên mở bài đăng mình được phép xem.<br>2. Thành viên chọn một reaction trong `LIKE`, `LOVE`, `HAHA`, `SAD`.<br>3. Hệ thống kiểm tra quyền và kiểm tra reaction đang hoạt động của thành viên trên bài đó.<br>4. Hệ thống tạo hoặc cập nhật reaction; mỗi thành viên chỉ có một reaction đang hoạt động trên một bài. |
| Luồng thay thế / ngoại lệ | 2a. Thành viên bỏ reaction hiện tại, hệ thống xóa reaction tương ứng.<br>3a. Người dùng không có quyền xem bài đăng, hệ thống từ chối yêu cầu. |
| Hậu điều kiện | Reaction của thành viên được cập nhật trên bài đăng. |
| Quy tắc nghiệp vụ | BR-COM-07 |

### UC-COM-04: Chia sẻ tin tức gia đình

| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-COM-03 |
| Actor | BRANCH_ADMIN; SYSTEM_ADMIN |
| Mô tả ngắn | Trưởng chi hoặc quản trị hệ thống đăng tin tức gia đình theo quyền được cấp. |
| Tiền điều kiện | Người dùng đã đăng nhập và đã được xác minh thành viên. |
| Luồng chính | 1. Trưởng chi hoặc quản trị hệ thống chọn chức năng đăng tin tức gia đình.<br>2. Người dùng nhập nội dung tin tức.<br>3. Hệ thống kiểm tra dữ liệu và quyền truy cập.<br>4. Hệ thống lưu tin tức và hiển thị cho thành viên được phép xem. |
| Luồng thay thế / ngoại lệ | 2a. Nội dung không hợp lệ, hệ thống yêu cầu nhập lại.<br>3a. Người dùng không có quyền, hệ thống từ chối yêu cầu. |
| Hậu điều kiện | Tin tức gia đình được lưu và hiển thị theo quyền truy cập. |
| Quy tắc nghiệp vụ | BR-COM-04 |

### UC-COM-05: Tải và chia sẻ ảnh

| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-COM-04 |
| Actor | Thành viên |
| Mô tả ngắn | Thành viên tải ảnh lên và chia sẻ ảnh trong phạm vi gia đình. |
| Tiền điều kiện | Người dùng đã đăng nhập và đã được xác minh thành viên. |
| Luồng chính | 1. Thành viên chọn chức năng tải ảnh.<br>2. Thành viên chọn tệp ảnh và gửi yêu cầu.<br>3. Hệ thống kiểm tra tệp và quyền truy cập.<br>4. Hệ thống lưu thông tin ảnh và hiển thị ảnh. |
| Luồng thay thế / ngoại lệ | 2a. Tệp không phải `jpg`, `png`, `webp` hoặc vượt quá 5 MB, hệ thống trả lỗi `FILE_002` hoặc `FILE_001`.<br>3a. Người dùng không có quyền, hệ thống từ chối yêu cầu. |
| Hậu điều kiện | Ảnh được lưu và có thể được xem bởi các thành viên được phép. |
| Quy tắc nghiệp vụ | BR-COM-08 |

### UC-COM-06: Đăng thông báo (announcement)

| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-COM-05 |
| Actor | BRANCH_ADMIN; SYSTEM_ADMIN |
| Mô tả ngắn | Trưởng chi (hoặc quản trị hệ thống) đăng thông báo cho các thành viên trong phạm vi được quản lý. |
| Tiền điều kiện | Người dùng đã đăng nhập và có quyền Trưởng chi trở lên. |
| Luồng chính | 1. Trưởng chi chọn chức năng tạo thông báo.<br>2. Trưởng chi nhập nội dung thông báo.<br>3. Hệ thống kiểm tra quyền và dữ liệu.<br>4. Hệ thống lưu thông báo và hiển thị cho đối tượng được phép xem. |
| Luồng thay thế / ngoại lệ | 2a. Nội dung không hợp lệ, hệ thống yêu cầu nhập lại.<br>3a. Người dùng không có quyền Trưởng chi trở lên, hệ thống từ chối yêu cầu (403 `PERM_001`). |
| Hậu điều kiện | Thông báo được lưu và hiển thị theo phạm vi được phép. |
| Quy tắc nghiệp vụ | BR-COM-09 |

### UC-EVT-01: Tạo sự kiện

| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-EVT-01 |
| Actor | MEMBER (đã xác minh); BRANCH_ADMIN; SYSTEM_ADMIN |
| Mô tả ngắn | Người dùng có quyền tạo sự kiện nhập thông tin và tạo một sự kiện gia đình. |
| Tiền điều kiện | Người dùng đã đăng nhập và có quyền tạo sự kiện. |
| Luồng chính | 1. Người dùng chọn chức năng tạo sự kiện.<br>2. Người dùng nhập thông tin sự kiện.<br>3. Hệ thống kiểm tra dữ liệu và quyền.<br>4. Hệ thống lưu sự kiện và hiển thị thông tin sự kiện. |
| Luồng thay thế / ngoại lệ | 2a. Thông tin sự kiện không hợp lệ, hệ thống yêu cầu nhập lại.<br>3a. Người dùng không có quyền, hệ thống từ chối yêu cầu. |
| Hậu điều kiện | Sự kiện được tạo và có thể được thành viên được phép xem. |
| Quy tắc nghiệp vụ | BR-EVT-01 |

### UC-EVT-02: RSVP

| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-EVT-02 |
| Actor | Thành viên |
| Mô tả ngắn | Thành viên chọn trạng thái tham dự một sự kiện. |
| Tiền điều kiện | Người dùng đã đăng nhập, đã được xác minh và có quyền xem sự kiện. |
| Luồng chính | 1. Thành viên mở thông tin sự kiện; hệ thống hiển thị trạng thái RSVP hiện tại của thành viên (`myRsvpStatus`) và số người theo từng trạng thái (`rsvpCounts`).<br>2. Thành viên chọn trạng thái Tham dự, Không tham dự hoặc Có thể.<br>3. Hệ thống kiểm tra quyền và trạng thái sự kiện.<br>4. Hệ thống lưu trạng thái RSVP. |
| Luồng thay thế / ngoại lệ | 2a. Thành viên thay đổi lựa chọn, hệ thống cập nhật trạng thái mới.<br>3a. Sự kiện đã bắt đầu nên không thể RSVP; hệ thống trả HTTP 409 `EVT_001`.<br>3b. Sự kiện đã bị xóa hoặc không tồn tại; hệ thống trả HTTP 404 `NOT_FOUND_001`. |
| Hậu điều kiện | Trạng thái RSVP của thành viên được lưu. |
| Quy tắc nghiệp vụ | BR-EVT-02 |

### UC-EVT-03: Xem và quản lý người tham dự

| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-EVT-03 |
| Actor | Xem danh sách: MEMBER đã xác minh có quyền xem sự kiện. Thêm, gỡ: MEMBER (người tạo sự kiện); BRANCH_ADMIN; SYSTEM_ADMIN |
| Mô tả ngắn | Thành viên xem danh sách người tham dự của sự kiện mình được xem (`srs.md` Phụ lục A: "Phản hồi RSVP, xem người tham dự"); người tạo sự kiện và vai trò quản trị thêm, gỡ người tham dự. |
| Tiền điều kiện | Sự kiện tồn tại; người dùng đã xác minh và có quyền xem sự kiện. Để thêm, gỡ: là người tạo sự kiện, BRANCH_ADMIN trong phạm vi quản lý hoặc SYSTEM_ADMIN. |
| Luồng chính | 1. Thành viên có quyền xem sự kiện mở sự kiện.<br>2. Hệ thống hiển thị danh sách người tham dự và trạng thái RSVP; người dùng có thể lọc theo trạng thái.<br>3. Người tạo sự kiện, BRANCH_ADMIN trong phạm vi quản lý hoặc SYSTEM_ADMIN thêm người tham dự (chọn người dùng cùng gia đình) hoặc gỡ người tham dự.<br>4. Hệ thống kiểm tra quyền và cập nhật danh sách. |
| Luồng thay thế / ngoại lệ | 2a. Không có người tham dự, hệ thống hiển thị danh sách trống.<br>3a. Người không phải người tạo sự kiện, BRANCH_ADMIN trong phạm vi hoặc SYSTEM_ADMIN thực hiện thêm, gỡ thì bị từ chối (403 `PERM_001`); việc xem danh sách không bị từ chối.<br>3b. Người được thêm không thuộc cùng gia đình hoặc không tồn tại: hệ thống trả 404 `NOT_FOUND_001`; đã có trong danh sách: 409 `CONFLICT_001`. |
| Hậu điều kiện | Danh sách người tham dự được cập nhật theo thao tác hợp lệ. |
| Quy tắc nghiệp vụ | BR-EVT-03 |

### UC-EVT-04: Thư viện ảnh sự kiện

| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-EVT-04 |
| Actor | Thành viên |
| Mô tả ngắn | Thành viên xem và chia sẻ ảnh thuộc một sự kiện được phép truy cập. |
| Tiền điều kiện | Người dùng đã đăng nhập, đã được xác minh và có quyền xem sự kiện. |
| Luồng chính | 1. Thành viên mở sự kiện.<br>2. Thành viên chọn thư viện ảnh.<br>3. Hệ thống kiểm tra quyền truy cập.<br>4. Hệ thống hiển thị các ảnh thuộc sự kiện. |
| Luồng thay thế / ngoại lệ | 2a. Sự kiện chưa có ảnh, hệ thống hiển thị thư viện trống.<br>3a. Người dùng có quyền có thể tải ảnh lên; tệp không đúng định dạng hoặc vượt kích thước bị từ chối.<br>4a. Người dùng không có quyền, hệ thống từ chối truy cập. |
| Hậu điều kiện | Thành viên xem được các ảnh sự kiện mà mình có quyền truy cập. |
| Quy tắc nghiệp vụ | BR-EVT-04 |

### UC-EVT-05: Nhắc nhở sự kiện

| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-EVT-05 |
| Actor | Hệ thống |
| Mô tả ngắn | Hệ thống gửi nhắc nhở cho người dùng liên quan đến sự kiện. |
| Tiền điều kiện | Sự kiện tồn tại và có thông tin thời gian phù hợp để gửi nhắc nhở. |
| Luồng chính | 1. Hệ thống kiểm tra các sự kiện sắp diễn ra.<br>2. Hệ thống xác định người dùng cần nhận nhắc nhở.<br>3. Hệ thống tạo và gửi thông báo nhắc nhở.<br>4. Hệ thống lưu thông tin thông báo. |
| Luồng thay thế / ngoại lệ | 2a. Không có người dùng phù hợp, hệ thống không gửi thông báo. |
| Hậu điều kiện | Người dùng liên quan nhận được nhắc nhở hoặc hệ thống ghi nhận trạng thái gửi. |
| Quy tắc nghiệp vụ | BR-EVT-05 |

## 5. Quy tắc nghiệp vụ

| Mã | Quy tắc |
|---|---|
| BR-COM-01 | Chỉ thành viên đã đăng nhập và được xác minh mới được gọi các API yêu cầu quyền thành viên; người chưa xác minh nhận HTTP 403 với mã `PERM_002`. |
| BR-COM-02 | Mọi truy vấn dữ liệu Community phải giới hạn theo `family_id` lấy từ ngữ cảnh người dùng đã đăng nhập; không tin `family_id` do client tự gửi; riêng `SYSTEM_ADMIN` (không thuộc gia đình nào) truyền `familyId` ở API danh sách, tạo mới và thống kê (`conventions.md` mục 6.2). Yêu cầu truy cập dữ liệu của gia đình khác phải bị từ chối (chống IDOR). |
| BR-COM-03 | `post`, `announcement` và `event` có `branch_id UUID NULL`: `NULL` là phạm vi toàn gia đình; có giá trị là phạm vi chi. Nội dung có `branch_id` của chi X hiển thị cho thành viên thuộc chi X và các chi con của X (đi theo `branch.parent_branch_id`, cùng chiều với phạm vi quản lý của `BRANCH_ADMIN` ở BR-GEN-14); thành viên của chi cha hoặc chi khác nhánh không thấy. `BRANCH_ADMIN` quản lý cả gia đình và `SYSTEM_ADMIN` thấy tất cả. |
| BR-COM-04 | `type=POST` cho phép MEMBER đã xác minh tạo bài thường; `type=NEWS` chỉ cho `BRANCH_ADMIN` trở lên. MEMBER không được đổi `type` khi PATCH bài đăng. |
| BR-COM-05 | Chỉ người tạo được sửa bài. Xóa bài được phép với người tạo, `BRANCH_ADMIN` trong phạm vi chi, hoặc `SYSTEM_ADMIN` khi kiểm duyệt (FR-ADM-02); `BRANCH_ADMIN` chỉ gỡ bài thuộc chi mình quản lý, không phải quyền kiểm duyệt toàn hệ thống; xóa mềm qua `deleted_at`. |
| BR-COM-06 | Thành viên chỉ được xem và bình luận trong phạm vi nội dung mình có quyền xem. Người dùng chỉ được sửa/xóa bình luận của chính mình; `BRANCH_ADMIN` (trong phạm vi chi mình quản lý) và `SYSTEM_ADMIN` (kiểm duyệt) có thể xóa bình luận vi phạm. Độ dài nội dung được kiểm tra ở backend (đã chốt 9/10/2026): nội dung bài đăng, thông báo và mô tả sự kiện tối đa 5000 ký tự; bình luận tối đa 1000 ký tự; tiêu đề thông báo, tên sự kiện, địa điểm tối đa 255 ký tự (khớp kiểu `VARCHAR(255)`). Vượt giới hạn trả 400 `VALID_001`. |
| BR-COM-07 | Mỗi người dùng chỉ có một reaction đang hoạt động trên mỗi bài; các giá trị hợp lệ: `LIKE`, `LOVE`, `HAHA`, `SAD`. Người dùng chỉ được sửa/xóa reaction của mình. |
| BR-COM-08 | Ảnh tải lên chỉ nhận `jpg`, `png`, `webp`, tối đa 5 MB mỗi tệp. Ngoại lệ (đã chốt 9/10/2026): với `owner_type = HERITAGE` cho phép thêm `pdf`, tối đa 10 MB mỗi tệp, để lưu tài liệu lịch sử và kho lưu trữ số (FR-HER-01, FR-HER-05). Tổng kích thước mỗi request vẫn tối đa 10 MB. Sai định dạng trả 415 `FILE_002`, quá kích thước trả 413 `FILE_001`. Tên tệp lưu trên kho (`storage_key`) sinh bằng UUID, tên gốc đã làm sạch lưu ở `file_name` chỉ để hiển thị. Ảnh không được công khai bằng URL trực tiếp, phải phục vụ qua endpoint kiểm tra quyền. Ảnh có `owner_type` là `HERITAGE` hoặc `PERSON` tải lên bằng `POST /media`; quyền trên tài nguyên sở hữu do module `heritage`/`genealogy` quyết định. |
| BR-COM-09 | Chỉ `BRANCH_ADMIN` trong phạm vi quản lý hoặc `SYSTEM_ADMIN` được tạo/sửa thông báo; `announcement` có `is_pinned` để biểu diễn trạng thái ghim. |
| BR-COM-10 | Xóa bài, bình luận, reaction, thông báo hoặc media dùng xóa mềm khi tài nguyên có `deleted_at`; hành động xóa nội dung cần audit log theo cơ chế chung SCRUM-57/NFR-11. |
| BR-EVT-01 | Chỉ MEMBER đã xác minh, `BRANCH_ADMIN` hoặc `SYSTEM_ADMIN` được tạo sự kiện. Người tạo, `BRANCH_ADMIN` trong phạm vi và `SYSTEM_ADMIN` được sửa/xóa sự kiện theo quyền tương ứng. |
| BR-EVT-02 | RSVP chỉ nhận `GOING`, `NOT_GOING`, `MAYBE`; mỗi người dùng có tối đa một RSVP đang hoạt động cho một sự kiện. Không được RSVP sau khi sự kiện bắt đầu: trả HTTP 409 `EVT_001`. Sự kiện đã bị xóa mềm hoặc không tồn tại: trả HTTP 404 `NOT_FOUND_001`. |
| BR-EVT-03 | Danh sách người tham dự có thể lọc theo trạng thái RSVP. Thêm/gỡ người tham dự chỉ do người tạo sự kiện hoặc vai trò quản trị có quyền trong phạm vi thực hiện. Người được thêm phải thuộc cùng `family_id` với sự kiện; thêm trùng `(event_id, user_id)` trả HTTP 409 `CONFLICT_001`. Nếu người dùng đã được thêm, RSVP của chính họ cập nhật cùng bản ghi `event_participant`. Thành viên đã xác minh có quyền xem sự kiện được xem danh sách người tham dự (`srs.md` Phụ lục A); danh sách chỉ gồm họ tên và trạng thái RSVP, không kèm thông tin liên hệ. Chi tiết sự kiện vẫn trả `myRsvpStatus` và `rsvpCounts`. |
| BR-EVT-04 | `events` dùng media chung do module `community` sở hữu; truy cập ảnh sự kiện phải kiểm tra quyền sự kiện/gia đình. Không tạo bảng ảnh riêng cho events. |
| BR-EVT-05 | Nhắc nhở chỉ hướng đến người đã RSVP `GOING` hoặc `MAYBE`; nhắc vào 24 giờ và 1 giờ trước `start_at` (đã chốt 9/10/2026). Mỗi mốc nhắc có loại riêng (`EVENT_REMINDER_24H`, `EVENT_REMINDER_1H`) nên ràng buộc duy nhất `(user_id, ref_id, type)` khi có `ref_id` vừa chống gửi trùng vừa cho phép đủ 2 lần nhắc; nếu sau này đổi mốc nhắc thì đổi tên loại tương ứng. Kênh gửi: thông báo trong ứng dụng (bản ghi `notification`, web và mobile cùng đọc qua `GET /notifications`); push notification trên mobile là phần mở rộng, không thuộc phạm vi cam kết. Mỗi người nhận có một bản ghi `notification` riêng (bảng có `user_id` và `is_read` theo từng người). |
| BR-COM-11 | Khi bài đăng được tạo/sửa/xóa, module thông báo cho module `ai` qua interface `AiGateway` để cập nhật embedding; không gọi REST nội bộ. |
| BR-EVT-06 | Khi sự kiện được tạo/sửa/xóa, module thông báo cho module `ai` qua interface `AiGateway` để cập nhật embedding; không gọi REST nội bộ. |
| BR-COM-12 | Báo cáo nội dung vi phạm (SCRUM-50; `auth.md` UC-AUTH-09, BR-AUTH-15). Thành viên đã xác minh được báo cáo bài đăng, bình luận, sự kiện, thông báo trong phạm vi mình xem được; mỗi người chỉ báo cáo một lần cho mỗi nội dung, báo cáo trùng trả HTTP 409 `CONFLICT_001`. Báo cáo lưu ở bảng `content_report`. Module Community cài đặt interface `ContentModerationPort` (`findReports`, `hideContent`, `dismissReport`) để module admin đọc và xử lý. Ẩn nội dung: đặt `hidden_at` của nội dung và chuyển báo cáo sang `HIDDEN`; bỏ qua: chuyển báo cáo sang `DISMISSED`. Khi trưởng chi hoặc quản trị gỡ nội dung của người khác, module gọi `AuditLogger.log` với action `CONTENT_DELETED`. |

Mọi API cần có kiểm thử quyền truy cập chéo: người dùng thuộc gia đình A gọi dữ liệu gia đình B phải bị từ chối. Các thao tác xóa bài, bình luận và sự kiện phải phát sinh audit log qua cơ chế chung của hệ thống.

## 6. Thiết kế bảng cơ sở dữ liệu của module

> Các bảng dưới đây là thiết kế của module; `id` dùng UUID. Các trường audit theo quy ước chung gồm `created_at`, `updated_at`, `deleted_at`, `created_by`, `updated_by`. Thời điểm (`created_at`, `updated_at`, `deleted_at`, `start_at`, `end_at`) dùng `TIMESTAMPTZ` (lưu theo UTC), khớp định dạng ISO-8601 có múi giờ ở `conventions.md`; ngày thuần dùng `DATE`.

| Bảng | Mục đích | Trạng thái |
|---|---|---|
| `post` | Bài đăng (có trường loại: POST / NEWS) | Hoàn thành |
| `comment` | Bình luận | Hoàn thành |
| `reaction` | Reaction | Hoàn thành |
| `media` | Ảnh và tệp tải lên (tệp `pdf` chỉ với `owner_type = HERITAGE`) | Hoàn thành |
| `announcement` | Thông báo ghim | Hoàn thành |
| `notification` | Thông báo gửi người dùng | Hoàn thành |
| `event` | Sự kiện | Hoàn thành |
| `event_participant` | Người tham dự và trạng thái RSVP | Hoàn thành |
| `content_report` | Báo cáo nội dung vi phạm (BR-COM-12) | Hoàn thành |

**Bảng `post`**

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Mã bài đăng |
| family_id | UUID | NOT NULL | Gia đình sở hữu bài đăng; kiểm tra ở service, không FK sang module khác |
| branch_id | UUID | NULL | NULL = cả gia đình; có giá trị = chi/phạm vi chi |
| content | TEXT | NOT NULL | Nội dung bài đăng |
| type | VARCHAR(20) | NOT NULL, CHECK | `POST` hoặc `NEWS` |
| created_by | UUID | NOT NULL | Người tạo bài đăng |
| updated_by | UUID | NULL | Người cập nhật gần nhất |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |
| hidden_at | TIMESTAMPTZ | NULL | Thời điểm bị quản trị ẩn, khác `deleted_at` |

**Bảng `comment`**

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Mã bình luận |
| post_id | UUID | NOT NULL, FK | Bài đăng được bình luận |
| content | TEXT | NOT NULL | Nội dung bình luận |
| created_by | UUID | NOT NULL | Người tạo |
| updated_by | UUID | NULL | Người cập nhật |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |
| hidden_at | TIMESTAMPTZ | NULL | Thời điểm bị quản trị ẩn, khác `deleted_at` |

**Bảng `reaction`**

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Mã reaction |
| post_id | UUID | NOT NULL, FK | Bài đăng được reaction |
| type | VARCHAR(20) | NOT NULL, CHECK | `LIKE`, `LOVE`, `HAHA`, `SAD` |
| created_by | UUID | NOT NULL | Người tạo |
| updated_by | UUID | NULL | Người cập nhật |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc:** Một người dùng không có nhiều reaction đang hoạt động trên cùng một bài đăng.

**Bảng `media`**

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Mã tệp |
| owner_type | VARCHAR(20) | NOT NULL, CHECK | `POST`, `EVENT`, `HERITAGE` hoặc `PERSON` (ảnh đại diện hồ sơ của `genealogy`, được `person.avatar_media_id` tham chiếu) |
| owner_id | UUID | NOT NULL | ID tài nguyên sở hữu; kiểm tra ở service |
| family_id | UUID | NOT NULL | Gia đình dùng để lọc quyền truy cập |
| file_size | BIGINT | NOT NULL | Kích thước tệp theo byte |
| file_name | VARCHAR(255) | NOT NULL | Tên tệp gốc đã làm sạch, chỉ để hiển thị |
| storage_key | VARCHAR(255) | NOT NULL, UNIQUE | Khóa lưu trữ nội bộ (sinh bằng UUID); không trả ra API, ảnh chỉ lấy qua `GET /media/{id}/content` |
| caption | VARCHAR(255) | NULL | Chú thích ảnh |
| file_type | VARCHAR(100) | NOT NULL | Loại tệp (MIME), ví dụ `image/jpeg`, `image/png`, `image/webp`; `application/pdf` chỉ với `owner_type = HERITAGE` (BR-COM-08) |
| created_by | UUID | NOT NULL | Người tải lên |
| updated_by | UUID | NULL | Người cập nhật |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

API trả `contentType` ← `file_type`, `fileSize` ← `file_size`; `contentUrl` được tính từ `id`.

**Bảng `announcement`**

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Mã thông báo |
| family_id | UUID | NOT NULL | Gia đình sở hữu thông báo; kiểm tra ở service, không FK chéo module |
| branch_id | UUID | NULL | NULL = cả gia đình; có giá trị = chi/phạm vi chi |
| is_pinned | BOOLEAN | NOT NULL DEFAULT FALSE | Trạng thái ghim thông báo |
| title | VARCHAR(255) | NOT NULL | Tiêu đề thông báo |
| content | TEXT | NOT NULL | Nội dung thông báo |
| created_by | UUID | NOT NULL | Người tạo |
| updated_by | UUID | NULL | Người cập nhật |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |
| hidden_at | TIMESTAMPTZ | NULL | Thời điểm bị quản trị ẩn, khác `deleted_at` |

**Bảng `notification`**

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Mã thông báo |
| user_id | UUID | NOT NULL | Người nhận thông báo; tham chiếu `users(id)` của module auth, kiểm tra ở service, không FK chéo module |
| ref_type | VARCHAR(30) | NULL | Loại tài nguyên tham chiếu, ví dụ EVENT hoặc ANNOUNCEMENT |
| ref_id | UUID | NULL | ID tài nguyên tham chiếu |
| type | VARCHAR(50) | NOT NULL, CHECK | `EVENT_REMINDER_24H`, `EVENT_REMINDER_1H`, `ANNOUNCEMENT` |
| title | VARCHAR(255) | NOT NULL | Tiêu đề |
| content | TEXT | NOT NULL | Nội dung |
| is_read | BOOLEAN | NOT NULL DEFAULT FALSE | Trạng thái đã đọc |
| created_by | UUID | NULL | Nguồn tạo thông báo nếu có |
| updated_by | UUID | NULL | Người cập nhật |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Bảng `event`**

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Mã sự kiện |
| family_id | UUID | NOT NULL | Gia đình tổ chức; kiểm tra ở service, không FK sang module khác |
| branch_id | UUID | NULL | NULL = cả gia đình; có giá trị = chi/phạm vi chi |
| title | VARCHAR(255) | NOT NULL | Tên sự kiện |
| description | TEXT | NULL | Mô tả sự kiện |
| start_at | TIMESTAMPTZ | NOT NULL | Thời gian bắt đầu |
| end_at | TIMESTAMPTZ | NULL | Thời gian kết thúc |
| location | VARCHAR(255) | NULL | Địa điểm |
| created_by | UUID | NOT NULL | MEMBER (người tạo sự kiện) |
| updated_by | UUID | NULL | Người cập nhật |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |
| hidden_at | TIMESTAMPTZ | NULL | Thời điểm bị quản trị ẩn, khác `deleted_at` |

**Ràng buộc:** Nếu có `end_at` thì thời gian kết thúc phải lớn hơn `start_at`.

**Bảng `event_participant`**

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Mã bản ghi tham dự |
| event_id | UUID | NOT NULL, FK | Sự kiện |
| user_id | UUID | NOT NULL | Người được ghi nhận tham dự (khác `created_by` khi người tạo sự kiện thêm hộ); tham chiếu `users(id)` của module auth, kiểm tra ở service, không FK chéo module |
| rsvp_status | VARCHAR(20) | NOT NULL, CHECK | `GOING`, `NOT_GOING` hoặc `MAYBE`; mặc định `MAYBE` khi người tạo sự kiện thêm hộ |
| created_by | UUID | NOT NULL | Người thực hiện thao tác tạo bản ghi |
| updated_by | UUID | NULL | Người cập nhật |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc:** `event_id` là FK tới `event(id)` (cùng module); `user_id` chỉ lưu UUID của `users(id)` thuộc module auth, không tạo FK, service kiểm tra người dùng tồn tại và cùng `family_id`; chỉ mục duy nhất từng phần `(event_id, user_id) WHERE deleted_at IS NULL` bảo đảm một RSVP hoạt động cho mỗi người/sự kiện. `created_by` là người thực hiện thao tác (có thể khác `user_id`).

**Bảng `content_report`**

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | Mã báo cáo |
| family_id | UUID | NOT NULL | Gia đình của nội dung bị báo cáo; kiểm tra ở service, không FK sang module khác |
| target_type | VARCHAR(20) | NOT NULL, CHECK | `POST`, `COMMENT`, `EVENT` hoặc `ANNOUNCEMENT` |
| target_id | UUID | NOT NULL | ID nội dung bị báo cáo; kiểm tra ở service |
| reporter_id | UUID | NOT NULL | Người báo cáo; tham chiếu `users(id)` của module auth, kiểm tra ở service, không FK chéo module |
| reason | VARCHAR(255) | NOT NULL | Lý do do người báo cáo nhập |
| content_snippet | VARCHAR(200) | NOT NULL | Trích tối đa 200 ký tự nội dung bị báo cáo |
| status | VARCHAR(20) | NOT NULL, CHECK, DEFAULT 'PENDING' | `PENDING`, `HIDDEN` hoặc `DISMISSED` |
| created_by | UUID | NOT NULL | Người tạo bản ghi (người báo cáo) |
| updated_by | UUID | NULL | Người cập nhật (quản trị xử lý báo cáo) |
| created_at | TIMESTAMPTZ | NOT NULL | Thời gian tạo |
| updated_at | TIMESTAMPTZ | NOT NULL | Thời gian cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |

**Ràng buộc:** chỉ mục duy nhất từng phần `(target_type, target_id, reporter_id) WHERE deleted_at IS NULL` bảo đảm mỗi người chỉ báo cáo một lần cho mỗi nội dung (BR-COM-12). **Chỉ mục:** `(status, created_at)`.

### Khóa ngoại và chỉ mục

- Khóa ngoại chỉ dùng trong cùng module (`architecture.md` mục 7, quyết định 9): `comment.post_id` → `post(id)`; `reaction.post_id` → `post(id)`; `event_participant.event_id` → `event(id)`.
- `notification.user_id` và `event_participant.user_id` tham chiếu `users(id)` của module auth (tên bảng `users` đã chốt ở `architecture.md` mục 7): chỉ lưu UUID có chỉ mục, không tạo FK; service kiểm tra tồn tại và `family_id` qua interface Java của module auth.
- `family_id`, `branch_id`, `created_by`, `updated_by` thuộc module khác: lưu UUID và kiểm tra quyền ở service, không tạo FK chéo module.
- Chỉ mục đề xuất: `post(family_id, created_at DESC)`; `comment(post_id, created_at)`; `reaction(post_id, created_by) WHERE deleted_at IS NULL` UNIQUE; `event(family_id, start_at)`; `event_participant(event_id, user_id) WHERE deleted_at IS NULL` UNIQUE; `notification(user_id, is_read, created_at DESC)`; `notification(user_id, ref_id, type) WHERE deleted_at IS NULL AND ref_id IS NOT NULL` UNIQUE; `media(owner_type, owner_id)`; `content_report(status, created_at)`; `content_report(target_type, target_id, reporter_id) WHERE deleted_at IS NULL` UNIQUE.
- Nội dung (`post`, `comment`, `event`, `announcement`) có `hidden_at` khác NULL không hiện ở bảng tin và các danh sách.

### 6.1. Mã lỗi nghiệp vụ của module

Các lỗi dùng chung (`AUTH_001`, `AUTH_002`, `PERM_001`, `PERM_002`, `VALID_001`, `NOT_FOUND_001`, `CONFLICT_001`, `FILE_001`, `FILE_002`, `SERVER_001`...) tuân theo `docs/04-api/conventions.md`. Mã riêng của module dùng cho xung đột quy tắc nghiệp vụ và thường trả HTTP `409`.

| Mã lỗi | HTTP | Ý nghĩa |
|---|---:|---|
| `EVT_001` | 409 | Sự kiện đã bắt đầu nên không thể RSVP (sự kiện đã xóa trả 404 `NOT_FOUND_001`). |

Thêm người tham dự đã có trong danh sách trả mã chung `CONFLICT_001` (409), không cần mã riêng.

Hiện tài liệu chưa xác định mã lỗi nghiệp vụ riêng cho Community; không tự tạo mã `COM_xxx` khi chưa có quy tắc cần mã riêng.

## 7. API của module

> Chi tiết được đặc tả trong `../../04-api/openapi/community-events.yaml`. Các quyền bên dưới luôn được kiểm tra thêm theo `family_id`, `branch_id` và trạng thái xác minh.

| Phương thức | Đường dẫn | Mô tả | Quyền (role) |
|---|---|---|---|
| POST | `/api/v1/posts` | Tạo bài đăng thường/tin tức | MEMBER đã xác minh (`POST`); BRANCH_ADMIN trở lên (`NEWS`) |
| GET | `/api/v1/posts` | Danh sách bài đăng; lọc `type`, `branchId`, phân trang | Thành viên có quyền xem |
| GET | `/api/v1/posts/{id}` | Chi tiết bài đăng | Thành viên có quyền xem |
| PATCH | `/api/v1/posts/{id}` | Sửa nội dung bài đăng, không đổi `type` | Người tạo |
| DELETE | `/api/v1/posts/{id}` | Xóa mềm bài đăng | Người tạo; BRANCH_ADMIN trong phạm vi; SYSTEM_ADMIN |
| GET | `/api/v1/posts/{id}/comments` | Danh sách bình luận | Thành viên có quyền xem bài |
| POST | `/api/v1/posts/{id}/comments` | Thêm bình luận | MEMBER đã xác minh |
| PATCH | `/api/v1/comments/{id}` | Sửa bình luận của mình | Người tạo bình luận |
| DELETE | `/api/v1/comments/{id}` | Xóa bình luận | Người tạo; BRANCH_ADMIN trong phạm vi chi; SYSTEM_ADMIN (kiểm duyệt) |
| POST | `/api/v1/posts/{id}/reactions` | Thêm/thay reaction | MEMBER đã xác minh |
| DELETE | `/api/v1/posts/{id}/reactions` | Xóa reaction của mình | MEMBER đã xác minh |
| POST | `/api/v1/posts/{id}/media` | Tải ảnh lên bài đăng | Thành viên có quyền xem/đăng bài |
| GET | `/api/v1/posts/{id}/media` | Danh sách ảnh của bài đăng | Thành viên có quyền xem bài |
| POST | `/api/v1/media` | Tải ảnh có `ownerType` là `HERITAGE` hoặc `PERSON`, hoặc tệp `pdf` với `ownerType = HERITAGE` (multipart: `file`, `ownerType`, `ownerId`, `caption`) | Theo quyền sửa tài nguyên sở hữu (module `heritage`/`genealogy`) |
| GET | `/api/v1/media/{id}/content` | Phục vụ nội dung ảnh sau khi kiểm tra quyền | Thành viên có quyền xem tài nguyên |
| DELETE | `/api/v1/media/{id}` | Xóa media | Người tạo hoặc quản trị được phép |
| POST | `/api/v1/announcements` | Tạo thông báo | BRANCH_ADMIN; SYSTEM_ADMIN |
| GET | `/api/v1/announcements` | Danh sách thông báo | Thành viên có quyền xem |
| GET | `/api/v1/announcements/{id}` | Chi tiết thông báo | Thành viên có quyền xem |
| PATCH | `/api/v1/announcements/{id}` | Sửa thông báo | BRANCH_ADMIN trong phạm vi; SYSTEM_ADMIN |
| DELETE | `/api/v1/announcements/{id}` | Xóa thông báo | BRANCH_ADMIN trong phạm vi; SYSTEM_ADMIN |
| GET | `/api/v1/notifications` | Danh sách thông báo của người đăng nhập | Chủ tài khoản |
| PATCH | `/api/v1/notifications/{id}` | Đánh dấu đã đọc (body `{"isRead": true}`) | Chủ tài khoản |
| POST | `/api/v1/content-reports` | Báo cáo nội dung vi phạm (body: `targetType`, `targetId`, `reason`) | MEMBER đã xác minh; BRANCH_ADMIN |
| POST | `/api/v1/events` | Tạo sự kiện | MEMBER đã xác minh, BRANCH_ADMIN, SYSTEM_ADMIN |
| GET | `/api/v1/events` | Danh sách sự kiện, lọc thời gian | Thành viên có quyền xem |
| GET | `/api/v1/events/{id}` | Chi tiết sự kiện | Thành viên có quyền xem |
| PATCH | `/api/v1/events/{id}` | Sửa sự kiện | Người tạo; BRANCH_ADMIN trong phạm vi; SYSTEM_ADMIN |
| DELETE | `/api/v1/events/{id}` | Xóa mềm sự kiện | Người tạo; BRANCH_ADMIN trong phạm vi; SYSTEM_ADMIN |
| PUT | `/api/v1/events/{id}/rsvp` | Tạo/cập nhật RSVP | MEMBER đã xác minh |
| GET | `/api/v1/events/{id}/participants` | Danh sách người tham dự, lọc RSVP | Thành viên có quyền xem sự kiện |
| POST | `/api/v1/events/{id}/participants` | Thêm người tham dự | Người tạo hoặc quản trị có quyền |
| DELETE | `/api/v1/participants/{id}` | Gỡ bản ghi người tham dự theo ID | Người tạo hoặc quản trị có quyền |
| GET | `/api/v1/events/{id}/media` | Danh sách ảnh sự kiện | Thành viên có quyền xem sự kiện |
| POST | `/api/v1/events/{id}/media` | Tải ảnh lên thư viện sự kiện | Thành viên có quyền chia sẻ ảnh |

### Dữ liệu tổng hợp trong phản hồi

- `Post` trả thêm `commentCount`, `reactionCounts` (`LIKE`, `LOVE`, `HAHA`, `SAD`) và `myReaction` (null nếu chưa thả).
- `Event` trả thêm `myRsvpStatus` (null nếu chưa RSVP) và `rsvpCounts` (`going`, `notGoing`, `maybe`); 2 trường này đủ để hiển thị nhanh; danh sách đầy đủ lấy ở `GET /events/{id}/participants`.
- `Media` trả `contentUrl` (đường dẫn `/api/v1/media/{id}/content`), không trả `storage_key`.

### Liên kết module

- Package backend: `com.familyconnect.modules.community` và `com.familyconnect.modules.events`.
- Bài đăng và bình luận trả `authorName` (họ tên người đăng) tra theo lô qua interface `UserDirectory` của module auth, không đọc bảng `users`; tài khoản đã xóa hiện "Thành viên đã rời hệ thống". Trường `canDelete` cho biết người đang xem có được xóa theo BR-COM-05, BR-COM-06 để giao diện chỉ hiện nút khi được phép.
- `events` và `heritage` sử dụng ảnh thông qua Java interface của module `community`; không tạo bảng ảnh riêng. Interface `CommunityQuery`: `getHeritageMedia(mediaId)` cho heritage, `getPersonMedia(mediaId)` cho genealogy (trả id ảnh và id hồ sơ sở hữu, chỉ với `owner_type = PERSON`). `genealogy` lưu ảnh đại diện qua `person.avatar_media_id` (UUID, không FK) trỏ vào `media` (`owner_type = PERSON`).
- Khi bài đăng hoặc sự kiện được tạo, sửa hay xóa, module tương ứng gọi `AiGateway` để thông báo module `ai` cập nhật embedding; không gọi REST nội bộ.
- Ảnh không được công khai bằng URL lưu trữ trực tiếp; endpoint nội dung phải kiểm tra quyền truy cập trước khi trả file.
- Module `community` cài đặt `ContentModerationPort` (`findReports`, `hideContent`, `dismissReport`; `auth.md` mục 6.3) để module admin đọc và xử lý báo cáo, không join trực tiếp bảng `content_report`; khi trưởng chi hoặc quản trị gỡ nội dung của người khác, gọi `AuditLogger.log` với action `CONTENT_DELETED` (BR-COM-12).

## 8. Màn hình liên quan

Màn hình và wireframe của module Community và Events được quản lý trong tài liệu UI chung của nhóm:

`docs/05-ui/figma-links.md`

Wireframe ở page `04 Community và Events` của file Figma chung (SCRUM-30).

Ghi chú cho wireframe SCRUM-30: cần có nút "Báo cáo" trên bài đăng, bình luận, sự kiện và thông báo (BR-COM-12).

## 9. Câu hỏi còn mở

- [x] Kênh nhắc nhở sự kiện: đã chốt 9/10/2026 là thông báo trong ứng dụng; push notification trên mobile là phần mở rộng nếu kịp (BR-EVT-05).
- [x] Thời điểm nhắc nhở: đã chốt 9/10/2026 là 24 giờ và 1 giờ trước `start_at` (BR-EVT-05).
- [x] Nội dung có `branch_id` của một chi: đã chốt 9/10/2026 là hiển thị cho chi đó và các chi con, khớp BR-GEN-14 của SCRUM-18 (BR-COM-03).
- [x] Tên bảng tài khoản mà `notification.user_id` và `event_participant.user_id` tham chiếu: đã chốt `users` (`architecture.md` mục 7); hai cột này chỉ lưu UUID, không tạo FK chéo module (quyết định 9).
- [x] Giới hạn độ dài: đã chốt 9/10/2026, bài đăng 5000 ký tự, bình luận 1000 ký tự (BR-COM-06).
- [x] Mỗi người nhận có một bản ghi `notification` riêng: đã chốt 9/10/2026 (BR-EVT-05).
- [x] Cho phép tệp `pdf` (tối đa 10 MB) với `owner_type = HERITAGE` (BR-COM-08, `conventions.md` mục 7.3): đã chốt 9/10/2026.
