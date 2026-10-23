package com.familyconnect.modules.heritage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.security.AuthenticatedUser;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.auth.api.UserQuery;
import com.familyconnect.modules.heritage.StubCommunityQuery;
import com.familyconnect.modules.heritage.StubGenealogyQuery;
import com.familyconnect.modules.heritage.api.CommunityQuery;
import com.familyconnect.modules.heritage.api.GenealogyQuery;
import com.familyconnect.modules.heritage.api.AiGateway;
import com.familyconnect.modules.heritage.entity.FamilyStory;
import com.familyconnect.modules.heritage.entity.HeritageDocument;
import com.familyconnect.modules.heritage.repository.FamilyStoryRepository;
import com.familyconnect.modules.heritage.repository.HeritageDocumentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeritageServiceTest {

    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID FAMILY_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_USER_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID DOCUMENT_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID STORY_ID = UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final UUID MEDIA_ID = UUID.fromString("60000000-0000-0000-0000-000000000006");

    @Mock private HeritageDocumentRepository documentRepository;
    @Mock private FamilyStoryRepository storyRepository;
    @Mock private MembershipQuery membershipQuery;
    @Mock private UserQuery userQuery;
    @Mock private AiGateway aiGateway;
    @Mock private ObjectProvider<GenealogyQuery> genealogyProvider;
    @Mock private ObjectProvider<CommunityQuery> communityProvider;

    private StubCommunityQuery communityQuery;
    private StubGenealogyQuery genealogyQuery;
    private HeritageService service;

    @BeforeEach
    void setUp() {
        communityQuery = new StubCommunityQuery();
        genealogyQuery = new StubGenealogyQuery();
        lenient().when(genealogyProvider.getIfAvailable()).thenReturn(genealogyQuery);
        lenient().when(communityProvider.getIfAvailable()).thenReturn(communityQuery);
        service = new HeritageService(documentRepository, storyRepository, membershipQuery,
                communityProvider, genealogyProvider, userQuery, aiGateway,
                Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC));
        authenticate(USER_ID);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void memberCanReadDocumentsButCannotCreateThem() {
        HeritageDocument document = mock(HeritageDocument.class);
        when(document.getFamilyId()).thenReturn(FAMILY_ID);
        when(documentRepository.findByIdAndDeletedAtIsNull(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(membershipQuery.getMembership(USER_ID))
                .thenReturn(membership(EffectiveRole.MEMBER, USER_ID));

        assertThat(service.getDocument(DOCUMENT_ID)).isNotNull();
        verify(aiGateway, never()).refreshEmbedding(any(), any(), any());
        assertThatThrownBy(() -> service.createDocument(FAMILY_ID,
                new com.familyconnect.modules.heritage.dto.HeritageRequests.CreateDocument(
                        "Document", null, null, MEDIA_ID)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void documentListFiltersByFamilyAndCategory() {
        when(membershipQuery.getMembership(USER_ID)).thenReturn(membership(EffectiveRole.MEMBER, USER_ID));
        when(documentRepository.findAllByFamilyIdAndCategoryAndDeletedAtIsNull(
                eq(FAMILY_ID), eq("gia-pha"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.listDocuments(null, "gia-pha", 0, 20, false);

        assertThat(result.data()).isEmpty();
        assertThat(result.meta().page()).isZero();
        verify(documentRepository).findAllByFamilyIdAndCategoryAndDeletedAtIsNull(
                eq(FAMILY_ID), eq("gia-pha"), any());
    }

    @Test
    void storyAuthorCanUpdateButOtherMemberCannot() throws Exception {
        FamilyStory story = new FamilyStory(FAMILY_ID, "Title", "Content", null, USER_ID);
        setId(story, STORY_ID);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(story));
        when(membershipQuery.getMembership(USER_ID)).thenReturn(membership(EffectiveRole.MEMBER, USER_ID));
        when(userQuery.findFullName(USER_ID)).thenReturn(Optional.of("Author"));

        var updated = service.updateStory(STORY_ID, new ObjectMapper().readTree("{\"title\":\"Updated\"}"));
        assertThat(updated.title()).isEqualTo("Updated");
        verify(aiGateway).refreshEmbedding(AiGateway.ContentType.STORY, STORY_ID, FAMILY_ID);

        authenticate(OTHER_USER_ID);
        when(membershipQuery.getMembership(OTHER_USER_ID))
                .thenReturn(membership(EffectiveRole.MEMBER, OTHER_USER_ID));
        assertThatThrownBy(() -> service.deleteStory(STORY_ID))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void documentCreationRejectsMediaFromAnotherFamily() {
        authenticate(OTHER_USER_ID);
        when(membershipQuery.getMembership(OTHER_USER_ID))
                .thenReturn(membership(EffectiveRole.BRANCH_ADMIN, OTHER_USER_ID));
        doNothing().when(membershipQuery).requireFamilyAccess(OTHER_USER_ID, FAMILY_ID);
        communityQuery.add(MEDIA_ID, UUID.randomUUID(), "HERITAGE");

        assertThatThrownBy(() -> service.createDocument(FAMILY_ID,
                new com.familyconnect.modules.heritage.dto.HeritageRequests.CreateDocument(
                        "Document", null, null, MEDIA_ID)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void documentUpdateNotifiesAiGateway() throws Exception {
        HeritageDocument document = mock(HeritageDocument.class);
        when(document.getId()).thenReturn(DOCUMENT_ID);
        when(document.getFamilyId()).thenReturn(FAMILY_ID);
        when(documentRepository.findByIdAndDeletedAtIsNull(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(membershipQuery.getMembership(USER_ID)).thenReturn(membership(EffectiveRole.BRANCH_ADMIN, USER_ID));

        service.updateDocument(DOCUMENT_ID, new ObjectMapper().readTree("{\"title\":\"Updated\"}"));

        verify(document).update(eq("Updated"), isNull(), eq(false), isNull(), eq(false), eq(USER_ID));
        verify(aiGateway).refreshEmbedding(AiGateway.ContentType.DOCUMENT, DOCUMENT_ID, FAMILY_ID);
    }

    @Test
    void branchAdminCanCreateDocumentWithFamilyMedia() {
        when(membershipQuery.getMembership(USER_ID))
                .thenReturn(membership(EffectiveRole.BRANCH_ADMIN, USER_ID));
        communityQuery.add(MEDIA_ID, FAMILY_ID, "HERITAGE");
        when(documentRepository.save(any(HeritageDocument.class)))
                .thenAnswer(invocation -> {
                    HeritageDocument saved = invocation.getArgument(0);
                    setId(saved, DOCUMENT_ID);
                    return saved;
                });

        service.createDocument(FAMILY_ID,
                new com.familyconnect.modules.heritage.dto.HeritageRequests.CreateDocument(
                        "Document", "Description", "gia-pha", MEDIA_ID));

        ArgumentCaptor<HeritageDocument> saved = ArgumentCaptor.forClass(HeritageDocument.class);
        verify(documentRepository).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(FAMILY_ID);
        assertThat(saved.getValue().getMediaId()).isEqualTo(MEDIA_ID);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(USER_ID);
        verify(aiGateway).refreshEmbedding(AiGateway.ContentType.DOCUMENT, DOCUMENT_ID, FAMILY_ID);
    }

    @Test
    void storyCreationValidatesRelatedPersonThroughGenealogyStub() {
        UUID personId = UUID.fromString("70000000-0000-0000-0000-000000000007");
        genealogyQuery.add(personId, FAMILY_ID);
        when(membershipQuery.getMembership(USER_ID)).thenReturn(membership(EffectiveRole.MEMBER, USER_ID));
        when(userQuery.findFullName(USER_ID)).thenReturn(Optional.of("Author"));
        when(storyRepository.save(any(FamilyStory.class))).thenAnswer(invocation -> {
            FamilyStory saved = invocation.getArgument(0);
            setId(saved, STORY_ID);
            return saved;
        });

        var result = service.createStory(FAMILY_ID,
                new com.familyconnect.modules.heritage.dto.HeritageRequests.CreateStory(
                        "Story", "Content", personId));

        assertThat(result.relatedPersonId()).isEqualTo(personId);
        verify(aiGateway).refreshEmbedding(AiGateway.ContentType.STORY, STORY_ID, FAMILY_ID);

        UUID unrelatedPersonId = UUID.fromString("80000000-0000-0000-0000-000000000008");
        genealogyQuery.add(unrelatedPersonId, UUID.randomUUID());
        assertThatThrownBy(() -> service.createStory(FAMILY_ID,
                new com.familyconnect.modules.heritage.dto.HeritageRequests.CreateStory(
                        "Story", "Content", unrelatedPersonId)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void softDeleteDoesNotRemoveMedia() {
        HeritageDocument document = mock(HeritageDocument.class);
        when(document.getId()).thenReturn(DOCUMENT_ID);
        when(document.getFamilyId()).thenReturn(FAMILY_ID);
        when(documentRepository.findByIdAndDeletedAtIsNull(DOCUMENT_ID)).thenReturn(Optional.of(document));
        when(membershipQuery.getMembership(USER_ID)).thenReturn(membership(EffectiveRole.BRANCH_ADMIN, USER_ID));

        service.deleteDocument(DOCUMENT_ID);

        verify(document).delete(any(), eq(USER_ID));
        verify(aiGateway).refreshEmbedding(AiGateway.ContentType.DOCUMENT, DOCUMENT_ID, FAMILY_ID);
        verify(documentRepository).findByIdAndDeletedAtIsNull(DOCUMENT_ID);
        verifyNoMoreInteractions(documentRepository);
    }

    @Test
    void storyAuthorCanSoftDeleteAndNotifiesAiGateway() {
        FamilyStory story = mock(FamilyStory.class);
        when(story.getId()).thenReturn(STORY_ID);
        when(story.getFamilyId()).thenReturn(FAMILY_ID);
        when(story.getCreatedBy()).thenReturn(USER_ID);
        when(storyRepository.findByIdAndDeletedAtIsNull(STORY_ID)).thenReturn(Optional.of(story));
        when(membershipQuery.getMembership(USER_ID)).thenReturn(membership(EffectiveRole.MEMBER, USER_ID));

        service.deleteStory(STORY_ID);

        verify(story).delete(any(), eq(USER_ID));
        verify(aiGateway).refreshEmbedding(AiGateway.ContentType.STORY, STORY_ID, FAMILY_ID);
    }

    private static MembershipView membership(EffectiveRole role, UUID userId) {
        return new MembershipView(userId, FAMILY_ID, null, role, true);
    }

    private static void authenticate(UUID userId) {
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(userId, List.of("MEMBER")), null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static void setId(FamilyStory story, UUID id) throws Exception {
        var field = FamilyStory.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(story, id);
    }

    private static void setId(HeritageDocument document, UUID id) throws Exception {
        var field = HeritageDocument.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(document, id);
    }
}
