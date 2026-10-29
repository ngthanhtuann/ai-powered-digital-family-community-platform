package com.familyconnect.modules.community.media;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.community.api.MediaOwnerPolicy;
import com.familyconnect.modules.community.repository.CommunityStore;
import com.familyconnect.modules.community.repository.CommunityStore.Post;
import com.familyconnect.modules.community.dto.CommunityDto.PostType;
import com.familyconnect.modules.community.service.CommunityPlatformPort;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Actor;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {
    @Mock MediaRepository repository;
    @Mock MediaStorage storage;
    @Mock CommunityStore posts;
    @Mock CommunityPlatformPort platform;
    @Mock MediaOwnerPolicy heritagePolicy;

    private final UUID family = UUID.randomUUID();
    private final UUID postId = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    private MediaService service() { return new MediaService(repository, storage, posts, platform, List.of()); }
    private Actor member() { return new Actor(user, family, null, Role.MEMBER, true); }
    private Post post(UUID author) {
        return new Post(postId, family, null, "Nội dung", PostType.POST, author,
            OffsetDateTime.now(), OffsetDateTime.now());
    }

    @Test void validPostUploadStoresMetadataAndDoesNotExposeStorageKey() throws IOException {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(posts.findPost(postId)).thenReturn(Optional.of(post(user)));
        var input = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1});
        var stored = new MediaStorage.StoredFile("internal-only.png", "photo.png", "image/png", 1);
        when(storage.store(input, false)).thenReturn(stored);
        UUID mediaId = UUID.randomUUID();
        var saved = new MediaRepository.Media(mediaId, "POST", postId, family, 1,
            "photo.png", stored.storageKey(), null, "image/png", user,
            OffsetDateTime.now(), OffsetDateTime.now());
        when(repository.insert(any(UUID.class), eq("POST"), eq(postId), eq(family), eq(1L),
            eq("photo.png"), eq("internal-only.png"), isNull(), eq("image/png"), eq(user)))
            .thenReturn(saved);

        var result = service().uploadPost(postId, input);

        assertEquals(mediaId, result.id());
        assertEquals("/api/v1/media/" + mediaId + "/content", result.contentUrl());
        assertFalse(result.toString().contains("internal-only.png"));
    }

    @Test void failedMetadataInsertDeletesStoredFile(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
            throws IOException {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(posts.findPost(postId)).thenReturn(Optional.of(post(user)));
        var input = new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1});
        var stored = new MediaStorage.StoredFile("orphan.png", "photo.png", "image/png", 1);
        java.nio.file.Path file = java.nio.file.Files.writeString(dir.resolve("orphan.png"), "x");
        when(storage.store(input, false)).thenReturn(stored);
        when(storage.resolve("orphan.png")).thenReturn(file);
        when(repository.insert(any(), any(), any(), any(), anyLong(), any(), any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("DB lỗi"));

        assertThrows(IllegalStateException.class, () -> service().uploadPost(postId, input));
        assertFalse(java.nio.file.Files.exists(file), "Tệp phải bị xóa khi không ghi được metadata");
    }

    @Test void memberCannotUploadToSomeoneElsesPost() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(posts.findPost(postId)).thenReturn(Optional.of(post(UUID.randomUUID())));
        ApiException exception = assertThrows(ApiException.class,
            () -> service().uploadPost(postId, new MockMultipartFile("file", "test.png", "image/png", new byte[] {1})));
        assertEquals(ErrorCode.PERM_001, exception.code());
        verifyNoInteractions(repository, storage);
    }

    @Test void deniedPostReadDoesNotReturnMedia() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(posts.findPost(postId)).thenReturn(Optional.of(post(user)));
        doThrow(new ApiException(ErrorCode.PERM_001)).when(platform).requireCanRead(any(), eq(family), isNull());
        ApiException exception = assertThrows(ApiException.class,
            () -> service().listPostMedia(postId, 0, 20, "createdAt,desc"));
        assertEquals(ErrorCode.PERM_001, exception.code());
        verifyNoInteractions(repository);
    }

    @Test void deletedMediaIsNeverServed() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        UUID id = UUID.randomUUID();
        when(repository.find(id)).thenReturn(Optional.empty());
        ApiException exception = assertThrows(ApiException.class, () -> service().content(id));
        assertEquals(ErrorCode.NOT_FOUND_001, exception.code());
    }

    @Test void ownerModuleCanDenyContentBeforeAnyFileIsRead() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(heritagePolicy.ownerType()).thenReturn("HERITAGE");
        UUID id = UUID.randomUUID();
        var metadata = new MediaRepository.Media(id, "HERITAGE", family, family,
            123, "history.png", "internal.png", null, "image/png", user,
            OffsetDateTime.now(), OffsetDateTime.now());
        when(repository.find(id)).thenReturn(Optional.of(metadata));
        doThrow(new ApiException(ErrorCode.PERM_001)).when(heritagePolicy).requireRead(family, family);

        var service = new MediaService(repository, storage, posts, platform, List.of(heritagePolicy));
        ApiException error = assertThrows(ApiException.class, () -> service.content(id));
        assertEquals(ErrorCode.PERM_001, error.code());
        verifyNoInteractions(storage);
    }

    @Test void invalidSortIsRejected() {
        when(platform.requireAuthenticatedActor()).thenReturn(member());
        when(posts.findPost(postId)).thenReturn(Optional.of(post(user)));
        ApiException exception = assertThrows(ApiException.class,
            () -> service().listPostMedia(postId, 0, 20, "fileName,desc"));
        assertEquals(ErrorCode.VALID_001, exception.code());
        verifyNoInteractions(repository);
    }
}
