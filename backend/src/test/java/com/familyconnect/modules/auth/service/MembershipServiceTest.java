package com.familyconnect.modules.auth.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.GenealogyPort;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.auth.dto.AuthResponses.MembershipData;
import com.familyconnect.modules.auth.dto.AuthResponses.MembershipStatus;
import com.familyconnect.modules.auth.entity.MemberRole;
import com.familyconnect.modules.auth.entity.MemberVerification;
import com.familyconnect.modules.auth.entity.Role;
import com.familyconnect.modules.auth.entity.VerificationStatus;
import com.familyconnect.modules.auth.repository.MemberVerificationRepository;
import com.familyconnect.modules.auth.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MembershipQuery và MembershipCommand mà module Gia phả (SCRUM-37) sẽ gọi (auth.md mục 6.3). */
class MembershipServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID FAMILY = UUID.randomUUID();

    private MemberVerificationRepository verifications;
    private UserRoleRepository userRoles;
    private GenealogyPort genealogy;
    private MembershipService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        verifications = mock(MemberVerificationRepository.class);
        userRoles = mock(UserRoleRepository.class);
        genealogy = mock(GenealogyPort.class);
        ObjectProvider<GenealogyPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(genealogy);
        when(userRoles.findRoleCodes(USER)).thenReturn(List.of(Role.MEMBER));
        Clock clock = Clock.fixed(Instant.parse("2026-10-19T03:00:00Z"), ZoneOffset.UTC);
        service = new MembershipService(verifications, userRoles, provider, clock);
    }

    /** Bản ghi member_verification với trạng thái và vai trò cho trước. */
    private MemberVerification record(VerificationStatus status, MemberRole role) {
        MemberVerification mv = MemberVerification.familyOwner(USER, FAMILY, OffsetDateTime.now());
        ReflectionTestUtils.setField(mv, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(mv, "status", status);
        ReflectionTestUtils.setField(mv, "role", role);
        return mv;
    }

    private void approved(MemberRole role) {
        when(verifications.findByUserIdAndStatusIn(eq(USER), any()))
                .thenReturn(Optional.of(record(VerificationStatus.APPROVED, role)));
    }

    // ---- MembershipQuery ----

    @Test
    void systemAdminIsVerifiedWithoutFamily() {
        when(userRoles.findRoleCodes(USER)).thenReturn(List.of(Role.MEMBER, Role.SYSTEM_ADMIN));

        MembershipView view = service.getMembership(USER);

        assertThat(view.role()).isEqualTo(EffectiveRole.SYSTEM_ADMIN);
        assertThat(view.verified()).isTrue();
        assertThat(view.familyId()).isNull();
        assertThatCode(() -> service.requireFamilyAccess(USER, UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void approvedBranchAdminGetsEffectiveRoleBranchAdmin() {
        approved(MemberRole.BRANCH_ADMIN);

        MembershipView view = service.getMembership(USER);

        assertThat(view.role()).isEqualTo(EffectiveRole.BRANCH_ADMIN);
        assertThat(view.verified()).isTrue();
        assertThat(view.familyId()).isEqualTo(FAMILY);
    }

    @Test
    void notVerifiedUserGetsPerm002() {
        when(verifications.findByUserIdAndStatusIn(eq(USER), any())).thenReturn(Optional.empty());

        assertThat(service.getMembership(USER).verified()).isFalse();
        assertThatThrownBy(() -> service.requireVerified(USER))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.PERM_002);
        assertThatThrownBy(() -> service.requireFamilyAccess(USER, FAMILY))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.PERM_002);
    }

    @Test
    void verifiedMemberOfOtherFamilyGetsPerm001() {
        approved(MemberRole.MEMBER);

        assertThatCode(() -> service.requireFamilyAccess(USER, FAMILY)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireFamilyAccess(USER, UUID.randomUUID()))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.PERM_001);
    }

    // ---- MembershipCommand ----

    @Test
    void assignFamilyOwnerCancelsPendingRequestAndCreatesApprovedBranchAdmin() {
        MemberVerification pending = record(VerificationStatus.PENDING, MemberRole.MEMBER);
        when(verifications.findByUserIdAndStatusIn(eq(USER), any())).thenReturn(Optional.of(pending));

        service.assignFamilyOwner(USER, FAMILY);

        assertThat(pending.getStatus()).isEqualTo(VerificationStatus.CANCELLED);
        verify(verifications).saveAndFlush(pending);
        ArgumentCaptor<MemberVerification> saved = ArgumentCaptor.forClass(MemberVerification.class);
        verify(verifications).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(saved.getValue().getRole()).isEqualTo(MemberRole.BRANCH_ADMIN);
        assertThat(saved.getValue().getBranchId()).isNull();
        assertThat(saved.getValue().getFamilyId()).isEqualTo(FAMILY);
    }

    @Test
    void assignFamilyOwnerWhenAlreadyInAFamilyReturnsAuth005() {
        approved(MemberRole.MEMBER);

        assertThatThrownBy(() -> service.assignFamilyOwner(USER, UUID.randomUUID()))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.AUTH_005);
        verify(verifications, never()).save(any());
    }

    @Test
    void assignFamilyOwnerRejectsSystemAdmin() {
        when(userRoles.findRoleCodes(USER)).thenReturn(List.of(Role.MEMBER, Role.SYSTEM_ADMIN));

        assertThatThrownBy(() -> service.assignFamilyOwner(USER, FAMILY))
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.PERM_001);
    }

    // ---- Schema Membership trả cho frontend ----

    @Test
    void describePendingIncludesJoinRequestIdAndFamilyName() {
        MemberVerification pending = record(VerificationStatus.PENDING, MemberRole.MEMBER);
        when(verifications.findByUserIdAndStatusIn(eq(USER), any())).thenReturn(Optional.of(pending));
        when(genealogy.findFamilyName(FAMILY)).thenReturn(Optional.of("Dòng họ Nguyễn"));

        MembershipData data = service.describe(USER, List.of(Role.MEMBER));

        assertThat(data.status()).isEqualTo(MembershipStatus.PENDING);
        assertThat(data.joinRequestId()).isEqualTo(pending.getId());
        assertThat(data.familyName()).isEqualTo("Dòng họ Nguyễn");
        assertThat(data.canCreateFamily()).isTrue();
    }

    @Test
    void describeWithoutAnyRecordIsNone() {
        when(verifications.findByUserIdAndStatusIn(eq(USER), any())).thenReturn(Optional.empty());
        when(verifications.findFirstByUserIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(USER, VerificationStatus.REJECTED))
                .thenReturn(Optional.empty());

        MembershipData data = service.describe(USER, List.of(Role.MEMBER));

        assertThat(data.status()).isEqualTo(MembershipStatus.NONE);
        assertThat(data.canCreateFamily()).isTrue();
    }
}
