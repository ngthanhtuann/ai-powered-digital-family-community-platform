package com.familyconnect.modules.auth.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.SystemConfigQuery;
import com.familyconnect.modules.auth.dto.SystemConfigDtos.SystemConfigData;
import com.familyconnect.modules.auth.entity.Role;
import com.familyconnect.modules.auth.repository.SystemConfigRepository;
import com.familyconnect.modules.auth.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static com.familyconnect.modules.auth.api.SystemConfigQuery.*;

/**
 * UC-AUTH-12, BR-AUTH-18: xem và đổi cấu hình hệ thống (chỉ SYSTEM_ADMIN); module khác đọc qua
 * {@link SystemConfigQuery} với bộ nhớ đệm tối đa 60 giây, nên đổi xong có hiệu lực trong vòng 1 phút.
 */
@Service
public class SystemConfigService implements SystemConfigQuery {

    static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final List<String> IMAGE_TYPES = List.of("jpg", "png", "webp");

    /** Kiểm tra và chuẩn hóa giá trị theo auth.md mục 5.3; sai thì ném 400 VALID_001. */
    private static final Map<String, UnaryOperator<String>> RULES = Map.of(
            APP_NAME, v -> text(v, 100),
            AI_SEARCH_ENABLED, SystemConfigService::bool,
            AI_CHAT_ENABLED, SystemConfigService::bool,
            AI_SUMMARY_ENABLED, SystemConfigService::bool,
            AI_CHAT_LIMIT_PER_HOUR, v -> integer(v, 1, 200),
            UPLOAD_MAX_IMAGE_MB, v -> integer(v, 1, 5),
            UPLOAD_ALLOWED_IMAGE_TYPES, SystemConfigService::imageTypes,
            MEMBERSHIP_APPROVAL_MODE, v -> oneOf(v, Set.of("MANUAL", "AUTO")),
            AUTH_ACCESS_TOKEN_MINUTES, v -> integer(v, 5, 60),
            AUTH_REFRESH_TOKEN_DAYS, v -> integer(v, 1, 30));

    private record Snapshot(Map<String, String> values, Instant loadedAt) {
    }

    private final SystemConfigRepository repository;
    private final UserRoleRepository roles;
    private final AuditLogger audit;
    private final Clock clock;
    private volatile Snapshot cache;

    public SystemConfigService(SystemConfigRepository repository, UserRoleRepository roles, AuditLogger audit,
                               Clock clock) {
        this.repository = repository;
        this.roles = roles;
        this.audit = audit;
        this.clock = clock;
    }

    /** GET /admin/configs. */
    @Transactional(readOnly = true)
    public List<SystemConfigData> list(UUID adminId) {
        requireAdmin(adminId);
        return repository.findAll();
    }

    /** PUT /admin/configs/{key}: chỉ đổi giá trị khóa có sẵn, ghi CONFIG_CHANGED kèm giá trị cũ và mới. */
    @Transactional
    public SystemConfigData update(UUID adminId, String key, String value) {
        requireAdmin(adminId);
        SystemConfigData current = repository.findForUpdate(key)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001, "Không có khóa cấu hình này"));
        UnaryOperator<String> rule = RULES.get(key);
        if (rule == null) {
            // Khóa có trong bảng nhưng chưa có quy tắc: không cho đổi để tránh lưu giá trị chưa kiểm tra.
            throw ApiException.invalidField("value", "Khóa này chưa hỗ trợ đổi qua giao diện");
        }
        String normalized = rule.apply(value);
        UUID id = repository.updateValue(key, normalized, adminId);
        audit.log("CONFIG_CHANGED", "SYSTEM_CONFIG", id, null,
                Map.of("key", key, "oldValue", current.value(), "newValue", normalized));
        invalidate();
        return repository.findAll().stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow();
    }

    @Override
    public String getString(String key) {
        Snapshot snapshot = cache;
        Instant now = clock.instant();
        if (snapshot == null || snapshot.loadedAt().plus(CACHE_TTL).isBefore(now)) {
            snapshot = new Snapshot(repository.findAll().stream()
                    .collect(Collectors.toUnmodifiableMap(SystemConfigData::key, SystemConfigData::value)), now);
            cache = snapshot;
        }
        String value = snapshot.values().get(key);
        return value != null ? value : DEFAULTS.get(key);
    }

    /**
     * Xóa bộ nhớ đệm ngay (đọc tiếp trong cùng giao dịch thấy giá trị mới) và xóa lần nữa khi giao dịch kết thúc,
     * dù commit hay rollback, để không giữ lại giá trị chưa commit hoặc đã bị hủy.
     */
    private void invalidate() {
        cache = null;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    cache = null;
                }
            });
        }
    }

    private void requireAdmin(UUID adminId) {
        if (!roles.findRoleCodes(adminId).contains(Role.SYSTEM_ADMIN)) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    static String text(String value, int max) {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > max) {
            throw ApiException.invalidField("value", "Phải từ 1 đến " + max + " ký tự");
        }
        return trimmed;
    }

    static String bool(String value) {
        return oneOf(value, Set.of("true", "false"));
    }

    static String integer(String value, int min, int max) {
        String trimmed = value.trim();
        if (!trimmed.matches("\\d{1,6}") || Integer.parseInt(trimmed) < min || Integer.parseInt(trimmed) > max) {
            throw ApiException.invalidField("value", "Phải là số nguyên từ " + min + " đến " + max);
        }
        return String.valueOf(Integer.parseInt(trimmed));
    }

    static String oneOf(String value, Set<String> allowed) {
        String trimmed = value.trim();
        if (!allowed.contains(trimmed)) {
            throw ApiException.invalidField("value", "Chỉ nhận một trong: " + String.join(", ", allowed.stream().sorted().toList()));
        }
        return trimmed;
    }

    /** Tập con không rỗng của jpg,png,webp; lưu theo thứ tự chuẩn để so sánh dễ. */
    static String imageTypes(String value) {
        Set<String> picked = Arrays.stream(value.split(",")).map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
        if (picked.isEmpty() || !IMAGE_TYPES.containsAll(picked)) {
            throw ApiException.invalidField("value", "Chỉ nhận tập con không rỗng của jpg,png,webp");
        }
        return IMAGE_TYPES.stream().filter(picked::contains).collect(Collectors.joining(","));
    }
}
