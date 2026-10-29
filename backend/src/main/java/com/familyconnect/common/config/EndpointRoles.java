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
            // Di sản (heritage-directory-dashboard.yaml)
            Map.entry("GET /heritage/documents", SIGNED_IN),
            Map.entry("POST /heritage/documents", ADMINS),
            Map.entry("GET /heritage/documents/{}", SIGNED_IN),
            Map.entry("PATCH /heritage/documents/{}", ADMINS),
            Map.entry("DELETE /heritage/documents/{}", ADMINS),
            Map.entry("GET /heritage/stories", SIGNED_IN),
            Map.entry("POST /heritage/stories", SIGNED_IN),
            Map.entry("GET /heritage/stories/{}", SIGNED_IN),
            Map.entry("PATCH /heritage/stories/{}", SIGNED_IN),
            Map.entry("DELETE /heritage/stories/{}", SIGNED_IN));

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
