# Từ điển dữ liệu

> **Người viết:** TV2 (Nguyễn Minh Trí) | **Reviewer:** TV5 (Lê Nhựt) | **Task Jira:** SCRUM-28 | **Hạn nộp review:** Thứ Ba 13/10 (Sprint 2)
> **Trạng thái:** Nháp 
> Cách làm: tạo nhánh `docs/<mã SCRUM>-<tên-ngắn>` từ `develop`, điền vào file này, mở Pull Request vào `develop`. Xem `docs/README.md`.

> Gộp từ mục "Thiết kế bảng cơ sở dữ liệu" của từng file use case. Mỗi bảng một khối.

## Bảng `ten_bang`
**Module:** ... | **Người sở hữu:** ...

| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| created_at | TIMESTAMPTZ | NOT NULL | |
| updated_at | TIMESTAMPTZ | NOT NULL | |
| deleted_at | TIMESTAMPTZ | NULL | xóa mềm |

**Index:** ...
