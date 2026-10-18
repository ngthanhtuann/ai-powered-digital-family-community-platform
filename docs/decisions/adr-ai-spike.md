# ADR: Spike AI, RAG, Spring AI, pgvector

> **Người làm:** TV5 (Lê Nhựt) | **Reviewer:** TV3 (PiLo257) | **Task Jira:** SCRUM-24
> **Ngày:** 09/10/2026 (chốt `vector(768)`), cập nhật 14/10/2026 (số liệu đo) và 18/10/2026 (phiên bản Spring Boot, mục 4) | **Người quyết định:** TV5 đề xuất, TV1 chốt (`erd.md` X12)
> **Trạng thái:** Đã merge vào `develop`; chọn Gemini ở mức sơ bộ (còn bảng đánh giá top-3 và chấm chất lượng câu trả lời, mục 3.2 và 3.3) | **Ngày thử nghiệm:** số liệu đo được ghi ngày 14/10/2026 (commit `d8c7417`, SCRUM-34); giá tra ngày 09/10/2026. TV5 xác nhận lại ngày chạy thực tế nếu khác.
> Mã thử nghiệm nằm ở nhánh `spike/SCRUM-24-rag` (không merge vào `develop`). **Không có API key trong repo.** Thiết kế chi tiết RAG viết ở `ai-rag.md` (SCRUM-34, Sprint 2).

## 1. Bối cảnh

FamilyConnect cần chatbot hỏi đáp gia phả và tìm kiếm ngữ nghĩa bằng tiếng Việt (FR-AI trong `srs.md`). Spike này kiểm tra ba việc: tạo embedding tiếng Việt, lưu và tìm kiếm bằng pgvector, gọi LLM với ngữ cảnh đính kèm. Từ đó chọn nhà cung cấp LLM.

## 2. Môi trường thử

| Hạng mục | Giá trị |
|---|---|
| Nhà cung cấp | Google Gemini API (gói miễn phí) |
| Model embedding | `gemini-embedding-001` |
| Model LLM | `gemini-3.8-flash` |
| Thư viện | Spring AI `2.0.1` (BOM); pgvector extension chạy trong image `pgvector/pgvector:pg18` |
| Dữ liệu thử | 10 câu tiếng Việt về gia phả (dữ liệu giả, không dùng dữ liệu thật của gia đình) |
| Khóa API | Biến môi trường `GEMINI_API_KEY`, không commit |

## 3. Kết quả

### 3.1. Embedding tiếng Việt

| Chỉ số | Kết quả |
|---|---|
| Số chiều mặc định của model | 3072 |
| Thời gian tạo embedding trung bình | 683,5 ms (số lần đo: 10) |

### 3.2. Tìm kiếm ngữ nghĩa (10 câu, lấy 3 câu gần nhất)

| Chỉ số | Kết quả |
|---|---|
| Thời gian tìm kiếm trung bình | 588,3 ms |
| P95 | 628,2 ms |
| Thời gian trên đã gồm bước tạo embedding cho câu hỏi chưa? | có |

Chất lượng tiếng Việt: Chất lượng truy xuất tiếng Việt: đã chạy 10 truy vấn với `topK=3`. Điểm tương đồng top-1 lần lượt là 0,8882; 0,8940; 0,8937; 0,8386; 0,8940; 0,8250; 0,8697; 0,9134; 0,8578; 0,9501. Các kết quả top-1 liên quan đến câu hỏi đã được quan sát trong quá trình thử nghiệm, nhưng chưa có bảng đối chiếu đầy đủ cả 3 kết quả với đáp án kỳ vọng cho cả 10 câu; vì vậy chưa kết luận tỷ lệ đúng top-3 hoặc đánh dấu “Đạt”. Ngưỡng 0,88 là giá trị thử nghiệm ban đầu, chưa được xác nhận tối ưu.

### 3.3. Gọi LLM với ngữ cảnh

| Chỉ số | Kết quả |
|---|---|
| Độ trễ trả lời (đo từ lúc gửi đến khi nhận đủ) | Có 3 lần đo RAG hoàn chỉnh đã ghi nhận:<br>- Lần 1: retrieval 798,6 ms; LLM 5.208,9 ms; tổng 6.007,6 ms.<br>- Lần 2: retrieval 521,7 ms; LLM 17.904,4 ms; tổng 18.426,0 ms.<br>- Lần 3: retrieval 695,6 ms; LLM 41.556,8 ms; tổng 42.252,5 ms.<br>Trung bình 3 lần: retrieval 672,0 ms; LLM 21.556,7 ms; tổng 22.228,7 ms. Chưa đủ mẫu để xem đây là benchmark ổn định; P95 chưa được dùng làm kết luận vì chỉ có 3 mẫu. |
| Chất lượng câu trả lời tiếng Việt | Chưa có bảng đánh giá có cấu trúc cho 5 câu mẫu; không kết luận chất lượng đạt/chưa đạt khi chưa chấm từng câu. |
| Hành vi khi ngữ cảnh không có thông tin | Prompt đã được thiết kế yêu cầu trả đúng “Không tìm thấy thông tin.” khi context không đủ; chưa có bộ kiểm thử xác nhận có hệ thống cho trường hợp này. Có ghi nhận một số lần chạy RAG trả “Không tìm thấy thông tin.”, nhưng chưa đủ để khẳng định tỷ lệ đúng. |

### 3.4. Chi phí ước tính

| Hạng mục | Giá |
|---|---|
| LLM, đầu vào | $0,75 / 1 triệu token |
| LLM, đầu ra | $3,75 / 1 triệu token |
| Mức giá áp dụng đến | 31/12/2026; từ 01/01/2027 tăng gấp đôi ($1,50 / $7,50) |
| Gói miễn phí | Đầu vào và đầu ra miễn phí, nhưng nội dung được Google dùng để cải thiện sản phẩm |
| Nguồn | https://ai.google.dev/gemini-api/docs/pricing (tra ngày 09/10/2026; giá trên áp dụng cho dòng Gemini Flash 3.6, 3.7, 3.8; `gemini-3.8-flash`) |

Ước tính cho nhóm: chưa thể tính chi phí tháng đáng tin cậy vì spike chưa ghi số token đầu vào/đầu ra trung bình và số lượt hỏi dự kiến mỗi ngày. Công thức khi có số liệu:
`Chi phí tháng = (input_tokens_tháng / 1.000.000 × đơn giá input) + (output_tokens_tháng / 1.000.000 × đơn giá output)`.
Gói miễn phí đã gặp lỗi HTTP 429 do vượt hạn mức `generate_content_free_tier_requests` của model `gemini-3.8-flash`; thông báo tại thời điểm thử yêu cầu chờ khoảng 7 giờ 41 phút. Đây là hạn mức tài khoản/model tại lần chạy đó, không ghi thành hạn mức cố định mỗi ngày/phút. Cần kiểm tra trang hạn mức hiện hành của tài khoản trước khi công bố con số RPM/RPD.

## 4. Phát hiện kỹ thuật cần xử lý

1. **Số chiều vector.** `gemini-embedding-001` trả 3072 chiều mặc định, nhưng pgvector chỉ tạo được chỉ mục HNSW và IVFFlat cho kiểu `vector` tối đa 2000 chiều. Hai cách xử lý:
   - Chọn `outputDimensionality` 768 hoặc 1536 khi gọi API (đề xuất, vì nhẹ và đủ cho gia phả), hoặc
   - Dùng kiểu `halfvec(3072)` (chỉ mục tối đa 4000 chiều).
   - **Quyết định cho thiết kế (đã chốt 9/10/2026):** dùng `outputDimensionality = 768`, cột `vector(768)` với chỉ mục HNSW. Lý do: nằm trong giới hạn 2000 chiều của chỉ mục, nhẹ nhất về lưu trữ và tốc độ, đủ cho dữ liệu gia phả cỡ vài trăm đến vài nghìn đoạn. SCRUM-34 ghi vào `ai-rag.md`; nếu bảng đánh giá 10 câu tiếng Việt ở Sprint 2 không đạt thì thử lại với 1536.
2. **Tên bảng.** Bảng vector đặt là `ai_embedding_chunk` (tiền tố `ai_` theo `architecture.md` mục 7), không dùng `embedding_chunk`.
3. **Phiên bản Spring Boot (chốt 18/10/2026).** Spring AI 2.0.x (bản dùng trong spike) chỉ chạy trên Spring Boot 4 (https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now, tra ngày 18/10/2026), trong khi `backend/pom.xml` đang là Spring Boot 3.3.5. Quyết định: nâng Spring Boot lên 4.x để dùng Spring AI 2.0.x như spike đã đo. Việc nâng `pom.xml` (kèm springdoc bản tương thích) làm ở một task code đầu Sprint 3, có chạy test; không gộp vào task tài liệu. Dự án chạy PostgreSQL bằng image `pgvector/pgvector:pg16` (`docker-compose.yml`, `ci.yml`), khác bản `pg18` dùng khi spike.

## 5. Rủi ro

| Rủi ro | Mức | Cách giảm |
|---|---|---|
| Gói miễn phí giới hạn số yêu cầu, có thể hết hạn mức khi demo | Cao | Cache câu trả lời (`ai_summary_cache`), giới hạn số câu hỏi mỗi người mỗi giờ (RATE_001), có phương án chuyển nhà cung cấp |
| Dữ liệu gia đình gửi ra dịch vụ bên ngoài; theo trang giá của Google, nội dung gửi qua gói miễn phí được dùng để cải thiện sản phẩm | Cao | Chỉ gửi ngữ cảnh đã lọc theo `family_id` và quyền xem; không gửi mật khẩu, số điện thoại, thông tin trẻ dưới 16 tuổi (`privacy.md`); dùng dữ liệu giả khi thử; chuyển sang gói trả phí (không dùng dữ liệu để huấn luyện) trước khi chạy với dữ liệu thật |
| LLM bịa thông tin ngoài ngữ cảnh | Trung bình | Prompt chỉ cho trả lời theo ngữ cảnh; ngưỡng điểm tối thiểu; kèm nguồn trong câu trả lời |
| Độ trễ: tìm kiếm khoảng 0,6 giây, nhưng gọi LLM đo được 5 đến 42 giây (trung bình 21,6 giây, 3 lần đo) | Cao | Giao diện chạy bất đồng bộ, có chỉ báo đang tải; đặt thời gian chờ tối đa cho lời gọi LLM (đề xuất 30 giây, cấu hình được), quá thời gian trả 503 `AI_001`; đo lại khi có gói trả phí |
| Phụ thuộc một nhà cung cấp | Thấp | Gọi qua interface của Spring AI để đổi nhà cung cấp bằng cấu hình |
| Giá LLM tăng gấp đôi từ 01/01/2027 | Trung bình | Tính chi phí theo giá mới trong báo cáo cuối kỳ; cân nhắc giới hạn số câu hỏi |

## 6. Quyết định

**Quyết định sơ bộ chọn Gemini, xác nhận sau khi hoàn tất đo ở Sprint 2.** Google Gemini dự kiến là nhà cung cấp LLM và embedding cho giai đoạn đồ án, tích hợp qua Spring AI.

Lý do: đã tích hợp và đo thành công với tiếng Việt; tìm kiếm ngữ nghĩa nhanh (khoảng 0,6 giây); có gói miễn phí phù hợp giai đoạn phát triển. Độ trễ gọi LLM ở gói miễn phí còn cao và dao động (5 đến 42 giây), nên chưa coi là đạt; cần đo lại và áp dụng thời gian chờ ở mục 5.

Điều kiện đi kèm: giải quyết số chiều vector (mục 4), thực hiện các biện pháp giảm rủi ro ở mục 5.

## 7. Việc tiếp theo

- SCRUM-34: viết `ai-rag.md` (phân đoạn dữ liệu, prompt hệ thống, ngưỡng điểm, giới hạn, quyền dữ liệu) dựa trên quyết định này.
- Bảng `ai_embedding_chunk` đưa vào ERD (SCRUM-28) với cột `vector(768)`.
- TV5: lập bảng đối chiếu cho 10 câu hỏi (câu hỏi, 3 kết quả gần nhất, đáp án kỳ vọng, đúng/sai) và chấm 5 câu trả lời mẫu; chỉ đánh dấu "Đạt" khi có bảng này.
