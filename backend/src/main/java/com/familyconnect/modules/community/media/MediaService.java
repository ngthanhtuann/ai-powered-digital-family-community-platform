package com.familyconnect.modules.community.media;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.community.api.CommunityQuery;
import com.familyconnect.modules.community.api.MediaOwnerPolicy;
import com.familyconnect.modules.community.dto.CommunityDto.PageSuccess;
import com.familyconnect.modules.community.repository.CommunityStore;
import com.familyconnect.modules.community.repository.CommunityStore.Post;
import com.familyconnect.modules.community.service.CommunityPlatformPort;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** [4-04] UC-COM-05: upload, đọc có quyền, xóa mềm; metadata lưu trong PostgreSQL. */
@Service
public class MediaService implements CommunityQuery {
    private final MediaRepository repository;
    private final MediaStorage storage;
    private final CommunityStore posts;
    private final CommunityPlatformPort platform;
    private final Map<String, MediaOwnerPolicy> policies;

    public record MediaView(UUID id, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                            String ownerType, UUID ownerId, UUID familyId, String fileName,
                            String contentType, long fileSize, String contentUrl, String caption) {
        public static MediaView of(MediaRepository.Media media) {
            return new MediaView(media.id(), media.createdAt(), media.updatedAt(), media.ownerType(),
                media.ownerId(), media.familyId(), media.fileName(), media.fileType(), media.fileSize(),
                "/api/v1/media/" + media.id() + "/content", media.caption());
        }
    }

    public record MediaContent(Path path, String contentType) {}

    public MediaService(MediaRepository repository, MediaStorage storage, CommunityStore posts,
                        CommunityPlatformPort platform, List<MediaOwnerPolicy> policies) {
        this.repository = repository;
        this.storage = storage;
        this.posts = posts;
        this.platform = platform;
        this.policies = policies.stream().collect(Collectors.toUnmodifiableMap(
            MediaOwnerPolicy::ownerType, Function.identity()));
    }

    private Actor actor() {
        Actor actor = platform.requireAuthenticatedActor();
        if (actor == null || actor.userId() == null) throw new ApiException(ErrorCode.AUTH_002);
        if (!actor.verified() && actor.role() != CommunityPlatformPort.Role.SYSTEM_ADMIN) {
            throw new ApiException(ErrorCode.PERM_002);
        }
        return actor;
    }

    private Post readablePost(Actor actor, UUID id) {
        Post post = posts.findPost(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        platform.requireCanRead(actor, post.familyId(), post.branchId());
        return post;
    }

    private MediaOwnerPolicy policy(String type) {
        MediaOwnerPolicy policy = policies.get(type);
        if (policy == null) throw ApiException.invalidField("ownerType", "ownerType phải là HERITAGE hoặc PERSON");
        return policy;
    }

    private static String caption(String value) {
        if (value != null && value.length() > 255) {
            throw ApiException.invalidField("caption", "Chú thích tối đa 255 ký tự");
        }
        return value;
    }

    @Transactional
    public MediaView upload(String ownerType, UUID ownerId, String caption, MultipartFile file) throws IOException {
        Actor current = actor();
        if (ownerId == null) throw ApiException.invalidField("ownerId", "Thiếu tài nguyên sở hữu");
        MediaOwnerPolicy owner = policy(ownerType);
        UUID familyId = owner.requireUploadFamily(ownerId);
        return save(file, ownerType, ownerId, familyId, caption(caption), current.userId());
    }

    @Transactional
    public MediaView uploadPost(UUID postId, MultipartFile file) throws IOException {
        Actor current = actor();
        Post post = readablePost(current, postId);
        if (!post.createdBy().equals(current.userId())) throw new ApiException(ErrorCode.PERM_001);
        return save(file, "POST", postId, post.familyId(), null, current.userId());
    }

    private MediaView save(MultipartFile file, String type, UUID ownerId, UUID family,
                           String caption, UUID userId) throws IOException {
        MediaStorage.StoredFile stored = storage.store(file, "HERITAGE".equals(type));
        try {
            MediaRepository.Media media = repository.insert(UUID.randomUUID(), type, ownerId, family,
                stored.size(), stored.fileName(), stored.storageKey(), caption, stored.mimeType(), userId);
            return MediaView.of(media);
        } catch (RuntimeException e) {
            // Ghi metadata lỗi thì xóa tệp vừa lưu để không để lại tệp không ai quản lý.
            Files.deleteIfExists(storage.resolve(stored.storageKey()));
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public PageSuccess<MediaView> listPostMedia(UUID postId, int page, int size, String sort) {
        Actor current = actor();
        readablePost(current, postId);
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.invalidField("page", "Phân trang không hợp lệ");
        }
        String order = order(sort);
        var result = repository.list("POST", postId, page, size, order);
        return PageSuccess.of(result.items().stream().map(MediaView::of).toList(), page, size, result.total());
    }

    private static String order(String sort) {
        String[] parts = (sort == null ? "createdAt,desc" : sort).split(",", -1);
        if (parts.length != 2 || !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc"))) {
            throw ApiException.invalidField("sort", "Giá trị sort không hợp lệ");
        }
        String column = switch (parts[0]) {
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            case "id" -> "id";
            default -> throw ApiException.invalidField("sort", "Trường sắp xếp không hợp lệ");
        };
        String dir = parts[1].equalsIgnoreCase("asc") ? "ASC" : "DESC";
        return column + " " + dir + (column.equals("id") ? "" : ", id " + dir);
    }

    private void requireRead(Actor current, MediaRepository.Media media) {
        switch (media.ownerType()) {
            case "POST" -> {
                Post post = readablePost(current, media.ownerId());
                if (!media.familyId().equals(post.familyId())) throw new ApiException(ErrorCode.PERM_001);
            }
            case "HERITAGE", "PERSON" -> policy(media.ownerType()).requireRead(media.ownerId(), media.familyId());
            // EVENT chưa có module quản lý sự kiện trong develop; không cấp quyền mặc định.
            default -> throw new ApiException(ErrorCode.NOT_FOUND_001);
        }
    }

    @Transactional(readOnly = true)
    public MediaContent content(UUID id) {
        Actor current = actor();
        MediaRepository.Media media = repository.find(id)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        requireRead(current, media);
        Path file = storage.resolve(media.storageKey());
        if (!Files.isRegularFile(file)) throw new ApiException(ErrorCode.NOT_FOUND_001);
        return new MediaContent(file, media.fileType());
    }

    @Transactional
    public void delete(UUID id) {
        Actor current = actor();
        MediaRepository.Media media = repository.find(id)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        requireRead(current, media);
        boolean own = media.createdBy().equals(current.userId());
        switch (media.ownerType()) {
            case "POST" -> {
                if (!own) {
                    Post post = readablePost(current, media.ownerId());
                    platform.requireCanModerate(current, post.familyId(), post.branchId());
                }
            }
            case "HERITAGE", "PERSON" ->
                policy(media.ownerType()).requireDelete(media.id(), media.ownerId(), media.familyId(), own);
            default -> throw new ApiException(ErrorCode.NOT_FOUND_001);
        }
        if (!repository.softDelete(id, current.userId())) throw new ApiException(ErrorCode.NOT_FOUND_001);
        platform.logContentDeleted("MEDIA", id, media.familyId(), own);
        // Theo ADR: soft delete giữ lại tệp vật lý.
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<HeritageMedia> getHeritageMedia(UUID id) {
        return repository.find(id).filter(m -> "HERITAGE".equals(m.ownerType()))
            .map(m -> new HeritageMedia(m.id(), m.familyId(), m.ownerType(), MediaView.of(m).contentUrl()));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<PersonMedia> getPersonMedia(UUID id) {
        return repository.find(id).filter(m -> "PERSON".equals(m.ownerType()))
            .map(m -> new PersonMedia(m.id(), m.ownerId()));
    }
}
