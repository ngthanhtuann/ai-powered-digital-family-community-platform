# [7-01] Báo cáo kiểm thử hồi quy toàn hệ thống

**Người làm:** TV1 Tuấn

**Reviewer:** TV4

**Nhánh chuẩn đối chiếu:** `develop` tại `b1e8294` (12/11/2026, sau khi merge PR #113, #114, #115)
**Phạm vi:** toàn bộ backend, web, mobile; luồng xuyên module; các nhóm test bắt buộc ở `test-plan.md` mục 3.

> Trạng thái chỉ ghi "Đạt" khi đã chạy và có kết quả. Phần chưa chạy ghi "Chưa xác minh".

## 1. Tóm tắt

| Hạng mục | Kết quả |
|---|---|
| Test tự động backend (`mvn -B verify`) | **426/426 đạt** |
| Web (`gen:api`, `lint`, `build`) | Đạt |
| Mobile (`gen:api`, `typecheck`, `lint`, `prettier`, `expo export` Android) | Đạt |
| Kiểm thử API trên hệ thống chạy thật (`docker/test/regression-api.mjs`) | **53/53 ca đạt** |
| Dữ liệu demo (`docker/seed-demo.ps1`) | Đạt (53 người, bước tự kiểm tra đạt) |
| Bug mới | **1 Minor** (SCRUM-102, mục 5). Không có bug Critical hoặc Major |
| Đối chiếu FR Must (mục 4.3) | 43/46 FR có test đạt; 3 FR còn chờ (lý do ở mục 4.3) |
| Chưa xác minh | Giao diện 3 cỡ màn hình (UI), AI với LLM thật |

## 2. Môi trường

- Máy: laptop Windows 11, Docker Desktop 29.1.3.
- Test tự động backend: PostgreSQL `pgvector/pgvector:pg16` tạm, cơ sở dữ liệu trống.
- Kiểm thử API: stack `docker compose` riêng (`-p fcseed`, cổng 18080), image backend build từ `develop` `b1e8294`, cơ sở dữ liệu trống. Không có `GEMINI_API_KEY` nên AI trả 503 `AI_001`, đúng thiết kế (NFR-08).

## 3. Test tự động

### 3.1. Backend theo module

| Module | Số test | Kết quả |
|---|---|---|
| Gia phả (gồm 153 ca thuật toán quan hệ SCRUM-42) | 183 | Đạt |
| Auth, Admin | 70 | Đạt |
| Community | 57 | Đạt |
| AI | 53 | Đạt |
| Directory | 18 | Đạt |
| Heritage | 16 | Đạt |
| Events | 14 | Đạt |
| Chung (bảo mật, JWT, đồng bộ `x-roles` với tài liệu) | 13 | Đạt |
| Dashboard | 2 | Đạt |
| **Tổng** | **426** | **Đạt** |

### 3.2. Web và mobile

| Kiểm tra | Web | Mobile |
|---|---|---|
| Kiểu sinh từ OpenAPI (`npm run gen:api`) không lệch | Đạt | Đạt |
| Lint | Đạt (không cảnh báo) | Đạt |
| Build / typecheck | `next build` đạt | `tsc` đạt |
| Đóng gói | - | `expo export --platform android` đạt |

## 4. Kiểm thử API trên hệ thống chạy thật

`docker/test/regression-api.mjs` tự tạo tài khoản và gia đình giả mới mỗi lần chạy. Chạy lại:

```powershell
node docker/test/regression-api.mjs --api http://localhost:8080/api/v1 --admin-email <email SYSTEM_ADMIN> --admin-password <mật khẩu>
```

### 4.1. Luồng xuyên module (Jira SCRUM-65 bước 3)

Đăng ký → xin vào gia đình → trưởng họ thêm thành viên và duyệt → thêm trẻ em, người đã mất, quan hệ cha-con → tra quan hệ, xem cây → đăng bài kèm ảnh → tạo sự kiện, RSVP → hỏi chatbot.

| Mã | Bước | Kết quả thực tế | Trạng thái |
|---|---|---|---|
| FLOW-01, 02 | Tạo gia đình, chi | 201 | Đạt |
| FLOW-03 | Xin vào gia đình | 201 `PENDING` | Đạt |
| FLOW-04, 05 | Thêm thành viên vào cây, duyệt kèm gắn hồ sơ | 201, 200 `APPROVED` | Đạt |
| FLOW-06 | Thành viên đã duyệt xem gia đình | 200 | Đạt |
| FLOW-07, 08 | Thêm trẻ em, người đã mất, quan hệ cha-con | 201 | Đạt |
| FLOW-09, 10 | Tra quan hệ ("con"), xem cây | 200 | Đạt |
| FLOW-11, 12 | Đăng bài, bài hiện trên bảng tin | 201, 200 | Đạt |
| FLOW-13, 14 | Tạo sự kiện, RSVP | 201, 200 | Đạt |
| AI-01 | Hỏi chatbot khi chưa có khóa LLM | 503 `AI_001`, chức năng khác vẫn chạy | Đạt |

### 4.2. Nhóm bắt buộc (`test-plan.md` mục 3)

| Nhóm | Ca trong kịch bản API | Test tự động bổ sung | Trạng thái |
|---|---|---|---|
| 3.1 `PERM` | PERM-01 (chưa xác minh 403 `PERM_002`), PERM-02, 03 (sai vai trò 403 `PERM_001`) | `RbacMatrixIntegrationTest` (mọi endpoint), `EndpointRolesDocsSyncTest` (`x-roles` khớp OpenAPI) | Đạt |
| 3.2 `IDOR` | IDOR-01 đến 06: gia đình B gọi gia đình, người, cây, ảnh, quan hệ của A đều 403 `PERM_001`; bảng tin B không có bài của A | `RbacMatrixIntegrationTest` (trưởng chi gia đình khác) | Đạt |
| 3.3 `FILE` | FILE-01 (png 201), FILE-02 (đổi đuôi 415 `FILE_002`), FILE-03 (> 5 MB 413 `FILE_001`) | `MediaStorageTest`, `MediaApiIntegrationTest` (PDF Heritage, tệp gia đình khác) | Đạt |
| 3.4 `API` | API-01 (meta), API-02 (`size=101` 400 `VALID_001`), API-03 (DELETE 200, `data` null), API-04 (19 phản hồi lỗi đều đúng dạng `{success:false, error:{code,message,details[]}}`) | | Đạt |
| 3.5 `INT` | INT-01 (gắn quan hệ cho người gia đình khác bị 403), INT-02 (ID không tồn tại 404 `NOT_FOUND_001`) | | Đạt |
| 3.6 `PRIV` | PRIV-01 (trẻ < 16: không có ngày sinh đầy đủ, địa chỉ), PRIV-02 (không có trường nguyên nhân tử vong), PRIV-03 (hồ sơ không trả email, điện thoại, `userId`) | `RelationshipExplanationServiceTest` (tên trẻ em được thay trước khi gửi LLM) | Đạt |
| 3.7 `AI` | AI-01 (LLM không dùng được: 503 `AI_001`), AI-02 (chưa xác minh 403 `PERM_002`) | `AiChatGroundingTest`, `AiSearchRateLimiterTest`, `SemanticSearchServiceTest` | Đạt một phần; trả lời thật với LLM: Chưa xác minh |
| 3.8 `UI` | | | **Chưa xác minh** |
| 3.9 `AUTH` | AUTH-01 đến 08: đăng ký, đăng nhập, sai mật khẩu 401 `AUTH_001`, không hoặc sai token 401 `AUTH_002`, làm mới token, tài khoản khóa 403 `AUTH_003` | `AuthApiIntegrationTest` (gồm 429 `RATE_001`, token hết hạn) | Đạt |
| 3.10 Thuật toán quan hệ | | `RelationshipCaseCatalogTest`: 153 ca từ bộ ca mẫu SCRUM-42 | Đạt |

Kết quả từng ca của lần chạy cuối có trong đầu ra của kịch bản (53 dòng, 53 "Đạt").

### 4.3. Đối chiếu FR Must với test (`test-plan.md` mục 7)

Cả 46 FR trong `srs.md` đều là Must. Cột "Test" ghi lớp test backend (đã chạy, đạt ở mục 3.1) và mã ca trong kịch bản API (mục 4). Đối chiếu bằng endpoint mà test thực sự gọi.

| FR | Test | Trạng thái |
|---|---|---|
| FR-AUTH-01 Đăng ký, xác thực | `AuthApiIntegrationTest`; AUTH-01 đến 08 | Đạt |
| FR-AUTH-02 RBAC | `RbacMatrixIntegrationTest`, `EndpointRolesDocsSyncTest`; PERM-01 đến 03 | Đạt |
| FR-AUTH-03 Hồ sơ người dùng | `AuthApiIntegrationTest`, `AuthServiceTest`; AUTH-07 | Đạt |
| FR-AUTH-04 Xác minh thành viên | `JoinRequestApiIntegrationTest`; FLOW-03 đến 06 | Đạt |
| FR-GEN-01 đến 03 Gia đình, chi, thành viên | `GenealogyApiIntegrationTest`; FLOW-01, 02, 04, 07 | Đạt |
| FR-GEN-04, 05 Cha mẹ con, hôn nhân | `FamilyTreeApiIntegrationTest`, `RelationshipApiIntegrationTest`; FLOW-08 | Đạt |
| FR-GEN-06 Cây gia phả | `FamilyTreeApiIntegrationTest`; FLOW-10 | Đạt |
| FR-GEN-07 Hiển thị đường quan hệ | API trả đường đi (`KinshipApiIntegrationTest`); phần tô sáng trên giao diện chưa kiểm tra | Đạt phần API; giao diện Chưa xác minh |
| FR-GEN-08 Truy vấn quan hệ | `RelationshipCaseCatalogTest` (153 ca), `KinshipApiIntegrationTest`; FLOW-09 | Đạt |
| FR-COM-01 Bài đăng | `CommunityServiceTest`, `CommunityControllerTest`; FLOW-11, 12, API-03 | Đạt |
| FR-COM-02 Bình luận | Kịch bản: COM-01, 02 (thêm và đọc bình luận); `AdminApiIntegrationTest` (kiểm duyệt) | Đạt |
| FR-COM-03 Reaction | `CommunityControllerTest`; COM-03 | Đạt |
| FR-COM-04 Chia sẻ ảnh | `MediaApiIntegrationTest`, `MediaStorageTest`; FILE-01 đến 03 | Đạt |
| FR-COM-05 Thông báo dòng họ | `AnnouncementNotificationApiIntegrationTest` | Đạt |
| FR-EVT-01 đến 03 Sự kiện, RSVP, người tham dự | `EventApiIntegrationTest`, `EventServiceTest`; FLOW-13, 14 | Đạt |
| FR-EVT-04 Thư viện ảnh sự kiện | Kịch bản: EVT-01 (tải ảnh vào sự kiện) | Đạt |
| FR-EVT-05 Nhắc sự kiện | `EventReminderSchedulerTest`, `NotificationServiceTest` | Đạt |
| FR-DIR-01 đến 04 Danh bạ, hồ sơ nghề, học vấn, tìm kiếm | `DirectoryApiIntegrationTest`, `DirectoryServiceTest`, `DirectoryControllerTest` | Đạt |
| FR-HER-01, 02 Tài liệu, câu chuyện | `HeritageServiceTest`, `HeritageControllerTest` | Đạt |
| FR-HER-03 Người tiêu biểu | `OutstandingMemberApiIntegrationTest` | Đạt |
| FR-HER-04, 05 Ảnh, lưu trữ số (PDF) | `MediaApiIntegrationTest`, `MediaStorageTest`, `HeritageControllerTest` | Đạt |
| FR-AI-01 Tìm kiếm ngữ nghĩa | `SemanticSearchApiIntegrationTest`, `SemanticSearchServiceTest` (LLM giả lập) | Đạt; với Gemini thật Chưa xác minh |
| FR-AI-02 Trợ lý AI | `AiChatApiIntegrationTest`, `AiChatGroundingTest` (LLM giả lập); AI-01, 02 | Đạt; với Gemini thật Chưa xác minh |
| FR-AI-03 Giải thích quan hệ | `RelationshipExplanationServiceTest` | Đạt |
| FR-AI-04 Tóm tắt | `AiSummaryApiIntegrationTest`, `AiSummaryServiceTest` | Đạt |
| FR-AI-05 Gợi ý | Chưa có: SCRUM-70 chưa làm | **Chưa có test** |
| FR-DSH-01 đến 04 Thống kê | `DashboardApiIntegrationTest` (một endpoint thống kê chung, phạm vi theo chi) | Đạt |
| FR-DSH-05 Báo cáo | Xuất CSV: `DashboardApiIntegrationTest`. PDF: SCRUM-95 chưa làm | Đạt phần CSV; PDF **chưa có** |
| FR-ADM-01, 02 Người dùng, kiểm duyệt | `AdminApiIntegrationTest`, `AdminConcurrencyIntegrationTest`; ADM-01, 02 | Đạt |
| FR-ADM-03 Nhật ký kiểm toán | `AdminApiIntegrationTest`; NFR-11 | Đạt |
| FR-ADM-04 Sao lưu, khôi phục | `BackupServiceTest` | Đạt |
| FR-ADM-05 Cấu hình hệ thống | `SystemConfigApiIntegrationTest` trong PR #116 (431/431 đạt) | Đạt trên PR #116, chưa vào `develop` |

**Kết luận mục 7 của test-plan:** 43/46 FR có test đạt; FR-ADM-05 chờ merge PR #116; FR-AI-05 và phần PDF của FR-DSH-05 chưa có vì task SCRUM-70, SCRUM-95 chưa làm.

## 5. Bug

| Mã | Mô tả | Mức độ | Trạng thái |
|---|---|---|---|
| SCRUM-102 | [Gia phả] Tìm thành viên theo họ tên (`GET /families/{id}/persons?q=`) không khớp khi gõ không dấu; Danh bạ và Admin đã hỗ trợ (`unaccent`) | Minor | To Do, giao TV2 (subtask của SCRUM-66) |

Lần chạy đầu của kịch bản có 1 ca không đạt (IDOR-04 trả 404). Nguyên nhân nằm ở **kịch bản**: ca xóa bài chạy trước nên ảnh không còn. Sau khi sửa kịch bản, ca này trả 403 `PERM_001` đúng mong đợi. Không phải lỗi hệ thống nên không tạo bug.

## 6. Chưa xác minh và việc cần làm tiếp

| Hạng mục | Lý do | Đề xuất |
|---|---|---|
| `UI`: các màn hình web ở 360, 768, 1440 px | Chưa chạy trên trình duyệt | Chủ module kiểm tra trong SCRUM-66, 67 (sửa bug, responsive) |
| AI với LLM thật (trả lời đúng, "không tìm thấy", chỉ dữ liệu gia đình người hỏi) | Máy thử chưa có `GEMINI_API_KEY` | Đo trong SCRUM-69 (đánh giá AI) |
| FR-AI-05, PDF của FR-DSH-05 | SCRUM-70, SCRUM-95 chưa làm | Viết test khi hai task này xong |
| NFR-09 (`docker compose up` máy sạch), NFR-10 (2 bản backend) | Thuộc SCRUM-68, 77, 96, chưa làm | Hồi quy lại sau khi các task này merge |
| PR #116 (SCRUM-94, cấu hình hệ thống) | Chưa merge vào `develop` lúc chạy | Đã có test riêng: 431/431 đạt. Chạy lại kịch bản này sau khi merge |

## 7. Code freeze

Theo kế hoạch Sprint 7, **code freeze cuối tuần 14/11/2026**: sau thời điểm này `develop` chỉ nhận PR sửa bug. Trước khi freeze, chạy lại `mvn -B verify` và `docker/test/regression-api.mjs` trên `develop` mới nhất, rồi cập nhật bảng ở mục 1.
