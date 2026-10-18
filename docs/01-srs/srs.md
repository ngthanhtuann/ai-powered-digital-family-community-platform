# SRS: Đặc tả yêu cầu phần mềm FamilyConnect

> **Người viết:** TV1 (Tuấn Nguyễn Thanh) | **Reviewer:** TV4 (Huy Quốc) | **Task Jira:** SCRUM-14 | **Hạn nộp review:** Thứ Năm 8/10
> **Phiên bản:** 0.3 (các mục nhóm tự quyết đã chốt ngày 9/10/2026; còn chờ giảng viên xác nhận ở mục 3 và báo giảng viên mục 7.2) | **Ngày:** 9/10/2026
> **Nguyên tắc viết:** mọi yêu cầu trong tài liệu này **bám theo đề tài giảng viên giao** (mục 3.2 a đến g). Phần nhóm tự bổ sung được ghi rõ là "bổ sung của nhóm".

---

## 1. Giới thiệu

### 1.1 Mục đích
Tài liệu này đặc tả yêu cầu của hệ thống **FamilyConnect: Nền tảng Cộng đồng Gia đình số tích hợp Trí tuệ nhân tạo** (tiếng Anh: *AI-powered Digital Family Community Platform*). Tài liệu là căn cứ chung để nhóm phát triển thiết kế, lập trình, kiểm thử và để giảng viên đối chiếu sản phẩm với đề tài. Đối tượng đọc: 5 thành viên nhóm và giảng viên hướng dẫn.

### 1.2 Phạm vi sản phẩm
FamilyConnect là nền tảng số **kết hợp quản lý gia phả với các dịch vụ cộng đồng gia đình** nhằm củng cố mối quan hệ giữa các thành viên và gìn giữ di sản gia đình. Hệ thống tích hợp trong một hệ sinh thái phần mềm thống nhất:

- quản lý gia phả (genealogy management),
- giao tiếp trong gia đình (family communication),
- quản lý sự kiện (event management),
- chia sẻ tri thức và lưu trữ di sản (knowledge sharing, heritage),
- dịch vụ hỗ trợ bởi AI (AI-assisted services).

Hệ thống gồm ba thành phần kết nối qua RESTful API, đúng kiến trúc đề tài mô tả:
1. **Web Management Portal** (ứng dụng web, bắt buộc theo yêu cầu giảng viên),
2. **Mobile Application** dành cho thành viên gia đình (đa nền tảng),
3. **AI Service Layer** (tìm kiếm ngữ nghĩa, trợ lý hỏi đáp, giải thích quan hệ, tóm tắt, gợi ý).

### 1.3 Thuật ngữ và viết tắt
| Thuật ngữ | Giải thích |
|---|---|
| FR / NFR | Yêu cầu chức năng (Functional Requirement) / phi chức năng (Non-Functional Requirement) |
| UC | Use case |
| RBAC | Role-Based Access Control, phân quyền theo vai trò |
| JWT | JSON Web Token, cơ chế xác thực |
| REST / RESTful API | Kiểu thiết kế API dựa trên HTTP |
| RAG | Retrieval-Augmented Generation, AI trả lời dựa trên dữ liệu được truy xuất |
| LLM | Large Language Model, mô hình ngôn ngữ lớn |
| Embedding, pgvector | Biểu diễn văn bản dạng vector; phần mở rộng PostgreSQL để tìm theo vector |
| Gia phả | Hồ sơ dòng họ: các thành viên và quan hệ cha-mẹ-con, hôn nhân |
| Chi họ | Một nhánh của dòng họ (branch) |
| RSVP | Xác nhận tham dự sự kiện (Tham dự / Không / Có thể) |
| MVP | Bản sản phẩm nhỏ nhất vẫn dùng được cho mục đích chính |

### 1.4 Tài liệu tham chiếu
- Đề cương đề tài *FamilyConnect* (mục 3.2: Context, Proposed Solutions, Functional Requirements, Non-Functional Requirements, Theory & Practical, Products, Proposed Tasks).
- Yêu cầu của giảng viên về công nghệ: **Web app bắt buộc**, **Backend Java (Spring Boot) bắt buộc**, **Frontend React, Next.js** hoặc tương đương.
- Các tài liệu cùng thư mục `docs/`: [privacy.md](privacy.md), [use-cases/](use-cases/), [../04-api/conventions.md](../04-api/conventions.md).

---

## 2. Mô tả tổng quan

### 2.1 Bối cảnh bài toán
Các gia đình hiện đại ngày càng phân tán về địa lý vì học tập, việc làm, di cư và toàn cầu hóa. Hệ quả là liên lạc giữa các thành viên thưa dần, tư liệu gia phả khó duy trì, thế hệ trẻ ít cơ hội hiểu cội nguồn và xây dựng quan hệ trong gia đình. Mạng xã hội hiện có giúp liên lạc nhưng không được thiết kế để lưu gia phả hay hỗ trợ cộng tác theo gia đình; các phần mềm gia phả truyền thống chỉ ghi dòng dõi mà thiếu tính năng cộng đồng tương tác. Cần một nền tảng chuyên biệt vừa gìn giữ di sản, vừa kết nối, tổ chức hoạt động, chia sẻ tri thức và hỗ trợ lẫn nhau giữa các thế hệ, tận dụng điện toán di động, đám mây và AI (tìm kiếm cá nhân hóa, gợi ý nội dung, hỗ trợ tri thức, truy xuất ngữ nghĩa).

### 2.2 Giải pháp tổng thể (theo đề tài)
- Tổ chức dữ liệu gia phả theo **đồ thị gia đình** (family graph): trực quan hóa quan hệ, điều hướng qua các thế hệ, duy trì hồ sơ chính xác.
- Bổ sung dịch vụ cộng đồng: tin tức gia đình, tổ chức sự kiện, kho lưu trữ số, danh bạ thành viên, để gia phả không chỉ là thông tin tĩnh.
- Tích hợp AI làm trợ lý: tìm kiếm ngữ nghĩa, truy xuất tri thức, giải thích quan hệ, gợi ý cá nhân hóa.
- Kiến trúc **module** gồm Web Management Portal, Mobile Application và AI Service Layer qua RESTful API, dễ mở rộng.

### 2.3 Phân tích các bên liên quan (stakeholder analysis)
| Bên liên quan | Vai trò | Mối quan tâm / kỳ vọng | Mức ảnh hưởng |
|---|---|---|---|
| Giảng viên hướng dẫn | Đưa đề tài, đánh giá | Sản phẩm đúng đề tài (Web, Spring Boot, đủ module, Mobile, AI), tài liệu đầy đủ, chạy được bằng Docker | Cao |
| Thành viên gia đình | Người dùng chính | Xem gia phả dễ hiểu, kết nối và chia sẻ với người thân, tìm thông tin nhanh, dùng được trên điện thoại | Cao |
| Trưởng chi / người quản lý gia phả | Người nhập và duy trì dữ liệu | Thêm thành viên, quan hệ chính xác và nhanh; kiểm soát ai được xem dữ liệu; báo cáo thống kê | Cao |
| Quản trị hệ thống | Vận hành | Quản lý người dùng, kiểm duyệt, nhật ký, sao lưu, cấu hình, hệ thống ổn định | Trung bình |
| Nhóm phát triển (5 thành viên) | Xây dựng sản phẩm | Phạm vi rõ, hoàn thành trong 8 tuần, có thể kiểm thử | Cao |
| Nhà cung cấp LLM | Dịch vụ AI bên ngoài | Giới hạn gọi API, chi phí, chính sách dữ liệu | Thấp |

### 2.4 Tác nhân (Actor) và vai trò
| Actor | Mô tả | Vai trò hệ thống |
|---|---|---|
| **Khách** | Chưa đăng nhập | `GUEST` |
| **Thành viên** | Đã đăng ký; **đã được xác minh** thì xem được dữ liệu gia đình, chưa xác minh thì chỉ xem hồ sơ của mình và xin tham gia | `MEMBER` |
| **Trưởng chi / quản lý gia phả** | Nhập và duy trì dữ liệu gia phả, duyệt thành viên mới của gia đình/chi mình | `BRANCH_ADMIN` |
| **Quản trị hệ thống** | Quản trị toàn hệ thống | `SYSTEM_ADMIN` |
| **AI Service** | Hệ thống bên ngoài (LLM) mà lớp AI gọi tới | (không phải người dùng) |

Ma trận phân quyền chi tiết: xem **Phụ lục A**.

### 2.5 Môi trường vận hành và ràng buộc công nghệ
| Ràng buộc | Nội dung | Nguồn |
|---|---|---|
| Ứng dụng web | **Bắt buộc** | Yêu cầu giảng viên |
| Backend | **Java, Spring Boot** (bắt buộc) | Yêu cầu giảng viên |
| Frontend web | React / Next.js | Yêu cầu giảng viên |
| Mobile | Ứng dụng đa nền tảng (nhóm chọn React Native + Expo) | Đề tài mục b, d, f |
| Cơ sở dữ liệu | PostgreSQL (kèm pgvector cho tìm kiếm ngữ nghĩa) | Đề tài mục d |
| Xác thực | JWT | Đề tài mục d |
| Giao tiếp | RESTful API | Đề tài mục b, d |
| AI | Lớp AI Service tích hợp LLM (nhóm chọn Spring AI) | Đề tài mục b, d |
| Triển khai | Docker (Docker Compose) | Đề tài mục d |

### 2.6 Giả định và phụ thuộc
- Có kết nối Internet và khóa truy cập LLM để dùng các tính năng AI.
- Dữ liệu minh họa (gia phả 4 đến 5 thế hệ, 50 đến 100 người) do nhóm tự tạo cho buổi demo.
- Thời gian thực hiện 8 tuần với 5 thành viên, mỗi sprint 1 tuần.
- Chi phí gọi LLM nằm trong hạn mức miễn phí hoặc thấp; hệ thống giới hạn số câu hỏi AI mỗi người.

---

## 3. Yêu cầu chức năng

> Bảng dưới liệt kê **đầy đủ 46 chức năng ở mục c) của đề tài**, nhóm theo 9 nhóm như đề tài. Mọi chức năng đề tài nêu đều là **Must** (giảng viên yêu cầu). Cột "Sprint" là sprint dự kiến hoàn thành; cột "Task Jira" là task thực hiện.
> **Mức độ thực hiện:** mặc định là **đầy đủ**. Các mục giảm mức độ được ghi rõ ở mục 7.2.
> **Lưu ý về đề tài:** ba mục trong đề tài bị thiếu chữ (ở nhóm Genealogy: "management"; nhóm Directory: "directory"; nhóm Dashboard: "demographics"). Nhóm hiểu theo ngữ cảnh như ghi trong bảng. **Cần giảng viên xác nhận.** Bản đề tài trong `README.md` ở thư mục gốc là bản nhóm chép lại, ba chỗ thiếu chữ đã được điền theo cách hiểu của nhóm ("Person management", "Member directory", "Member demographics").

| Mã | Yêu cầu | Gốc trong đề tài | Ưu tiên | Sprint | Task Jira |
|---|---|---|---|---|---|
| **User & Security** | | | | | |
| FR-AUTH-01 | Đăng ký và xác thực người dùng | User registration and authentication | Must | S3 | SCRUM-35, 36 |
| FR-AUTH-02 | Phân quyền theo vai trò (RBAC) | Role-Based Access Control (RBAC) | Must | S4 | SCRUM-43 |
| FR-AUTH-03 | Quản lý hồ sơ người dùng | User profile management | Must | S4 | SCRUM-43 |
| FR-AUTH-04 | Xác minh thành viên gia đình | Family member verification | Must | S3-S6 | SCRUM-35, 57 |
| **Family & Genealogy Management** | | | | | |
| FR-GEN-01 | Quản lý gia đình | Family management | Must | S3 | SCRUM-37 |
| FR-GEN-02 | Quản lý chi họ | Branch management | Must | S3 | SCRUM-37 |
| FR-GEN-03 | Quản lý thành viên | "management" (đề tài ghi thiếu chữ; hiểu là Member management) | Must | S3 | SCRUM-37 |
| FR-GEN-04 | Quản lý quan hệ cha-mẹ-con | Parent-child relationship management | Must | S4 | SCRUM-44 |
| FR-GEN-05 | Quản lý hôn nhân | Marriage management | Must | S4 | SCRUM-44 |
| FR-GEN-06 | Cây gia phả tương tác | Interactive genealogy tree | Must | S4-S5 | SCRUM-45, 52 |
| FR-GEN-07 | Hiển thị trực quan quan hệ | Relationship visualization | Must | S6 | SCRUM-56 |
| FR-GEN-08 | Truy vấn quan hệ họ hàng | Relationship query | Must | S5 | SCRUM-51 |
| **Community** | | | | | |
| FR-COM-01 | Tạo và quản lý bài đăng | Create and manage posts | Must | S3 | SCRUM-38 |
| FR-COM-02 | Bình luận và reaction bài đăng | Comment and react to posts | Must | S3 | SCRUM-38 |
| FR-COM-03 | Chia sẻ tin tức gia đình | Share family news | Must | S3 | SCRUM-38 |
| FR-COM-04 | Chia sẻ ảnh | Photo sharing | Must | S4 | SCRUM-46 |
| FR-COM-05 | Thông báo của gia đình | Family announcements | Must | S5 | SCRUM-53 |
| **Events** | | | | | |
| FR-EVT-01 | Tạo sự kiện gia đình | Create family events | Must | S4 | SCRUM-47 |
| FR-EVT-02 | Quản lý RSVP | RSVP management | Must | S4 | SCRUM-47 |
| FR-EVT-03 | Quản lý người tham dự | Participant management | Must | S4 | SCRUM-47 |
| FR-EVT-04 | Thư viện ảnh sự kiện | Event gallery | Must | S5 | SCRUM-53 |
| FR-EVT-05 | Nhắc nhở sự kiện | Event reminders | Must | S5 | SCRUM-53 |
| **Family Directory** | | | | | |
| FR-DIR-01 | Danh bạ thành viên | "directory" (đề tài ghi thiếu chữ; hiểu là Member directory) | Must | S4 | SCRUM-48 |
| FR-DIR-02 | Hồ sơ nghề nghiệp | Professional profiles | Must | S4 | SCRUM-48 |
| FR-DIR-03 | Hồ sơ học vấn | Education profiles | Must | S4 | SCRUM-48 |
| FR-DIR-04 | Tìm thành viên theo nghề nghiệp, nơi ở hoặc thế hệ | Search members by profession, location, or generation | Must | S6 | SCRUM-61 |
| **Family Heritage** | | | | | |
| FR-HER-01 | Tài liệu lịch sử | Historical documents | Must | S3 | SCRUM-39 |
| FR-HER-02 | Câu chuyện gia đình | Family stories | Must | S3 | SCRUM-39 |
| FR-HER-03 | Người tiêu biểu của gia đình | Outstanding family members | Must | S6 | SCRUM-62 |
| FR-HER-04 | Thư viện ảnh | Photo gallery | Must | S5 | SCRUM-87 |
| FR-HER-05 | Kho lưu trữ số | Digital archives | Must | S5 | SCRUM-87 |
| **AI-assisted Services** | | | | | |
| FR-AI-01 | Tìm kiếm ngữ nghĩa bằng AI | AI-powered semantic search | Must | S3-S4 | SCRUM-41, 49 |
| FR-AI-02 | Trợ lý AI hỏi đáp tri thức gia đình | AI family knowledge assistant | Must | S5-S6 | SCRUM-55, 63 |
| FR-AI-03 | AI giải thích quan hệ | AI relationship explanation | Must | S6 | SCRUM-64 |
| FR-AI-04 | AI tóm tắt nội dung | AI content summarization | Must | S5 | SCRUM-88 |
| FR-AI-05 | AI gợi ý thành viên và tài nguyên liên quan | AI recommendation of related members and family resources | Must | S7 | SCRUM-70 |
| **Dashboard & Reporting** | | | | | |
| FR-DSH-01 | Thống kê gia đình | Family statistics | Must | S5 | SCRUM-54 |
| FR-DSH-02 | Dashboard hoạt động cộng đồng | Community activity dashboard | Must | S5 | SCRUM-54 |
| FR-DSH-03 | Thống kê sự kiện | Event statistics | Must | S5 | SCRUM-54 |
| FR-DSH-04 | Thống kê nhân khẩu | "demographics" (đề tài ghi thiếu chữ; hiểu là Family demographics) | Must | S5 | SCRUM-54 |
| FR-DSH-05 | Tạo báo cáo (xuất PDF, xuất dữ liệu CSV) | Report generation | Must | S6-S7 | SCRUM-62, 95 |
| **Administration** | | | | | |
| FR-ADM-01 | Quản lý người dùng | User management | Must | S5 | SCRUM-50 |
| FR-ADM-02 | Kiểm duyệt nội dung | Content moderation | Must | S5 | SCRUM-50 |
| FR-ADM-03 | Nhật ký kiểm toán | Audit logging | Must | S6 | SCRUM-57 |
| FR-ADM-04 | Sao lưu và khôi phục | Backup & Restore | Must | S5 | SCRUM-86 |
| FR-ADM-05 | Cấu hình hệ thống | System configuration | Must | S7 | SCRUM-94 |

**Tổng: 46 yêu cầu chức năng.** Số lượng mỗi nhóm: User & Security 4, Family & Genealogy 8, Community 5, Events 5, Family Directory 4, Family Heritage 5, AI-assisted Services 5, Dashboard & Reporting 5, Administration 5.

---

## 4. Use case
Đặc tả chi tiết từng use case (tác nhân, tiền điều kiện, luồng chính, luồng thay thế, hậu điều kiện, quy tắc nghiệp vụ) nằm trong thư mục `use-cases/`:

| Module | File | Người viết | Nhóm FR phủ |
|---|---|---|---|
| Auth, RBAC, Profile, Admin | [use-cases/auth.md](use-cases/auth.md) | TV1 Tuấn | AUTH, ADM |
| Gia phả | [use-cases/genealogy.md](use-cases/genealogy.md) | TV2 Trí | GEN |
| Community và Events | [use-cases/community-events.md](use-cases/community-events.md) | TV3 PiLo257 | COM, EVT |
| Heritage, Directory, Dashboard | [use-cases/heritage-directory-dashboard.md](use-cases/heritage-directory-dashboard.md) | TV4 Huy Quốc | HER, DIR, DSH |
| AI | [use-cases/ai.md](use-cases/ai.md) | TV5 Lê Nhựt | AI |

> Sơ đồ use case vẽ theo từng module, đặt trong mỗi file ở `use-cases/`. Sơ đồ quy trình nghiệp vụ: xem `02-design/business-process.md` (SCRUM-83).

---

## 5. Yêu cầu phi chức năng
Từ NFR-01 đến NFR-11 lấy từ mục d) của đề tài (11 mục). NFR-12 đến NFR-14 là **bổ sung của nhóm** (ưu tiên Should).

| Mã | Yêu cầu | Gốc trong đề tài | Tiêu chí đo | Ưu tiên | Task Jira |
|---|---|---|---|---|---|
| NFR-01 | Ứng dụng web responsive | Responsive Web Application | Giao diện web hiển thị đúng và dùng được ở 3 cỡ màn hình: điện thoại (từ 360 px), máy tính bảng, máy tính | Must | SCRUM-33, 40 |
| NFR-02 | Ứng dụng di động đa nền tảng | Cross-platform Mobile Application | Một mã nguồn (React Native + Expo) chạy trên Android và iOS, dùng chung REST API với web; có file APK và hướng dẫn chạy bằng Expo Go | Must | SCRUM-82 (Epic Mobile) |
| NFR-03 | Xác thực an toàn bằng JWT | Secure Authentication (JWT) | Access token ngắn hạn kèm refresh token; mật khẩu băm BCrypt; token hết hạn bị từ chối (mã lỗi AUTH_002) | Must | SCRUM-35 |
| NFR-04 | Kiến trúc RESTful API | RESTful API Architecture | Mọi API theo quy ước chung `/api/v1/...`, cấu trúc response và mã lỗi thống nhất; có đặc tả OpenAPI và Swagger UI | Must | SCRUM-21, 78 |
| NFR-05 | Kiến trúc phần mềm dạng module | Modular Software Architecture | Mỗi module nghiệp vụ nằm trong package riêng (controller, service, repository, dto, entity), giao tiếp qua service, không phụ thuộc vòng | Must | SCRUM-23 |
| NFR-06 | Trực quan hóa đồ thị tương tác | Interactive Graph Visualization | Cây gia phả hỗ trợ zoom, kéo, mở/thu nhánh, làm nổi bật đường quan hệ; mượt với 100 người, tải nhánh theo yêu cầu | Must | SCRUM-52, 56 |
| NFR-07 | Cơ sở dữ liệu PostgreSQL | PostgreSQL Database | Toàn bộ dữ liệu lưu trên PostgreSQL (kèm pgvector cho tìm kiếm ngữ nghĩa); thay đổi schema chỉ qua Flyway migration | Must | SCRUM-28 |
| NFR-08 | Tích hợp dịch vụ AI | AI Service Integration | Lớp AI Service riêng (`/api/v1/ai/**`) gọi LLM qua cấu hình; khi LLM lỗi hoặc quá thời gian thì trả thông báo thân thiện, không làm hỏng chức năng khác | Must | SCRUM-23, 55 |
| NFR-09 | Triển khai bằng Docker | Docker Deployment | Chạy toàn bộ hệ thống (web, backend, cơ sở dữ liệu) bằng `docker compose up` trên máy sạch trong tối đa 15 phút theo hướng dẫn | Must | SCRUM-68, 77 |
| NFR-10 | Tính sẵn sàng cao (mức cơ bản) | High Availability | Container lỗi tự khởi động lại; chạy 2 bản backend sau bộ cân bằng tải; tắt một bản backend vẫn phục vụ; dữ liệu không mất khi khởi động lại. Hạn chế còn lại (cơ sở dữ liệu một nút) được ghi rõ | Must (mức cơ bản) | SCRUM-96 |
| NFR-11 | Ghi nhật ký kiểm toán | Audit Logging | Mọi thao tác nhạy cảm (tạo, sửa, xóa người dùng, thành viên, quan hệ, nội dung, cấu hình, sao lưu) ghi lại người thực hiện, hành động, đối tượng, thời điểm | Must | SCRUM-57 |
| NFR-12 | Hiệu năng (bổ sung của nhóm) | (không có trong đề tài) | API cây gia phả dưới 1 giây với 5.000 người; tìm kiếm ngữ nghĩa dưới 2 giây | Should | SCRUM-59 |
| NFR-13 | Quyền riêng tư dữ liệu (bổ sung của nhóm) | (không có trong đề tài; ghi vào mục "Other comments") | Chỉ thành viên đã xác minh mới xem dữ liệu gia đình; quy tắc cho trẻ em và người đã mất; không gửi dữ liệu nhạy cảm cho LLM | Should | SCRUM-17 |
| NFR-14 | Độ tin cậy của AI (bổ sung của nhóm) | (không có trong đề tài) | AI chỉ trả lời từ dữ liệu gia đình; không có thông tin thì trả lời "không tìm thấy"; tỷ lệ trả lời đúng từ 80% trên bộ câu hỏi kiểm tra | Should | SCRUM-69 |

---

## 6. Quyền riêng tư và bảo mật dữ liệu
Tóm tắt (chi tiết ở [privacy.md](privacy.md)):
- Chỉ thành viên **đã được xác minh** mới xem được dữ liệu gia đình.
- Trường dữ liệu nhạy cảm (số điện thoại, địa chỉ, ngày sinh đầy đủ) hiển thị theo vai trò; quy tắc riêng cho **trẻ em** và **người đã mất**.
- Dữ liệu gửi LLM bên ngoài chỉ gồm đoạn cần thiết, **không** gửi mật khẩu, số điện thoại; truy vấn AI luôn lọc theo gia đình và quyền xem.
- Mật khẩu băm BCrypt; secret lưu bằng biến môi trường, không đưa vào Git.

---

## 7. Phạm vi

### 7.1 Trong phạm vi
Toàn bộ nội dung đề tài yêu cầu:
- 46 chức năng ở mục 3 (đề tài mục c).
- 11 yêu cầu phi chức năng NFR-01 đến NFR-11 (đề tài mục d).
- 11 sản phẩm bàn giao (đề tài mục f), đối chiếu ở **Phụ lục B**.
- 5 gói công việc (đề tài mục g): phân tích và thiết kế; phát triển nền tảng; Mobile và tích hợp AI; trực quan hóa và phân tích; kiểm thử và triển khai.

### 7.2 Mục thực hiện ở mức độ giảm (nhóm đã chốt 9/10/2026, cần báo giảng viên)
Đề tài yêu cầu các mục này; nhóm làm ở mức sau vì giới hạn 8 tuần:
| Mục | Mức độ thực hiện | Lý do |
|---|---|---|
| NFR-10 High Availability | **Cơ bản**: tự khởi động lại, 2 bản backend sau load balancer, dữ liệu bền vững; cơ sở dữ liệu một nút, chưa có replication | Phạm vi sinh viên, ghi hạn chế trong báo cáo |
| NFR-02 Mobile | Có đăng nhập, bảng tin, sự kiện, thông báo, xem cây gia phả, tra quan hệ, chat AI, tìm kiếm, danh bạ, Heritage; **Admin và Dashboard chi tiết chỉ có trên web** | Thời gian; Admin và Dashboard phù hợp màn hình lớn |
| FR-GEN-08 Truy vấn quan hệ | Đầy đủ với quan hệ cơ bản (cha mẹ, con, anh chị em, ông bà, chú bác, cô dì, cậu dì); quan hệ họ hàng xa phức tạp có thể trả "chưa hỗ trợ" | Độ phức tạp của thuật toán |
| FR-AI-05 Gợi ý | Gợi ý dựa trên độ gần của embedding (bản đơn giản nếu trễ) | Thời gian |
| FR-DSH-05 Báo cáo | 3 loại báo cáo PDF (gia phả, cộng đồng, sự kiện) và xuất CSV | Thời gian |

### 7.3 Ngoài phạm vi
Chỉ ghi những thứ **đề tài không yêu cầu**:
- Thanh toán, thương mại điện tử.
- Mạng xã hội công khai (kết bạn người lạ, bài đăng công khai); mọi nội dung chỉ trong phạm vi gia đình.
- Nhắn tin trực tiếp thời gian thực giữa thành viên (đề tài chỉ nêu bài đăng, bình luận, thông báo).
- Ứng dụng native riêng cho từng hệ điều hành (nhóm dùng một mã nguồn đa nền tảng).
- Tự huấn luyện mô hình AI (dùng LLM có sẵn qua API và RAG).

### 7.4 Phạm vi MVP và các mốc

MVP là thứ tự thực hiện, không phải giảm chức năng: mọi chức năng đề tài yêu cầu (46) đều phải hoàn thành.

| Mốc | Hết Sprint | Số chức năng (cộng dồn) | Nội dung chính |
|---|---|---|---|
| MVP-1 Lõi | Sprint 4 (30/10) | 21/46 | Auth, gia phả (CRUD, quan hệ), bài đăng, sự kiện, danh bạ, tài liệu lịch sử, tìm kiếm ngữ nghĩa, khung Mobile |
| MVP-2 Đủ tính năng | Sprint 6 (13/11) | 43/46 | Cây tương tác, truy vấn quan hệ, chatbot AI, dashboard, Admin, Mobile cơ bản |
| Hoàn thiện | Sprint 7 (20/11) | 46/46 | AI gợi ý, báo cáo PDF, cấu hình hệ thống, Docker, High Availability cơ bản, code freeze |

Đường đi chính của MVP: đăng ký, đăng nhập, tạo gia đình, thêm thành viên và quan hệ, xem cây, đăng bài và tạo sự kiện, hỏi AI về gia phả.

---

## 8. Ma trận truy vết
Cột trống được điền dần trong các sprint sau; mỗi hàng nối yêu cầu với thiết kế, mã nguồn và kiểm thử.

| Mã FR/NFR | Use case | Bảng DB | API | Màn hình | Test case | Task Jira |
|---|---|---|---|---|---|---|
| FR-AUTH-01 | UC-AUTH-01, 02, 03, 04 | users, role, user_role, refresh_token | POST /users, /auth/login, /auth/refresh | Đăng nhập, Đăng ký | TC-AUTH-001 | SCRUM-35, 36 |
| FR-GEN-08 | UC-GEN-08 | parent_child, person | GET /relationships | Tra quan hệ | | SCRUM-51 |
| FR-AI-02 | UC-AI-02 | ai_embedding_chunk, ai_chat_session, ai_chat_message | POST /ai/chat | Chat AI | | SCRUM-55, 63 |
| (điền thêm) | | | | | | |

---

## Phụ lục A. Ma trận phân quyền (role x chức năng)
`Không` = không được; `Có` = được; ghi chú trong ngoặc là phạm vi dữ liệu. **Đã chốt 9/10/2026** (leader quyết định, báo lại nhóm ở họp Sprint 2).

| Chức năng | Khách (GUEST) | Thành viên chưa xác minh | Thành viên (MEMBER) | Trưởng chi (BRANCH_ADMIN) | Quản trị (SYSTEM_ADMIN) |
|---|---|---|---|---|---|
| Đăng ký, đăng nhập | Có | Có | Có | Có | Có |
| Xem và sửa hồ sơ của chính mình | Không | Có | Có | Có | Có |
| Xin tham gia gia đình | Không | Có | Không (đã tham gia) | Không | Không |
| Tạo gia đình mới (người tạo trở thành trưởng chi của gia đình đó) | Không | Có (nếu chưa thuộc gia đình nào) | Không (đã thuộc gia đình) | Không | Có |
| Duyệt hoặc từ chối yêu cầu tham gia | Không | Không | Không | Có (gia đình/chi của mình) | Có |
| Xem cây gia phả và danh sách thành viên | Không | Không | Có (gia đình của mình) | Có | Có |
| Thêm, sửa, xóa thành viên, quan hệ cha-mẹ-con, hôn nhân | Không | Không | Không | Có | Có |
| Tra quan hệ họ hàng, xem giải thích AI | Không | Không | Có | Có | Có |
| Đăng bài, bình luận, reaction, tải ảnh | Không | Không | Có | Có | Có |
| Đăng tin tức gia đình, thông báo (announcement) | Không | Không | Không | Có | Có |
| Tạo sự kiện | Không | Không | Có | Có | Có |
| Phản hồi RSVP, xem người tham dự | Không | Không | Có | Có | Có |
| Thêm tài liệu, câu chuyện, ảnh, kho lưu trữ số (Heritage) | Không | Không | Có (câu chuyện, ảnh) | Có (tất cả) | Có |
| Quản lý người tiêu biểu | Không | Không | Không | Có | Có |
| Sửa hồ sơ nghề nghiệp và học vấn | Không | Không | Của mình | Của thành viên trong chi | Có |
| Tìm kiếm ngữ nghĩa, hỏi trợ lý AI, tóm tắt, gợi ý | Không | Không | Có | Có | Có |
| Xem dashboard thống kê | Không | Không | Không | Có (gia đình/chi của mình) | Có |
| Tạo báo cáo PDF, xuất CSV | Không | Không | Không | Có | Có |
| Quản lý người dùng (khóa, đổi vai trò) | Không | Không | Không | Không | Có |
| Gỡ bài đăng, bình luận, sự kiện của người khác | Không | Không | Không | Có (chi mình quản lý, kèm chi con) | Có |
| Kiểm duyệt nội dung (toàn hệ thống, FR-ADM-02) | Không | Không | Không | Không | Có |
| Xem nhật ký kiểm toán | Không | Không | Không | Không | Có |
| Sao lưu, khôi phục dữ liệu | Không | Không | Không | Không | Có |
| Cấu hình hệ thống | Không | Không | Không | Không | Có |

> Ghi chú: "Thành viên chưa xác minh" là một **trạng thái** của vai trò `MEMBER` (chờ trưởng chi duyệt), không phải vai trò riêng. Vì vậy hệ thống chỉ có 4 vai trò: GUEST, MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN.

> Ghi chú: Phạm vi của `BRANCH_ADMIN` là chi được gán (kèm các chi con); nếu không gán chi cụ thể thì quản lý cả gia đình. Mỗi người dùng thuộc tối đa một gia đình. Bảng người dùng đặt tên `users` (vì `user` là từ khóa PostgreSQL).

---

## Phụ lục B. Đối chiếu sản phẩm bàn giao (đề tài mục f)
| Sản phẩm đề tài yêu cầu | Đáp ứng bởi | Task Jira |
|---|---|---|
| FamilyConnect Web Portal | Ứng dụng web Next.js | SCRUM-33, 40 và các task FE |
| FamilyConnect Mobile Application | Ứng dụng React Native + Expo, APK | SCRUM-85, 89, 90, 91, 92, 93 |
| Genealogy Management Module | Module Gia phả (CRUD, cây, truy vấn quan hệ) | SCRUM-37, 44, 45, 51, 52, 56 |
| Community Management Module | Bài đăng, bình luận, reaction, ảnh, thông báo | SCRUM-38, 46, 53 |
| Event Management Module | Sự kiện, RSVP, nhắc nhở, gallery | SCRUM-47, 53 |
| Family Heritage Module | Tài liệu, câu chuyện, người tiêu biểu, ảnh, kho lưu trữ số | SCRUM-39, 62, 87 |
| AI Assistant Module | Tìm kiếm ngữ nghĩa, trợ lý, giải thích, tóm tắt, gợi ý | SCRUM-41, 49, 55, 63, 64, 88, 70 |
| Dashboard & Reporting Module | Thống kê, báo cáo PDF, xuất CSV | SCRUM-54, 62, 95 |
| RESTful API Services | API theo quy ước chung, Swagger | SCRUM-21, 78 |
| Docker Deployment Package | Docker Compose, hướng dẫn triển khai | SCRUM-68, 77, 96 |
| Software Documentation | SRS, thiết kế, API, kiểm thử, hướng dẫn, báo cáo | SCRUM-14 và các task Docs |

---

## Phụ lục C. Đối chiếu lý thuyết và thực hành (đề tài mục e)
| Nội dung đề tài | Áp dụng trong dự án |
|---|---|
| Software Engineering | Quy trình Scrum, SRS, kiểm thử, quản lý bằng Jira và GitHub |
| Object-Oriented Analysis and Design | Use case, mô hình lớp, sơ đồ trình tự |
| Software Architecture | Kiến trúc module Web, Mobile, AI Service Layer |
| Enterprise Information Systems | Hệ thống nhiều vai trò, nhiều module, nhật ký kiểm toán |
| Database Design | ERD, từ điển dữ liệu, Flyway migration |
| Graph Data Modeling | Mô hình đồ thị gia đình trên PostgreSQL, truy vấn đệ quy |
| Human-Computer Interaction | Design system, wireframe, giao diện responsive |
| Mobile Application Development | React Native + Expo |
| RESTful API Design | Quy ước API, OpenAPI, Swagger |
| Authentication & Authorization | JWT, RBAC |
| Software Testing | Unit test, test API, kiểm thử chéo, hiệu năng |
| Information Retrieval | Tìm kiếm ngữ nghĩa, embedding, pgvector |
| Large Language Model (LLM) Integration | RAG, trợ lý AI, tóm tắt, gợi ý |

---

