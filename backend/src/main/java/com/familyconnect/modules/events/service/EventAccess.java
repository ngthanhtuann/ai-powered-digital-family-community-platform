package com.familyconnect.modules.events.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.ai.api.AiGateway;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.GenealogyPort;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.UserDirectory;
import com.familyconnect.modules.events.repository.EventStore.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Quyền sự kiện theo BR-COM-03, BR-EVT-01..03; đọc module khác qua interface Java. */
@Component
public class EventAccess {
    private static final Logger log = LoggerFactory.getLogger(EventAccess.class);
    private final MembershipQuery membership;
    private final ObjectProvider<GenealogyPort> genealogy;
    private final UserDirectory users;
    private final AuditLogger audit;
    private final AiGateway ai;

    public record Actor(UUID id, UUID familyId, UUID branchId, EffectiveRole role) {}

    public EventAccess(MembershipQuery membership, ObjectProvider<GenealogyPort> genealogy,
                       UserDirectory users, AuditLogger audit, AiGateway ai) {
        this.membership = membership;
        this.genealogy = genealogy;
        this.users = users;
        this.audit = audit;
        this.ai = ai;
    }

    public Actor actor() {
        UUID userId = CurrentUser.require().id();
        var m = membership.getMembership(userId);
        if (!m.verified() && m.role() != EffectiveRole.SYSTEM_ADMIN) throw new ApiException(ErrorCode.PERM_002);
        return new Actor(userId,m.familyId(),m.branchId(),m.role());
    }

    public UUID family(Actor actor, UUID requested) {
        if (actor.role() == EffectiveRole.SYSTEM_ADMIN) {
            if (requested == null) throw ApiException.invalidField("familyId", "Cần chọn gia đình");
            return requested;
        }
        if (actor.familyId() == null) throw new ApiException(ErrorCode.PERM_001);
        if (requested != null && !requested.equals(actor.familyId())) throw new ApiException(ErrorCode.PERM_001);
        return actor.familyId();
    }

    private boolean within(UUID family, UUID child, UUID ancestor) {
        GenealogyPort port = genealogy.getIfAvailable();
        return port != null && port.isBranchWithin(family,child,ancestor);
    }

    public boolean canRead(Actor actor, UUID family, UUID branch) {
        if (actor.role() == EffectiveRole.SYSTEM_ADMIN) return true;
        if (!family.equals(actor.familyId())) return false;
        if (branch == null || (actor.role() == EffectiveRole.BRANCH_ADMIN && actor.branchId() == null)) return true;
        if (actor.branchId() == null) return false;
        return within(family,actor.branchId(),branch)
            || (actor.role() == EffectiveRole.BRANCH_ADMIN && within(family,branch,actor.branchId()));
    }

    public void requireRead(Actor actor, UUID family, UUID branch) {
        if (!canRead(actor,family,branch)) throw new ApiException(ErrorCode.PERM_001);
    }

    public void requirePublish(Actor actor, UUID family, UUID branch) {
        if (branch != null) {
            GenealogyPort port = genealogy.getIfAvailable();
            if (port == null || port.findBranchName(family,branch).isEmpty()) {
                throw new ApiException(ErrorCode.NOT_FOUND_001);
            }
        }
        requireRead(actor,family,branch);
    }

    public void requireManage(Actor actor, Event event) {
        if (event.createdBy().equals(actor.id()) || actor.role() == EffectiveRole.SYSTEM_ADMIN) return;
        if (actor.role() != EffectiveRole.BRANCH_ADMIN || !event.familyId().equals(actor.familyId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        if (actor.branchId() != null && (event.branchId() == null ||
                !within(event.familyId(),event.branchId(),actor.branchId()))) {
            throw new ApiException(ErrorCode.PERM_001);
        }
    }

    public void requireFamilyUser(UUID userId, UUID familyId) {
        var m = membership.getMembership(userId);
        if (!m.verified() || !familyId.equals(m.familyId())) throw new ApiException(ErrorCode.NOT_FOUND_001);
    }

    public Map<UUID,String> names(Collection<UUID> userIds) { return users.getFullNames(userIds); }

    public void eventDeleted(Event event, Actor actor) {
        audit.log("CONTENT_DELETED","EVENT",event.id(),event.familyId(),
                  Map.of("byOwner",event.createdBy().equals(actor.id())));
    }

    /** BR-EVT-06: chỉ cập nhật AI sau khi giao dịch sự kiện đã commit. */
    public void eventChanged(Event event, boolean deleted) {
        Runnable work = () -> {
            try {
                if (deleted) ai.removeSource(AiGateway.SourceType.EVENT,event.id());
                else ai.upsertSource(AiGateway.SourceType.EVENT,event.id(),event.familyId(),event.branchId(),
                    event.title() + " " + (event.description() == null ? "" : event.description()));
            } catch (RuntimeException ex) { log.warn("Không cập nhật được AI cho sự kiện {}",event.id(),ex); }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { work.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { work.run(); }
        });
    }
}
