package com.familyconnect.modules.community.service;

import java.util.List;
import java.util.UUID;

/**
 * Integration boundary for SCRUM-35, SCRUM-37, SCRUM-57 and AiGateway.
 * No production adapter is supplied here: an authenticated, verified actor
 * must come from the real Auth module, not a header, request body or test user.
 */
public interface CommunityPlatformPort {
    enum Role { MEMBER, BRANCH_ADMIN, SYSTEM_ADMIN }
    record Actor(UUID userId, UUID familyId, UUID branchId, Role role, boolean verified) {}

    Actor requireAuthenticatedActor();
    boolean branchExists(UUID familyId, UUID branchId);
    void requireCanPublishInBranch(Actor actor, UUID familyId, UUID branchId);
    void requireCanRead(Actor actor, UUID familyId, UUID branchId);
    void requireCanModerate(Actor actor, UUID familyId, UUID branchId);
    /** Ancestors of the member's branch, including itself (BR-COM-03). */
    List<UUID> readableContentBranchIds(Actor actor, UUID familyId);
    void logContentDeleted(String contentType, UUID contentId, UUID familyId);
    void postChanged(UUID postId, UUID familyId);
}
