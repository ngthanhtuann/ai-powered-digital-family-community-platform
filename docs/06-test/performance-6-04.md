# [6-04] Báo cáo kiểm thử hiệu năng

**Người làm:** TV1 Tuấn (nhận lại từ TV2 ngày 12/11/2026)

**Reviewer:** TV5

**Nhánh chuẩn đối chiếu:** `develop` tại `5b02b03` (12/11/2026); phần AI đo lại 15/11/2026 trên `develop` tại `45f4ce8` (mục 5.3)
**Phạm vi:** API cây gia phả, tra quan hệ, bảng tin, tìm kiếm ngữ nghĩa, chatbot (Jira SCRUM-59, NFR-12).

> Mọi số liệu dưới đây đo thật trên máy ghi ở mục 2, mỗi kịch bản chạy một lần. Phần chưa đo được ghi "Chưa xác minh".

## 1. Tiêu chí

| Mã | Tiêu chí | Nguồn |
|---|---|---|
| NFR-12 | API cây gia phả dưới 1 giây với 5.000 người | `srs.md` mục NFR, Jira SCRUM-59 |
| NFR-12 | Tìm kiếm ngữ nghĩa dưới 2 giây | như trên |
| SCRUM-59 | Đo ở 10 đến 50 người dùng đồng thời; ghi trung bình, p95, tỉ lệ lỗi trước và sau tối ưu | Jira SCRUM-59 |

Thời gian so với tiêu chí lấy **p95** (95% yêu cầu nhanh hơn mức này).

## 2. Môi trường

| Thành phần | Giá trị |
|---|---|
| Máy | Laptop AMD Ryzen 7 7735HS (8 nhân, 16 luồng), RAM 15,2 GB, Windows 11 |
| Docker | Docker Desktop 29.1.3, cấp 16 CPU và 7,4 GB RAM |
| Backend | Image build từ `backend/` (Java 17.0.21), chạy bằng `docker-compose.yml` với stack riêng `-p fcseed` |
| Cơ sở dữ liệu | `pgvector/pgvector:pg16` (PostgreSQL 16.15), volume riêng của stack thử |
| Công cụ đo | k6 0.54.0 chạy bằng Docker image `grafana/k6:0.54.0`, cùng mạng Docker với backend |

k6, backend và cơ sở dữ liệu chạy **trên cùng một máy**, nên số liệu thấp hơn khi triển khai thật trên máy chủ riêng.

## 3. Dữ liệu thử

Dữ liệu **giả**, nạp bằng `docker/perf/prepare-perf.ps1` (tạo tài khoản và gia đình qua API, sau đó `seed-perf.sql` thêm hàng loạt bằng SQL):

| Loại | Số lượng |
|---|---|
| Người trong gia đình "Họ Lê (perf)" | 6.250 (5.000 người trong họ và 1.250 vợ) |
| Quan hệ cha mẹ – con | 9.998 |
| Hôn nhân | 1.250 |
| Số đời | 7 |
| Bài đăng | 2.000 |
| Sự kiện | 200 |

Lượt đo 12/11 chưa có embedding vì stack thử không có `GEMINI_API_KEY` (xem mục 6). Lượt đo AI 15/11 (mục 5.3) có khóa Gemini thật; dữ liệu demo (`docker/seed-demo.ps1`) được lập chỉ mục bằng Gemini, còn 6.250 người và 2.000 bài của gia đình perf được gán **embedding ngẫu nhiên** (8.250 chunk) bằng SQL để chỉ mục có đúng quy mô; vector câu hỏi vẫn do Gemini tạo.

## 4. Kịch bản đo

`docker/perf/k6-api.js`. Mỗi API là một scenario `constant-vus` chạy **30 giây**, các scenario chạy nối tiếp để số liệu không lẫn nhau. Mỗi lần đo chạy với 10 và với 50 người dùng đồng thời (VU). AI chỉ chạy tối đa 5 VU vì phụ thuộc LLM.

| Scenario | API | Ghi chú |
|---|---|---|
| `tree_depth3` | `GET /families/{id}/tree` | Mặc định 3 đời, giống giao diện web và mobile khi mở cây |
| `tree_depth10` | `GET /families/{id}/tree?depth=10` | Cả cây 6.250 người trong một lần (phản hồi 3,4 MB), trường hợp nặng nhất |
| `relationships` | `GET /relationships?personA=&personB=` | Hai người ngẫu nhiên trong 1.000 người |
| `feed` | `GET /posts?page=&size=20` | Trang 0 đến 4 của bảng tin |
| `ai_search` | `GET /ai/search?q=` | 4 câu hỏi mẫu; từ 15/11 chạy tốc độ cố định 60 request/phút (gói miễn phí Gemini chỉ cho 100 embedding/phút) |

Cách chạy lại (stack thử đang chạy, thay `<mạng>` bằng mạng compose, ví dụ `java_default`):

```powershell
powershell -ExecutionPolicy Bypass -File docker\perf\prepare-perf.ps1 -Api http://localhost:8080/api/v1 -DbContainer familyconnect-db
$OutputEncoding = New-Object Text.UTF8Encoding $false   # PowerShell 5.1: giữ tiếng Việt khi đẩy script vào docker
Get-Content -Raw docker\perf\k6-api.js | docker run --rm -i --network <mạng> -e BASE=http://backend:8080/api/v1 -e VUS=10 -e OUT=/out/k6-10vus.json -v "${PWD}\perf-out:/out" grafana/k6:0.54.0 run -
```

## 5. Kết quả

Đơn vị ms. "req/s" là số yêu cầu chia cho 30 giây. "Trước" là `develop` tại `5b02b03`, "Sau" là nhánh SCRUM-59 (mục 6).

### 5.1. 10 người dùng đồng thời

| API | Trước: TB / p95 / max | Sau: TB / p95 / max | req/s trước → sau | Lỗi | Đạt NFR-12 (sau) |
|---|---|---|---|---|---|
| Cây 3 đời | 202 / 251 / 414 | 80 / **120** / 251 | 49 → 124 | 0% | Đạt |
| Cả cây 6.250 người | 1.728 / 2.093 / 2.406 | 633 / **831** / 1.401 | 6 → 16 | 0% | Đạt (trước đó không đạt) |
| Quan hệ | 18 / 26 / 68 | 17 / 24 / 111 | 543 → 577 | 0% | Đạt |
| Bảng tin | 8 / 10 / 47 | 7 / 9 / 77 | 1.259 → 1.351 | 0% | Đạt |
| Tìm kiếm AI | 6 / 7 / 36 | 6 / 7 / 25 | - | 100% (503 `AI_001`, chưa có khóa) | Xem mục 5.3 |

### 5.2. 50 người dùng đồng thời

| API | Trước: TB / p95 / max | Sau: TB / p95 / max | req/s trước → sau | Lỗi | Đạt NFR-12 (sau) |
|---|---|---|---|---|---|
| Cây 3 đời | 863 / 1.188 / 1.724 | 326 / **475** / 1.084 | 59 → 154 | 0% | Đạt (trước đó không đạt) |
| Cả cây 6.250 người | 6.946 / 10.622 / 13.141 | 2.690 / **4.283** / 7.490 | 8 → 19 | 0% | **Không đạt** |
| Quan hệ | 89 / 155 / 1.484 | 79 / 139 / 260 | 558 → 628 | 0% | Đạt |
| Bảng tin | 32 / 67 / 187 | 30 / 64 / 201 | 1.570 → 1.635 | 0% | Đạt |
| Tìm kiếm AI | 6 / 7 / 21 | 6 / 7 / 22 | - | 100% (503 `AI_001`, chưa có khóa) | Xem mục 5.3 |

Không có lỗi 5xx nào ở các API không dùng AI trong mọi lần đo.

### 5.3. Tìm kiếm AI và chat với khóa Gemini (15/11/2026)

Stack riêng `-p fcperf`, dữ liệu mục 3. Tài khoản `perf` (trưởng chi toàn gia đình, tìm trên 8.250 chunk). k6 gọi backend trong mạng Docker, 60 request/phút trong 60 giây.

| Tìm kiếm AI | TB / trung vị / p95 / max (ms) | Số request | Lỗi | Đạt NFR-12 (< 2 giây) |
|---|---|---|---|---|
| Trước (`develop`) | 1.502 / 2.129 / 2.417 / 2.534 | 72 | 0% | **Không đạt** |
| Sau (mục 6.4) | 488 / 475 / **538** / 1.055 | 72 | 0% | Đạt |

Tách thời gian một lần tìm kiếm (trước khi sửa): gọi Gemini Embedding khoảng 400 ms (đo trong JVM của container), câu tìm kiếm SQL 830–970 ms, câu đếm tổng 800–870 ms.

| Chat (`/ai/chat`, gia đình demo, model `gemini-3.5-flash-lite`) | Số câu | Lỗi | TB / p95 / max (ms) |
|---|---|---|---|
| Hai tài khoản, 10 câu mỗi tài khoản, cách nhau 4 giây | 20 | 0% | khoảng 970 / 2.139 / 2.139 |

Ghi chú khi đo:

- Gọi API từ Windows bằng `localhost` bị cộng thêm khoảng 2,1 giây mỗi request (máy thử IPv6 `::1` trước, cổng Docker chỉ mở IPv4); `/actuator/health` qua `localhost` mất 2.108 ms, qua `127.0.0.1` mất 9 ms. Đo bằng script trên Windows phải dùng `127.0.0.1`; k6 chạy trong mạng Docker không bị ảnh hưởng.
- Khi chạy 5 VU liên tục sau khi sửa, 90% request trả 503 `AI_001` vì vượt hạn mức 100 embedding/phút của gói miễn phí Gemini (Gemini trả 429 `RESOURCE_EXHAUSTED`). Đây là giới hạn của gói, nên kịch bản AI chuyển sang tốc độ cố định.
- Chat trên `develop` dùng model mặc định `gemini-2.5-flash`, model này trả 404 với khóa tạo mới (Google không còn cấp cho người dùng mới), nên mọi câu chat trả 503 `AI_001`. Số liệu chat ở trên đo với `gemini-3.5-flash-lite` (mặc định của nhánh SCRUM-69, PR #124).

## 6. Phân tích và tối ưu

### 6.1. Tìm điểm chậm (EXPLAIN ANALYZE trên dữ liệu mục 3)

| Truy vấn của API cây | Thời gian | Nhận xét |
|---|---|---|
| Hậu duệ của gốc, 10 đời (`FamilyTreeRepository.findDescendants`) | 26 ms | Dùng index `ux_parent_child_pair`, `person_pkey` |
| Cạnh cha-con (`findParentEdges`) | 12 ms | Ổn |
| Tính đời (`GenerationSql`) | **963 ms** | Điểm chậm chính |

`GenerationSql` có hai vấn đề:

1. Đời của người vào họ bằng hôn nhân tính bằng subquery tương quan. Với mỗi người trong 1.251 người, subquery quét lại toàn bộ CTE `blood` (4.999 dòng, CTE không có index), tổng khoảng 6,25 triệu dòng.
2. API cây tính đời cho **cả gia đình** ở mỗi lần gọi, kể cả khi chỉ trả về khoảng 20 người (cây 3 đời).

### 6.2. Thay đổi (không đổi schema, API, kết quả)

| # | Thay đổi | File | Kiểm chứng |
|---|---|---|---|
| 1 | Đổi subquery tương quan thành `spouse_generation` gom một lần bằng JOIN | `GenerationSql.java` | Trên cả 2 gia đình của DB thử (6.303 người), kết quả giống hệt bản cũ; 950 ms còn 122 ms |
| 2 | Thêm `GenerationSql.CTE_FOR_IDS`: chỉ đi lên tổ tiên từ những người trên cây và vợ/chồng của họ; API cây dùng bản này. Danh bạ, Dashboard vẫn dùng `CTE` cả gia đình | `GenerationSql.java`, `FamilyTreeRepository.java` | So 128 người trên 40 gốc ngẫu nhiên ở 2 gia đình: 0 khác biệt so với bản cả gia đình |

`mvn -B verify` sau thay đổi: 426 test, 0 lỗi (gồm `FamilyTreeApiIntegrationTest`, `DirectoryApiIntegrationTest`, `DashboardApiIntegrationTest` dùng chung `GenerationSql`).

**Index:** các truy vấn của API cây đã chạy EXPLAIN đều dùng index sẵn có (`ux_parent_child_pair`, `person_pkey`, `idx_marriage_person1/2`); bảng tin đã có `idx_post_family_created` và đạt ngưỡng nên chưa chạy EXPLAIN. Vì vậy **không thêm index hay migration**.

### 6.3. AI (tìm kiếm, chatbot)

- Không có `GEMINI_API_KEY`: `/ai/search` trả 503 `AI_001` sau khoảng 7 ms, không làm chậm hay lỗi API khác, đúng NFR-08.
- Có khóa: số liệu ở mục 5.3; điểm chậm và cách sửa ở mục 6.4.

### 6.4. Tối ưu tìm kiếm AI (15/11/2026)

`EmbeddingChunkRepository` viết `1 - (c.embedding <=> CAST(? AS vector))` ngay trong biểu thức. Với câu prepared (JDBC), PostgreSQL parse lại chuỗi 768 số của vector câu hỏi **cho từng dòng**. Đo bằng `EXPLAIN ANALYZE` trên 8.250 chunk, generic plan:

| Truy vấn | Cast trong biểu thức | Tính vector một lần (CTE) |
|---|---|---|
| Đếm kết quả tìm kiếm | 802 ms | 39 ms |
| Top-k cho chat (cast ở cả `SELECT` và `ORDER BY`) | 1.676 ms | 63 ms |

Sửa: thêm `WITH q AS MATERIALIZED (SELECT CAST(? AS vector) AS v)` và `CROSS JOIN q` cho ba truy vấn tìm kiếm, đếm, chat (`EmbeddingChunkRepository.java`). Cả hai cách đều không dùng index HNSW vì lọc theo `family_id`, nên không mất index. Lọc trẻ em với thành viên thường (800 hồ sơ cần ẩn) sau khi sửa vẫn 98 ms. Không đổi schema, API, kết quả. `mvn -B verify` sau thay đổi: 435 test, 0 lỗi.
- Cache và giới hạn gọi: đã có bảng cache tóm tắt (`V15__create_ai_summary_cache.sql`), giới hạn chat theo giờ và rate limiter cho search, summary (`AiChatRateLimiter`, `AiSearchRateLimiter`, `SummaryRateLimiter`). Mới đọc code, chưa đo hiệu quả.

## 7. Đánh giá tiêu chí

| Tiêu chí | Kết quả |
|---|---|
| API cây dưới 1 giây với 5.000 người | **Đạt** với cây 3 đời ở cả 10 và 50 VU, và với cả cây 6.250 người ở 10 VU (p95 831 ms). **Không đạt** khi 50 VU cùng tải cả cây (p95 4,3 giây) |
| Tìm kiếm dưới 2 giây | **Đạt** sau tối ưu mục 6.4 (p95 538 ms trên 8.250 chunk, 60 request/phút); trước tối ưu không đạt (p95 2.417 ms). Chưa đo ở tải cao hơn hạn mức gói miễn phí Gemini |
| Báo cáo có số liệu cho mọi API đã đo | Đạt (cây, quan hệ, bảng tin; tìm kiếm AI và chat ở mục 5.3) |

## 8. Hạn chế và đề xuất

1. **Cả cây cùng lúc ở 50 VU:** chi phí còn lại là đọc và trả 3,4 MB JSON cho 6.250 người mỗi lần. Giao diện web và mobile không gọi kiểu này: chúng tải 3 đời rồi mở nhánh dần. Nếu cần, có thể làm task riêng: cache cây theo gia đình (xóa khi có thay đổi), hoặc giới hạn số node mỗi lần trả.
2. **AI:** tải tìm kiếm bị giới hạn bởi hạn mức Gemini (gói miễn phí 100 embedding/phút), không phải bởi hệ thống; triển khai thật cần gói trả phí hoặc cache embedding câu hỏi. Chat trên `develop` cần đổi model mặc định (PR #124). Lời gọi embedding chưa có thời gian chờ (SCRUM-104).
3. Mỗi kịch bản chạy một lần, trên một máy chạy chung k6, backend và DB. Nên đo lại trên máy triển khai (SCRUM-68) để có số liệu sát thực tế.
