package com.familyconnect.modules.auth.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.dto.AuthRequests;
import com.familyconnect.modules.auth.dto.AuthResponses.LoginData;
import com.familyconnect.modules.auth.dto.AuthResponses.MembershipData;
import com.familyconnect.modules.auth.dto.AuthResponses.TokenData;
import com.familyconnect.modules.auth.entity.RefreshToken;
import com.familyconnect.modules.auth.entity.Role;
import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.entity.UserStatus;
import com.familyconnect.modules.auth.repository.RefreshTokenRepository;
import com.familyconnect.modules.auth.repository.RoleRepository;
import com.familyconnect.modules.auth.repository.UserRepository;
import com.familyconnect.modules.auth.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");
    private static final String PASSWORD = "Matkhau123";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private UserRoleRepository userRoleRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private MembershipService membershipService;
    private TokenIssuer tokenIssuer;
    private AuditLogger auditLogger;
    private AuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        userRoleRepository = mock(UserRoleRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        membershipService = mock(MembershipService.class);
        tokenIssuer = mock(TokenIssuer.class);
        auditLogger = mock(AuditLogger.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        when(tokenIssuer.now()).thenReturn(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        service = new AuthService(userRepository, roleRepository, userRoleRepository, refreshTokenRepository,
                membershipService, tokenIssuer, new LoginRateLimiter(5, clock), encoder, auditLogger, clock);
    }

    private User user(UserStatus status) {
        User user = new User("an.nguyen@example.com", encoder.encode(PASSWORD), "Nguyễn Văn An", null,
                LocalDate.of(1990, 5, 12));
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(user, "status", status);
        return user;
    }

    // ---- Đăng nhập ----

    @Test
    void loginSuccessReturnsTokensAndMembership() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findActiveByEmail("an.nguyen@example.com")).thenReturn(Optional.of(user));
        when(userRoleRepository.findRoleCodes(user.getId())).thenReturn(List.of(Role.MEMBER));
        when(tokenIssuer.issue(user.getId(), List.of(Role.MEMBER)))
                .thenReturn(TokenData.bearer("access", "refresh", 900));
        when(membershipService.describe(user.getId(), List.of(Role.MEMBER))).thenReturn(MembershipData.none());

        // Email có khoảng trắng và chữ hoa vẫn đăng nhập được (BR-AUTH-01).
        LoginData data = service.login(new AuthRequests.Login("  An.Nguyen@Example.com ", PASSWORD), "1.2.3.4");

        assertThat(data.accessToken()).isEqualTo("access");
        assertThat(data.refreshToken()).isEqualTo("refresh");
        assertThat(data.tokenType()).isEqualTo("Bearer");
        assertThat(data.user().roles()).containsExactly(Role.MEMBER);
        assertThat(data.membership().status().name()).isEqualTo("NONE");
        verify(auditLogger).log(eq("LOGIN_SUCCESS"), eq("USER"), eq(user.getId()), isNull(), anyMap());
    }

    @Test
    void loginWrongPasswordReturnsAuth001() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findActiveByEmail(any())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new AuthRequests.Login(user.getEmail(), "sai-mat-khau"), "1.2.3.4"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.AUTH_001);
        verify(tokenIssuer, never()).issue(any(), anyList());
        verify(auditLogger).log(eq("LOGIN_FAILED"), eq("USER"), eq(user.getId()), isNull(), anyMap());
    }

    @Test
    void loginUnknownEmailReturnsSameAuth001() {
        when(userRepository.findActiveByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new AuthRequests.Login("khong.co@example.com", PASSWORD), "1.2.3.4"))
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.AUTH_001);
    }

    @Test
    void loginLockedAccountWithCorrectPasswordReturnsAuth003() {
        User user = user(UserStatus.LOCKED);
        when(userRepository.findActiveByEmail(any())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new AuthRequests.Login(user.getEmail(), PASSWORD), "1.2.3.4"))
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.AUTH_003);
        verify(auditLogger).log(eq("LOGIN_BLOCKED"), eq("USER"), eq(user.getId()), isNull(), anyMap());
    }

    @Test
    void loginLockedAccountWithWrongPasswordStillReturnsAuth001() {
        User user = user(UserStatus.LOCKED);
        when(userRepository.findActiveByEmail(any())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new AuthRequests.Login(user.getEmail(), "sai-mat-khau"), "1.2.3.4"))
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.AUTH_001);
    }

    @Test
    void sixthLoginInAMinuteReturnsRate001() {
        when(userRepository.findActiveByEmail(any())).thenReturn(Optional.empty());
        var request = new AuthRequests.Login("an.nguyen@example.com", "sai");
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(request, "1.2.3.4"))
                    .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.AUTH_001);
        }
        assertThatThrownBy(() -> service.login(request, "1.2.3.4"))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.RATE_001);
    }

    // ---- Đăng ký ----

    @Test
    void registerUnder16ReturnsValid001OnDateOfBirth() {
        var request = new AuthRequests.Register("be@example.com", PASSWORD, "Bé Na", null, LocalDate.of(2011, 1, 1));

        assertThatThrownBy(() -> service.register(request))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALID_001);
                    assertThat(e.details()).singleElement().extracting("field").isEqualTo("dateOfBirth");
                });
    }

    @Test
    void registerExactly16TodayIsAllowed() {
        // Hôm nay theo giờ Việt Nam là 19/10/2026: sinh 19/10/2010 vừa đủ 16 tuổi.
        when(userRepository.existsActiveByEmail(any())).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(roleRepository.findByCode(Role.MEMBER)).thenReturn(Optional.of(mock(Role.class)));

        var data = service.register(new AuthRequests.Register("Vua.Du@Example.com ", PASSWORD, " Vừa Đủ ", "",
                LocalDate.of(2010, 10, 19)));

        assertThat(data.email()).isEqualTo("vua.du@example.com");
        assertThat(data.fullName()).isEqualTo("Vừa Đủ");
        assertThat(data.phone()).isNull();
        assertThat(data.roles()).containsExactly(Role.MEMBER);
    }

    @Test
    void registerDuplicateEmailReturnsAuth004() {
        when(userRepository.existsActiveByEmail("an.nguyen@example.com")).thenReturn(true);
        var request = new AuthRequests.Register("an.nguyen@example.com", PASSWORD, "An", null, LocalDate.of(1990, 5, 12));

        assertThatThrownBy(() -> service.register(request))
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.AUTH_004);
    }

    // ---- Làm mới token ----

    @Test
    void refreshWithRevokedTokenReturnsAuth002() {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), "hash", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(1));
        ReflectionTestUtils.setField(token, "revokedAt", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMinutes(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refresh(new AuthRequests.Refresh("cu")))
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.AUTH_002);
    }

    @Test
    void refreshWithExpiredTokenReturnsAuth002() {
        RefreshToken token = new RefreshToken(UUID.randomUUID(), "hash", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.refresh(new AuthRequests.Refresh("het-han")))
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.AUTH_002);
    }

    @Test
    void refreshUsedConcurrentlyReturnsAuth002() {
        User user = user(UserStatus.ACTIVE);
        RefreshToken token = new RefreshToken(user.getId(), "hash", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusDays(1));
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(userRepository.findActiveById(user.getId())).thenReturn(Optional.of(user));
        when(refreshTokenRepository.revokeIfActive(eq(token.getId()), any())).thenReturn(0);

        assertThatThrownBy(() -> service.refresh(new AuthRequests.Refresh("dang-dung")))
                .extracting(e -> ((ApiException) e).code())
                .isEqualTo(ErrorCode.AUTH_002);
        verify(tokenIssuer, never()).issue(any(), anyList());
    }

    // ---- Đổi mật khẩu ----

    @Test
    void changePasswordWithWrongCurrentReturnsValid001OnCurrentPassword() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findActiveById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.changePassword(user.getId(),
                new AuthRequests.ChangePassword("sai", "Matkhaumoi456")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.VALID_001);
                    assertThat(e.details()).singleElement().extracting("field").isEqualTo("currentPassword");
                });
        verify(refreshTokenRepository, never()).revokeAllForUser(any(), any());
    }

    @Test
    void changePasswordRevokesAllRefreshTokens() {
        User user = user(UserStatus.ACTIVE);
        when(userRepository.findActiveById(user.getId())).thenReturn(Optional.of(user));

        service.changePassword(user.getId(), new AuthRequests.ChangePassword(PASSWORD, "Matkhaumoi456"));

        assertThat(encoder.matches("Matkhaumoi456", user.getPasswordHash())).isTrue();
        verify(refreshTokenRepository).revokeAllForUser(eq(user.getId()), any());
    }

    @Test
    void maskEmailHidesMostOfLocalPart() {
        assertThat(AuthService.maskEmail("an.nguyen@example.com")).isEqualTo("an***@example.com");
        assertThat(AuthService.maskEmail("a@example.com")).isEqualTo("a***@example.com");
    }
}
