package com.familyconnect.modules.auth.api;

import java.util.Map;

/**
 * Module khác đọc cấu hình hệ thống qua interface này (BR-AUTH-18, UC-AUTH-12), bộ nhớ đệm tối đa 60 giây.
 * Khóa và giá trị mặc định theo auth.md mục 5.3 (migration V20). Giá trị đã được kiểm tra kiểu khi lưu.
 */
@FunctionalInterface
public interface SystemConfigQuery {

    String APP_NAME = "app.name";
    String AI_SEARCH_ENABLED = "ai.search.enabled";
    String AI_CHAT_ENABLED = "ai.chat.enabled";
    String AI_SUMMARY_ENABLED = "ai.summary.enabled";
    String AI_CHAT_LIMIT_PER_HOUR = "ai.chat.limit_per_hour";
    String UPLOAD_MAX_IMAGE_MB = "upload.max_image_mb";
    String UPLOAD_ALLOWED_IMAGE_TYPES = "upload.allowed_image_types";
    String MEMBERSHIP_APPROVAL_MODE = "membership.approval_mode";
    String AUTH_ACCESS_TOKEN_MINUTES = "auth.access_token_minutes";
    String AUTH_REFRESH_TOKEN_DAYS = "auth.refresh_token_days";

    /** Giá trị mặc định, giống cột default_value của migration V20 (SystemConfigApiIntegrationTest đối chiếu). */
    Map<String, String> DEFAULTS = Map.of(
            APP_NAME, "FamilyConnect",
            AI_SEARCH_ENABLED, "true",
            AI_CHAT_ENABLED, "true",
            AI_SUMMARY_ENABLED, "true",
            AI_CHAT_LIMIT_PER_HOUR, "20",
            UPLOAD_MAX_IMAGE_MB, "5",
            UPLOAD_ALLOWED_IMAGE_TYPES, "jpg,png,webp",
            MEMBERSHIP_APPROVAL_MODE, "MANUAL",
            AUTH_ACCESS_TOKEN_MINUTES, "15",
            AUTH_REFRESH_TOKEN_DAYS, "7");

    String getString(String key);

    default int getInt(String key) {
        return Integer.parseInt(getString(key));
    }

    default boolean getBoolean(String key) {
        return Boolean.parseBoolean(getString(key));
    }

    /** Chỉ dùng giá trị mặc định, cho test đơn vị không có cơ sở dữ liệu. */
    static SystemConfigQuery defaults() {
        return DEFAULTS::get;
    }
}
