package com.familyconnect.modules.auth.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.common.web.ClientIp;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.repository.AuditLogRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ghi nhật ký vào bảng audit_log (SCRUM-57). Chạy chung transaction với thao tác: thao tác bị hủy thì
 * bản ghi cũng không lưu. Người thực hiện, vai trò hiệu lực, IP lấy từ ngữ cảnh request.
 */
@Component
public class JdbcAuditLogger implements AuditLogger {

    /** Lúc đăng nhập, đăng ký chưa có phiên; người làm chính là tài khoản đích (targetId). Đăng nhập sai không tính. */
    private static final Set<String> SELF_ACTIONS = Set.of("LOGIN_SUCCESS", "USER_REGISTERED");

    private final AuditLogRepository repository;
    private final MembershipQuery membership;

    public JdbcAuditLogger(AuditLogRepository repository, MembershipQuery membership) {
        this.repository = repository;
        this.membership = membership;
    }

    @Override
    public void log(String action, String targetType, UUID targetId, UUID familyId, Map<String, Object> detail) {
        UUID actor = CurrentUser.find().map(AuthenticatedUser::id)
                .orElse(SELF_ACTIONS.contains(action) ? targetId : null);
        String role = actor == null ? null : membership.getMembership(actor).role().name();
        repository.insert(actor, role, action, targetType, targetId, familyId, ClientIp.current().orElse(null), detail);
    }
}
