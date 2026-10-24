package com.familyconnect.modules.community;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.auth.api.GenealogyPort;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.UserDirectory;
import com.familyconnect.modules.community.dto.CommunityDto.PostType;
import com.familyconnect.modules.community.repository.CommunityStore;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Actor;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Role;
import com.familyconnect.modules.community.service.ModulePlatformAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Phạm vi chi theo BR-COM-03, BR-COM-05; cây chi: root > child, root > sibling. */
class ModulePlatformAdapterTest {

    private final UUID family = UUID.randomUUID();
    private final UUID root = UUID.randomUUID();
    private final UUID child = UUID.randomUUID();
    private final UUID sibling = UUID.randomUUID();
    private final GenealogyPort genealogy = mock(GenealogyPort.class);
    private final AuditLogger audit = mock(AuditLogger.class);
    private final AiGateway ai = mock(AiGateway.class);
    private final CommunityStore store = mock(CommunityStore.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private ModulePlatformAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ObjectProvider<GenealogyPort> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(genealogy);
        when(genealogy.isBranchWithin(any(), any(), any())).thenAnswer(inv -> {
            UUID branch = inv.getArgument(1);
            UUID ancestor = inv.getArgument(2);
            return branch.equals(ancestor) || ancestor.equals(root);
        });
        adapter = new ModulePlatformAdapter(mock(MembershipQuery.class), provider, audit, ai, store, users);
    }

    private Actor actor(Role role, UUID branch) {
        return new Actor(UUID.randomUUID(), family, branch, role, true);
    }

    @Test
    void memberSeesOwnBranchAncestorsAndFamilyWideOnly() {
        Actor member = actor(Role.MEMBER, child);
        assertThat(adapter.canRead(member, family, null)).isTrue();
        assertThat(adapter.canRead(member, family, child)).isTrue();
        assertThat(adapter.canRead(member, family, root)).isTrue();
        assertThat(adapter.canRead(member, family, sibling)).isFalse();
    }

    @Test
    void parentBranchMemberDoesNotSeeChildBranchContent() {
        assertThat(adapter.canRead(actor(Role.MEMBER, root), family, child)).isFalse();
    }

    @Test
    void memberWithoutBranchSeesFamilyWideContentOnly() {
        Actor member = actor(Role.MEMBER, null);
        assertThat(adapter.canRead(member, family, null)).isTrue();
        assertThat(adapter.canRead(member, family, root)).isFalse();
    }

    @Test
    void branchAdminSeesManagedSubtreeAndAncestors() {
        Actor admin = actor(Role.BRANCH_ADMIN, root);
        assertThat(adapter.canRead(admin, family, child)).isTrue();
        assertThat(adapter.canRead(admin, family, sibling)).isTrue();
        assertThat(adapter.canRead(actor(Role.BRANCH_ADMIN, child), family, sibling)).isFalse();
    }

    @Test
    void otherFamilyIsDeniedExceptForSystemAdmin() {
        UUID otherFamily = UUID.randomUUID();
        assertThat(adapter.canRead(actor(Role.BRANCH_ADMIN, null), otherFamily, null)).isFalse();
        assertThat(adapter.canRead(new Actor(UUID.randomUUID(), null, null, Role.SYSTEM_ADMIN, true),
                otherFamily, root)).isTrue();
    }

    @Test
    void moderationIsLimitedToManagedBranches() {
        adapter.requireCanModerate(actor(Role.BRANCH_ADMIN, null), family, null);
        adapter.requireCanModerate(actor(Role.BRANCH_ADMIN, root), family, child);
        assertThatThrownBy(() -> adapter.requireCanModerate(actor(Role.BRANCH_ADMIN, child), family, root))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> adapter.requireCanModerate(actor(Role.BRANCH_ADMIN, root), family, null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> adapter.requireCanModerate(actor(Role.MEMBER, null), family, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void deletionIsAuditedAndAiIsNotifiedWithPostContent() {
        UUID postId = UUID.randomUUID();
        adapter.logContentDeleted("POST", postId, family, true);
        verify(audit).log("CONTENT_DELETED", "POST", postId, family, Map.of("byOwner", true));

        OffsetDateTime now = OffsetDateTime.now();
        when(store.findPost(postId)).thenReturn(Optional.of(
                new CommunityStore.Post(postId, family, child, "Họp mặt cuối năm", PostType.POST, UUID.randomUUID(), now, now)));
        adapter.postChanged(postId, family);
        verify(ai).upsertSource(AiGateway.SourceType.POST, postId, family, child, "Họp mặt cuối năm");

        UUID removed = UUID.randomUUID();
        when(store.findPost(removed)).thenReturn(Optional.empty());
        adapter.postChanged(removed, family);
        verify(ai).removeSource(AiGateway.SourceType.POST, removed);
    }

    @Test
    void authorNamesFallBackForDeletedAccounts() {
        UUID active = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        when(users.getFullNames(any())).thenReturn(Map.of(active, "Nguyễn Văn An"));

        Map<UUID, String> names = adapter.authorNames(List.of(active, deleted));

        assertThat(names).containsEntry(active, "Nguyễn Văn An")
                .containsEntry(deleted, "Thành viên đã rời hệ thống");
    }
}
