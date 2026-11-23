package com.familyconnect.modules.auth.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.auth.dto.AuthRequests;
import com.familyconnect.modules.auth.dto.AuthResponses.PasswordResetCodeData;
import com.familyconnect.modules.auth.entity.PasswordResetCode;
import com.familyconnect.modules.auth.entity.Role;
import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.repository.PasswordResetCodeRepository;
import com.familyconnect.modules.auth.repository.RefreshTokenRepository;
import com.familyconnect.modules.auth.repository.UserRepository;
import com.familyconnect.modules.auth.repository.UserRoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** UC-AUTH-15, BR-AUTH-24: trưởng họ hoặc quản trị cấp mã dùng một lần; người quên mật khẩu dùng mã để đặt lại. */
@Service
public class PasswordResetService {

    static final Duration TTL = Duration.ofMinutes(30);
    static final int MAX_ATTEMPTS = 5;
    /** Bỏ 0, O, 1, I để đọc qua điện thoại không nhầm. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordResetCodeRepository codes;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MembershipQuery membership;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter rateLimiter;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public PasswordResetService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                                PasswordResetCodeRepository codes, RefreshTokenRepository refreshTokenRepository,
                                MembershipQuery membership, PasswordEncoder passwordEncoder,
                                LoginRateLimiter rateLimiter, AuditLogger auditLogger, Clock clock) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.codes = codes;
        this.refreshTokenRepository = refreshTokenRepository;
        this.membership = membership;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiter = rateLimiter;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional
    public PasswordResetCodeData issue(UUID actorId, UUID userId) {
        if (actorId.equals(userId)) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        UUID familyId = null;
        if (!userRoleRepository.findRoleCodes(actorId).contains(Role.SYSTEM_ADMIN)) {
            MembershipView actor = membership.getMembership(actorId);
            if (!isFamilyHead(actor)) {
                throw new ApiException(ErrorCode.PERM_001);
            }
            MembershipView target = membership.getMembership(userId);
            if (!target.verified() || !actor.familyId().equals(target.familyId())) {
                throw new ApiException(ErrorCode.NOT_FOUND_001); // không lộ tài khoản của dòng họ khác
            }
            if (isFamilyHead(target)) {
                throw new ApiException(ErrorCode.PERM_001);
            }
            familyId = actor.familyId();
        }
        userRepository.findActiveById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        OffsetDateTime now = OffsetDateTime.now(clock);
        codes.discardActive(userId, now);
        String code = newCode();
        PasswordResetCode saved = codes.saveAndFlush(new PasswordResetCode(userId, passwordEncoder.encode(code), now.plus(TTL), actorId));
        auditLogger.log("PASSWORD_RESET_CODE_ISSUED", "USER", userId, familyId, Map.of());
        return new PasswordResetCodeData(code, saved.getExpiresAt());
    }

    /** Ném lỗi vẫn giữ lần đếm sai đã ghi, nên không rollback theo ApiException. */
    @Transactional(noRollbackFor = ApiException.class)
    public void reset(AuthRequests.PasswordReset request, String clientIp) {
        String email = AuthService.normalizeEmail(request.email());
        rateLimiter.check(clientIp, email);
        OffsetDateTime now = OffsetDateTime.now(clock);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        User user = userRepository.findActiveByEmail(email).orElse(null);
        PasswordResetCode active = user == null ? null
                : codes.findActive(user.getId(), now).stream().findFirst().orElse(null);
        if (active == null) {
            throw invalidCode();
        }
        if (!passwordEncoder.matches(code, active.getCodeHash())) {
            active.recordFailure(now, MAX_ATTEMPTS);
            codes.saveAndFlush(active);
            throw invalidCode();
        }
        active.markUsed(now);
        codes.saveAndFlush(active);
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), now);
        auditLogger.log("PASSWORD_RESET", "USER", user.getId(), null, Map.of());
    }

    private static boolean isFamilyHead(MembershipView m) {
        return m.verified() && m.role() == EffectiveRole.BRANCH_ADMIN && m.branchId() == null && m.familyId() != null;
    }

    private static ApiException invalidCode() {
        return ApiException.invalidField("code", "Email hoặc mã không đúng, hoặc mã đã hết hạn");
    }

    String newCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
