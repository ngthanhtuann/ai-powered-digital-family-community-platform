# Quy ước API chung

> **Người viết:** TV3 (PiLo257)
> **Reviewer:** TV1 (Tuấn Nguyễn Thanh)
> **Task Jira:** SCRUM-21
> **Trạng thái:** Đã merge vào `develop` (Sprint 1)

Tài liệu này quy định chuẩn API chung của FamilyConnect cho web và mobile. Tất cả module phải tuân theo tài liệu này khi thiết kế OpenAPI và triển khai backend.

Tham chiếu:

* `docs/02-design/architecture.md`, mục 7.
* `docs/01-srs/privacy.md`.

## 1. URL và HTTP Method

### 1.1. Quy ước URL

* API sử dụng tiền tố `/api/v1/`.
* Tên tài nguyên dùng danh từ số nhiều, chữ thường.
* Tài nguyên có nhiều từ sử dụng `kebab-case`.
* Không dùng động từ trong URL, ngoại trừ các endpoint đã thống nhất: `/auth/login`, `/auth/refresh`, `/ai/chat`.
* Tài nguyên lồng nhau tối đa 2 cấp.
* Tham số đường dẫn dùng `camelCase`, ví dụ `{familyId}`, `{postId}`. Tài nguyên chính của endpoint có thể dùng `{id}`; tài nguyên cha trong đường dẫn lồng nhau dùng tên đầy đủ, ví dụ `/families/{familyId}/branches`.
* ID và khóa ngoại trong API sử dụng UUID dạng chuỗi.

Ví dụ:

```text
/api/v1/families
/api/v1/families/{familyId}
/api/v1/posts
/api/v1/events/{eventId}/participants
/api/v1/auth/login
/api/v1/auth/refresh
/api/v1/ai/chat
```

### 1.2. HTTP Method

| Method | Mục đích                                          |
| ------ | ------------------------------------------------- |
| GET    | Đọc dữ liệu                                       |
| POST   | Tạo dữ liệu mới                                   |
| PUT    | Cập nhật toàn bộ hoặc thực hiện thao tác thay thế |
| PATCH  | Cập nhật một phần                                 |
| DELETE | Xóa tài nguyên theo quy tắc của module            |

### 1.3. HTTP Status khi thành công

| HTTP Status | Trường hợp                                 |
| ----------- | ------------------------------------------ |
| 200 OK      | Đọc dữ liệu, cập nhật dữ liệu, xóa dữ liệu |
| 201 Created | Tạo dữ liệu mới                            |

Không sử dụng `204 No Content` cho API xóa. API xóa phải trả về `200` cùng response envelope thống nhất:

```json
{
  "success": true,
  "data": null
}
```

## 2. Cấu trúc Response

### 2.1. Response thành công

Response thành công sử dụng cấu trúc thống nhất.

Đối với API trả về một đối tượng:

```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "title": "Họp mặt gia đình",
    "createdAt": "2026-10-09T10:00:00+07:00",
    "updatedAt": "2026-10-09T10:00:00+07:00"
  }
}
```

Đối với API trả về danh sách:

```json
{
  "success": true,
  "data": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Họp mặt gia đình",
      "createdAt": "2026-10-09T10:00:00+07:00",
      "updatedAt": "2026-10-09T10:00:00+07:00"
    }
  ],
  "meta": {
    "page": 0,
    "size": 20,
    "total": 1
  }
}
```

Quy tắc:

* `success`: trạng thái xử lý request.
* `data`: dữ liệu trả về; là mảng đối với API danh sách.
* `meta`: chỉ có ở API trả về danh sách.
* API trả về một đối tượng không có `meta`.
* `meta.total` là tổng số bản ghi phù hợp với điều kiện truy vấn.
* Khi xóa thành công, `data` có giá trị `null`.

### 2.2. Response lỗi

Mọi lỗi phải tuân theo một định dạng duy nhất:

```json
{
  "success": false,
  "error": {
    "code": "VALID_001",
    "message": "Dữ liệu không hợp lệ",
    "details": [
      {
        "field": "title",
        "message": "Trường này không được để trống"
      }
    ]
  }
}
```

Quy tắc:

* `success` luôn là `false`.
* `error.code` là mã lỗi theo quy ước chung hoặc mã riêng của module.
* `error.message` là thông báo dễ hiểu.
* `error.details` là mảng các lỗi chi tiết; mỗi phần tử có `field` và `message`. Khi không có lỗi chi tiết, dùng mảng rỗng `[]`.
* `VALID_001` sử dụng `details` để mô tả lỗi theo từng trường khi phù hợp.

Định dạng lỗi trên là **chuẩn duy nhất cho cả web và mobile**. Backend có thể sử dụng `ProblemDetail` của Spring bên trong, nhưng response trả ra phải đúng JSON `{success:false,error:{code,message,details}}`. Tất cả lỗi, bao gồm lỗi hệ thống HTTP 500 và lỗi bảo mật HTTP 401, 403, đều phải theo định dạng này.

## 3. Mã lỗi

Các API phải trả đúng HTTP status tương ứng với mã lỗi.

| Mã lỗi          | HTTP | Ý nghĩa                                      |
| --------------- | ---: | -------------------------------------------- |
| `AUTH_001`      |  401 | Sai email hoặc mật khẩu                      |
| `AUTH_002`      |  401 | Token hết hạn hoặc không hợp lệ              |
| `AUTH_003`      |  403 | Tài khoản bị khóa hoặc chưa kích hoạt        |
| `PERM_001`      |  403 | Không đủ quyền truy cập                      |
| `PERM_002`      |  403 | Thành viên chưa được xác minh                |
| `VALID_001`     |  400 | Dữ liệu không hợp lệ                         |
| `NOT_FOUND_001` |  404 | Không tìm thấy tài nguyên                    |
| `CONFLICT_001`  |  409 | Xung đột dữ liệu khi module chưa có mã riêng |
| `FILE_001`      |  413 | Tệp vượt kích thước cho phép                 |
| `FILE_002`      |  415 | Định dạng tệp không được hỗ trợ              |
| `RATE_001`      |  429 | Vượt quá giới hạn request                    |
| `SERVER_001`    |  500 | Lỗi hệ thống                                 |
| `AI_001`        |  503 | Dịch vụ AI tạm thời không khả dụng           |

Quy tắc quyền truy cập:

* Thành viên chưa được xác minh nhận `403 PERM_002` khi gọi chức năng yêu cầu xác minh.
* Người dùng truy cập dữ liệu thuộc gia đình khác nhận `403 PERM_001`.
* Lỗi xác thực, phân quyền và lỗi hệ thống phải sử dụng đúng response envelope tại mục 2.2.

### 3.1. Mã lỗi riêng của module

Các module có thể định nghĩa mã lỗi nghiệp vụ riêng theo dạng:

`<MODULE>_<số 3 chữ số>`

Tiền tố module đã thống nhất:

| Module         | Tiền tố |
| -------------- | ------- |
| Gia phả        | `GEN_`  |
| Community      | `COM_`  |
| Events         | `EVT_`  |
| Heritage       | `HER_`  |
| Directory      | `DIR_`  |
| Dashboard      | `DSH_`  |
| Administration | `ADM_`  |
| Auth           | `AUTH_` |
| AI             | `AI_`   |

Mã lỗi riêng dùng cho các trường hợp vi phạm quy tắc nghiệp vụ và trả về HTTP `409 Conflict`, trừ khi tài liệu đã thống nhất quy định cụ thể khác.

`AI_001` là mã dùng chung (HTTP `503`); mã nghiệp vụ riêng của module AI dùng từ `AI_002` trở đi.

`AUTH_001` đến `AUTH_003` là mã dùng chung ở mục 3; mã nghiệp vụ riêng của module Auth dùng từ `AUTH_004` trở đi.

Danh sách mã lỗi phải được ghi trong tài liệu use case và file OpenAPI tương ứng. Không tự ý tạo mã trùng hoặc thay đổi mã đã thống nhất.

## 4. Phân trang và sắp xếp

API trả về danh sách sử dụng các tham số:

```text
?page=0&size=20&sort=createdAt,desc
```

Quy tắc:

* `page`: số trang, bắt đầu từ `0`; mặc định là `0`.
* `size`: số bản ghi mỗi trang; mặc định là `20`, tối đa `100`.
* `sort`: tên trường theo `camelCase` và chiều sắp xếp `asc` hoặc `desc`.
* `sort` dùng tên thuộc tính API/entity theo `camelCase`, không dùng tên cột DB dạng `snake_case`.
* `meta.total` là tổng số bản ghi phù hợp với truy vấn.
* Nếu `size` vượt quá `100`, hoặc `sort` theo trường không hợp lệ, trả về HTTP `400` với mã `VALID_001`.
* Response của API danh sách phải có `data` là mảng và có `meta`.

Ví dụ hợp lệ:

```text
/api/v1/posts?page=0&size=20&sort=createdAt,desc
```

## 5. Định dạng dữ liệu

### 5.1. Tên trường và kiểu dữ liệu

* JSON sử dụng `camelCase`.
* ID và khóa ngoại trong API sử dụng UUID dạng chuỗi.
* Trường thời gian sử dụng `createdAt`, `updatedAt`, tương ứng với cột DB `created_at`, `updated_at`.
* Mọi response của thực thể phải có `createdAt` và `updatedAt`.
* API không trả `deletedAt`; dữ liệu đã xóa mềm không xuất hiện trong response.
* Trường không có giá trị trả về `null`, không tự ý bỏ trường khỏi response.
* Enum sử dụng `UPPER_SNAKE_CASE`.
* Encoding sử dụng UTF-8.

Ví dụ:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2026-10-09T10:00:00+07:00",
  "updatedAt": "2026-10-09T10:30:00+07:00"
}
```

### 5.2. Ngày và giờ

* Thời điểm có giờ sử dụng ISO-8601 kèm múi giờ.
* Cơ sở dữ liệu lưu thời điểm bằng `TIMESTAMPTZ` (UTC) và ngày thuần bằng `DATE`; API trả ISO-8601 kèm múi giờ hoặc `YYYY-MM-DD`.
* Ngày thuần, ví dụ ngày sinh, sử dụng định dạng `YYYY-MM-DD`.

Ví dụ:

```json
{
  "dateOfBirth": "2006-11-10",
  "startAt": "2026-10-09T10:00:00+07:00"
}
```

## 6. Authentication và Authorization

### 6.1. Xác thực

Các endpoint yêu cầu đăng nhập sử dụng header:

```text
Authorization: Bearer <access_token>
```

* Access token có thời hạn ngắn.
* Khi access token hết hạn hoặc không hợp lệ, trả về `401 AUTH_002`.
* Client sử dụng refresh token để gọi `POST /api/v1/auth/refresh` nhằm lấy access token mới.
* Endpoint công khai phải được khai báo rõ `security: []` trong OpenAPI.
* Backend phải kiểm tra quyền và trạng thái xác minh thành viên trước khi cho phép truy cập dữ liệu.

### 6.2. Phân quyền và dữ liệu gia đình

* Chỉ thành viên đã được xác minh mới được truy cập chức năng yêu cầu xác minh.
* Chỉ người dùng có quyền mới được đọc hoặc thay đổi dữ liệu.
* Dữ liệu gia đình chỉ được truy cập bởi người dùng thuộc phạm vi gia đình được phép.
* Không trả dữ liệu riêng tư của gia đình khác khi người dùng không có quyền.
* Trả `403 PERM_002` khi thành viên chưa được xác minh; trả `403 PERM_001` khi không đủ quyền, bao gồm truy cập dữ liệu gia đình khác.
* `SYSTEM_ADMIN` không thuộc gia đình nào (`use-cases/auth.md` BR-AUTH-08). Các API danh sách, tạo mới và thống kê lấy gia đình từ người đăng nhập có tham số `familyId` (chốt 18/10/2026): bắt buộc khi người gọi là `SYSTEM_ADMIN`, thiếu thì `400 VALID_001`; vai trò khác không cần truyền, nếu truyền khác gia đình của mình thì `403 PERM_001`. API theo id (ví dụ `/posts/{id}`) lấy gia đình từ chính bản ghi nên không cần tham số này.

## 7. Bảo mật

### 7.1. Giới hạn request

| Quy định                   | Giới hạn                    |
| -------------------------- | --------------------------- |
| Đăng nhập                  | 5 lần/phút theo IP và email |
| Chat AI                    | 20 câu/giờ/người dùng       |
| Tìm kiếm AI (`GET /ai/search`) | 30 lượt/giờ/người dùng trên mỗi bản backend (`ai.search.limit_per_hour`) |
| Kích thước JSON request    | Tối đa 1 MB                 |
| Dung lượng tệp trong một request tải tệp | Tối đa 10 MB/request (mục 7.3) |

Khi vượt giới hạn request, trả HTTP `429` với mã `RATE_001` và header `Retry-After` để client biết thời gian chờ trước khi thử lại.

### 7.2. CORS và log

* Danh sách origin được phép truy cập CORS phải lấy từ biến môi trường.
* Không ghi mật khẩu, access token, refresh token hoặc nội dung chat AI vào log.
* Không đưa thông tin bí mật vào response lỗi.
* Không hard-code mật khẩu, token hoặc secret trong mã nguồn.
* Backend phải kiểm tra quyền truy cập trước khi trả dữ liệu hoặc tệp.

### 7.3. Quy ước tải tệp

* Request tải tệp sử dụng `multipart/form-data`, tên trường là `file`.
* Định dạng ảnh được hỗ trợ: `jpg`, `png`, `webp`.
* Mỗi tệp ảnh tối đa 5 MB.
* Ngoại lệ cho tài liệu lịch sử và kho lưu trữ số (FR-HER-01, FR-HER-05): với `owner_type = HERITAGE`, cho phép thêm định dạng `pdf`, tối đa 10 MB mỗi tệp. Mọi trường hợp khác giữ `jpg`, `png`, `webp`, tối đa 5 MB. Đã chốt 9/10/2026.
* Tên tệp lưu trữ được tạo bằng UUID, không sử dụng trực tiếp tên tệp do người dùng cung cấp làm tên lưu trữ.
* Tệp chỉ được truy cập qua endpoint có kiểm tra quyền, không công khai chỉ bằng URL tệp.
* Tệp vượt giới hạn trả HTTP `413 FILE_001`.
* Định dạng tệp không được hỗ trợ trả HTTP `415 FILE_002`.
* Tổng kích thước các tệp trong một request tải tệp không vượt quá 10 MB. Giới hạn này tính trên dung lượng tệp; cấu hình máy chủ (`max-request-size`) đặt 11 MB để chừa phần đầu `multipart`, nhờ đó tệp `pdf` đúng 10 MB không bị từ chối.

## 8. Quy ước OpenAPI

Các file OpenAPI đặt tại:

`docs/04-api/openapi/`

OpenAPI là nguồn chuẩn để định nghĩa DTO của web và mobile. DTO được sinh tự động từ các file OpenAPI bằng công cụ `openapi-typescript`, không viết tay riêng ở hai nơi. Vì vậy, OpenAPI của mỗi module phải đầy đủ và khớp với dữ liệu thực tế của API.

Checklist bắt buộc cho mỗi module:

* Mỗi endpoint có request mẫu và response mẫu thành công, response lỗi, mã HTTP và quyền truy cập.
* Response thành công theo envelope `success`/`data`/`meta`, đặt tên schema `<Entity>Response`, `<Entity>ListResponse`; response lỗi dùng schema chung `ErrorResponse`.
* API danh sách dùng tham số `page`, `size`, `sort`; `data` là mảng và có `meta`.
* API trả về một đối tượng không có `meta`.
* Khai báo `bearerAuth` cho endpoint cần đăng nhập; endpoint công khai khai báo `security: []`.
* Khai báo các response lỗi phù hợp: `400`, `401`, `403`, `404`, `409`, `413`, `415`, `429`, `500`, `503` khi endpoint có liên quan.
* Phân biệt `PERM_001` và `PERM_002`.
* Mỗi `operationId` phải duy nhất trên toàn hệ thống.
* Ví dụ response phải khớp schema (đủ trường `required`); không dùng `example` cùng lúc với `examples` trong một media type.
* API tạo mới, kể cả tải tệp lên, trả HTTP `201`.
* ID và khóa ngoại là UUID dạng chuỗi.
* Enum sử dụng `UPPER_SNAKE_CASE`.
* Response của thực thể có `createdAt`, `updatedAt`.
* Mã lỗi riêng của module được khai báo trong OpenAPI và tài liệu use case.
* Response lỗi luôn tuân theo định dạng tại mục 2.2.
* API xóa trả HTTP `200` với `{ "success": true, "data": null }`, không dùng `204`.

Các module phải tuân theo quy ước này khi xây dựng API mới hoặc cập nhật API hiện có. Khi có thay đổi quy ước chung, nhóm cần thống nhất và cập nhật tài liệu trước khi áp dụng cho các module.

## 9. Trạng thái review

* [ ] Cả nhóm đã đọc và đồng ý với quy ước.
* [ ] Reviewer đã kiểm tra và approve PR.
* [ ] Các OpenAPI của module tuân theo quy ước chung.
