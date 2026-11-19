# Báo cáo cuối kỳ FamilyConnect: chương 1, 2, 6 và Other comments (SCRUM-71)

**Người viết:** TV1 Tuấn | **Reviewer:** TV4 | **Đối chiếu:** `develop` tại `c558794` (19/11/2026)

Tài liệu này là phần TV1 phụ trách trong dàn ý `report-outline.md`. Chương 3 (phân tích, thiết kế), 4 (cài đặt) và 5 (kiểm thử) lấy từ các tài liệu đã có, liệt kê ở mục 4. Bảng đối chiếu đề tài nằm ở `requirements-traceability.md`.

## 1. Mở đầu

**Bối cảnh.** Gia phả của nhiều dòng họ Việt Nam vẫn ghi trên giấy hoặc bảng tính rời rạc; con cháu ở xa ít có kênh chung để biết tin họ hàng, cách xưng hô và lịch sử dòng họ.

**Mục tiêu.** Xây dựng nền tảng cộng đồng gia đình số: cây gia phả nhiều thế hệ, tra cứu quan hệ và xưng hô, mạng xã hội nội bộ (bài đăng, bình luận, sự kiện), kho di sản, danh bạ nghề nghiệp, thống kê, và trợ lý AI trả lời trên chính dữ liệu của dòng họ.

**Phạm vi.** Web (Next.js), Mobile Android (Expo), backend Spring Boot dạng modular monolith, PostgreSQL + pgvector, Gemini API. Triển khai bằng Docker Compose; High Availability ở mức cơ bản (2 bản backend sau Nginx). Ngoài phạm vi: thanh toán, push notification, iOS bản phát hành.

## 2. Cơ sở lý thuyết và liên hệ với dự án

| Lĩnh vực | Lý thuyết chính | Áp dụng trong FamilyConnect |
|---|---|---|
| Software Engineering | Quy trình Scrum: sprint, backlog, review, nghiệm thu theo tiêu chí hoàn thành | Các sprint trên Jira (SCRUM-1 đến SCRUM-132), nhánh Git theo mã task, Pull Request có reviewer chéo, CI chạy `mvn -B verify` và build web (`docs/00-process/quy-trinh-lam-viec.md`) |
| OOAD | Phân tích use case, mô hình miền, đóng gói, đa hình, nguyên lý SOLID | Use case và quy tắc nghiệp vụ BR theo module (`docs/01-srs/use-cases/`), sơ đồ lớp (`class-diagram.md`); service tách Controller, Service, Repository, DTO |
| Software Architecture | Modular monolith, ranh giới module, phụ thuộc qua interface (port/adapter) | 5 module Auth/Admin, Genealogy, Community/Events, Heritage/Directory/Dashboard, AI; gọi nhau chỉ qua interface như `MembershipQuery`, `GenealogyPort`, `AiGateway` (`architecture.md`) |
| Enterprise Information Systems | Hệ thống nhiều vai trò, phân quyền, nhật ký kiểm toán, sao lưu, cấu hình vận hành | 4 vai trò, duyệt thành viên, audit log, backup/restore, cấu hình hệ thống áp dụng trong 1 phút |
| Database Design | Chuẩn hóa, khóa, ràng buộc, chỉ mục, migration có phiên bản | PostgreSQL 16, Flyway V1–V24, xóa mềm `deleted_at`, khóa ngoại chỉ trong module, ERD và từ điển dữ liệu (`docs/03-database/`) |
| Graph Data Modeling | Đồ thị có hướng, duyệt BFS, tổ tiên chung gần nhất (LCA), truy vấn đệ quy | Người là đỉnh, quan hệ cha mẹ – con và hôn nhân là cạnh; thuật toán xưng hô dựa trên LCA tối đa 30 đời, CTE đệ quy cho phạm vi "Con cháu của tôi" (`algorithm-relationship.md`, `genealogy-report.md`) |
| HCI | Thiết kế lấy người dùng làm trung tâm, nhất quán, phản hồi trạng thái, khả năng tiếp cận | Design system dùng token màu, cỡ chữ; mọi màn hình có trạng thái tải, rỗng, lỗi; bố cục 360/768/1440 px; nút tối thiểu 44 px (`design-system.md`) |
| Mobile Application Development | Ứng dụng đa nền tảng, điều hướng stack/drawer, lưu token an toàn | Expo SDK 58, React Navigation, token trong `expo-secure-store` (`mobile.md`, `adr-mobile.md`) |
| RESTful API Design | Tài nguyên, phương thức HTTP, mã trạng thái, phân trang, đặc tả OpenAPI | Tiền tố `/api/v1`, envelope `success/data/error`, mã lỗi chung, OpenAPI theo module; kiểu TypeScript sinh từ OpenAPI (`conventions.md`) |
| Authentication & Authorization | JWT, refresh token, băm mật khẩu, RBAC, chống IDOR | JWT + refresh token, BCrypt, giới hạn đăng nhập; RBAC kiểm tra ở service; mọi truy vấn lọc theo `family_id`; test ma trận quyền và IDOR |
| Software Testing | Kiểm thử đơn vị, tích hợp, hồi quy, hiệu năng, kiểm thử theo rủi ro | 484 test backend (JUnit, Mockito, test tích hợp trên PostgreSQL thật); đo tải k6; kiểm thử hồi quy toàn hệ thống (`docs/06-test/`) |
| Information Retrieval | Biểu diễn vector, độ tương đồng cosine, chỉ mục ANN, đánh giá theo top-k | Embedding `gemini-embedding-001` 768 chiều, pgvector HNSW, ngưỡng cosine; tìm kiếm ngữ nghĩa đạt 15/15 truy vấn (`semantic-search-evaluation.md`) |
| LLM Integration | Retrieval-Augmented Generation, prompt có ràng buộc, chống bịa (grounding), trích nguồn | Chỉ đưa ngữ cảnh người hỏi được xem; quan hệ do thuật toán tính sẵn; trả "Không tìm thấy thông tin." khi thiếu dữ kiện; chat đạt 40/40 câu (`ai-quality-scrum-69.md`, `ai-rag.md`) |

## 3. Kết luận

**Đã làm được.** Toàn bộ mục chức năng c) của đề tài có trên Web; phần lớn có trên Mobile (bảng đối chiếu `requirements-traceability.md`). Thuật toán xưng hô xử lý nội/ngoại, dâu/rể, con nuôi, con riêng, hôn nhân đã ly hôn. Trợ lý AI trả lời có trích nguồn, hiểu câu hỏi về chính người hỏi ("Tôi là ai?"). Mạng xã hội nội bộ có phạm vi người xem theo dòng họ, chi hoặc con cháu, trả lời bình luận và thông báo.

**Hạn chế.**
- High Availability mức cơ bản: một PostgreSQL, chưa có bản sao cơ sở dữ liệu.
- Chưa có push notification; thông báo chỉ trong ứng dụng.
- Mobile chưa có Dashboard, quản trị, người tiêu biểu; một số màn hình mới chỉ kiểm tra biên dịch, chưa thử trên thiết bị.
- Chất lượng AI đo trên dữ liệu giả của một dòng họ; chưa đo trên dữ liệu thật.
- Chi phí LLM chưa có số liệu token thực tế (chỉ ước tính).

**Hướng phát triển.** Bản sao PostgreSQL và lưu tệp trên object storage; push notification (Expo Notifications); nhập/xuất GEDCOM; ghi lượng token để tính chi phí thật; bản iOS.

## Other comments

**Quyền riêng tư dữ liệu (`privacy.md`).**
- Trẻ dưới 16 tuổi: thành viên thường chỉ thấy tên, quan hệ, năm sinh; không có tài khoản riêng; không đưa vào ngữ cảnh gửi AI.
- Người đã mất: hiển thị họ tên, năm sinh, ngày mất, nơi an táng cho thành viên đã xác minh; không lưu nguyên nhân tử vong.
- Thông tin cá nhân của người đã có tài khoản chỉ chính chủ sửa (BR-GEN-20); số điện thoại chỉ chủ và quản trị thấy.
- Dữ liệu gửi Gemini không gồm email, số điện thoại, địa chỉ chi tiết; khi dùng gói miễn phí chỉ dùng dữ liệu giả.

**Rủi ro.**
- Phụ thuộc dịch vụ LLM bên ngoài: lỗi hoặc hết quota thì trả `AI_001`, các chức năng khác vẫn chạy.
- Một máy chủ cơ sở dữ liệu là điểm lỗi đơn; giảm thiểu bằng sao lưu và khôi phục (`backup-restore.md`).
- Sai sót dữ liệu gia phả do người nhập dẫn tới xưng hô sai; giảm thiểu bằng phân quyền sửa và nhật ký kiểm toán.

**High Availability mức cơ bản.** `docker-compose.ha.yml` chạy 2 bản backend sau Nginx, tự khởi động lại, giới hạn tần suất dùng chung qua PostgreSQL; thử tắt một bản backend: 30/30 yêu cầu vẫn trả HTTP 200 (`high-availability-7-11.md`).

**Chi phí LLM.** Ước tính theo bảng giá Gemini ngày 17/11/2026 và giả định 3.000 token vào, 300 token ra mỗi câu hỏi: khoảng 0,00165 USD/câu, 16,5 USD cho 10.000 câu/tháng (`ai-report-scrum-80.md`, PR #148). Chỉ lập lại embedding khi nội dung đổi (băm nội dung).

## 4. Nguồn cho chương 3, 4, 5

| Chương | Tài liệu |
|---|---|
| 3. Phân tích và thiết kế | `docs/01-srs/srs.md`, `docs/01-srs/use-cases/*.md`, `docs/02-design/architecture.md`, `class-diagram.md`, `business-process.md`, `docs/03-database/erd.md`, `data-dictionary.md`, `docs/04-api/openapi/*.yaml` |
| 4. Cài đặt | `genealogy-report.md` (TV1), báo cáo module của TV2–TV5, `docs/02-design/ai-rag.md`, `algorithm-relationship.md` |
| 5. Kiểm thử và đánh giá | `docs/06-test/test-plan.md`, `regression-7-01.md`, `performance-6-04.md`, `security-config-6-05.md`, `high-availability-7-11.md`, `ai-quality-scrum-69.md` |
| Demo | `demo-script.md` (SCRUM-72) |
