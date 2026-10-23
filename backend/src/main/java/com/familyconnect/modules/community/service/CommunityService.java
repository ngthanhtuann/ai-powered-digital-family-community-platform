package com.familyconnect.modules.community.service;

import com.familyconnect.modules.community.dto.CommunityDto.*;
import com.familyconnect.modules.community.repository.CommunityStore;
import com.familyconnect.modules.community.repository.CommunityStore.*;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Actor;
import com.familyconnect.modules.community.service.CommunityPlatformPort.Role;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for UC-COM-01..04. Fail closed while Auth/Genealogy are not
 * implemented: no service bean exists without a real CommunityPlatformPort.
 */
@Service
@ConditionalOnBean(CommunityPlatformPort.class)
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
            throw new CommunityFailure(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "AUTH_002", "Yêu cầu đăng nhập hợp lệ");
        }
        if (!current.verified() && current.role() != Role.SYSTEM_ADMIN) {
            throw new CommunityFailure(org.springframework.http.HttpStatus.FORBIDDEN,
                "PERM_002", "Thành viên chưa được xác minh");
        }
        return current;
    }

    private UUID family(Actor actor, UUID requested) {
        if (actor.role() == Role.SYSTEM_ADMIN) {
            if (requested == null) throw CommunityFailure.invalid("familyId", "Quản trị viên phải chọn gia đình");
            return requested;
        }
        if (actor.familyId() == null) throw CommunityFailure.forbidden();
        if (requested != null && !requested.equals(actor.familyId())) throw CommunityFailure.forbidden();
        return actor.familyId();
    }

    private void canPublish(Actor actor, UUID family, UUID branch) {
        if (branch != null && !platform.branchExists(family,branch)) throw CommunityFailure.notFound();
        platform.requireCanPublishInBranch(actor,family,branch);
    }
    private Post readablePost(Actor actor, UUID id) {
        Post post = store.findPost(id).orElseThrow(CommunityFailure::notFound);
        platform.requireCanRead(actor, post.familyId(), post.branchId());
        return post;
    }
    private PostView asView(Post post, UUID user) {
        return new PostView(post.id(),post.createdAt(),post.updatedAt(),post.familyId(),
            post.branchId(),post.content(),post.type(),post.createdBy(),store.commentCount(post.id()),
            store.reactionCounts(post.id()),store.myReaction(post.id(),user).map(Reaction::type).orElse(null));
    }
    private CommentView asView(Comment comment) {
        return new CommentView(comment.id(),comment.createdAt(),comment.updatedAt(),
            comment.postId(),comment.content(),comment.createdBy());
    }
    private ReactionView asView(Reaction reaction) {
        return new ReactionView(reaction.id(),reaction.createdAt(),reaction.updatedAt(),
            reaction.postId(),reaction.type(),reaction.createdBy());
    }
    private String nonBlank(String content, int max, String field) {
        if (content == null || content.isBlank() || content.length() > max)
            throw CommunityFailure.invalid(field,"Nội dung phải có từ 1 đến " + max + " ký tự");
        return content;
    }
    private void pagination(int page, int size) {
        if (page < 0) throw CommunityFailure.invalid("page","page phải bắt đầu từ 0");
        if (size < 1 || size > 100) throw CommunityFailure.invalid("size","size phải từ 1 đến 100");
    }
    private String ordering(String sort, boolean comments) {
        String[] parts = (sort == null ? "createdAt,desc" : sort).split(",",-1);
        if (parts.length != 2 || !("asc".equalsIgnoreCase(parts[1]) || "desc".equalsIgnoreCase(parts[1])))
            throw CommunityFailure.invalid("sort","Giá trị sort không hợp lệ");
        String column = switch (parts[0]) {
            case "id" -> "id";
            case "createdAt" -> "created_at";
            case "updatedAt" -> "updated_at";
            case "content" -> "content";
            case "type" -> comments ? null : "type";
            default -> null;
        };
        if (column == null) throw CommunityFailure.invalid("sort","Trường sắp xếp không hợp lệ");
        String dir = parts[1].equalsIgnoreCase("asc") ? "ASC" : "DESC";
        return column + " " + dir + (column.equals("id") ? "" : ", id " + dir);
    }

    @Transactional
    public PostView createPost(UUID familyId, CreatePost input) {
        Actor user = actor();
        if (input == null || input.type() == null) throw CommunityFailure.invalid("type","Cần chọn loại bài đăng");
        UUID family = family(user,familyId);
        if (input.type() == PostType.NEWS && user.role() == Role.MEMBER) throw CommunityFailure.forbidden();
        String content = nonBlank(input.content(),5000,"content");
        canPublish(user,family,input.branchId());
        Post created = store.insertPost(family,input.branchId(),content,input.type(),user.userId());
        platform.postChanged(created.id(),family);
        return asView(created,user.userId());
    }

    @Transactional(readOnly=true)
    public PageSuccess<PostView> posts(UUID familyId, UUID branchId, PostType type, int page, int size, String sort) {
        Actor user = actor();
        UUID family = family(user,familyId);
        pagination(page,size);
        String order = ordering(sort,false);
        boolean allBranches = user.role() != Role.MEMBER;
        List<UUID> visible = allBranches ? List.of() : platform.readableContentBranchIds(user,family);
        Page<Post> found = store.posts(family,allBranches,visible,branchId,type,page,size,order);
        return PageSuccess.of(found.items().stream().map(p -> asView(p,user.userId())).toList(),
                              page,size,found.total());
    }

    @Transactional(readOnly=true)
    public PostView post(UUID id) {
        Actor user = actor();
        return asView(readablePost(user,id),user.userId());
    }

    @Transactional
    public PostView updatePost(UUID id, UpdatePost input) {
        Actor user = actor();
        Post post = readablePost(user,id);
        if (!post.createdBy().equals(user.userId())) throw CommunityFailure.forbidden();
        if (input == null || input.type() != null) throw CommunityFailure.invalid("type","Không được đổi loại bài đăng");
        String content = input.content() == null ? post.content() : nonBlank(input.content(),5000,"content");
        UUID branch = input.branchSpecified() ? input.branchId() : post.branchId();
        canPublish(user,post.familyId(),branch);
        Post updated = store.updatePost(id,content,branch,user.userId());
        platform.postChanged(id,post.familyId());
        return asView(updated,user.userId());
    }

    @Transactional
    public void deletePost(UUID id) {
        Actor user = actor();
        Post post = readablePost(user,id);
        boolean owner = post.createdBy().equals(user.userId());
        if (!owner) {
            if (user.role() == Role.MEMBER) throw CommunityFailure.forbidden();
            platform.requireCanModerate(user,post.familyId(),post.branchId());
        }
        store.deletePost(id);
        if (!owner) platform.logContentDeleted("POST",id,post.familyId());
        platform.postChanged(id,post.familyId());
    }

    @Transactional(readOnly=true)
    public PageSuccess<CommentView> comments(UUID postId, int page, int size, String sort) {
        Actor user = actor();
        readablePost(user,postId);
        pagination(page,size);
        Page<Comment> found = store.comments(postId,page,size,ordering(sort,true));
        return PageSuccess.of(found.items().stream().map(this::asView).toList(),page,size,found.total());
    }

    @Transactional
    public CommentView createComment(UUID postId, CommentInput input) {
        Actor user = actor();
        readablePost(user,postId);
        return asView(store.insertComment(postId,nonBlank(input == null ? null : input.content(),1000,"content"),user.userId()));
    }

    @Transactional
    public CommentView updateComment(UUID id, CommentInput input) {
        Actor user = actor();
        Comment comment = store.findComment(id).orElseThrow(CommunityFailure::notFound);
        readablePost(user,comment.postId());
        if (!comment.createdBy().equals(user.userId())) throw CommunityFailure.forbidden();
        return asView(store.updateComment(id,nonBlank(input == null ? null : input.content(),1000,"content"),user.userId()));
    }

    @Transactional
    public void deleteComment(UUID id) {
        Actor user = actor();
        Comment comment = store.findComment(id).orElseThrow(CommunityFailure::notFound);
        Post post = readablePost(user,comment.postId());
        boolean owner = comment.createdBy().equals(user.userId());
        if (!owner) {
            if (user.role() == Role.MEMBER) throw CommunityFailure.forbidden();
            platform.requireCanModerate(user,post.familyId(),post.branchId());
        }
        store.deleteComment(id);
        if (!owner) platform.logContentDeleted("COMMENT",id,post.familyId());
    }

    @Transactional
    public ReactionView react(UUID postId, ReactionInput input) {
        Actor user = actor();
        readablePost(user,postId);
        if (input == null || input.type() == null) throw CommunityFailure.invalid("type","Reaction không hợp lệ");
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
        Reaction mine = store.myReaction(postId,user.userId()).orElseThrow(CommunityFailure::notFound);
        store.deleteReaction(mine.id());
    }
}
