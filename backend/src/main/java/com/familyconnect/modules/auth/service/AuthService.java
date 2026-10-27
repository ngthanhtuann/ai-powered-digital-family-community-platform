package com.familyconnect.modules.auth.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.dto.AuthRequests;
import com.familyconnect.modules.auth.dto.AuthResponses.LoginData;
import com.familyconnect.modules.auth.dto.AuthResponses.MembershipData;
import com.familyconnect.modules.auth.dto.AuthResponses.TokenData;
import com.familyconnect.modules.auth.dto.AuthResponses.UserData;
import com.familyconnect.modules.auth.entity.RefreshToken;
import com.familyconnect.modules.auth.entity.Role;
import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.entity.UserRole;
import com.familyconnect.modules.auth.repository.RefreshTokenRepository;
import com.familyconnect.modules.auth.repository.RoleRepository;
import com.familyconnect.modules.auth.repository.UserRepository;
import com.familyconnect.modules.auth.repository.UserRoleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Đăng ký, đăng nhập, làm mới token, đăng xuất, đổi mật khẩu (UC-AUTH-01 đến 04). */
@Service
public class AuthService {

    static final int MIN_AGE = 16;
    /** Ngày sinh tính theo giờ Việt Nam để "đủ 16 tuổi" khớp với lịch người dùng. */
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MembershipService membershipService;
    private final TokenIssuer tokenIssuer;
    private final LoginRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;
    private final Clock clock;
    /** Hash giả để so mật khẩu khi email không tồn tại, thời gian phản hồi không lộ email có hay không. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       UserRoleRepository userRoleRepository, RefreshTokenRepository refreshTokenRepository,
                       MembershipService membershipService, TokenIssuer tokenIssuer, LoginRateLimiter rateLimiter,
                       PasswordEncoder passwordEncoder, AuditLogger auditLogger, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.membershipService = membershipService;
        this.tokenIssuer = tokenIssuer;
        this.rateLimiter = rateLimiter;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /** UC-AUTH-01. Không tự đăng nhập; client gọi tiếp /auth/login. */
    @Transactional
    public UserData register(AuthRequests.Register request) {
        String email = normalizeEmail(request.email());
        requireMinAge(request.dateOfBirth());
        if (userRepository.existsActiveByEmail(email)) {
            throw new ApiException(ErrorCode.AUTH_004);
        }
        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName().trim(),
                blankToNull(request.phone()), request.dateOfBirth());
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Hai request đăng ký cùng email cùng lúc: unique index chặn bản thứ hai.
            throw new ApiException(ErrorCode.AUTH_004);
        }
        Role member = roleRepository.findByCode(Role.MEMBER)
                .orElseThrow(() -> new IllegalStateException("Thiếu vai trò MEMBER trong bảng role"));
        userRoleRepository.save(new UserRole(user.getId(), member));

        auditLogger.log("USER_REGISTERED", "USER", user.getId(), null, Map.of());
        return UserData.of(user, List.of(Role.MEMBER));
    }

    /** UC-AUTH-02. Sai email hoặc mật khẩu cùng trả AUTH_001; tài khoản khóa chỉ báo khi mật khẩu đúng. */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginData login(AuthRequests.Login request, String clientIp) {
        String email = normalizeEmail(request.email());
        rateLimiter.check(clientIp, email);

        User user = userRepository.findActiveByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            auditLogger.log("LOGIN_FAILED", "USER", null, null, Map.of("email", maskEmail(email)));
            throw new ApiException(ErrorCode.AUTH_001);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditLogger.log("LOGIN_FAILED", "USER", user.getId(), null, Map.of("email", maskEmail(email)));
            throw new ApiException(ErrorCode.AUTH_001);
        }
        if (!user.isActive()) {
            auditLogger.log("LOGIN_BLOCKED", "USER", user.getId(), null, Map.of());
            throw new ApiException(ErrorCode.AUTH_003);
        }

        List<String> roles = userRoleRepository.findRoleCodes(user.getId());
        TokenData tokens = tokenIssuer.issue(user.getId(), roles);
        user.recordLogin(tokenIssuer.now());
        userRepository.save(user);
        MembershipData membership = membershipService.describe(user.getId(), roles);

        auditLogger.log("LOGIN_SUCCESS", "USER", user.getId(), null, Map.of());
        return LoginData.of(tokens, UserData.of(user, roles), membership);
    }

    /** UC-AUTH-03 (làm mới): refresh token dùng một lần, thành công thì cấp cặp mới. */
    @Transactional
    public TokenData refresh(AuthRequests.Refresh request) {
        OffsetDateTime now = tokenIssuer.now();
        RefreshToken token = refreshTokenRepository.findByTokenHash(TokenIssuer.hash(request.refreshToken()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_002));
        User user = userRepository.findActiveById(token.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_002));
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.AUTH_003);
        }
        if (refreshTokenRepository.revokeIfActive(token.getId(), now) == 0) {
            // Request khác vừa dùng token này.
            throw new ApiException(ErrorCode.AUTH_002);
        }
        return tokenIssuer.issue(user.getId(), userRoleRepository.findRoleCodes(user.getId()));
    }

    /** UC-AUTH-03 (đăng xuất): thu hồi mọi refresh token của người dùng. */
    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, tokenIssuer.now());
        auditLogger.log("LOGOUT", "USER", userId, null, Map.of());
    }

    /** UC-AUTH-04: sai mật khẩu hiện tại trả 400 VALID_001 với field currentPassword. */
    @Transactional
    public void changePassword(UUID userId, AuthRequests.ChangePassword request) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_002));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.invalidField("currentPassword", "Mật khẩu hiện tại không đúng");
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId, tokenIssuer.now());
        auditLogger.log("PASSWORD_CHANGED", "USER", userId, null, Map.of());
    }

    /** GET /users/me (UC-AUTH-05): hồ sơ của chính mình, kể cả khi chưa xác minh gia đình. */
    @Transactional(readOnly = true)
    public UserData myProfile(UUID userId) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_002));
        return UserData.of(user, userRoleRepository.findRoleCodes(userId));
    }

    /** PATCH /users/me (UC-AUTH-05): chỉ sửa hồ sơ của chính mình; email không đổi được. */
    @Transactional
    public UserData updateProfile(UUID userId, AuthRequests.UpdateProfile request) {
        if (request.nothingSent()) {
            throw new ApiException(ErrorCode.VALID_001, "Cần gửi ít nhất một trường để cập nhật");
        }
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_002));
        if (request.dateOfBirth() != null) {
            requireMinAge(request.dateOfBirth());
        }
        user.updateProfile(
                request.fullName() != null ? request.fullName().trim() : user.getFullName(),
                request.phoneSent() ? blankToNull(request.phone()) : user.getPhone(),
                request.dateOfBirth() != null ? request.dateOfBirth() : user.getDateOfBirth());
        user = userRepository.saveAndFlush(user);
        auditLogger.log("PROFILE_UPDATED", "USER", userId, null, Map.of());
        return UserData.of(user, userRoleRepository.findRoleCodes(userId));
    }

    /** GET /users/me/membership. */
    @Transactional(readOnly = true)
    public MembershipData myMembership(UUID userId) {
        return membershipService.describe(userId, userRoleRepository.findRoleCodes(userId));
    }

    /** BR-AUTH-03: đủ 16 tuổi khi đăng ký và khi sửa ngày sinh. */
    private void requireMinAge(LocalDate dateOfBirth) {
        LocalDate today = LocalDate.now(clock.withZone(APP_ZONE));
        if (dateOfBirth.plusYears(MIN_AGE).isAfter(today)) {
            throw ApiException.invalidField("dateOfBirth", "Bạn phải đủ " + MIN_AGE + " tuổi");
        }
    }

    /** BR-AUTH-01: cắt khoảng trắng và đổi chữ thường. */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Che bớt email khi ghi nhật ký LOGIN_FAILED (auth.md mục 6.4): an.nguyen@x.com -> an***@x.com. */
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + email.substring(at);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
