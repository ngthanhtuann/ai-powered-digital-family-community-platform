package com.familyconnect.modules.ai.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.ai.repository.EmbeddingChunkRepository;
import com.familyconnect.modules.ai.service.AiScopeResolver.AiScope;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SCRUM-106: phạm vi AI dùng chung cho tìm kiếm, chat, gợi ý phải giữ đúng quy tắc của ba bản cũ. */
class AiScopeResolverTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC);
    private final UUID userId = UUID.randomUUID();
    private final UUID familyId = UUID.randomUUID();
    private MembershipQuery memberships;
    private GenealogyQuery genealogy;
    private EmbeddingChunkRepository chunks;
    private AiScopeResolver resolver;

    @BeforeEach
    void setUp() {
        memberships = mock(MembershipQuery.class);
        genealogy = mock(GenealogyQuery.class);
        chunks = mock(EmbeddingChunkRepository.class);
        resolver = new AiScopeResolver(memberships, genealogy, chunks, CLOCK);
    }

    private AuthenticatedUser member(EffectiveRole role, UUID branch) {
        when(memberships.getMembership(userId)).thenReturn(new MembershipView(userId, familyId, branch, role, true));
        return new AuthenticatedUser(userId, List.of(role.name()));
    }

    @Test
    void systemAdminMustNameFamilyAndSeesEveryBranch() {
        AuthenticatedUser admin = new AuthenticatedUser(userId, List.of("SYSTEM_ADMIN"));
        assertThat(assertThrows(ApiException.class, () -> resolver.resolve(admin, null)).code())
                .isEqualTo(ErrorCode.VALID_001);
        AiScope scope = resolver.resolve(admin, familyId);
        assertThat(scope.allBranches()).isTrue();
        assertThat(resolver.visibleBranches(scope)).isEmpty();
        verify(chunks, never()).findBranchIds(any());
    }

    @Test
    void memberCannotAskForAnotherFamily() {
        AuthenticatedUser user = member(EffectiveRole.MEMBER, null);
        assertThat(assertThrows(ApiException.class, () -> resolver.resolve(user, UUID.randomUUID())).code())
                .isEqualTo(ErrorCode.PERM_001);
        AiScope scope = resolver.resolve(user, null);
        assertThat(scope.familyId()).isEqualTo(familyId);
        verify(memberships).requireFamilyAccess(userId, familyId);
    }

    @Test
    void unverifiedMembershipIsRejected() {
        when(memberships.getMembership(userId)).thenReturn(new MembershipView(userId, null, null, EffectiveRole.MEMBER, false));
        assertThat(assertThrows(ApiException.class,
                () -> resolver.resolve(new AuthenticatedUser(userId, List.of("MEMBER")), null)).code())
                .isEqualTo(ErrorCode.PERM_002);
    }

    @Test
    void memberSeesOwnAndAncestorBranchesBranchAdminAlsoChildBranches() {
        UUID own = UUID.randomUUID(), ancestor = UUID.randomUUID(), child = UUID.randomUUID(), other = UUID.randomUUID();
        when(chunks.findBranchIds(familyId)).thenReturn(List.of(own, ancestor, child, other));
        when(genealogy.isBranchWithin(familyId, own, own)).thenReturn(true);
        when(genealogy.isBranchWithin(familyId, own, ancestor)).thenReturn(true);
        when(genealogy.isBranchWithin(familyId, child, own)).thenReturn(true);

        AiScope memberScope = resolver.resolve(member(EffectiveRole.MEMBER, own), null);
        assertThat(memberScope.allBranches()).isFalse();
        assertThat(resolver.visibleBranches(memberScope)).containsExactly(own, ancestor);

        AiScope adminScope = resolver.resolve(member(EffectiveRole.BRANCH_ADMIN, own), null);
        assertThat(resolver.visibleBranches(adminScope)).containsExactly(own, ancestor, child);
    }

    @Test
    void familyWideBranchAdminSeesAllBranches() {
        AiScope scope = resolver.resolve(member(EffectiveRole.BRANCH_ADMIN, null), null);
        assertThat(scope.allBranches()).isTrue();
    }

    @Test
    void minorsHiddenFromMembersAlwaysAndFromEveryoneWhenSentToLlm() {
        UUID minor = UUID.randomUUID();
        when(genealogy.findMinorPersonIds(familyId, LocalDate.now(CLOCK))).thenReturn(List.of(minor));
        AiScope memberScope = resolver.resolve(member(EffectiveRole.MEMBER, null), null);
        AiScope adminScope = resolver.resolve(member(EffectiveRole.BRANCH_ADMIN, null), null);

        assertThat(resolver.hiddenMinors(memberScope, false)).containsExactly(minor);
        assertThat(resolver.hiddenMinors(adminScope, false)).isEmpty();
        assertThat(resolver.hiddenMinors(adminScope, true)).containsExactly(minor);
    }
}
