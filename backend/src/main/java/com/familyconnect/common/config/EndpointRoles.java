package com.familyconnect.common.config;

import io.swagger.v3.oas.models.PathItem;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ghi x-roles (vai trò được gọi, theo docs/04-api/openapi/*.yaml) lên Swagger cho từng endpoint đã code (SCRUM-43).
 * Chỉ để hiển thị: quyền thật kiểm ở service qua MembershipQuery. Docker chỉ build thư mục backend nên không đọc
 * được docs lúc chạy; EndpointRolesDocsSyncTest so bảng này với tài liệu, endpoint mới thiếu hoặc lệch là test fail.
 */
@Configuration
public class EndpointRoles {

    static final String API_PREFIX = "/api/v1";

    private static final List<String> ANYONE = List.of("GUEST", "MEMBER", "BRANCH_ADMIN", "SYSTEM_ADMIN");
    private static final List<String> SIGNED_IN = List.of("MEMBER", "BRANCH_ADMIN", "SYSTEM_ADMIN");
    private static final List<String> ADMINS = List.of("BRANCH_ADMIN", "SYSTEM_ADMIN");
    private static final List<String> SYSTEM_ADMIN = List.of("SYSTEM_ADMIN");

    /** Khóa "METHOD /đường-dẫn" không có tiền tố /api/v1, biến đường dẫn viết {}. */
    private static final Map<String, List<String>> ROLES = Map.ofEntries(
            // Auth (auth.yaml)
            Map.entry("POST /users", ANYONE),
            Map.entry("POST /auth/login", ANYONE),
            Map.entry("POST /auth/refresh", ANYONE),
            Map.entry("DELETE /auth/sessions/current", SIGNED_IN),
            Map.entry("GET /users/me", SIGNED_IN),
            Map.entry("PATCH /users/me", SIGNED_IN),
            Map.entry("PUT /users/me/password", SIGNED_IN),
            Map.entry("GET /users/me/membership", SIGNED_IN),
            // Xin tham gia và duyệt (UC-AUTH-06, 07); phạm vi chi kiểm ở JoinRequestService.
            Map.entry("POST /join-requests", List.of("MEMBER")),
            Map.entry("GET /join-requests", ADMINS),
            Map.entry("PATCH /join-requests/{}", ADMINS),
            Map.entry("DELETE /join-requests/{}", List.of("MEMBER")),
            // Gia phả (genealogy.yaml). Tạo gia đình: MEMBER chưa thuộc gia đình nào hoặc SYSTEM_ADMIN.
            Map.entry("GET /families", SIGNED_IN),
            Map.entry("POST /families", List.of("MEMBER", "SYSTEM_ADMIN")),
            Map.entry("GET /families/{}", SIGNED_IN),
            Map.entry("PUT /families/{}", ADMINS),
            Map.entry("DELETE /families/{}", SYSTEM_ADMIN),
            Map.entry("GET /families/{}/branches", SIGNED_IN),
            Map.entry("POST /families/{}/branches", ADMINS),
            Map.entry("PUT /branches/{}", ADMINS),
            Map.entry("DELETE /branches/{}", ADMINS),
            Map.entry("GET /families/{}/persons", SIGNED_IN),
            Map.entry("POST /families/{}/persons", ADMINS),
            Map.entry("GET /persons/{}", SIGNED_IN),
            Map.entry("PUT /persons/{}", ADMINS),
            Map.entry("DELETE /persons/{}", ADMINS),
            Map.entry("POST /persons/{}/parents", ADMINS),
            Map.entry("DELETE /persons/{}/parents/{}", ADMINS),
            Map.entry("POST /marriages", ADMINS),
            Map.entry("PUT /marriages/{}", ADMINS),
            Map.entry("DELETE /marriages/{}", ADMINS),
            Map.entry("GET /families/{}/tree", SIGNED_IN),
            Map.entry("GET /relationships", SIGNED_IN),
            // Sự kiện (BR-EVT-01..03, community-events.yaml).
            Map.entry("GET /events", SIGNED_IN),
            Map.entry("POST /events", SIGNED_IN),
            Map.entry("GET /events/{}", SIGNED_IN),
            Map.entry("PATCH /events/{}", SIGNED_IN),
            Map.entry("DELETE /events/{}", SIGNED_IN),
            Map.entry("PUT /events/{}/rsvp", SIGNED_IN),
            Map.entry("GET /events/{}/participants", SIGNED_IN),
            Map.entry("POST /events/{}/participants", SIGNED_IN),
            Map.entry("DELETE /participants/{}", SIGNED_IN),
            // Cộng đồng, tệp (community-events.yaml). Đăng NEWS chỉ trưởng chi: kiểm ở service.
            Map.entry("GET /posts", SIGNED_IN),
            Map.entry("POST /posts", SIGNED_IN),
            Map.entry("GET /posts/{}", SIGNED_IN),
            Map.entry("PATCH /posts/{}", SIGNED_IN),
            Map.entry("DELETE /posts/{}", SIGNED_IN),
            Map.entry("GET /posts/{}/comments", SIGNED_IN),
            Map.entry("POST /posts/{}/comments", SIGNED_IN),
            Map.entry("PATCH /comments/{}", SIGNED_IN),
            Map.entry("DELETE /comments/{}", SIGNED_IN),
            Map.entry("POST /posts/{}/reactions", SIGNED_IN),
            Map.entry("DELETE /posts/{}/reactions", SIGNED_IN),
            Map.entry("POST /media", SIGNED_IN),
            Map.entry("GET /media/{}/content", SIGNED_IN),
            Map.entry("DELETE /media/{}", SIGNED_IN),
            Map.entry("GET /posts/{}/media", SIGNED_IN),
            Map.entry("POST /posts/{}/media", SIGNED_IN),
            // SCRUM-53: thư viện ảnh sự kiện, thông báo và announcements.
            Map.entry("GET /events/{}/media", SIGNED_IN),
            Map.entry("POST /events/{}/media", SIGNED_IN),
            Map.entry("GET /notifications", SIGNED_IN),
            Map.entry("PATCH /notifications/{}", SIGNED_IN),
            Map.entry("GET /announcements", SIGNED_IN),
            Map.entry("POST /announcements", ADMINS),
            Map.entry("GET /announcements/{}", SIGNED_IN),
            Map.entry("PATCH /announcements/{}", ADMINS),
            Map.entry("DELETE /announcements/{}", ADMINS),
            Map.entry("POST /content-reports", List.of("MEMBER", "BRANCH_ADMIN")),
            // Quản trị (auth.yaml, UC-AUTH-08, 09): chỉ SYSTEM_ADMIN, kiểm ở AdminService.
            Map.entry("GET /admin/users", SYSTEM_ADMIN),
            Map.entry("GET /admin/users/{}", SYSTEM_ADMIN),
            Map.entry("PATCH /admin/users/{}", SYSTEM_ADMIN),
            Map.entry("PUT /admin/users/{}/roles", SYSTEM_ADMIN),
            Map.entry("GET /admin/content-reports", SYSTEM_ADMIN),
            Map.entry("PATCH /admin/content-reports/{}", SYSTEM_ADMIN),
            Map.entry("PUT /admin/users/{}/membership", SYSTEM_ADMIN),
            Map.entry("POST /admin/backups", SYSTEM_ADMIN),
            Map.entry("GET /admin/backups", SYSTEM_ADMIN),
            Map.entry("POST /admin/restores", SYSTEM_ADMIN),
            Map.entry("GET /admin/audit-logs", SYSTEM_ADMIN),
            Map.entry("GET /admin/configs", SYSTEM_ADMIN),
            Map.entry("PUT /admin/configs/{}", SYSTEM_ADMIN),
            // Trưởng họ bổ nhiệm trưởng chi (UC-AUTH-14): chỉ BRANCH_ADMIN không gán chi, kiểm ở FamilyAccountService.
            Map.entry("GET /families/{}/accounts", ADMINS),
            Map.entry("PUT /families/{}/accounts/{}", ADMINS),
            // Di sản (heritage-directory-dashboard.yaml)
            Map.entry("GET /heritage/documents", SIGNED_IN),
            Map.entry("POST /heritage/documents", ADMINS),
            Map.entry("GET /heritage/documents/{}", SIGNED_IN),
            Map.entry("PATCH /heritage/documents/{}", ADMINS),
            Map.entry("DELETE /heritage/documents/{}", ADMINS),
            Map.entry("GET /heritage/photos", SIGNED_IN),
            Map.entry("GET /heritage/stories", SIGNED_IN),
            Map.entry("POST /heritage/stories", SIGNED_IN),
            Map.entry("GET /heritage/stories/{}", SIGNED_IN),
            Map.entry("PATCH /heritage/stories/{}", SIGNED_IN),
            Map.entry("DELETE /heritage/stories/{}", SIGNED_IN),
            Map.entry("GET /heritage/outstanding-members", SIGNED_IN),
            Map.entry("POST /heritage/outstanding-members", ADMINS),
            Map.entry("PATCH /heritage/outstanding-members/{}", ADMINS),
            Map.entry("DELETE /heritage/outstanding-members/{}", ADMINS),
            // Danh bạ (heritage-directory-dashboard.yaml). Quyền sửa hồ sơ kiểm ở service (UC-DIR-02).
            Map.entry("GET /directory/members", SIGNED_IN),
            Map.entry("GET /directory/members/{}/profession-profile", SIGNED_IN),
            Map.entry("PUT /directory/members/{}/profession-profile", SIGNED_IN),
            Map.entry("GET /directory/members/{}/education-profile", SIGNED_IN),
            Map.entry("PUT /directory/members/{}/education-profile", SIGNED_IN),
            // Dashboard (heritage-directory-dashboard.yaml): chỉ quản trị viên gia đình/chi.
            Map.entry("GET /dashboard/statistics", ADMINS),
            Map.entry("GET /dashboard/reports/members", ADMINS),
            // AI (ai.yaml). Phạm vi gia đình, chi và hồ sơ trẻ em được kiểm ở các service AI.
            Map.entry("GET /ai/search", SIGNED_IN),
            Map.entry("POST /ai/chat", SIGNED_IN),
            Map.entry("POST /ai/relationship-explanations", SIGNED_IN),
            Map.entry("POST /ai/summaries", SIGNED_IN));

    /** "get", "/families/{familyId}" -> "GET /families/{}". */
    static String key(String method, String path) {
        return method.toUpperCase(Locale.ROOT) + " " + path.replaceAll("\\{[^}]+}", "{}");
    }

    @Bean
    OpenApiCustomizer endpointRolesCustomizer() {
        return openApi -> openApi.getPaths().forEach((path, item) -> {
            if (!path.startsWith(API_PREFIX)) {
                return;
            }
            Map<PathItem.HttpMethod, io.swagger.v3.oas.models.Operation> operations = item.readOperationsMap();
            operations.forEach((method, operation) -> {
                List<String> roles = ROLES.get(key(method.name(), path.substring(API_PREFIX.length())));
                if (roles != null) {
                    operation.addExtension("x-roles", roles);
                }
            });
        });
    }
}
