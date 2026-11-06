package com.familyconnect.modules.community.service;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.community.dto.CommunityDto.PageSuccess;
import com.familyconnect.modules.community.repository.JdbcAnnouncementStore;
import com.familyconnect.modules.community.repository.JdbcAnnouncementStore.Announcement;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Actor;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AnnouncementService {
    public record Create(@NotBlank @Size(max=255) String title,
                         @NotBlank @Size(max=5000) String content,
                         UUID branchId, Boolean isPinned) {}

    /** PATCH phải phân biệt thiếu branchId với branchId:null (cả gia đình). */
    public static final class Update {
        private String title;
        private String content;
        private UUID branchId;
        private Boolean isPinned;
        private boolean branchSet;
        public String title() { return title; }
        public String content() { return content; }
        public UUID branchId() { return branchId; }
        public Boolean isPinned() { return isPinned; }
        public boolean branchSet() { return branchSet; }
        @JsonSetter("title") public void setTitle(String value) { title = value; }
        @JsonSetter("content") public void setContent(String value) { content = value; }
        @JsonSetter("branchId") public void setBranchId(UUID value) { branchId = value; branchSet = true; }
        @JsonSetter("isPinned") public void setIsPinned(Boolean value) { isPinned = value; }
    }

    private final JdbcAnnouncementStore store;
    private final CommunityPlatformPort platform;
    private final NotificationService notifications;

    public AnnouncementService(JdbcAnnouncementStore store, CommunityPlatformPort platform,
                               NotificationService notifications) {
        this.store = store;
        this.platform = platform;
        this.notifications = notifications;
    }

    private Actor actor() {
        Actor actor = platform.requireAuthenticatedActor();
        if (actor == null || actor.userId() == null) throw new ApiException(ErrorCode.AUTH_002);
        if (!actor.verified() && actor.role() != Role.SYSTEM_ADMIN) throw new ApiException(ErrorCode.PERM_002);
        return actor;
    }

    private UUID family(Actor actor, UUID requested) {
        if (actor.role() == Role.SYSTEM_ADMIN) {
            if (requested == null) throw ApiException.invalidField("familyId", "Cần chọn gia đình");
            return requested;
        }
        if (actor.familyId() == null || requested != null && !requested.equals(actor.familyId()))
            throw new ApiException(ErrorCode.PERM_001);
        return actor.familyId();
    }

    private void requireManage(Actor actor, UUID family, UUID branch) {
        if (actor.role() == Role.MEMBER) throw new ApiException(ErrorCode.PERM_001);
        platform.requireCanModerate(actor,family,branch);
    }

    private void requireScope(Actor actor, UUID family, UUID branch) {
        if (branch != null && !platform.branchExists(family,branch)) throw new ApiException(ErrorCode.NOT_FOUND_001);
        requireManage(actor,family,branch);
    }

    private Announcement readable(Actor actor, UUID id) {
        Announcement found = store.find(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        platform.requireCanRead(actor,found.familyId(),found.branchId());
        return found;
    }

    private static String text(String value, String field, int length) {
        if (value == null || value.isBlank() || value.length() > length)
            throw ApiException.invalidField(field,"Độ dài không hợp lệ (1-" + length + ")");
        return value;
    }

    @Transactional
    public Announcement create(UUID requestedFamily, Create input) {
        Actor actor = actor();
        if (input == null) throw ApiException.invalidField("title", "Thiếu thông báo");
        UUID family = family(actor,requestedFamily);
        requireScope(actor,family,input.branchId());
        Announcement saved = store.create(family,input.branchId(),text(input.title(),"title",255),
            text(input.content(),"content",5000),Boolean.TRUE.equals(input.isPinned()),actor.userId());
        notifications.announce(family,saved.branchId(),saved.id(),saved.title(),saved.content(),actor.userId());
        return saved;
    }

    @Transactional(readOnly = true)
    public PageSuccess<Announcement> list(UUID requestedFamily, UUID branch, int page, int size, String sort) {
        Actor actor = actor();
        UUID family = family(actor,requestedFamily);
        if (page < 0 || size < 1 || size > 100) throw ApiException.invalidField("page", "Phân trang không hợp lệ");
        String[] parts = (sort == null ? "createdAt,desc" : sort).split(",",-1);
        if (parts.length != 2 || !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc")))
            throw ApiException.invalidField("sort", "Sort không hợp lệ");
        String column = switch (parts[0]) {
            case "id" -> "id";
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            case "title" -> "title";
            default -> throw ApiException.invalidField("sort", "Sort không hợp lệ");
        };
        String direction = parts[1].equalsIgnoreCase("asc") ? "ASC" : "DESC";
        boolean all = actor.role() == Role.SYSTEM_ADMIN ||
            (actor.role() == Role.BRANCH_ADMIN && actor.branchId() == null);
        List<UUID> visible = all ? List.of() : store.branchIds(family).stream()
            .filter(id -> platform.canRead(actor,family,id)).toList();
        var found = store.list(family,all,visible,branch,page,size,
            column + " " + direction + (column.equals("id") ? "" : ", id " + direction));
        return PageSuccess.of(found.items(),page,size,found.total());
    }

    @Transactional(readOnly = true)
    public Announcement get(UUID id) { return readable(actor(),id); }

    @Transactional
    public Announcement update(UUID id, Update input) {
        Actor actor = actor();
        Announcement old = readable(actor,id);
        requireManage(actor,old.familyId(),old.branchId());
        if (input == null) throw ApiException.invalidField("announcement", "Thiếu thông tin cập nhật");
        UUID branch = input.branchSet() ? input.branchId() : old.branchId();
        requireScope(actor,old.familyId(),branch);
        String title = input.title() == null ? old.title() : text(input.title(),"title",255);
        String content = input.content() == null ? old.content() : text(input.content(),"content",5000);
        boolean pinned = input.isPinned() == null ? old.isPinned() : input.isPinned();
        return store.update(id,branch,title,content,pinned,actor.userId());
    }

    @Transactional
    public void delete(UUID id) {
        Actor actor = actor();
        Announcement found = readable(actor,id);
        requireManage(actor,found.familyId(),found.branchId());
        store.delete(id,actor.userId());
        platform.logContentDeleted("ANNOUNCEMENT",id,found.familyId(),found.createdBy().equals(actor.userId()));
    }
}
