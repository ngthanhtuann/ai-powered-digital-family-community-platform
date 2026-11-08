package com.familyconnect.common.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Mọi module ghi nhật ký kiểm toán qua interface này (auth.md mục 6.3, BR-AUTH-16).
 * Người thực hiện, vai trò, IP lấy từ ngữ cảnh request. Không truyền mật khẩu, token, nội dung câu hỏi AI.
 * Bản cài đặt: JdbcAuditLogger (module auth) ghi bảng audit_log.
 */
public interface AuditLogger {

    void log(String action, String targetType, UUID targetId, UUID familyId, Map<String, Object> detail);
}
