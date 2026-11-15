# Bảng đối chiếu đề tài

> Dùng cho báo cáo cuối (task SCRUM-71, TV1). Đối chiếu từng mục đề tài giáo viên giao với nơi đã làm. Cột Mức độ và Bằng chứng điền ngày 16/11/2026 theo `develop` tại `f895207` (`mvn -B verify` 464 test đạt); PR #136, #137, #138 ghi rõ ở dòng liên quan. Test nằm ở `backend/src/test/java/com/familyconnect/`.

| Mục của đề tài | Mã FR/NFR | Task Jira | Mức độ (đầy đủ / cơ bản) | Bằng chứng (link, ảnh) |
|---|---|---|---|---|
| User registration and authentication | FR-AUTH-01 | SCRUM-35, 36 | Đầy đủ | Test `AuthApiIntegrationTest`, `AuthServiceTest`, `JwtServiceTest`; web `/register`, `/login`; mobile `RegisterScreen`, `LoginScreen` |
| Role-Based Access Control | FR-AUTH-02 | SCRUM-43 | Đầy đủ | Test `RbacMatrixIntegrationTest` (ma trận quyền), `EndpointRolesDocsSyncTest` (khớp `x-roles` OpenAPI), `SecurityConfigTest` |
| User profile management | FR-AUTH-03 | SCRUM-43 | Đầy đủ | Test `AuthApiIntegrationTest`; web `/profile`; mobile `ProfileScreen`, `ChangePasswordScreen` |
| Family member verification | FR-AUTH-04 | SCRUM-35, 57 | Đầy đủ | Test `JoinRequestApiIntegrationTest`, `MembershipServiceTest`; web `/join-family`, `/join-requests`, `/pending-approval`; mobile `MembershipScreens` |
| Family / Branch / Member management | FR-GEN-01..03 | SCRUM-37 | Đầy đủ | Test `GenealogyApiIntegrationTest` (gồm tìm họ tên không dấu, SCRUM-102); web `/families`, `/members`, `/persons` |
| Parent-child, Marriage management | FR-GEN-04, 05 | SCRUM-44 | Đầy đủ | Test `GenealogyApiIntegrationTest`, `RelationshipApiIntegrationTest`; web `RelationModal` trên trang cây |
| Interactive genealogy tree | FR-GEN-06 | SCRUM-45, 52 | Đầy đủ | Test `FamilyTreeApiIntegrationTest` (cây 500 người dưới 500 ms); web `/families/{id}/tree`; mobile `FamilyTreeScreen`; `docs/06-test/performance-6-04.md` |
| Relationship visualization | FR-GEN-07 | SCRUM-56 | Đầy đủ | Web: tô sáng đường quan hệ trên cây, trang `/relationship` (SCRUM-108, PR #137); mobile `RelationshipScreen` |
| Relationship query | FR-GEN-08 | SCRUM-51 | Đầy đủ | Test `RelationshipApiIntegrationTest`, `KinshipApiIntegrationTest`, `RelationshipCaseCatalogTest`; `docs/02-design/algorithm-relationship.md`; `docs/09-report/genealogy-report.md` |
| Posts, comments, reactions, share family news | FR-COM-01..03 | SCRUM-38 | Đầy đủ | Test `CommunityServiceTest`, `CommunityControllerTest`, `ModulePlatformAdapterTest`; web `/community`; mobile `FeedScreen`, `PostDetailScreen`, `CreatePostScreen`; `docs/06-test/community-posts-3-04.md` |
| Photo sharing | FR-COM-04 | SCRUM-46 | Đầy đủ | Test `MediaApiIntegrationTest`, `MediaServiceTest`, `MediaStorageTest`; mobile chọn hoặc chụp ảnh (`expo-image-picker`) |
| Family announcements | FR-COM-05 | SCRUM-53 | Đầy đủ | Test `AnnouncementNotificationApiIntegrationTest`, `NotificationServiceTest` |
| Create events, RSVP, participants | FR-EVT-01..03 | SCRUM-47 | Đầy đủ | Test `EventApiIntegrationTest`, `EventServiceTest`; web `/events`; mobile `EventsScreen`, `EventDetailScreen`; `docs/06-test/events-4-05.md` |
| Event gallery, reminders | FR-EVT-04, 05 | SCRUM-53 | Đầy đủ | Test `EventReminderSchedulerTest`, `NotificationServiceTest`; mobile `NotificationsScreen`; `docs/06-test/events-notifications-5-04.md`. Nhắc nhở trong ứng dụng, chưa có push notification |
| Member directory, professional and education profiles | FR-DIR-01..03 | SCRUM-48 | Đầy đủ | Test `DirectoryApiIntegrationTest`, `DirectoryServiceTest`, `DirectoryControllerTest`; web `/directory`; mobile `DirectoryScreen`, `PersonDetailScreen` (SCRUM-92) |
| Search members by profession, location, generation | FR-DIR-04 | SCRUM-61 | Đầy đủ | Test `DirectoryApiIntegrationTest`, `PersonServiceSearchIndexTest` (tìm không dấu, lọc nghề, nơi ở, thế hệ) |
| Historical documents, family stories | FR-HER-01, 02 | SCRUM-39 | Đầy đủ | Test `HeritageServiceTest`, `HeritageControllerTest`; web `/heritage/documents`, `/heritage/stories`; mobile `HeritageScreen` (xem) |
| Outstanding family members | FR-HER-03 | SCRUM-62 | Đầy đủ (web) | Test `OutstandingMemberApiIntegrationTest`; web `/heritage/outstanding-members`. Mobile không có (`mobile.md` mục 2) |
| Photo gallery, Digital archives | FR-HER-04, 05 | SCRUM-87 | Đầy đủ | Web `/heritage/photos`, `/heritage/documents`; mobile `HeritageScreen` tab Ảnh, Tài liệu; mở PDF trên mobile SCRUM-109 (PR #138, chưa thử trên thiết bị) |
| AI semantic search | FR-AI-01 | SCRUM-41, 49 | Đầy đủ | Test `SemanticSearchApiIntegrationTest`, `SemanticSearchServiceTest`; đo 15 câu với Gemini thật: 15/15 ở ngưỡng 0,65 (`docs/decisions/semantic-search-evaluation.md`, SCRUM-105); web `/search`; mobile `AiSearchScreen` |
| AI family knowledge assistant | FR-AI-02 | SCRUM-55, 63 | Đầy đủ | Test `AiChatApiIntegrationTest`, `AiChatGroundingTest`, `ChatCitationTest` (PR #136); `docs/06-test/ai-quality-scrum-69.md`, `docs/06-test/ai-chat-5-06.md`; web `/assistant`; mobile `AiChatScreen` |
| AI relationship explanation | FR-AI-03 | SCRUM-64 | Đầy đủ | Test `RelationshipExplanationServiceTest`; nút "Giải thích bằng AI" trên panel cây và trang `/relationship` |
| AI content summarization | FR-AI-04 | SCRUM-88 | Đầy đủ | Test `AiSummaryApiIntegrationTest`, `AiSummaryServiceTest`; `docs/06-test/ai-summary-5-09.md` |
| AI recommendation | FR-AI-05 | SCRUM-70 | Đầy đủ | Test `RecommendationApiIntegrationTest` |
| Family statistics, community, event statistics, demographics | FR-DSH-01..04 | SCRUM-54 | Đầy đủ | Test `DashboardApiIntegrationTest`; web `/dashboard` |
| Report generation, data export | FR-DSH-05 | SCRUM-95, 62 | Đầy đủ | Test `DashboardApiIntegrationTest` (PDF, CSV); web `/reports`, nút xuất CSV ở `/directory` |
| User management, content moderation | FR-ADM-01, 02 | SCRUM-50 | Đầy đủ | Test `AdminApiIntegrationTest`, `AdminConcurrencyIntegrationTest`, `ContentReportNotificationsTest`; web `/admin` |
| Audit logging | FR-ADM-03 | SCRUM-57 | Đầy đủ | `JdbcAuditLogger`; test `AdminApiIntegrationTest` (đọc nhật ký), `AuthServiceTest` |
| Backup & Restore | FR-ADM-04 | SCRUM-86 | Đầy đủ | Test `BackupServiceTest`; backup và restore đạt trên stack thử (`docs/06-test/high-availability-7-11.md`); hướng dẫn `docs/07-deploy/backup-restore.md` |
| System configuration | FR-ADM-05 | SCRUM-94 | Đầy đủ | Test `SystemConfigApiIntegrationTest`; web `/admin` |
| Cross-platform Mobile Application | NFR-02 | SCRUM-84, 85, 89, 90, 91, 92, 93 | Đầy đủ (chưa thử thiết bị phần mới) | Expo SDK 58, APK dev build (SCRUM-93); `docs/06-test/mobile-community-events-6-10.md`. SCRUM-92 (Danh bạ, Di sản) và SCRUM-109 (PDF) mới kiểm biên dịch, chưa thử trên điện thoại |
| High Availability | NFR-10 | SCRUM-96 | Cơ bản | `docker-compose.ha.yml`: 2 bản backend sau Nginx, tự restart, rate limit dùng chung; failover 30/30 HTTP 200 (`docs/06-test/high-availability-7-11.md`) |
| Docker Deployment | NFR-09 | SCRUM-68, 77 | Cơ bản | `docker-compose.yml`, `docker-compose.ha.yml`, `docs/07-deploy/deploy.md`. Chưa thử trên máy sạch (SCRUM-77) |
| Responsive Web, RESTful API, JWT, Modular architecture | NFR-01, 03, 04, 05 | SCRUM-40, 21, 35, 23 | Đầy đủ (API, JWT, kiến trúc); responsive mới kiểm một phần | API theo `docs/04-api/conventions.md`; `JwtServiceTest`; modular monolith theo `architecture.md`, module gọi nhau qua interface. Responsive: layout theo `design-system.md`; SCRUM-67 làm responsive Community/Events; `/relationship` đã kiểm 360/768/1440 (PR #137); `regression-7-01.md` ghi phần UI các màn còn lại chưa chạy trên trình duyệt |
| Interactive Graph Visualization | NFR-06 | SCRUM-52, 56 | Đầy đủ | Cây React Flow (`@xyflow/react`) và bố cục dagre, mở/thu nhánh, tải lười |
| PostgreSQL Database | NFR-07 | SCRUM-28 | Đầy đủ | PostgreSQL 16 + pgvector, Flyway V1 đến V22; `docs/03-database/erd.md`, `data-dictionary.md` |
| AI Service Integration | NFR-08 | SCRUM-23, 55 | Đầy đủ | Gemini qua REST (`GeminiHttp`, timeout); test `ChatLanguageModelTest`, `EmbeddingPipelineServiceTest`, `EmbeddingIndexServiceTest` |
| Audit Logging | NFR-11 | SCRUM-57 | Đầy đủ | `AuditLogger` dùng chung; ghi đăng nhập, đổi quyền, duyệt, backup, restore, truy vấn AI |
