# Bảo mật và quyền riêng tư dữ liệu gia đình

> **Người viết:** TV1 (Tuấn Nguyễn Thanh) | **Reviewer:** TV4 (Huy Quốc) | **Task Jira:** SCRUM-17 | **Hạn nộp review:** Thứ Năm 8/10
> **Trạng thái:** Đã merge vào `develop` (Sprint 1). Liên quan: NFR-13 trong [srs.md](srs.md), module AI (Lê Nhựt), `docs/04-api/conventions.md`.

## 1. Mục đích
FamilyConnect lưu thông tin của người thật (họ tên, ngày sinh, liên hệ, quan hệ huyết thống, ảnh). Tài liệu quy định **ai được xem dữ liệu nào**, **quy tắc riêng cho trẻ em và người đã mất**, **dữ liệu nào được gửi cho AI bên ngoài** và các biện pháp bảo mật chính.

## 2. Dữ liệu nhạy cảm
| Nhóm | Ví dụ | Mức |
|---|---|---|
| Tài khoản | Email, mật khẩu (đã băm) | Cao |
| Liên hệ | Số điện thoại, địa chỉ chi tiết | Cao |
| Trẻ em dưới 16 tuổi | Mọi thông tin của người dưới 16 tuổi | Cao |
| Danh tính người còn sống | Họ tên, ngày sinh đầy đủ, ảnh | Trung bình |
| Quan hệ gia đình, hồ sơ nghề nghiệp, học vấn | Cây gia phả, nơi làm việc, trường học | Trung bình |
| Nội dung và dữ liệu AI | Bài đăng, ảnh, câu hỏi gửi trợ lý (`ai_chat_message`), vector (`ai_embedding_chunk`) | Trung bình |

## 3. Ai được xem gì
Có 4 vai trò: `GUEST` (khách), `MEMBER` (thành viên), `BRANCH_ADMIN` (trưởng chi), `SYSTEM_ADMIN` (quản trị). **Thành viên chưa được xác minh** chỉ xem và sửa hồ sơ của chính mình.

| Dữ liệu | Khách | Thành viên (đã xác minh) | Trưởng chi | Quản trị |
|---|---|---|---|---|
| Họ tên, năm sinh | Không | Có (gia đình của mình) | Có | Có |
| Ngày sinh đầy đủ | Không | Không (trừ của mình) | Có | Có |
| Số điện thoại | Không | Không (trừ của mình) | Có | Có |
| Email | Không | Không (trừ của mình) | Có (trong chi) | Có |
| Địa chỉ chi tiết | Không | Chỉ tỉnh/thành | Có | Có |
| Nghề nghiệp, học vấn | Không | Có | Có | Có |
| Cây gia phả, quan hệ, bài đăng, tài liệu | Không | Có (gia đình của mình) | Có | Có |
| Thông tin trẻ em dưới 16 tuổi | Không | Chỉ tên, quan hệ, năm sinh | Có | Có |
| Nhật ký hệ thống (có địa chỉ IP) | Không | Không | Không | Có |

**Mật khẩu** không ai xem được, chỉ lưu dạng băm (BCrypt). Bản đầu **không** làm màn hình cho từng người tự ẩn hiện từng trường; có thể bổ sung sau nếu còn thời gian.

## 4. Trẻ em và người đã mất
**Trẻ em (dưới 16 tuổi, tính từ ngày sinh):**
- Thành viên thường chỉ thấy **tên, quan hệ trong cây, năm sinh**; ẩn ngày sinh đầy đủ, liên hệ, trường học.
- **Không tạo tài khoản đăng nhập riêng**; hồ sơ do cha mẹ hoặc trưởng chi quản lý.
- **Không đưa vào ngữ cảnh gửi AI**, chỉ giữ họ tên và quan hệ tối giản.

**Người đã mất:**
- Hiển thị đầy đủ họ tên, năm sinh, ngày mất, nơi an táng cho thành viên đã xác minh (giá trị cốt lõi của gia phả).
- **Không lưu nguyên nhân tử vong chi tiết.** Không hiện thông tin liên hệ của người thân kèm hồ sơ.

## 5. Xác minh thành viên và tách dữ liệu gia đình
1. Người dùng đăng ký (`MEMBER`, **chưa xác minh**), gửi yêu cầu tham gia một gia đình.
2. **Trưởng chi duyệt hoặc từ chối.** Chỉ khi được duyệt mới xem được dữ liệu gia đình. Chưa duyệt mà gọi API dữ liệu gia đình hoặc AI thì trả **403 `PERM_002`**.
3. Mọi truy vấn dữ liệu **lọc theo `family_id`** của người đang đăng nhập; mỗi API kiểm tra quyền để chống đổi ID trên đường dẫn (IDOR). Mỗi API mới có một test: gia đình A gọi dữ liệu gia đình B phải bị từ chối.

## 6. Dữ liệu gửi cho AI (LLM bên ngoài)
**Được gửi:** họ tên, quan hệ, năm sinh, nghề nghiệp mức chung của người **cùng gia đình** với người hỏi; nội dung bài đăng, câu chuyện, tài liệu trong phạm vi gia đình.

**Không bao giờ gửi:** mật khẩu, token, email, số điện thoại, địa chỉ chi tiết, địa chỉ IP, thông tin trẻ em (ngoài họ tên và quan hệ), dữ liệu của gia đình khác.

**Việc cần làm cho module AI (Lê Nhựt):**
- [ ] Mỗi đoạn trong `ai_embedding_chunk` có `family_id`; lọc theo `family_id` và quyền xem **trước khi** ghép prompt.
- [ ] Chỉ đưa trường trong danh sách "được gửi" vào ngữ cảnh.
- [ ] Chưa xác minh dùng AI trả **403 `PERM_002`**; vượt giới hạn câu hỏi trả **429 `RATE_001`**.
- [ ] Khóa API LLM lưu trong biến môi trường; không có thông tin thì trả "Không tìm thấy thông tin"; ghi nhãn "Do AI tạo". Audit log chỉ ghi sự kiện, **không chứa nội dung câu hỏi**.

## 7. Bảo mật kỹ thuật
- Mật khẩu băm BCrypt; đăng nhập bằng JWT (access token ngắn hạn, refresh token).
- Phân quyền RBAC và kiểm tra `family_id` ở mọi API; giới hạn số lần đăng nhập và chat AI (rate limit).
- Chống SQL injection (dùng JPA, không ghép chuỗi SQL) và XSS (escape nội dung hiển thị); kiểm tra định dạng và kích thước tệp tải lên, đặt tên tệp bằng UUID.
- Khóa bí mật và mật khẩu để trong `.env`, **không commit lên Git**; không mở cổng PostgreSQL ra Internet; dùng HTTPS khi triển khai.
- Ghi audit log mọi thao tác nhạy cảm (ai, làm gì, lúc nào).

**Hạn chế:** đây là đồ án môn học, chưa được kiểm định theo quy định bảo vệ dữ liệu cá nhân. Bản demo chỉ dùng **dữ liệu mẫu**, không nhập thông tin thật của người thân.

