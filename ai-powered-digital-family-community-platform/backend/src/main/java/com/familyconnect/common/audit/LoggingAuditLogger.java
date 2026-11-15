package com.familyconnect.common.audit;

import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.common.web.ClientIp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** Bản tạm của AuditLogger: ghi ra log ứng dụng. SCRUM-57 xóa lớp này và cài bản ghi bảng audit_log. */
@Component
public class LoggingAuditLogger implements AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    @Override
    public void log(String action, String targetType, UUID targetId, UUID familyId, Map<String, Object> detail) {
        UUID actor = CurrentUser.find().map(AuthenticatedUser::id).orElse(null);
        log.info("action={} actor={} targetType={} targetId={} familyId={} ip={} detail={}",
                action, actor, targetType, targetId, familyId, ClientIp.current().orElse(null), detail);
    }
}
