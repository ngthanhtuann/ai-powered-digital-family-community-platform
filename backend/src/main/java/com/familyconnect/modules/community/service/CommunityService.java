package com.familyconnect.modules.community.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.community.dto.CommunityDto.*;
import com.familyconnect.modules.community.repository.CommunityStore;
import com.familyconnect.modules.community.repository.CommunityStore.*;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Actor;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;

/** Nghiệp vụ UC-COM-01..04 (community-events.md); quyền và phạm vi chi hỏi qua CommunityPlatformPort. */
@Service
public class CommunityService {
    private final CommunityStore store;
    private final CommunityPlatformPort platform;

    public CommunityService(CommunityStore store, CommunityPlatformPort platform) {
        this.store = store;
        this.platform = platform;
    }

    private Actor actor() {
        Actor current = platform.requireAuthenticatedActor();
        if (current == null || current.userId() == null || current.role() == null) {
            throw new ApiException(ErrorCode.AUTH_002);
        }
        if (!current.verified() && current.role() != Role.SYSTEM_ADMIN) {
            throw new ApiException(ErrorCode.PERM_002);
        }
        return current;
    }

    private UUID family(Actor actor, UUID requested) {
        if (actor.role() == Role.SYSTEM_ADMIN) {
            if (requested == null) throw ApiException.invalidField("familyId", "Quản trị viên phải chọn gia đình");
            return requested;
        }
        if (actor.familyId() == null) throw new ApiException(ErrorCode.PERM_001);
        if (requested != null && !requested.equals(actor.familyId())) throw new ApiException(ErrorCode.PERM_001);
        return actor.familyId();
    }

    private void canPublish(Actor actor, UUID family, UUID branch) {
        if (branch != null && !platform.branchExists(family,branch)) throw new ApiException(ErrorCode.NOT_FOUND_001);
        platform.requireCanPublishInBranch(actor,family,branch);
    }
    private Post readablePost(Actor actor, UUID id) {
        Post post = store.findPost(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        platform.requireCanRead(actor, post.familyId(), post.branchId());
        return post;
    }
    /** Một trang bài: đếm bình luận, reaction, tên tác giả theo lô (số truy vấn cố định). */
    private List<PostView> views(List<Post> posts, Actor user) {
        if (posts.isEmpty()) return List.of();
        List<UUID> ids = posts.stream().map(Post::id).toList();
        Map<UUID,Long> comments = store.commentCounts(ids);
        Map<UUID,Map<ReactionType,Long>> reactions = store.reactionCounts(ids);
        Map<UUID,ReactionType> mine = store.myReactions(ids,user.userId());
        Map<UUID,String> names = platform.authorNames(posts.stream().map(Post::createdBy).collect(Collectors.toSet()));
        Map<UUID,Boolean> moderatable = new HashMap<>();
        return posts.stream().map(post -> new PostView(post.id(),post.createdAt(),post.updatedAt(),
            post.familyId(),post.branchId(),post.content(),post.type(),post.createdBy(),
            names.get(post.createdBy()),canDelete(user,post,post.createdBy(),moderatable),
            comments.getOrDefault(post.id(),0L),reactions.get(post.id()),mine.get(post.id()))).toList();
    }
    private PostView asView(Post post, Actor user) {
        return views(List.of(post),user).get(0);
    }
    private List<CommentView> commentViews(List<Comment> comments, Post post, Actor user) {
        Map<UUID,String> names = platform.authorNames(
            comments.stream().map(Comment::createdBy).collect(Collectors.toSet()));
        Map<UUID,Boolean> moderatable = new HashMap<>();
        return comments.stream().map(comment -> new CommentView(comment.id(),comment.createdAt(),
            comment.updatedAt(),comment.postId(),comment.content(),comment.createdBy(),
            names.get(comment.createdBy()),canDelete(user,post,comment.createdBy(),moderatable))).toList();
    }
    /** BR-COM-05, BR-COM-06; nhớ kết quả theo chi để một trang không hỏi Gia phả lặp lại. */
    private boolean canDelete(Actor user, Post post, UUID owner, Map<UUID,Boolean> moderatable) {
        if (owner.equals(user.userId())) return true;
        if (user.role() == Role.MEMBER) return false;
        UUID key = post.branchId() == null ? post.familyId() : post.branchId();
        return moderatable.computeIfAbsent(key, k -> platform.canModerate(user,post.familyId(),post.branchId()));
    }
    private ReactionView asView(Reaction reaction) {
        return new ReactionView(reaction.id(),reaction.createdAt(),reaction.updatedAt(),
            reaction.postId(),reaction.type(),reaction.createdBy());
    }
    private String nonBlank(String content, int max, String field) {
        if (content == null || content.isBlank() || content.length() > max)
            throw ApiException.invalidField(field,"Nội dung phải có từ 1 đến " + max + " ký tự");
        return content;
    }
    private void pagination(int page, int size) {
        if (page < 0) throw ApiException.invalidField("page","page phải bắt đầu từ 0");
        if (size < 1 || size > 100) throw ApiException.invalidField("size","size phải từ 1 đến 100");
    }
    private String ordering(String sort, boolean comments) {
        String[] parts = (sort == null ? "createdAt,desc" : sort).split(",",-1);
        if (parts.length != 2 || !("asc".equalsIgnoreCase(parts[1]) || "desc".equalsIgnoreCase(parts[1])))
            throw ApiException.invalidField("sort","Giá trị sort không hợp lệ");
        String column = switch (parts[0]) {
            case "id" -> "id";
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            case "content" -> "content";
            case "type" -> comments ? null : "type";
            default -> null;
        };
        if (column == null) throw ApiException.invalidField("sort","Trường sắp xếp không hợp lệ");
        String dir = parts[1].equalsIgnoreCase("asc") ? "ASC" : "DESC";
        return column + " " + dir + (column.equals("id") ? "" : ", id " + dir);
    }

    @Transactional
    public PostView createPost(UUID familyId, CreatePost input) {
        Actor user = actor();
        if (input == null || input.type() == null) throw ApiException.invalidField("type","Cần chọn loại bài đăng");
        UUID family = family(user,familyId);
        if (input.type() == PostType.NEWS && user.role() == Role.MEMBER) throw new ApiException(ErrorCode.PERM_001);
        String content = nonBlank(input.content(),5000,"content");
        canPublish(user,family,input.branchId());
        Post created = store.insertPost(family,input.branchId(),content,input.type(),user.userId());
        platform.postChanged(created.id(),family);
        return asView(created,user);
    }

    @Transactional(readOnly=true)
    public PageSuccess<PostView> posts(UUID familyId, UUID branchId, PostType type, int page, int size, String sort) {
        Actor user = actor();
        UUID family = family(user,familyId);
        pagination(page,size);
        String order = ordering(sort,false);
        // BR-COM-03: SYSTEM_ADMIN và trưởng chi quản lý cả gia đình thấy mọi chi; người khác chỉ thấy chi trong phạm vi.
        boolean allBranches = user.role() == Role.SYSTEM_ADMIN
            || (user.role() == Role.BRANCH_ADMIN && user.branchId() == null);
        List<UUID> visible = allBranches ? List.of() : store.postBranchIds(family).stream()
            .filter(branch -> platform.canRead(user,family,branch)).toList();
        Page<Post> found = store.posts(family,allBranches,visible,branchId,type,page,size,order);
        return PageSuccess.of(views(found.items(),user),
                              page,size,found.total());
    }

    @Transactional(readOnly=true)
    public PostView post(UUID id) {
        Actor user = actor();
        return asView(readablePost(user,id),user);
    }

    @Transactional
    public PostView updatePost(UUID id, UpdatePost input) {
        Actor user = actor();
        Post post = readablePost(user,id);
        if (!post.createdBy().equals(user.userId())) throw new ApiException(ErrorCode.PERM_001);
        if (input == null || input.type() != null) throw ApiException.invalidField("type","Không được đổi loại bài đăng");
        String content = input.content() == null ? post.content() : nonBlank(input.content(),5000,"content");
        UUID branch = input.branchSpecified() ? input.branchId() : post.branchId();
        canPublish(user,post.familyId(),branch);
        Post updated = store.updatePost(id,content,branch,user.userId());
        platform.postChanged(id,post.familyId());
        return asView(updated,user);
    }

    @Transactional
    public void deletePost(UUID id) {
        Actor user = actor();
        Post post = readablePost(user,id);
        boolean owner = post.createdBy().equals(user.userId());
        if (!owner) {
            if (user.role() == Role.MEMBER) throw new ApiException(ErrorCode.PERM_001);
            platform.requireCanModerate(user,post.familyId(),post.branchId());
        }
        store.deletePost(id);
        platform.logContentDeleted("POST",id,post.familyId(),owner);
        platform.postChanged(id,post.familyId());
    }

    @Transactional(readOnly=true)
    public PageSuccess<CommentView> comments(UUID postId, int page, int size, String sort) {
        Actor user = actor();
        Post post = readablePost(user,postId);
        pagination(page,size);
        Page<Comment> found = store.comments(postId,page,size,ordering(sort,true));
        return PageSuccess.of(commentViews(found.items(),post,user),page,size,found.total());
    }

    @Transactional
    public CommentView createComment(UUID postId, CommentInput input) {
        Actor user = actor();
        Post post = readablePost(user,postId);
        Comment created = store.insertComment(postId,nonBlank(input == null ? null : input.content(),1000,"content"),user.userId());
        return commentViews(List.of(created),post,user).get(0);
    }

    @Transactional
    public CommentView updateComment(UUID id, CommentInput input) {
        Actor user = actor();
        Comment comment = store.findComment(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        Post post = readablePost(user,comment.postId());
        if (!comment.createdBy().equals(user.userId())) throw new ApiException(ErrorCode.PERM_001);
        Comment updated = store.updateComment(id,nonBlank(input == null ? null : input.content(),1000,"content"),user.userId());
        return commentViews(List.of(updated),post,user).get(0);
    }

    @Transactional
    public void deleteComment(UUID id) {
        Actor user = actor();
        Comment comment = store.findComment(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        Post post = readablePost(user,comment.postId());
        boolean owner = comment.createdBy().equals(user.userId());
        if (!owner) {
            if (user.role() == Role.MEMBER) throw new ApiException(ErrorCode.PERM_001);
            platform.requireCanModerate(user,post.familyId(),post.branchId());
        }
        store.deleteComment(id);
        platform.logContentDeleted("COMMENT",id,post.familyId(),owner);
    }

    @Transactional
    public ReactionView react(UUID postId, ReactionInput input) {
        Actor user = actor();
        readablePost(user,postId);
        if (input == null || input.type() == null) throw ApiException.invalidField("type","Reaction không hợp lệ");
        store.lockPost(postId);
        var current = store.myReaction(postId,user.userId());
        Reaction updated = current.isPresent()
            ? store.updateReaction(current.get().id(),user.userId(),input.type())
            : store.insertReaction(postId,user.userId(),input.type());
        return asView(updated);
    }

    @Transactional
    public void removeReaction(UUID postId) {
        Actor user = actor();
        readablePost(user,postId);
        store.lockPost(postId);
        Reaction mine = store.myReaction(postId,user.userId()).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        store.deleteReaction(mine.id());
    }
}
