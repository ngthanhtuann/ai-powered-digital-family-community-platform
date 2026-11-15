# Kế hoạch kiểm thử (Test Plan)

> **Người viết:** TV1 (Tuấn Nguyễn Thanh) | **Reviewer:** TV4 (Huy Quốc) | **Task Jira:** SCRUM-27 | **Hạn nộp review:** Thứ Năm 15/10 (Sprint 2)
> **Trạng thái:** Đã merge vào `develop` (Sprint 2) | Mẫu test case: [test-case-template.md](test-case-template.md)

## 1. Các mức kiểm thử

| Mức | Công cụ | Ai làm |
|---|---|---|
| Unit test (backend) | JUnit, Mockito | Chủ module, trước khi mở PR |
| API test | Postman hoặc MockMvc | Chủ module viết, người test chéo chạy lại |
| Giao diện (thủ công) | Trình duyệt, Expo Go/APK | Người test chéo |
| Hiệu năng | k6 hoặc JMeter | TV2 (SCRUM-59, Sprint 6) |
| Hồi quy toàn hệ thống | Chạy lại các test case đã Đạt | TV1 (SCRUM-65, Sprint 7) |

## 2. Test chéo

| Người test | Module được test |
|---|---|
| TV1 | TV3 (Community, Events) |
| TV2 | TV4 (Heritage, Directory, Dashboard) |
| TV3 | TV5 (AI) |
| TV4 | TV1 (Auth, Admin) |
| TV5 | TV2 (Gia phả) |

Khi task code sang cột **Testing**, người test chéo chạy test case của module đó, điền kết quả vào mẫu test case, và báo bug theo mục 5. (Bảng này giống bảng reviewer PR trong `quy-trinh-lam-viec.md` mục 2.3.)

## 3. Những nhóm test bắt buộc

Quy tắc chung: kết quả mong đợi của API luôn ghi **HTTP status + mã lỗi** (theo `conventions.md` mục 3).

### 3.1. Phân quyền theo endpoint (`PERM`)
Sinh từ `x-roles` trong các file OpenAPI và `srs.md` Phụ lục A. Mỗi endpoint có các test case:

| Tình huống | Kết quả |
|---|---|
| Đúng role, đã xác minh, đúng phạm vi | 2xx |
| Sai role | 403 `PERM_001` |
| Chưa xác minh | 403 `PERM_002` |
| Không token hoặc token hết hạn | 401 `AUTH_002` |

Lưu ý: người chưa xác minh vẫn được đăng nhập, sửa hồ sơ mình, xin vào gia đình, tạo gia đình (Phụ lục A), nên các endpoint đó không có dòng `PERM_002`. Endpoint công khai (`security: []`) thì gọi không token vẫn thành công.

### 3.2. Chống IDOR (`IDOR`)
Mỗi API có một test case: gia đình A gọi dữ liệu gia đình B (đổi ID trên đường dẫn, query hoặc body) phải nhận 403 `PERM_001`; danh sách của A không chứa bản ghi của B (`genealogy.md` BR-GEN-14).

### 3.3. Tải tệp (`FILE`)

| Tình huống | Kết quả |
|---|---|
| Ảnh `jpg`/`png`/`webp` ≤ 5 MB | 201 |
| Ảnh > 5 MB | 413 `FILE_001` |
| Tổng dung lượng tệp trong một request > 10 MB | 413 `FILE_001` |
| `pdf` ≤ 10 MB, `owner_type = HERITAGE` | 201 |
| `pdf` > 10 MB, HERITAGE | 413 `FILE_001` |
| `pdf` với owner khác HERITAGE, hoặc định dạng khác | 415 `FILE_002` |
| Tệp đổi đuôi thành `.jpg` nhưng nội dung không phải ảnh | 415 `FILE_002` (kiểm tra theo nội dung) |
| Truy cập tệp của gia đình khác | 403, không có URL công khai |

### 3.4. Quy ước phản hồi (`API`)
- Tạo mới trả 201. DELETE trả 200 `{success:true, data:null}`.
- Danh sách có `meta {page,size,total}`; `page` từ 0; `size` tối đa 100 (`size=101` hoặc `sort` sai trả 400 `VALID_001`).
- Mọi lỗi (kể cả 401, 403, 500) có dạng `{success:false, error:{code,message,details}}`, `details` luôn là mảng.

### 3.5. Toàn vẹn dữ liệu chéo module (`INT`)
Không còn khóa ngoại DB giữa các module, nên mỗi API nhận `media_id`, `person_id`, `family_id`, `user_id` phải được test: ID không tồn tại bị từ chối; ID thuộc gia đình khác bị từ chối (403 `PERM_001`).

### 3.6. Quyền riêng tư (`PRIV`)

| Tình huống | Kết quả |
|---|---|
| MEMBER xem trẻ em dưới 16 tuổi | Chỉ thấy tên, quan hệ, năm sinh |
| MEMBER xem người khác | Không thấy ngày sinh đầy đủ, số điện thoại, email, địa chỉ chi tiết |
| Người đã mất | Không có trường nguyên nhân tử vong |
| Dữ liệu đưa vào ngữ cảnh AI | Không chứa dữ liệu trẻ em (ngoài họ tên, quan hệ), mật khẩu, token, email, số điện thoại |
| Log và response lỗi | Không chứa mật khẩu, token, nội dung chat AI |

### 3.7. AI (`AI`)

| Tình huống | Kết quả |
|---|---|
| Chưa xác minh | 403 `PERM_002` |
| Hỏi quá 20 câu/giờ | 429 `RATE_001` |
| Quản trị tắt tính năng (`ai.search.enabled`, `ai.chat.enabled`, `ai.summary.enabled` = `false`) | 409 `AI_002` |
| Truy vấn | Chỉ trả dữ liệu của gia đình người hỏi |
| Không có thông tin | Trả "Không tìm thấy thông tin" |
| LLM lỗi | 503 `AI_001`, chức năng khác vẫn chạy |

### 3.8. Giao diện (`UI`)
Mỗi màn hình web kiểm tra ở 3 cỡ **360, 768, 1440 px** (NFR-01): không vỡ bố cục, không cuộn ngang, thao tác chính dùng được.

### 3.9. Xác thực (`AUTH`)
Đăng nhập đúng nhận token; sai mật khẩu 401 `AUTH_001`; quá 5 lần đăng nhập mỗi phút theo IP và email 429 `RATE_001`; token hết hạn 401 `AUTH_002`, làm mới bằng `POST /auth/refresh`; tài khoản khóa 403 `AUTH_003`.

### 3.10. Thuật toán quan hệ họ hàng
Dùng 53 ca mẫu có sẵn trong `algorithm-relationship.md` mục 7 (SCRUM-42), không chép lại.

## 4. Kiểm thử phi chức năng

| Mã | Tiêu chí (srs.md mục 5) | Task, người làm |
|---|---|---|
| NFR-12 | API cây gia phả < 1 giây với 5.000 người; tìm kiếm ngữ nghĩa < 2 giây | SCRUM-59, TV2 |
| NFR-09 | `docker compose up` trên máy sạch ≤ 15 phút | SCRUM-77, TV4 |
| NFR-10 | Tắt một bản backend vẫn phục vụ; khởi động lại không mất dữ liệu | SCRUM-96, TV3 |
| NFR-11 | Thao tác nhạy cảm đều có trong audit log | Người test chéo module Admin |
| NFR-14 | AI trả lời đúng ≥ 80% bộ câu hỏi kiểm tra | SCRUM-69, TV5 |

## 5. Báo bug trên Jira

Tạo issue loại **Bug**, tiêu đề `[Module] mô tả ngắn`, nội dung gồm: mã test case, các bước tái hiện, kết quả mong đợi, kết quả thực tế, ảnh chụp, mức độ.

| Mức độ | Nghĩa là |
|---|---|
| **Critical** | Chặn demo, hệ thống không chạy |
| **Major** | Sai chức năng chính |
| **Minor** | Lỗi giao diện hoặc nhỏ |

Giao bug cho chủ module; chủ module sửa trên nhánh `bugfix/SCRUM-<số>-...` và mở PR. Người test chạy lại: đạt thì đóng, không đạt thì mở lại. Ghi mã bug vào cột "Kết quả thực tế" của test case.

## 6. Dữ liệu test

Chỉ dùng dữ liệu mẫu. Bộ tài khoản tối thiểu: một khách; một MEMBER chưa xác minh; MEMBER và BRANCH_ADMIN của gia đình A; MEMBER của gia đình B (để test IDOR); một SYSTEM_ADMIN. Gia đình A cần có một trẻ dưới 16 tuổi và một người đã mất.

## 7. Tiêu chí hoàn thành

- Mọi FR Must có ít nhất một test case Đạt.
- Không còn bug Critical hoặc Major.
- Các nhóm ở mục 3 đã chạy trên mọi endpoint liên quan.
- Báo cáo kiểm thử do TV1 tổng hợp ở SCRUM-65 (Sprint 7).

## 8. Câu hỏi còn mở

- [ ] k6 hay JMeter: TV2 chọn khi làm SCRUM-59.
- [ ] Lưu bộ test case của từng module ở đâu: chốt ở họp DOCS GATE.
