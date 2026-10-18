# Mẫu test case

Cả nhóm dùng chung mẫu này. Một dòng là một test case, mỗi test case kiểm tra **một** kết quả mong đợi.

| ID | Module | Mô tả | Tiền điều kiện | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Trạng thái | Mã FR |
|---|---|---|---|---|---|---|---|---|
| TC-XXX-001 | | | | 1. ... | | | | |

## Cách điền

| Cột | Cách điền |
|---|---|
| ID | `TC-<NHÓM>-<số 3 chữ số>`. Nhóm: `AUTH`, `GEN`, `COM`, `EVT`, `HER`, `DIR`, `DSH`, `AI`, `ADM` (theo module) và `PERM`, `IDOR`, `FILE`, `API`, `INT`, `PRIV`, `UI` (nhóm kiểm thử chung, Test Plan mục 3) |
| Module | Tên module chứa chức năng |
| Mô tả | Một câu: kiểm tra điều gì |
| Tiền điều kiện | Tài khoản (vai trò, đã xác minh hay chưa, gia đình), dữ liệu cần có sẵn |
| Bước thực hiện | Đánh số 1, 2, 3. Với API ghi phương thức, đường dẫn, dữ liệu gửi; với giao diện ghi màn hình và cỡ màn hình đã test |
| Kết quả mong đợi | Với API ghi HTTP status và mã lỗi (ví dụ `403 PERM_001`) cùng phần dữ liệu chính; với giao diện ghi điều người dùng thấy |
| Kết quả thực tế | Để trống khi viết; điền khi chạy. Nếu có lỗi ghi mã bug Jira |
| Trạng thái | `Chưa chạy`, `Đạt`, `Không đạt` |
| Mã FR | Mã FR hoặc NFR trong `srs.md` (ví dụ `FR-AUTH-01`) |

## Ví dụ

| ID | Module | Mô tả | Tiền điều kiện | Bước thực hiện | Kết quả mong đợi | Kết quả thực tế | Trạng thái | Mã FR |
|---|---|---|---|---|---|---|---|---|
| TC-AUTH-001 | Auth | Đăng nhập đúng | Đã có tài khoản hợp lệ, chưa bị khóa | 1. `POST /api/v1/auth/login` với email và mật khẩu đúng | `200`, `success: true`, nhận access token và refresh token | | Chưa chạy | FR-AUTH-01 |
| TC-AUTH-002 | Auth | Đăng nhập sai mật khẩu | Đã có tài khoản hợp lệ | 1. `POST /api/v1/auth/login` với mật khẩu sai | `401 AUTH_001`, `success: false`, `error.details` là mảng | | Chưa chạy | FR-AUTH-01 |
| TC-FILE-001 | Events | Tải ảnh vượt 5 MB | Đăng nhập là MEMBER đã xác minh | 1. Tải ảnh `jpg` 6 MB lên `POST /api/v1/events/{id}/media` | `413 FILE_001` | | Chưa chạy | FR-EVT-04 |

## Quy trình báo bug

Khi trạng thái là `Không đạt`, tạo issue **Bug** trên Jira theo Test Plan mục 5: tiêu đề `[Module] mô tả ngắn`; mức độ Critical, Major hoặc Minor; các bước tái hiện; kết quả mong đợi; kết quả thực tế; ảnh chụp.
