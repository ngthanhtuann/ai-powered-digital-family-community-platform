package com.familyconnect.modules.community;

import com.familyconnect.modules.community.dto.CommunityDto.*;
import com.familyconnect.modules.community.repository.CommunityStore;
import com.familyconnect.modules.community.repository.CommunityStore.*;
import com.familyconnect.modules.community.service.*;
import com.familyconnect.modules.community.service.CommunityPlatformPort.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {
    @Mock CommunityStore store;
    @Mock CommunityPlatformPort platform;
    CommunityService service;
    UUID familyA = UUID.randomUUID();
    UUID familyB = UUID.randomUUID();
    UUID memberId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();
    UUID postId = UUID.randomUUID();
    UUID branchId = UUID.randomUUID();
    OffsetDateTime now = OffsetDateTime.now();

    private Actor member() { return new Actor(memberId, familyA, branchId, Role.MEMBER, true); }
    private Post otherPost() {
        return new Post(postId, familyA, branchId, "Nội dung", PostType.POST, authorId, now, now);
    }

    @BeforeEach void setUp() { service = new CommunityService(store,platform); }

    @Test void memberCannotPublishFamilyNews() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        CommunityFailure ex = assertThrows(CommunityFailure.class,
            () -> service.createPost(null,new CreatePost("Tin",PostType.NEWS,null)));
        assertEquals("PERM_001",ex.code());
        verifyNoInteractions(store);
    }

    @Test void crossFamilyQueryFailsBeforeDatabaseAccess() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        CommunityFailure ex = assertThrows(CommunityFailure.class,
            () -> service.posts(familyB,null,null,0,20,"createdAt,desc"));
        assertEquals(HttpStatus.FORBIDDEN,ex.status());
        verifyNoInteractions(store);
    }

    @Test void crossFamilyPostIsDeniedByMembershipAndBranchScope() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(store.findPost(postId)).thenReturn(Optional.of(
            new Post(postId,familyB,branchId,"Nội dung",PostType.POST,authorId,now,now)));
        doThrow(CommunityFailure.forbidden()).when(platform)
            .requireCanRead(any(),eq(familyB),eq(branchId));
        CommunityFailure ex = assertThrows(CommunityFailure.class,() -> service.post(postId));
        assertEquals("PERM_001",ex.code());
        verify(store,never()).commentCount(any());
    }

    @Test void unverifiedMemberCannotReadPosts() {
        when(platform.requireAuthenticatedActor())
            .thenReturn(new Actor(memberId,familyA,branchId,Role.MEMBER,false));
        CommunityFailure ex = assertThrows(CommunityFailure.class,() -> service.post(postId));
        assertEquals("PERM_002",ex.code());
        verifyNoInteractions(store);
    }

    @Test void memberCannotDeleteSomebodyElsesPost() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(store.findPost(postId)).thenReturn(Optional.of(otherPost()));
        CommunityFailure ex = assertThrows(CommunityFailure.class,() -> service.deletePost(postId));
        assertEquals("PERM_001",ex.code());
        verify(store,never()).deletePost(any());
    }

    @Test void branchAdminRemovalTriggersAuditAndAiGateway() {
        when(platform.requireAuthenticatedActor())
            .thenReturn(new Actor(memberId,familyA,branchId,Role.BRANCH_ADMIN,true));
        when(store.findPost(postId)).thenReturn(Optional.of(otherPost()));
        service.deletePost(postId);
        verify(platform).requireCanModerate(any(),eq(familyA),eq(branchId));
        verify(store).deletePost(postId);
        verify(platform).logContentDeleted("POST",postId,familyA);
        verify(platform).postChanged(postId,familyA);
    }

    @Test void memberFeedUsesAllowedAncestorBranchesAndPagination() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(platform.readableContentBranchIds(any(),eq(familyA))).thenReturn(List.of(branchId));
        when(store.posts(eq(familyA),eq(false),eq(List.of(branchId)),isNull(),eq(PostType.NEWS),
                         eq(0),eq(20),eq("created_at DESC, id DESC")))
            .thenReturn(new CommunityStore.Page<>(List.of(),0));
        var response = service.posts(null,null,PostType.NEWS,0,20,"createdAt,desc");
        assertEquals(0,response.meta().page());
        assertEquals(20,response.meta().size());
        assertEquals(0,response.meta().total());
    }

    @Test void sizeOverHundredFailsWithStandardError() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        CommunityFailure ex = assertThrows(CommunityFailure.class,
            () -> service.posts(null,null,null,0,101,"createdAt,desc"));
        assertEquals("VALID_001",ex.code());
    }

    @Test void typeCannotBeChangedUsingPatch() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(store.findPost(postId)).thenReturn(Optional.of(
            new Post(postId,familyA,branchId,"Nội dung",PostType.POST,memberId,now,now)));
        CommunityFailure ex = assertThrows(CommunityFailure.class,
            () -> service.updatePost(postId,new UpdatePost("Tin mới",null,PostType.NEWS)));
        assertEquals("VALID_001",ex.code());
        verify(store,never()).updatePost(any(),any(),any(),any());
    }

    @Test void reactionUpsertUpdatesInsteadOfCreatingSecondActiveReaction() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(store.findPost(postId)).thenReturn(Optional.of(otherPost()));
        UUID reactionId = UUID.randomUUID();
        when(store.myReaction(postId,memberId)).thenReturn(Optional.of(
            new Reaction(reactionId,postId,ReactionType.LIKE,memberId,now,now)));
        when(store.updateReaction(reactionId,memberId,ReactionType.LOVE))
            .thenReturn(new Reaction(reactionId,postId,ReactionType.LOVE,memberId,now,now));
        var response = service.react(postId,new ReactionInput(ReactionType.LOVE));
        assertEquals(ReactionType.LOVE,response.type());
        verify(store).lockPost(postId);
        verify(store,never()).insertReaction(any(),any(),any());
    }

    @Test void systemAdminMustSpecifyFamilyOnCreate() {
        when(platform.requireAuthenticatedActor())
            .thenReturn(new Actor(memberId,null,null,Role.SYSTEM_ADMIN,true));
        CommunityFailure ex = assertThrows(CommunityFailure.class,
            () -> service.createPost(null,new CreatePost("Tin",PostType.NEWS,null)));
        assertEquals("VALID_001",ex.code());
        verifyNoInteractions(store);
    }
}
