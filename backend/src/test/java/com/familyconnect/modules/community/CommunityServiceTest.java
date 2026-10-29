package com.familyconnect.modules.community;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
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
    @Mock com.familyconnect.modules.community.media.MediaRepository mediaRepository;
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

    @BeforeEach void setUp() { service = new CommunityService(store,platform,mediaRepository); }

    @Test void memberCannotPublishFamilyNews() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        ApiException ex = assertThrows(ApiException.class,
            () -> service.createPost(null,new CreatePost("Tin",PostType.NEWS,null)));
        assertEquals(ErrorCode.PERM_001,ex.code());
        verifyNoInteractions(store);
    }

    @Test void crossFamilyQueryFailsBeforeDatabaseAccess() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        ApiException ex = assertThrows(ApiException.class,
            () -> service.posts(familyB,null,null,0,20,"createdAt,desc"));
        assertEquals(ErrorCode.PERM_001,ex.code());
        verifyNoInteractions(store);
    }

    @Test void crossFamilyPostIsDeniedByMembershipAndBranchScope() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(store.findPost(postId)).thenReturn(Optional.of(
            new Post(postId,familyB,branchId,"Nội dung",PostType.POST,authorId,now,now)));
        doThrow(new ApiException(ErrorCode.PERM_001)).when(platform)
            .requireCanRead(any(),eq(familyB),eq(branchId));
        ApiException ex = assertThrows(ApiException.class,() -> service.post(postId));
        assertEquals(ErrorCode.PERM_001,ex.code());
        verify(store,never()).commentCounts(any());
    }

    @Test void unverifiedMemberCannotReadPosts() {
        when(platform.requireAuthenticatedActor())
            .thenReturn(new Actor(memberId,familyA,branchId,Role.MEMBER,false));
        ApiException ex = assertThrows(ApiException.class,() -> service.post(postId));
        assertEquals(ErrorCode.PERM_002,ex.code());
        verifyNoInteractions(store);
    }

    @Test void memberCannotDeleteSomebodyElsesPost() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(store.findPost(postId)).thenReturn(Optional.of(otherPost()));
        ApiException ex = assertThrows(ApiException.class,() -> service.deletePost(postId));
        assertEquals(ErrorCode.PERM_001,ex.code());
        verify(store,never()).deletePost(any());
    }

    @Test void branchAdminRemovalTriggersAuditAndAiGateway() {
        when(platform.requireAuthenticatedActor())
            .thenReturn(new Actor(memberId,familyA,branchId,Role.BRANCH_ADMIN,true));
        when(store.findPost(postId)).thenReturn(Optional.of(otherPost()));
        service.deletePost(postId);
        verify(platform).requireCanModerate(any(),eq(familyA),eq(branchId));
        verify(store).deletePost(postId);
        verify(platform).logContentDeleted("POST",postId,familyA,false);
        verify(platform).postChanged(postId,familyA);
    }

    @Test void ownerDeletionIsAuditedToo() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(store.findPost(postId)).thenReturn(Optional.of(
            new Post(postId,familyA,branchId,"Nội dung",PostType.POST,memberId,now,now)));
        service.deletePost(postId);
        verify(platform,never()).requireCanModerate(any(),any(),any());
        verify(platform).logContentDeleted("POST",postId,familyA,true);
    }

    @Test void branchScopedAdminSeesOnlyBranchesInScope() {
        UUID otherBranch = UUID.randomUUID();
        Actor admin = new Actor(memberId,familyA,branchId,Role.BRANCH_ADMIN,true);
        when(platform.requireAuthenticatedActor()).thenReturn(admin);
        when(store.postBranchIds(familyA)).thenReturn(List.of(branchId,otherBranch));
        when(platform.canRead(admin,familyA,branchId)).thenReturn(true);
        when(platform.canRead(admin,familyA,otherBranch)).thenReturn(false);
        when(store.posts(eq(familyA),eq(false),eq(List.of(branchId)),isNull(),isNull(),
                         eq(0),eq(20),anyString()))
            .thenReturn(new CommunityStore.Page<>(List.of(),0));
        service.posts(null,null,null,0,20,"createdAt,desc");
        verify(store).posts(eq(familyA),eq(false),eq(List.of(branchId)),isNull(),isNull(),eq(0),eq(20),anyString());
    }

    @Test void wholeFamilyAdminSkipsBranchFilter() {
        when(platform.requireAuthenticatedActor())
            .thenReturn(new Actor(memberId,familyA,null,Role.BRANCH_ADMIN,true));
        when(store.posts(eq(familyA),eq(true),eq(List.of()),isNull(),isNull(),eq(0),eq(20),anyString()))
            .thenReturn(new CommunityStore.Page<>(List.of(),0));
        service.posts(null,null,null,0,20,"createdAt,desc");
        verify(store,never()).postBranchIds(any());
    }

    @Test void memberFeedUsesAllowedAncestorBranchesAndPagination() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(store.postBranchIds(familyA)).thenReturn(List.of(branchId));
        when(platform.canRead(any(),eq(familyA),eq(branchId))).thenReturn(true);
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
        ApiException ex = assertThrows(ApiException.class,
            () -> service.posts(null,null,null,0,101,"createdAt,desc"));
        assertEquals(ErrorCode.VALID_001,ex.code());
    }

    @Test void typeCannotBeChangedUsingPatch() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(store.findPost(postId)).thenReturn(Optional.of(
            new Post(postId,familyA,branchId,"Nội dung",PostType.POST,memberId,now,now)));
        ApiException ex = assertThrows(ApiException.class,
            () -> service.updatePost(postId,new UpdatePost("Tin mới",null,PostType.NEWS)));
        assertEquals(ErrorCode.VALID_001,ex.code());
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
        ApiException ex = assertThrows(ApiException.class,
            () -> service.createPost(null,new CreatePost("Tin",PostType.NEWS,null)));
        assertEquals(ErrorCode.VALID_001,ex.code());
        verifyNoInteractions(store);
    }

    @Test void feedReturnsAuthorNamesAndCanDeletePerViewerUsingBatchQueries() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        Post mine = new Post(UUID.randomUUID(),familyA,null,"Của tôi",PostType.POST,memberId,now,now);
        Post other = new Post(postId,familyA,null,"Của người khác",PostType.POST,authorId,now,now);
        when(store.posts(eq(familyA),eq(false),eq(List.of()),isNull(),isNull(),eq(0),eq(20),anyString()))
            .thenReturn(new CommunityStore.Page<>(List.of(mine,other),2));
        when(store.reactionCounts(anyList())).thenReturn(Map.of(
            mine.id(),Map.of(ReactionType.LIKE,1L), other.id(),Map.of(ReactionType.LIKE,0L)));
        when(store.commentCounts(anyList())).thenReturn(Map.of(other.id(),3L));
        when(platform.authorNames(any())).thenReturn(Map.of(memberId,"Tôi",authorId,"Người khác"));

        var items = service.posts(null,null,null,0,20,"createdAt,desc").data();

        assertEquals("Tôi",items.get(0).authorName());
        assertTrue(items.get(0).canDelete());
        assertEquals(0L,items.get(0).commentCount());
        assertEquals("Người khác",items.get(1).authorName());
        assertFalse(items.get(1).canDelete());
        assertEquals(3L,items.get(1).commentCount());
        verify(store,times(1)).commentCounts(anyList());
        verify(platform,never()).canModerate(any(),any(),any());
    }
}
