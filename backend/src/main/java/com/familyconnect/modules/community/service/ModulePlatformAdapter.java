package com.familyconnect.modules.community.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.GenealogyPort;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.auth.api.UserDirectory;
import com.familyconnect.modules.community.repository.CommunityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Nối Community với các interface đã có: MembershipQuery, GenealogyPort, AuditLogger, AiGateway. */
@Component
public class ModulePlatformAdapter implements CommunityPlatformPort {

    private static final Logger log = LoggerFactory.getLogger(ModulePlatformAdapter.class);
    static final String DELETED_AUTHOR_NAME = "Thành viên đã rời hệ thống";

    private final MembershipQuery membershipQuery;
    private final ObjectProvider<GenealogyPort> genealogyPort;
    private final AuditLogger auditLogger;
    private final AiGateway aiGateway;
    private final CommunityStore store;
    private final UserDirectory userDirectory;

    public ModulePlatformAdapter(MembershipQuery membershipQuery, ObjectProvider<GenealogyPort> genealogyPort,
                                 AuditLogger auditLogger, AiGateway aiGateway, CommunityStore store,
                                 UserDirectory userDirectory) {
        this.membershipQuery = membershipQuery;
        this.genealogyPort = genealogyPort;
        this.auditLogger = auditLogger;
        this.aiGateway = aiGateway;
        this.store = store;
        this.userDirectory = userDirectory;
    }

    @Override
    public Actor requireAuthenticatedActor() {
        UUID userId = CurrentUser.require().id();
        MembershipView view = membershipQuery.getMembership(userId);
        return new Actor(userId, view.familyId(), view.branchId(), role(view.role()), view.verified());
    }

    @Override
    public boolean branchExists(UUID familyId, UUID branchId) {
        GenealogyPort port = genealogyPort.getIfAvailable();
        return port != null && port.findBranchName(familyId, branchId).isPresent();
    }

    @Override
    public boolean canRead(Actor actor, UUID familyId, UUID branchId) {
        if (actor.role() == Role.SYSTEM_ADMIN) {
            return true;
        }
        if (!familyId.equals(actor.familyId())) {
            return false;
        }
        if (branchId == null || (actor.role() == Role.BRANCH_ADMIN && actor.branchId() == null)) {
            return true;
        }
        if (actor.branchId() == null) {
            return false;
        }
        // Thành viên chi Y thấy nội dung của Y và các chi tổ tiên; trưởng chi còn thấy chi con mình quản lý.
        return within(familyId, actor.branchId(), branchId)
                || (actor.role() == Role.BRANCH_ADMIN && within(familyId, branchId, actor.branchId()));
    }

    @Override
    public void requireCanRead(Actor actor, UUID familyId, UUID branchId) {
        if (!canRead(actor, familyId, branchId)) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    @Override
    public void requireCanPublishInBranch(Actor actor, UUID familyId, UUID branchId) {
        requireCanRead(actor, familyId, branchId);
    }

    @Override
    public boolean canModerate(Actor actor, UUID familyId, UUID branchId) {
        if (actor.role() == Role.SYSTEM_ADMIN) {
            return true;
        }
        return actor.role() == Role.BRANCH_ADMIN && familyId.equals(actor.familyId())
                && (actor.branchId() == null || (branchId != null && within(familyId, branchId, actor.branchId())));
    }

    @Override
    public void requireCanModerate(Actor actor, UUID familyId, UUID branchId) {
        if (!canModerate(actor, familyId, branchId)) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    @Override
    public Map<UUID, String> authorNames(Collection<UUID> userIds) {
        Map<UUID, String> found = userDirectory.getFullNames(userIds);
        Map<UUID, String> names = new HashMap<>();
        userIds.forEach(id -> names.put(id, found.getOrDefault(id, DELETED_AUTHOR_NAME)));
        return names;
    }

    @Override
    public void logContentDeleted(String contentType, UUID contentId, UUID familyId, boolean byOwner) {
        auditLogger.log("CONTENT_DELETED", contentType, contentId, familyId, Map.of("byOwner", byOwner));
    }

    @Override
    public void postChanged(UUID postId, UUID familyId) {
        // Đọc ngay trong giao dịch, gọi AI sau khi commit (ai.md mục 6.1): lỗi AI không làm hỏng thao tác bài đăng.
        Runnable action = store.findPost(postId)
                .<Runnable>map(post -> () -> aiGateway.upsertSource(AiGateway.SourceType.POST, post.id(),
                        post.familyId(), post.branchId(), post.content()))
                .orElse(() -> aiGateway.removeSource(AiGateway.SourceType.POST, postId));
        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException ex) {
                log.warn("Không cập nhật được chỉ mục AI cho bài đăng {}", postId, ex);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safe.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safe.run();
            }
        });
    }

    private boolean within(UUID familyId, UUID branchId, UUID ancestorBranchId) {
        GenealogyPort port = genealogyPort.getIfAvailable();
        return port != null && port.isBranchWithin(familyId, branchId, ancestorBranchId);
    }

    private static Role role(EffectiveRole role) {
        return switch (role) {
            case SYSTEM_ADMIN -> Role.SYSTEM_ADMIN;
            case BRANCH_ADMIN -> Role.BRANCH_ADMIN;
            case MEMBER -> Role.MEMBER;
        };
    }
}
