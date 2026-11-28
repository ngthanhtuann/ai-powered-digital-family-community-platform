<div align="center">

# 🌳 FamilyConnect — Nền tảng Cộng đồng Gia đình số

**AI-powered Digital Family Community Platform**

Cây gia phả nhiều thế hệ, tra cứu xưng hô, mạng xã hội nội bộ dòng họ, kho di sản và trợ lý AI trả lời trên chính dữ liệu của gia đình.

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F?logo=springboot&logoColor=white)
![Next.js](https://img.shields.io/badge/Next.js-15-000000?logo=nextdotjs&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![Expo](https://img.shields.io/badge/Expo-SDK%2058-000020?logo=expo&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16%20%2B%20pgvector-4169E1?logo=postgresql&logoColor=white)
![Gemini](https://img.shields.io/badge/Gemini-API-8E75B2?logo=googlegemini&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)

</div>

---

## 📑 Mục lục

- [Giới thiệu](#-giới-thiệu)
- [Tính năng chính](#-tính-năng-chính)
- [Kiến trúc hệ thống](#️-kiến-trúc-hệ-thống)
- [Cấu trúc repository](#-cấu-trúc-repository)
- [Yêu cầu môi trường](#-yêu-cầu-môi-trường)
- [Bắt đầu nhanh](#-bắt-đầu-nhanh)
- [Cấu hình & biến môi trường](#️-cấu-hình--biến-môi-trường)
- [Kiểm thử & CI](#-kiểm-thử--ci)
- [Trợ lý AI](#-trợ-lý-ai)
- [Tài khoản demo](#-tài-khoản-demo)
- [Tài liệu dự án](#-tài-liệu-dự-án)
- [Nhóm thực hiện](#-nhóm-thực-hiện)
- [Đề tài gốc](#-đề-tài-gốc)

---

## ✨ Giới thiệu

Gia phả của nhiều dòng họ vẫn nằm trong sổ viết tay hoặc bảng tính rời rạc; con cháu ở xa ít có kênh chung để biết tin họ hàng, cách xưng hô và lịch sử dòng họ. **FamilyConnect** gom tất cả vào một nền tảng: gia phả dạng đồ thị, bảng tin nội bộ, sự kiện giỗ chạp, kho di sản, danh bạ nghề nghiệp, thống kê và trợ lý AI.

Hệ thống gồm **Web** (Next.js), **Mobile Android** (Expo) và **một backend dùng chung** (Spring Boot). Giao diện web chia khu vực theo vai trò:

| Khu vực | Đường dẫn chính | Vai trò |
| --- | --- | --- |
| Gia đình | `/`, `/assistant`, `/members`, `/tree`, `/relationship` | Thành viên đã xác minh |
| Cộng đồng | `/community`, `/events`, `/directory` | Thành viên đã xác minh |
| Di sản | `/heritage`, `/heritage/photos`, `/heritage/documents` | Thành viên đã xác minh |
| Quản lý dòng họ | `/join-requests`, `/family-roles`, `/dashboard`, `/reports` | `BRANCH_ADMIN` (trưởng chi, trưởng họ), `SYSTEM_ADMIN` |
| Quản trị hệ thống | `/admin/nguoi-dung`, `/admin/kiem-duyet`, `/admin/nhat-ky`, `/admin/sao-luu`, `/admin/cau-hinh` | `SYSTEM_ADMIN` |
| Tài khoản | `/login`, `/register`, `/join-family`, `/profile` | Mọi người dùng |

Bốn vai trò: `GUEST`, `MEMBER`, `BRANCH_ADMIN`, `SYSTEM_ADMIN`. Người mới đăng ký phải xin vào gia đình bằng **mã gia đình hoặc mã chi** và được trưởng họ, trưởng chi duyệt mới xem được dữ liệu.

---

## 🎯 Tính năng chính

- **Tài khoản & phân quyền** — đăng ký, đăng nhập JWT + refresh token dùng một lần; RBAC kiểm ở tầng service; xin vào gia đình, duyệt thành viên (tự chọn sẵn hồ sơ trên cây theo họ tên và ngày sinh).
- **Gia phả** — gia đình, chi họ, thành viên, quan hệ cha mẹ – con (ruột, nuôi, kế), hôn nhân; cây gia phả tương tác tải 3 đời rồi mở dần; tra cứu **"A là gì của B"** theo cách xưng hô Việt Nam (nội/ngoại, dâu/rể, dượng...).
- **Cộng đồng** — bài đăng, ảnh, bình luận, trả lời, cảm xúc; chọn người xem (cả dòng họ, chi của tôi, con cháu của tôi); thông báo gia đình có ghim; báo cáo vi phạm.
- **Sự kiện** — tạo sự kiện giỗ chạp, RSVP, danh sách người tham dự và điểm danh, thư viện ảnh, nhắc lịch 24 giờ và 1 giờ.
- **Di sản & danh bạ** — tài liệu lịch sử (PDF), câu chuyện, thư viện ảnh, người tiêu biểu; tìm thành viên theo nghề, nơi ở, thế hệ (gõ không dấu).
- **Thống kê & báo cáo** — dashboard nhân khẩu, hoạt động cộng đồng, sự kiện; xuất CSV và PDF.
- **Trợ lý AI** — tìm kiếm ngữ nghĩa, hỏi đáp có trích nguồn, giải thích quan hệ, tóm tắt, gợi ý; trả lời câu hỏi thống kê ("dòng họ có bao nhiêu người đã mất?") từ dữ liệu thật.
- **Quản trị** — quản lý người dùng, kiểm duyệt nội dung, nhật ký kiểm toán, sao lưu và khôi phục, cấu hình hệ thống.

---

## 🏗️ Kiến trúc hệ thống

```
┌───────────────────────────────┐   ┌───────────────────────────────┐
│ frontend/  Next.js 15 (:3000) │   │ mobile/  React Native + Expo  │
│ TanStack Query · Tailwind     │   │ token trong expo-secure-store │
└───────────────┬───────────────┘   └───────────────┬───────────────┘
                │      REST /api/v1  (JWT Bearer)   │
┌───────────────▼───────────────────────────────────▼───────────────┐
│ backend/  Spring Boot 3.3 (Java 17) · :8080 · Swagger UI          │
│ Modular monolith — 5 module, gọi nhau qua interface:              │
│  Auth/Admin · Gia phả · Cộng đồng/Sự kiện · Di sản/Danh bạ/        │
│  Dashboard · AI                                                   │
│  MembershipQuery · GenealogyQuery · AiGateway · AuditLogger ...   │
└───────────────┬───────────────────────────────────┬───────────────┘
                │                                   │
   PostgreSQL 16 + pgvector                  Gemini API
   (Flyway V1–V24, vector 768 chiều)         (embedding + chat)
```

- Module không đọc bảng của module khác; tham chiếu chéo chỉ lưu UUID và kiểm ở tầng service.
- Mọi API có tiền tố `/api/v1`, trả envelope `{ success, data, error }`; kiểu TypeScript của web và mobile **sinh từ OpenAPI** (`npm run gen:api`).
- High Availability mức cơ bản: `docker-compose.ha.yml` chạy 2 backend sau Nginx, giới hạn tần suất dùng chung qua PostgreSQL.

Chi tiết: [`docs/02-design/architecture.md`](docs/02-design/architecture.md).

---

## 📁 Cấu trúc repository

```
java/
├── backend/                       # Spring Boot API (Maven)
│   ├── pom.xml  Dockerfile
│   ├── scripts/                   # backup.sh, restore.sh
│   └── src/
│       ├── main/java/com/familyconnect/
│       │   ├── common/            # security, config, exception, audit, api
│       │   └── modules/           # auth, genealogy, community, events,
│       │                          # heritage, directory, dashboard, ai
│       ├── main/resources/db/migration/   # Flyway V1 → V24
│       └── test/                  # Unit + integration test (PostgreSQL thật)
│
├── frontend/                      # Web Next.js (App Router)
│   └── src/app/  src/components/  src/services/generated/
│
├── mobile/                        # App Android (Expo)
│   └── src/screens/  src/services/generated/
│
├── docker/                        # nginx (HA), seed-demo.ps1, perf (k6), test
├── docs/                          # SRS, thiết kế, ERD, OpenAPI, test, báo cáo
├── scripts/                       # Đánh giá chất lượng AI
├── docker-compose.yml             # db + backend + frontend
├── docker-compose.ha.yml          # 2 backend sau Nginx
├── .github/workflows/ci.yml       # CI backend + frontend
└── .env.example                   # Mẫu biến môi trường
```

---

## 🧰 Yêu cầu môi trường

| Thành phần | Phiên bản | Ghi chú |
| --- | --- | --- |
| Docker Desktop | có Docker Compose v2 | **Đủ để chạy toàn bộ hệ thống** |
| JDK | 17 | Chỉ khi chạy backend ngoài Docker |
| Maven | 3.9+ | Repo không kèm Maven Wrapper |
| Node.js | 20+ | Web và mobile |
| PostgreSQL | 16 + pgvector | Có sẵn trong Docker (`pgvector/pgvector:pg16`) |
| Gemini API key | — | Tùy chọn; để trống thì chatbot và tóm tắt AI không hoạt động |

---

## 🚀 Bắt đầu nhanh

### 1. Chạy toàn bộ bằng Docker (khuyến nghị)

```powershell
# 1) Tạo .env từ mẫu rồi điền tối thiểu POSTGRES_PASSWORD và JWT_SECRET
Copy-Item .env.example .env

# Sinh JWT_SECRET (PowerShell, không cần openssl)
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))

# 2) Build và chạy db + backend + frontend
docker compose --env-file .env up -d --build
docker compose ps        # chờ cả ba container ở trạng thái healthy (1–2 phút)

# 3) Nạp dữ liệu demo (dòng họ giả 53 người, 5 đời)
powershell -ExecutionPolicy Bypass -File docker\seed-demo.ps1
```

| Dịch vụ | Địa chỉ |
| --- | --- |
| Web | http://localhost:3000 |
| API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | `127.0.0.1:5432` (đổi bằng `POSTGRES_HOST_PORT`) |

> ⚠️ **Không dùng dữ liệu thật với gói miễn phí Gemini** — gói miễn phí có thể dùng dữ liệu để huấn luyện. Chỉ chạy với dữ liệu giả.

Hướng dẫn đầy đủ (máy sạch, xử lý sự cố): [`docs/07-deploy/deploy.md`](docs/07-deploy/deploy.md).

### 2. Phát triển từng phần

**Backend** (cần PostgreSQL: có thể chỉ bật `docker compose up -d db`):

```bash
cd backend
# đặt JWT_SECRET, SPRING_DATASOURCE_URL/_USERNAME/_PASSWORD trong môi trường hoặc cấu hình Run của IDE
mvn spring-boot:run          # → http://localhost:8080
mvn -B verify                # build + toàn bộ test (cần PostgreSQL có pgvector)
```

**Web:**

```bash
cd frontend
npm install
npm run dev                  # → http://localhost:3000
npm run lint
npm run build
npm run gen:api              # sinh lại kiểu TypeScript từ docs/04-api/openapi/*.yaml
```

**Mobile:**

```bash
cd mobile
npm install
# mobile/.env: EXPO_PUBLIC_API_BASE_URL=http://<IP LAN>:8080 (giả lập Android: http://10.0.2.2:8080)
npm start                    # Expo
npm run typecheck
npm run lint
```

### 3. High Availability (2 backend)

```bash
docker compose -f docker-compose.ha.yml up -d --build
```

> Không chạy đồng thời với `docker-compose.yml` vì dùng chung cổng và volume. Kết quả thử tắt một backend: [`docs/06-test/high-availability-7-11.md`](docs/06-test/high-availability-7-11.md).

---

## ⚙️ Cấu hình & biến môi trường

Sao chép `.env.example` thành `.env`. **Không commit `.env`.**

| Biến | Bắt buộc | Mô tả |
| --- | --- | --- |
| `POSTGRES_PASSWORD` | ✅ | Mật khẩu PostgreSQL; thiếu thì Docker Compose báo lỗi ngay |
| `POSTGRES_DB`, `POSTGRES_USER` | | Mặc định `familyconnect` |
| `POSTGRES_HOST_PORT` | | Cổng PostgreSQL mở ra máy (chỉ `127.0.0.1`), mặc định `5432` |
| `JWT_SECRET` | ✅ | Khóa ký JWT, tối thiểu 32 ký tự; thiếu thì backend dừng khi khởi động |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | nên điền | Tạo tài khoản `SYSTEM_ADMIN` đầu tiên |
| `CORS_ALLOWED_ORIGINS` | | Origin được phép gọi API |
| `NEXT_PUBLIC_API_BASE_URL` | | Địa chỉ backend web gọi tới; đổi thì phải build lại frontend |
| `GEMINI_API_KEY` | | Để trống thì bỏ qua lập chỉ mục AI |
| `GEMINI_CHAT_MODEL`, `GEMINI_SUMMARY_MODEL` | | Mặc định `gemini-3.5-flash-lite` |
| `AI_CHAT_TOP_K`, `AI_CHAT_MINIMUM_SCORE`, `AI_CHAT_TIMEOUT_MS` | | Số đoạn truy xuất, ngưỡng cosine, thời gian chờ LLM |
| `AUTH_REFRESH_RATE_LIMIT_PER_MINUTE` | | Giới hạn gọi `/auth/refresh` mỗi IP mỗi phút |
| `MEDIA_STORAGE_PATH` | | Thư mục lưu tệp khi chạy backend ngoài Docker |

### 🔐 Bảo mật

- Mật khẩu băm BCrypt; refresh token lưu dạng băm SHA-256, dùng một lần; khóa tạm khi đăng nhập sai 5 lần mỗi phút.
- Thứ tự kiểm quyền: `AUTH_002` → `AUTH_003` → `PERM_002` → `PERM_001`. Mọi truy vấn lọc theo `family_id` của người gọi (chống IDOR), có test ma trận quyền.
- Tệp tải lên lưu tên UUID, chỉ lấy qua API có kiểm quyền; ảnh jpg/png/webp tối đa 5 MB, PDF tối đa 10 MB.
- Trẻ dưới 16 tuổi chỉ hiện tên, quan hệ, năm sinh và **không đưa vào ngữ cảnh gửi AI** ([`docs/01-srs/privacy.md`](docs/01-srs/privacy.md)).

---

## 🧪 Kiểm thử & CI

CI (`.github/workflows/ci.yml`) chạy trên mọi Pull Request và push vào `develop`, `main`:

| Job | Nội dung |
| --- | --- |
| `backend` | JDK 17 · `mvn -B verify` trên PostgreSQL `pgvector/pgvector:pg16` (hơn 500 test: unit, tích hợp, ma trận quyền, IDOR) |
| `frontend` | Node 20 · `npm ci` → kiểu sinh từ OpenAPI phải khớp bản đã commit → `npm run lint` → `npm run build` |

Kết quả đo khác: hiệu năng k6 (`docs/06-test/performance-6-04.md`), hồi quy toàn hệ thống (`regression-7-01.md`), HA (`high-availability-7-11.md`), chất lượng AI (`ai-quality-scrum-69.md`). Kế hoạch kiểm thử: [`docs/06-test/test-plan.md`](docs/06-test/test-plan.md).

---

## 🤖 Trợ lý AI

- **RAG trên dữ liệu dòng họ** — nội dung được chia đoạn, tạo embedding `gemini-embedding-001` (768 chiều), lưu pgvector (HNSW). Câu hỏi chỉ truy xuất đoạn mà người hỏi **được phép xem**; không đủ ngữ cảnh thì trả "Không tìm thấy thông tin." thay vì đoán.
- **Quan hệ họ hàng do thuật toán tính** (tổ tiên chung gần nhất, tối đa 30 đời), AI chỉ diễn đạt lại; không để LLM tự suy ra quan hệ.
- **Câu hỏi thống kê** (đếm, liệt kê, lớn/nhỏ tuổi nhất) — Gemini chỉ chuyển câu hỏi thành bộ lọc trong danh sách cho phép, máy chủ tự đếm từ cơ sở dữ liệu; tối đa một lần gọi LLM mỗi câu (BR-AI-18).
- Hạn mức 20 câu/giờ mỗi tài khoản; Gemini lỗi hoặc hết quota trả `AI_001`, các chức năng khác vẫn chạy.

Thiết kế: [`docs/02-design/ai-rag.md`](docs/02-design/ai-rag.md) · đánh giá: [`docs/06-test/ai-quality-scrum-69.md`](docs/06-test/ai-quality-scrum-69.md), [`docs/decisions/semantic-search-evaluation.md`](docs/decisions/semantic-search-evaluation.md).

---

## 🔑 Tài khoản demo

Tạo bởi `docker/seed-demo.ps1` (dữ liệu giả, **chỉ dùng trên máy demo**). Mật khẩu chung: `Demo@2026`.

| Vai trò | Email | Ghi chú |
| --- | --- | --- |
| Trưởng họ (`BRANCH_ADMIN`, quản lý cả họ) | `phuc@demo.familyconnect.vn` | Người tạo dòng họ |
| Trưởng chi (`BRANCH_ADMIN`) | `hung@demo.familyconnect.vn` | Trưởng Chi 3 |
| Thành viên đã xác minh (`MEMBER`) | `ha@demo.familyconnect.vn` | Nguyễn Thị Hà, Chi 3 |
| Thành viên chờ duyệt | `tu@demo.familyconnect.vn` | Dùng để demo luồng duyệt |
| Quản trị hệ thống (`SYSTEM_ADMIN`) | theo `BOOTSTRAP_ADMIN_EMAIL` | Mật khẩu theo `BOOTSTRAP_ADMIN_PASSWORD` |

Kịch bản demo: [`docs/09-report/demo-script.md`](docs/09-report/demo-script.md).

---

## 📚 Tài liệu dự án

| Tài liệu | Nội dung |
| --- | --- |
| [`docs/README.md`](docs/README.md) | Mục lục tài liệu, người viết, reviewer |
| `docs/01-srs/srs.md`, `use-cases/`, `privacy.md` | Đặc tả yêu cầu, use case và quy tắc nghiệp vụ, quyền riêng tư |
| `docs/02-design/` | Kiến trúc, sơ đồ lớp, quy trình nghiệp vụ, thuật toán xưng hô, AI/RAG, mobile |
| `docs/03-database/` | ERD, từ điển dữ liệu |
| `docs/04-api/conventions.md`, `openapi/*.yaml` | Quy ước API và đặc tả OpenAPI từng module |
| `docs/05-ui/` | Design system, link Figma |
| `docs/06-test/` | Kế hoạch và kết quả kiểm thử |
| `docs/07-deploy/` | Triển khai, sao lưu và khôi phục |
| `docs/08-user-guide/` | Hướng dẫn sử dụng |
| `docs/09-report/` | Báo cáo cuối, bảng đối chiếu đề tài, kịch bản demo |
| `docs/decisions/` | Các quyết định kiến trúc (ADR) |

---

## 👥 Nhóm thực hiện

| Thành viên | Phụ trách |
| --- | --- |
| TV1 Nguyễn Thanh Tuấn | Auth, Admin (tài khoản, phân quyền, duyệt thành viên, sao lưu, cấu hình, HA); SRS, báo cáo cuối |
| TV2 Nguyễn Minh Trí | Gia phả (cây gia phả, quan hệ, thuật toán xưng hô); ERD |
| TV3 PiLo257 | Cộng đồng và Sự kiện; quy ước API |
| TV4 Huy Quốc | Di sản, Danh bạ, Dashboard; kiến trúc, triển khai |
| TV5 Lê Nhựt | Trợ lý AI (RAG) và ứng dụng Mobile |

---

## 📜 Đề tài gốc

<details>
<summary>Xem nội dung đề tài (bối cảnh, giải pháp, yêu cầu chức năng và phi chức năng, sản phẩm, gói công việc)</summary>

### Đề Tài Dự Án: FamilyConnect

### 1. Tên Dự Án / Project Names

* **Tiếng Anh (English Name):** FamilyConnect: AI-powered Digital Family Community Platform
* **Tiếng Việt (Vietnamese Name):** FamilyConnect: Nền tảng Cộng đồng Gia đình số tích hợp Trí tuệ nhân tạo

---

### 2. Nội Dung Đề Xuất Chính / Main Proposal Content

### a) Bối Cảnh & Đặt Vấn Đề / Context & Problem Statement

#### Tiếng Việt
* **Gia đình ngày càng phân tán:** Các gia đình hiện đại ngày càng sống xa nhau về địa lý do học tập, cơ hội nghề nghiệp, di cư và toàn cầu hóa. Vì vậy, việc liên lạc giữa các thành viên thưa dần, hồ sơ gia phả khó duy trì, và thế hệ trẻ có ít cơ hội hiểu về truyền thống gia đình hoặc xây dựng những mối quan hệ gắn bó trong gia đình.
* **Hạn chế của các giải pháp hiện có:** Các mạng xã hội hiện nay hỗ trợ giao tiếp nhưng không được thiết kế để lưu giữ gia phả, hỗ trợ cộng tác trong gia đình hay củng cố sự phát triển lâu dài của gia đình. Ngược lại, các hệ thống gia phả truyền thống chủ yếu chỉ ghi chép thông tin dòng họ mà không có các tính năng cộng đồng mang tính tương tác.
* **Nhu cầu về một nền tảng chuyên biệt:** Ngày càng cần một nền tảng số chuyên biệt không chỉ lưu giữ di sản gia đình mà còn tăng cường kết nối, hỗ trợ giao tiếp, tổ chức hoạt động gia đình, chia sẻ tri thức và thúc đẩy sự hỗ trợ lẫn nhau giữa các thế hệ.
* **Cơ hội từ công nghệ:** Những tiến bộ gần đây của điện toán di động, điện toán đám mây và Trí tuệ nhân tạo (AI) cho phép xây dựng các nền tảng cộng đồng thông minh có khả năng tìm kiếm cá nhân hóa, gợi ý nội dung, hỗ trợ tri thức và truy xuất thông tin theo ngữ nghĩa.
* **Giải pháp đề xuất:** Do đó, dự án đề xuất **FamilyConnect**, một nền tảng cộng đồng gia đình số tích hợp AI, kết hợp quản lý gia phả, giao tiếp gia đình, quản lý sự kiện, chia sẻ tri thức và các dịch vụ hỗ trợ bởi AI trong một hệ sinh thái phần mềm thống nhất.

#### English
* **Increasingly Distributed Families:** Modern families are becoming increasingly geographically distributed due to education, career opportunities, migration, and globalization. As a result, communication among family members becomes less frequent, genealogy records are difficult to maintain, and younger generations have fewer opportunities to understand their family heritage or build meaningful relationships within the family.
* **Limitations of Existing Solutions:** Existing social networking platforms facilitate communication but are not designed to preserve family genealogy, support family-oriented collaboration, or strengthen long-term family development. Likewise, traditional genealogy systems mainly focus on recording lineage information without providing interactive community features.
* **Need for a Dedicated Platform:** There is an increasing demand for a dedicated digital platform that not only preserves family heritage but also strengthens family connections, supports communication, organizes family activities, facilitates knowledge sharing, and promotes mutual support among generations.
* **Technological Opportunity:** Recent advances in mobile computing, cloud technologies, and Artificial Intelligence enable the development of intelligent community platforms capable of providing personalized search, content recommendation, knowledge assistance, and semantic information retrieval.
* **Proposed Solution Overview:** Therefore, this project proposes FamilyConnect, an AI-powered Digital Family Community Platform that integrates genealogy management, family communication, event management, knowledge sharing, and AI-assisted services into a unified software ecosystem.

---

### b) Giải Pháp Đề Xuất / Proposed Solutions

#### Tiếng Việt
* **Tổng quan:** FamilyConnect là nền tảng số kết hợp quản lý gia phả với các dịch vụ cộng đồng gia đình, nhằm củng cố mối quan hệ giữa các thành viên đồng thời bảo tồn di sản gia đình.
* **Đồ thị gia đình:** Nền tảng tổ chức thông tin gia phả theo cấu trúc đồ thị gia đình, cho phép người dùng trực quan hóa quan hệ, di chuyển qua các thế hệ và duy trì hồ sơ gia đình chính xác.
* **Dịch vụ cộng đồng:** Ngoài quản lý gia phả, FamilyConnect cung cấp các dịch vụ hướng cộng đồng như tin tức gia đình, tổ chức sự kiện, kho lưu trữ số và danh bạ thành viên. Các tính năng này khuyến khích tương tác và cộng tác liên tục thay vì xem gia phả như thông tin tĩnh.
* **Dịch vụ hỗ trợ bởi AI:** Để nâng cao trải nghiệm, nền tảng tích hợp các dịch vụ AI như tìm kiếm theo ngữ nghĩa, truy xuất tri thức thông minh, giải thích quan hệ họ hàng và gợi ý cá nhân hóa dựa trên thông tin gia đình. AI đóng vai trò trợ lý thông minh giúp người dùng tìm thông tin liên quan và khai thác kho tri thức gia đình hiệu quả hơn.
* **Kiến trúc:** Hệ thống theo kiến trúc module gồm Cổng quản trị Web, Ứng dụng di động cho thành viên gia đình và Tầng dịch vụ AI, kết nối qua RESTful API. Kiến trúc này hỗ trợ mở rộng trong tương lai và vẫn đảm bảo tính linh hoạt, khả năng co giãn.

#### English
* **Overview:** FamilyConnect is designed as a digital platform that combines genealogy management with family community services to strengthen relationships among family members while preserving family heritage.
* **Family Graph:** The platform organizes genealogy information using a structured family graph, allowing users to visualize family relationships, navigate across generations, and maintain accurate family records.
* **Community Services:** Beyond genealogy management, FamilyConnect provides community-oriented services including family news, event organization, digital archives, and member directories. These features encourage continuous interaction and collaboration among family members rather than treating genealogy as static information.
* **AI-assisted Services:** To enhance user experience, the platform integrates AI-assisted services that enable semantic search, intelligent knowledge retrieval, relationship explanation, and personalized recommendations based on family information. AI serves as an intelligent assistant that helps users discover relevant information and navigate the family knowledge base more efficiently.
* **Architecture:** The system adopts a modular architecture consisting of a Web Management Portal, a Mobile Application for family members, and an AI Service Layer connected through RESTful APIs. This architecture supports future system expansion while maintaining flexibility and scalability.

---

### c) Yêu Cầu Chức Năng / Functional Requirements

| Nhóm (Group) | Yêu Cầu Chi Tiết (English) | Chi Tiết Tiếng Việt |
| :--- | :--- | :--- |
| **User & Security** | • User registration and authentication<br>• Role-Based Access Control (RBAC)<br>• User profile management<br>• Family member verification | • Đăng ký và xác thực người dùng<br>• Phân quyền theo vai trò (RBAC)<br>• Quản lý hồ sơ người dùng<br>• Xác minh thành viên gia đình |
| **Family & Genealogy Management** | • Family management<br>• Branch management<br>• Person management<br>• Parent-child relationship management<br>• Marriage management<br>• Interactive genealogy tree<br>• Relationship visualization<br>• Relationship query | • Quản lý gia đình<br>• Quản lý chi họ<br>• Quản lý thành viên<br>• Quản lý quan hệ cha mẹ - con<br>• Quản lý hôn nhân<br>• Cây gia phả tương tác<br>• Trực quan hóa quan hệ<br>• Truy vấn quan hệ |
| **Community** | • Create and manage posts<br>• Comment and react to posts<br>• Share family news<br>• Photo sharing<br>• Family announcements | • Tạo và quản lý bài đăng<br>• Bình luận và thả cảm xúc<br>• Chia sẻ tin tức gia đình<br>• Chia sẻ ảnh<br>• Thông báo của gia đình |
| **Events** | • Create family events<br>• RSVP management<br>• Participant management<br>• Event gallery<br>• Event reminders | • Tạo sự kiện gia đình<br>• Quản lý phản hồi tham dự (RSVP)<br>• Quản lý người tham dự<br>• Thư viện ảnh sự kiện<br>• Nhắc nhở sự kiện |
| **Family Directory** | • Member directory<br>• Professional profiles<br>• Education profiles<br>• Search members by profession, location, or generation | • Danh bạ thành viên<br>• Hồ sơ nghề nghiệp<br>• Hồ sơ học vấn<br>• Tìm thành viên theo nghề nghiệp, nơi ở hoặc thế hệ |
| **Family Heritage** | • Historical documents<br>• Family stories<br>• Outstanding family members<br>• Photo gallery<br>• Digital archives | • Tài liệu lịch sử<br>• Câu chuyện gia đình<br>• Thành viên tiêu biểu<br>• Thư viện ảnh<br>• Kho lưu trữ số |
| **AI-assisted Services** | • AI-powered semantic search<br>• AI family knowledge assistant<br>• AI relationship explanation<br>• AI content summarization<br>• AI recommendation of related members and family resources | • Tìm kiếm theo ngữ nghĩa bằng AI<br>• Trợ lý tri thức gia đình bằng AI<br>• AI giải thích quan hệ họ hàng<br>• AI tóm tắt nội dung<br>• AI gợi ý thành viên và tài nguyên gia đình liên quan |
| **Dashboard & Reporting** | • Family statistics<br>• Community activity dashboard<br>• Event statistics<br>• Member demographics<br>• Report generation | • Thống kê gia đình<br>• Dashboard hoạt động cộng đồng<br>• Thống kê sự kiện<br>• Nhân khẩu học thành viên<br>• Tạo báo cáo |
| **Administration** | • User management<br>• Content moderation<br>• Audit logging<br>• Backup & Restore<br>• System configuration | • Quản lý người dùng<br>• Kiểm duyệt nội dung<br>• Ghi nhật ký kiểm toán<br>• Sao lưu và khôi phục<br>• Cấu hình hệ thống |

---

### d) Yêu Cầu Phi Chức Năng / Non-Functional Requirements

| Tiêu Chí (Attribute) | Yêu Cầu Chi Tiết (English) | Chi Tiết Tiếng Việt |
| :--- | :--- | :--- |
| **Platform** | • Responsive Web Application<br>• Cross-platform Mobile Application | • Ứng dụng Web responsive<br>• Ứng dụng di động đa nền tảng |
| **Security** | • Secure Authentication (JWT)<br>• Audit Logging | • Xác thực bảo mật (JWT)<br>• Ghi nhật ký kiểm toán |
| **Architecture** | • RESTful API Architecture<br>• Modular Software Architecture<br>• AI Service Integration | • Kiến trúc RESTful API<br>• Kiến trúc phần mềm module<br>• Tích hợp dịch vụ AI |
| **Data & Visualization** | • PostgreSQL Database<br>• Interactive Graph Visualization | • Cơ sở dữ liệu PostgreSQL<br>• Trực quan hóa đồ thị tương tác |
| **Deployment & Availability** | • Docker Deployment<br>• High Availability | • Triển khai bằng Docker<br>• Tính sẵn sàng cao |

---

### e) Cơ Sở Lý Thuyết & Thực Tiễn / Theory & Practical

| Lĩnh Vực (English) | Tiếng Việt |
| :--- | :--- |
| Software Engineering | Công nghệ phần mềm |
| Object-Oriented Analysis and Design | Phân tích và thiết kế hướng đối tượng |
| Software Architecture | Kiến trúc phần mềm |
| Enterprise Information Systems | Hệ thống thông tin doanh nghiệp |
| Database Design | Thiết kế cơ sở dữ liệu |
| Graph Data Modeling | Mô hình hóa dữ liệu đồ thị |
| Human-Computer Interaction | Tương tác người - máy |
| Mobile Application Development | Phát triển ứng dụng di động |
| RESTful API Design | Thiết kế RESTful API |
| Authentication & Authorization | Xác thực và phân quyền |
| Software Testing | Kiểm thử phần mềm |
| Information Retrieval | Truy xuất thông tin |
| Large Language Model (LLM) Integration | Tích hợp mô hình ngôn ngữ lớn (LLM) |

---

### f) Sản Phẩm Đầu Ra / Products (Expected Deliverables)

| Sản Phẩm (English) | Tiếng Việt |
| :--- | :--- |
| **FamilyConnect Web Portal** | Cổng quản trị Web FamilyConnect |
| **FamilyConnect Mobile Application** | Ứng dụng di động FamilyConnect |
| **Genealogy Management Module** | Module quản lý gia phả |
| **Community Management Module** | Module quản lý cộng đồng |
| **Event Management Module** | Module quản lý sự kiện |
| **Family Heritage Module** | Module di sản gia đình |
| **AI Assistant Module** | Module trợ lý AI |
| **Dashboard & Reporting Module** | Module Dashboard và báo cáo |
| **RESTful API Services** | Các dịch vụ RESTful API |
| **Docker Deployment Package** | Gói triển khai Docker |
| **Software Documentation** | Tài liệu phần mềm |

---

### g) Các Gói Công Việc Đề Xuất / Proposed Tasks

| Work Package | Nội Dung Chi Tiết (English) | Chi Tiết Tiếng Việt |
| :--- | :--- | :--- |
| **Work Package 1 – Requirements Analysis & System Design** | Requirement elicitation and stakeholder analysis; business process modelling; software architecture design; database design; UI/UX design; API specification. | Thu thập yêu cầu và phân tích các bên liên quan; mô hình hóa quy trình nghiệp vụ; thiết kế kiến trúc phần mềm; thiết kế cơ sở dữ liệu; thiết kế UI/UX; đặc tả API. |
| **Work Package 2 – Core Platform Development** | User authentication and authorization; family and genealogy management; community management; event management; family heritage management. | Xác thực và phân quyền người dùng; quản lý gia đình và gia phả; quản lý cộng đồng; quản lý sự kiện; quản lý di sản gia đình. |
| **Work Package 3 – Mobile Application & AI Integration** | Mobile application development; AI semantic search; AI knowledge assistant; AI relationship explanation; AI recommendation services. | Phát triển ứng dụng di động; tìm kiếm ngữ nghĩa bằng AI; trợ lý tri thức AI; AI giải thích quan hệ; dịch vụ gợi ý bằng AI. |
| **Work Package 4 – Visualization & Analytics** | Interactive genealogy visualization; dashboard development; statistical reporting; data export; system administration. | Trực quan hóa gia phả tương tác; phát triển Dashboard; báo cáo thống kê; xuất dữ liệu; quản trị hệ thống. |
| **Work Package 5 – Testing & Deployment** | System integration; functional testing; performance testing; Docker deployment; user documentation. | Tích hợp hệ thống; kiểm thử chức năng; kiểm thử hiệu năng; triển khai Docker; tài liệu hướng dẫn sử dụng. |

---

### 3. Ý Kiến Khác / Other Comments

*(Đề xuất bổ sung nếu có / Additional proposals, if any)*

Nhóm bổ sung ba yêu cầu phi chức năng (chi tiết ở `docs/01-srs/srs.md`, NFR-12 đến NFR-14) / The team adds three non-functional requirements (details in `docs/01-srs/srs.md`, NFR-12 to NFR-14):

* **Hiệu năng / Performance (NFR-12):** API cây gia phả dưới 1 giây với 5.000 người; tìm kiếm ngữ nghĩa dưới 2 giây. / Genealogy tree API under 1 second with 5,000 people; semantic search under 2 seconds.
* **Quyền riêng tư dữ liệu / Data privacy (NFR-13):** chỉ thành viên đã xác minh mới xem dữ liệu gia đình; quy tắc cho trẻ em và người đã mất; không gửi dữ liệu nhạy cảm cho LLM (`docs/01-srs/privacy.md`). / Only verified members can view family data; rules for children and deceased persons; no sensitive data is sent to the LLM.
* **Độ tin cậy của AI / AI reliability (NFR-14):** AI chỉ trả lời từ dữ liệu gia đình; không có thông tin thì trả lời "không tìm thấy"; tỷ lệ trả lời đúng từ 80% trên bộ câu hỏi kiểm tra. / The AI answers only from family data, replies "not found" when there is no information, and reaches at least 80% correct answers on the test question set.

</details>
