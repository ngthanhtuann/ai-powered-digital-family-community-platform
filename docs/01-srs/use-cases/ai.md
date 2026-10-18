# Use case: AI: tìm kiếm, trợ lý hỏi đáp, giải thích quan hệ, tóm tắt, gợi ý

> **Người viết:** TV5 (Lê Nhựt) | **Reviewer:** TV3 (PiLo257) | **Task Jira:** SCRUM-34 | **Hạn nộp review:** Thứ Năm 15/10 (Sprint 2)
> **Trạng thái:** Nháp 
> Cách làm: tạo nhánh `docs/<mã SCRUM>-<tên-ngắn>` từ `develop`, điền vào file này, mở Pull Request vào `develop`. Xem `docs/README.md`.

## 1. Phạm vi và Actor
Module `ai` cung cấp tìm kiếm ngữ nghĩa, hỏi đáp gia phả bằng RAG, giải thích quan hệ, tóm tắt và gợi ý thành viên/tài nguyên. Module sử dụng dữ liệu qua interface của các module `genealogy`, `community` và `heritage`; không SELECT trực tiếp bảng của module khác. Module khác gọi AI qua interface Java `AiGateway`, không gọi REST nội bộ.

### Actor
- **Thành viên đã xác minh (`MEMBER`)**: sử dụng các chức năng AI trong phạm vi dữ liệu mà mình được phép xem.
- **Trưởng chi (`BRANCH_ADMIN`)**: sử dụng các chức năng AI trong phạm vi gia đình/nhánh mà quyền quản trị cho phép; không được mặc nhiên xem dữ liệu ngoài phạm vi đó.
- **Quản trị hệ thống (`SYSTEM_ADMIN`)**: có quyền gọi API theo phân quyền hệ thống; không được bỏ qua các quy tắc bảo vệ dữ liệu gia đình.
- **Dịch vụ nội bộ qua `AiGateway`**: actor hệ thống cho lời gọi từ module khác; phải truyền danh tính người dùng/ngữ cảnh quyền để AI kiểm tra quyền thay cho người dùng cuối.

Tài khoản chưa xác minh không được sử dụng AI: trả HTTP `403`, mã `PERM_002`. Khi vượt giới hạn câu hỏi: HTTP `429`, mã `RATE_001`.

## 2. Danh sách use case

| Mã UC | Tên | Actor chính | Mã FR dự kiến | Ưu tiên |
|---|---|---|---|---|
| UC-AI-01 | Tìm kiếm ngữ nghĩa | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN đã xác minh | FR-AI-01 | Must |
| UC-AI-02 | Hỏi đáp với trợ lý AI về gia phả | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN đã xác minh | FR-AI-02 | Must |
| UC-AI-03 | Giải thích quan hệ họ hàng | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN đã xác minh | FR-AI-03 | Must |
| UC-AI-04 | Tóm tắt nội dung | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN đã xác minh | FR-AI-04 | Must |
| UC-AI-05 | Gợi ý thành viên và tài nguyên liên quan | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN đã xác minh | FR-AI-05 | Must |

## 3. Đặc tả từng use case
### UC-AI-01: Tìm kiếm ngữ nghĩa
| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-AI-01 (tạm; cần đối chiếu SRS tổng) |
| Actor | Thành viên đã xác minh; trưởng chi; quản trị hệ thống |
| Mô tả ngắn | Tìm nội dung liên quan theo ý nghĩa truy vấn thay vì chỉ khớp từ khóa. |
| Tiền điều kiện | Người dùng đã đăng nhập và xác minh; có quyền xem dữ liệu thuộc `family_id`; truy vấn không rỗng. |
| Dữ liệu đầu vào | `query` tiếng Việt; `familyId` lấy từ ngữ cảnh đã xác thực, không tin giá trị do client tự khai; `limit` tùy chọn. |
| Nguồn tri thức | Các chunk đã lập chỉ mục từ dữ liệu gia phả, bài đăng/câu chuyện và tài liệu di sản mà người dùng được quyền xem. Dữ liệu nguồn lấy qua interface module tương ứng. |
| Luồng chính | 1. Người dùng nhập truy vấn. 2. API xác thực tài khoản, trạng thái xác minh và quyền truy cập. 3. Module xác định `family_id` hợp lệ từ security context. 4. Tạo embedding truy vấn với `gemini-embedding-001`, cấu hình `outputDimensionality=768`. 5. Truy vấn `ai_embedding_chunk` theo độ tương đồng và lọc `family_id`, quyền xem, bản ghi chưa xóa mềm. 6. Loại kết quả dưới ngưỡng cấu hình; sắp xếp theo độ tương đồng. 7. Trả danh sách kết quả cùng loại nguồn, mã nguồn và điểm tương đồng nếu được phép hiển thị. |
| Luồng thay thế / ngoại lệ | 2a. Chưa xác minh: HTTP 403 `PERM_002`. 2b. Vượt hạn mức: HTTP 429 `RATE_001`. 4a. Lỗi embedding/LLM hoặc dịch vụ phụ thuộc: HTTP 503 `AI_001`. 5a. Không có kết quả phù hợp: trả danh sách rỗng và thông báo “Không tìm thấy thông tin phù hợp.” 5b. Không được trả kết quả từ gia đình khác dù điểm tương đồng cao. |
| Hậu điều kiện | Không thay đổi dữ liệu nghiệp vụ; kết quả chỉ chứa tài nguyên người dùng có quyền xem. |
| Quy tắc nghiệp vụ | BR-01, BR-02, BR-03, BR-05, BR-06 |

### UC-AI-02: Hỏi đáp với trợ lý AI về gia phả
| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-AI-02 (tạm; cần đối chiếu SRS tổng) |
| Actor | Thành viên đã xác minh; trưởng chi; quản trị hệ thống |
| Mô tả ngắn | Trả lời câu hỏi về gia phả dựa trên dữ liệu được truy xuất, có nguồn tham chiếu. |
| Tiền điều kiện | Đã đăng nhập, đã xác minh, được phép xem dữ liệu thuộc gia đình; câu hỏi hợp lệ; chưa vượt hạn mức. |
| Dữ liệu đầu vào | `question`; `sessionId` tùy chọn; `familyId` và quyền lấy từ security context. |
| Nguồn tri thức | Top-k chunk của `ai_embedding_chunk`, được lọc theo `family_id`, quyền xem và chính sách riêng tư. Lịch sử chat chỉ dùng trong cùng phiên và không vượt quyền truy cập hiện tại. |
| Luồng chính | 1. Người dùng gửi câu hỏi. 2. Hệ thống kiểm tra xác minh, quyền và rate limit. 3. Truy xuất top-k chunk sau khi lọc quyền. 4. Nếu không có ngữ cảnh đủ liên quan, trả “Không tìm thấy thông tin.” và không gọi LLM. 5. Loại dữ liệu cấm gửi LLM, bao gồm mật khẩu, số điện thoại và thông tin trẻ em theo `privacy.md` mục 8. 6. Ghép prompt hệ thống, câu hỏi và ngữ cảnh đã lọc. 7. Gọi Gemini qua Spring AI. 8. Lưu user/assistant message trong `ai_chat_message` và cập nhật `ai_chat_session` nếu có phiên. 9. Trả lời kèm nguồn được dùng. |
| Luồng thay thế / ngoại lệ | 2a. Chưa xác minh: 403 `PERM_002`. 2b. Quá hạn mức: 429 `RATE_001`. 4a. Không đủ ngữ cảnh: trả đúng “Không tìm thấy thông tin.” 7a. Timeout/quota/nhà cung cấp lỗi: 503 `AI_001`; không trả lời suy đoán. 8a. Lưu lịch sử lỗi: không làm lộ câu hỏi/nội dung nhạy cảm trong log kỹ thuật. |
| Hậu điều kiện | Có câu trả lời và nguồn hoặc thông báo không tìm thấy; lịch sử được lưu nếu chính sách lưu trữ cho phép. |
| Quy tắc nghiệp vụ | BR-01, BR-02, BR-03, BR-04, BR-05, BR-06, BR-07 |

### UC-AI-03: Giải thích quan hệ họ hàng
| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-AI-03 (tạm; cần đối chiếu SRS tổng) |
| Actor | Thành viên đã xác minh; trưởng chi; quản trị hệ thống |
| Mô tả ngắn | Diễn đạt dễ hiểu quan hệ giữa hai người dựa trên kết quả thuật toán quan hệ của module genealogy. |
| Tiền điều kiện | Người dùng đã xác minh; có quyền xem cả hai hồ sơ và đường quan hệ cần thiết. |
| Dữ liệu đầu vào | `personAId`, `personBId`, ngôn ngữ đầu ra tùy chọn. |
| Nguồn tri thức | Kết quả thuật toán quan hệ trong module genealogy và các dữ kiện được phép hiển thị. |
| Luồng chính | 1. Người dùng chọn hai thành viên. 2. AI kiểm tra quyền xem hai hồ sơ. 3. Gọi interface của genealogy để lấy kết quả thuật toán quan hệ. 4. Nếu có đường quan hệ hợp lệ, gửi kết quả đã lọc cho LLM để diễn đạt lại, không yêu cầu LLM tự suy luận quan hệ. 5. Trả lời kèm đường quan hệ hoặc các bước quan hệ nguồn. |
| Luồng thay thế / ngoại lệ | 2a. Thiếu quyền: trả lỗi theo quy ước API, không tiết lộ hồ sơ. 3a. Không tìm được quan hệ: trả “Không tìm thấy thông tin.” 4a. LLM lỗi: có thể trả kết quả thuật toán ở dạng cấu trúc nếu hợp đồng API hỗ trợ; nếu không, trả 503 `AI_001`. |
| Hậu điều kiện | Không thay đổi hồ sơ hoặc quan hệ; chỉ trả kết quả đã được kiểm chứng bởi thuật toán. |
| Quy tắc nghiệp vụ | BR-01, BR-02, BR-04, BR-05, BR-08 |

### UC-AI-04: Tóm tắt nội dung
| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-AI-04 (tạm; cần đối chiếu SRS tổng) |
| Actor | Thành viên đã xác minh; trưởng chi; quản trị hệ thống |
| Mô tả ngắn | Tóm tắt một bài đăng, câu chuyện hoặc tài liệu mà người dùng có quyền xem. |
| Tiền điều kiện | Đã đăng nhập và xác minh; có quyền xem nguồn; nguồn tồn tại và chưa bị xóa. |
| Dữ liệu đầu vào | `sourceType`, `sourceId`, `maxLength` tùy chọn. |
| Nguồn tri thức | Nội dung lấy qua interface của module community/heritage; cache trong `ai_summary_cache` nếu còn hiệu lực và đúng phiên bản nguồn. |
| Luồng chính | 1. Người dùng yêu cầu tóm tắt nguồn. 2. Hệ thống xác thực quyền xem nguồn. 3. Lấy nội dung qua interface module sở hữu dữ liệu. 4. Loại dữ liệu không được gửi LLM theo `privacy.md`. 5. Kiểm tra cache theo khóa nguồn, phiên bản/nội dung và tham số tóm tắt. 6. Nếu cache hợp lệ, trả tóm tắt. 7. Nếu chưa có cache, gọi Gemini với chỉ dẫn chỉ tóm tắt nội dung nguồn, lưu cache và trả kết quả. |
| Luồng thay thế / ngoại lệ | 2a. Không có quyền hoặc nguồn không tồn tại: trả lỗi theo conventions, không tiết lộ sự tồn tại của tài nguyên bị cấm. 4a. Sau khi lọc không còn nội dung đủ để tóm tắt: trả “Không tìm thấy thông tin.” 7a. LLM lỗi: 503 `AI_001`. |
| Hậu điều kiện | Tóm tắt được trả về; cache có thể được tạo/cập nhật; dữ liệu nguồn không bị sửa. |
| Quy tắc nghiệp vụ | BR-01, BR-02, BR-04, BR-05, BR-09 |

### UC-AI-05: Gợi ý thành viên và tài nguyên liên quan
| Mục | Nội dung |
|---|---|
| Mã FR liên quan | FR-AI-05 (tạm; cần đối chiếu SRS tổng) |
| Actor | Thành viên đã xác minh; trưởng chi; quản trị hệ thống |
| Mô tả ngắn | Đề xuất thành viên hoặc tài nguyên liên quan dựa trên nội dung và ngữ cảnh được phép truy cập. |
| Tiền điều kiện | Đã đăng nhập và xác minh; có quyền xem phạm vi dữ liệu đề xuất. |
| Dữ liệu đầu vào | `contextType` và `contextId` tùy chọn; `limit`; bộ lọc loại tài nguyên tùy chọn. |
| Nguồn tri thức | Chỉ mục embedding và dữ liệu thành viên/tài nguyên lấy qua interface module sở hữu; luôn lọc theo `family_id` và quyền xem. |
| Luồng chính | 1. Người dùng mở một hồ sơ/tài nguyên hoặc yêu cầu gợi ý. 2. Hệ thống kiểm tra quyền và hạn mức. 3. Tạo truy vấn từ ngữ cảnh được phép. 4. Tìm top-k ứng viên trong phạm vi `family_id`, loại bỏ bản ghi bị xóa, bản ghi người dùng không có quyền xem và nội dung nhạy cảm. 5. Xếp hạng và trả danh sách kèm lý do ngắn dựa trên dữ kiện có thật. |
| Luồng thay thế / ngoại lệ | 2a. Chưa xác minh: 403 `PERM_002`. 2b. Quá hạn mức: 429 `RATE_001`. 4a. Không có ứng viên phù hợp: trả danh sách rỗng. 5a. Không đủ dữ kiện để giải thích lý do: chỉ trả tài nguyên, không tự tạo lý do. 4b. Lỗi dịch vụ: 503 `AI_001`. |
| Hậu điều kiện | Không thay đổi dữ liệu nguồn; mọi gợi ý đều nằm trong phạm vi được phép xem. |
| Quy tắc nghiệp vụ | BR-01, BR-02, BR-03, BR-05, BR-10 |

## 4. Quy tắc nghiệp vụ
| Mã | Quy tắc |
|---|---|
| BR-01 | Chỉ tài khoản đã xác minh mới được dùng chức năng AI; nếu chưa xác minh, trả HTTP 403, mã `PERM_002`. |
| BR-02 | Mọi truy xuất phải lọc theo `family_id` và quyền xem trước khi ghép ngữ cảnh hoặc trả kết quả. `family_id` phải lấy/đối chiếu từ security context; không tin dữ liệu client tự khai. |
| BR-03 | Nếu không có kết quả phù hợp hoặc ngữ cảnh không chứa câu trả lời, trả chính xác `Không tìm thấy thông tin.`; không bịa hoặc suy luận ngoài dữ liệu. |
| BR-04 | Giới hạn số câu hỏi theo chính sách cấu hình; vượt giới hạn trả HTTP 429, mã `RATE_001`. |
| BR-05 | Chỉ module `ai` đọc/ghi các bảng `ai_*`; module khác gọi qua Java interface `AiGateway`, không join trực tiếp và không gọi REST nội bộ. |
| BR-06 | Khi truy xuất RAG, áp dụng lọc `family_id`, quyền xem và ngưỡng tương đồng trước khi ghép prompt. Ngưỡng 0,88 hiện là giá trị thử nghiệm ban đầu, chưa được xác nhận tối ưu. |
| BR-07 | Không gửi mật khẩu, số điện thoại hoặc thông tin trẻ em vào ngữ cảnh LLM; tuân thủ danh sách cho phép/cấm tại `docs/01-srs/privacy.md` mục 8. Dùng dữ liệu giả trong phát triển; không dùng gói miễn phí với dữ liệu gia đình thật. |
| BR-08 | Giải thích quan hệ phải dựa trên kết quả thuật toán của module genealogy; LLM chỉ diễn đạt lại, không tự suy luận quan hệ. |
| BR-09 | Cache tóm tắt phải gắn với nguồn và phiên bản/nội dung nguồn; vô hiệu hóa khi nguồn thay đổi hoặc bị xóa. |
| BR-10 | Gợi ý không được vượt phạm vi quyền xem và không được tạo ra dữ kiện/lý do không có trong nguồn. |
| BR-11 | Lỗi phụ thuộc AI hoặc timeout trả HTTP 503, mã `AI_001`; lỗi đầu vào dùng `AI_002` theo API conventions. Không ghi API key hoặc dữ liệu nhạy cảm vào log. |

## 5. Thiết kế bảng cơ sở dữ liệu
Quy ước chung: schema `public`; khóa chính UUID; thời gian `TIMESTAMPTZ`; các bảng có `id`, `created_at`, `updated_at`, `deleted_at`, `created_by`, `updated_by`. Không tạo khóa ngoại sang bảng module khác; `family_id`, `source_id`, `user_id` là UUID và được kiểm tra qua service/interface. Chỉ module `ai` đọc/ghi các bảng này.

### 5.1. `ai_embedding_chunk`
| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK, NOT NULL | ID chunk |
| family_id | UUID | NOT NULL, INDEX | Phạm vi gia đình |
| source_type | VARCHAR(40) | NOT NULL | `PERSON`, `POST`, `STORY`, `DOCUMENT` |
| source_id | UUID | NOT NULL, INDEX | ID nguồn từ module sở hữu; không FK liên module |
| chunk_no | INTEGER | NOT NULL, CHECK (chunk_no >= 0) | Thứ tự chunk trong nguồn |
| content | TEXT | NOT NULL | Nội dung đã lọc dữ liệu nhạy cảm |
| embedding | VECTOR(768) | NOT NULL | Vector từ `gemini-embedding-001`, `outputDimensionality=768` |
| content_hash | VARCHAR(64) | NOT NULL | Hash để phát hiện nội dung thay đổi |
| metadata | JSONB | NOT NULL DEFAULT '{}' | Metadata không nhạy cảm |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Ngày tạo |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Ngày cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |
| created_by | UUID | NULL, INDEX | Người tạo/tác vụ tạo |
| updated_by | UUID | NULL | Người cập nhật |

Ràng buộc/index đề xuất:
- `UNIQUE (family_id, source_type, source_id, chunk_no)` cho các chunk chưa xóa mềm (dùng partial unique index với `deleted_at IS NULL`).
- B-tree index `(family_id, source_type, source_id)` và `(family_id, deleted_at)`.
- HNSW cosine index trên `embedding` (ví dụ `USING hnsw (embedding vector_cosine_ops)`), kết hợp điều kiện lọc `family_id`; kiểm tra kế hoạch truy vấn với dữ liệu thực tế.
- `vector(768)` theo ADR; nếu đánh giá tiếng Việt không đạt, thử `1536` và cập nhật schema/ERD trước khi triển khai.

### 5.2. `ai_chat_session`
| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK, NOT NULL | ID phiên |
| family_id | UUID | NOT NULL, INDEX | Phạm vi gia đình |
| user_id | UUID | NOT NULL, INDEX | Chủ phiên; không FK liên module |
| title | VARCHAR(200) | NULL | Tiêu đề phiên |
| status | VARCHAR(20) | NOT NULL DEFAULT 'ACTIVE', CHECK status IN ('ACTIVE','ARCHIVED') | Trạng thái |
| last_message_at | TIMESTAMPTZ | NULL | Thời điểm có tin nhắn gần nhất |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Ngày tạo |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Ngày cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |
| created_by | UUID | NULL | Người tạo |
| updated_by | UUID | NULL | Người cập nhật |

Index: `(family_id, user_id, updated_at DESC)` và `(user_id, deleted_at)`.

### 5.3. `ai_chat_message`
| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK, NOT NULL | ID tin nhắn |
| session_id | UUID | NOT NULL, FK -> `ai_chat_session.id` | Phiên trong cùng module |
| role | VARCHAR(20) | NOT NULL, CHECK role IN ('USER','ASSISTANT','SYSTEM') | Vai trò tin nhắn |
| content | TEXT | NOT NULL | Nội dung; áp dụng chính sách lưu trữ và riêng tư |
| sources | JSONB | NOT NULL DEFAULT '[]' | Danh sách nguồn đã dùng, không chứa nội dung nhạy cảm |
| model_name | VARCHAR(100) | NULL | Model LLM dùng cho phản hồi |
| input_tokens | INTEGER | NULL, CHECK input_tokens >= 0 | Token đầu vào nếu nhà cung cấp trả |
| output_tokens | INTEGER | NULL, CHECK output_tokens >= 0 | Token đầu ra nếu nhà cung cấp trả |
| latency_ms | INTEGER | NULL, CHECK latency_ms >= 0 | Độ trễ tổng |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Ngày tạo |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Ngày cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |
| created_by | UUID | NULL | Người tạo |
| updated_by | UUID | NULL | Người cập nhật |

Index: `(session_id, created_at)`; FK nội bộ `session_id` có `ON DELETE RESTRICT` hoặc xóa mềm theo chính sách. Không lưu API key hoặc dữ liệu cấm vào metadata/log.

### 5.4. `ai_summary_cache`
| Cột | Kiểu | Khóa / Ràng buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK, NOT NULL | ID cache |
| family_id | UUID | NOT NULL, INDEX | Phạm vi gia đình |
| source_type | VARCHAR(40) | NOT NULL | Loại nguồn |
| source_id | UUID | NOT NULL, INDEX | ID nguồn; không FK liên module |
| source_version_hash | VARCHAR(64) | NOT NULL | Hash phiên bản/nội dung nguồn |
| summary | TEXT | NOT NULL | Nội dung tóm tắt đã lọc |
| model_name | VARCHAR(100) | NOT NULL | Model tạo tóm tắt |
| expires_at | TIMESTAMPTZ | NOT NULL | Hạn cache |
| created_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Ngày tạo |
| updated_at | TIMESTAMPTZ | NOT NULL DEFAULT now() | Ngày cập nhật |
| deleted_at | TIMESTAMPTZ | NULL | Xóa mềm |
| created_by | UUID | NULL | Người tạo/tác vụ |
| updated_by | UUID | NULL | Người cập nhật |

Index: `(family_id, source_type, source_id, source_version_hash)` và `(expires_at)`; unique partial index theo khóa cache khi `deleted_at IS NULL`.

> **Gửi TV2 (Trí):** đưa 4 bảng vào ERD tổng. Chú ý `ai_chat_message.session_id` là FK nội bộ; các UUID tham chiếu module khác không tạo FK.

## 6. API của module
Đặc tả chi tiết nằm tại `docs/04-api/openapi/ai.yaml`.

| Phương thức | Đường dẫn | Mô tả | Quyền |
|---|---|---|---|
| GET | `/api/v1/search` | Tìm kiếm ngữ nghĩa | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN đã xác minh |
| POST | `/api/v1/ai/chat` | Hỏi đáp RAG | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN đã xác minh |
| POST | `/api/v1/ai/relationship-explanations` | Diễn giải quan hệ đã tính | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN đã xác minh |
| POST | `/api/v1/ai/summaries` | Tóm tắt nội dung | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN đã xác minh |
| GET | `/api/v1/ai/recommendations` | Gợi ý thành viên/tài nguyên | MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN đã xác minh |

Các endpoint đều cần bearer JWT. Trường `familyId` trong query/body nếu được nhận chỉ là gợi ý/phạm vi yêu cầu; server phải đối chiếu với quyền của người dùng và không được dùng nó để mở rộng quyền truy cập. API không được lộ dữ liệu ngoài quyền người gọi.

## 7. Màn hình liên quan
Chưa có link Figma/wireframe trong tài liệu đầu vào. Bổ sung tại `docs/05-ui/figma-links.md` khi nhóm cung cấp.