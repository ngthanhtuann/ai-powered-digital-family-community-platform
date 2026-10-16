# Use case: AI: tìm kiếm, trợ lý hỏi đáp, giải thích quan hệ, tóm tắt, gợi ý

> **Người viết:** TV5 (Lê Nhựt) | **Reviewer:** TV3 (PiLo257) | **Task Jira:** SCRUM-34 | **Hạn nộp review:** Thứ Năm 15/10 (Sprint 2)
> **Trạng thái:** Nháp 
> Cách làm: tạo nhánh `docs/<mã SCRUM>-<tên-ngắn>` từ `develop`, điền vào file này, mở Pull Request vào `develop`. Xem `docs/README.md`.

## 1. Phạm vi và Actor
> Module này làm gì, ai dùng. Ghi rõ cách xử lý khi AI không biết (trả lời "không tìm thấy" thay vì bịa) và quyền dữ liệu theo `family_id`.

## 2. Danh sách use case
> Dưới đây là gợi ý ban đầu. Chỉnh, thêm, bớt cho đúng; gắn **mã FR** lấy từ `srs.md` sau khi Tuấn gửi bảng FR.

| Mã UC | Tên | Actor | Mã FR | Ưu tiên |
|---|---|---|---|---|
| UC-AI-01 | Tìm kiếm ngữ nghĩa | Thành viên | FR-AI-01 | Must |
| UC-AI-02 | Hỏi đáp với trợ lý AI về gia phả | Thành viên | FR-AI-02 | Must |
| UC-AI-03 | Giải thích quan hệ họ hàng | Thành viên | FR-AI-03 | Must |
| UC-AI-04 | Tóm tắt nội dung | Thành viên | FR-AI-04 | Must |
| UC-AI-05 | Gợi ý thành viên và tài nguyên liên quan | Thành viên | FR-AI-05 | Must |

## 3. Đặc tả từng use case
> Đã tạo sẵn 2 use case đầu. Sao chép mẫu từ `_template.md` cho các use case còn lại. Cần đủ **luồng chính, luồng thay thế, tiền/hậu điều kiện**.

### UC-AI-01: Tìm kiếm ngữ nghĩa
| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-... |
| Actor | |
| Mô tả ngắn | |
| Tiền điều kiện | |
| Luồng chính | 1. ...<br>2. ...<br>3. ... |
| Luồng thay thế / ngoại lệ | 2a. ... |
| Hậu điều kiện | |
| Quy tắc nghiệp vụ | BR-... |


### UC-AI-02: Hỏi đáp với trợ lý AI về gia phả
| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-... |
| Actor | |
| Mô tả ngắn | |
| Tiền điều kiện | |
| Luồng chính | 1. ...<br>2. ...<br>3. ... |
| Luồng thay thế / ngoại lệ | 2a. ... |
| Hậu điều kiện | |
| Quy tắc nghiệp vụ | BR-... |


## 4. Quy tắc nghiệp vụ
| Mã | Quy tắc |
|---|---|
| BR-01 | |

## 5. Thiết kế bảng cơ sở dữ liệu của module
> Gợi ý tên bảng ở dưới. Với mỗi bảng, điền cột, kiểu dữ liệu, khóa, ràng buộc. **Gửi cho TV2 (Trí) gộp vào ERD tổng muộn nhất đầu tuần 2.** Quy ước: tên bảng `snake_case` số ít, bảng của module AI có tiền tố `ai_` (`architecture.md` mục 7, quyết định 1), có `created_at`, `updated_at`, `deleted_at` (xóa mềm, kiểu `TIMESTAMPTZ`). Khóa ngoại chỉ dùng trong cùng module; tham chiếu sang bảng của module khác chỉ lưu UUID có index, kiểm tra ở service (quyết định 9).

| Bảng | Mục đích | Trạng thái |
|---|---|---|
| `ai_embedding_chunk` | Đoạn văn bản + vector (pgvector) + family_id để lọc quyền | |
| `ai_chat_session` | Phiên trò chuyện | |
| `ai_chat_message` | Tin nhắn | |
| `ai_summary_cache` | Cache kết quả tóm tắt | |

Mẫu mô tả một bảng:

**Bảng `ten_bang`**
| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK | |
| created_at | TIMESTAMPTZ | NOT NULL | |
| updated_at | TIMESTAMPTZ | NOT NULL | |
| deleted_at | TIMESTAMPTZ | NULL | xóa mềm |

## 6. API của module
> Chi tiết viết trong `../../04-api/openapi/ai.yaml`. Tóm tắt endpoint ở đây.

| Phương thức | Đường dẫn | Mô tả | Quyền (role) |
|---|---|---|---|
| | /api/v1/... | | |

## 7. Màn hình liên quan
> Dán link Figma (xem `docs/05-ui/figma-links.md`).

## 8. Câu hỏi còn mở
- [ ] ...
