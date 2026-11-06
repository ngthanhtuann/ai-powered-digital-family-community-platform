package com.familyconnect.modules.events.media;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.community.api.MediaOwnerPolicy;
import com.familyconnect.modules.events.repository.EventStore;
import com.familyconnect.modules.events.repository.EventStore.Event;
import com.familyconnect.modules.events.service.EventAccess;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Quyền EVENT do module Events quyết định; Community không JOIN bảng sự kiện. */
@Component
public class EventMediaOwnerPolicy implements MediaOwnerPolicy {
    private final EventStore events;
    private final EventAccess access;

    public EventMediaOwnerPolicy(EventStore events, EventAccess access) {
        this.events = events;
        this.access = access;
    }

    @Override public String ownerType() { return "EVENT"; }

    private Event readable(UUID id, UUID family) {
        Event found = events.findEvent(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        if (family != null && !family.equals(found.familyId())) throw new ApiException(ErrorCode.PERM_001);
        access.requireRead(access.actor(),found.familyId(),found.branchId());
        return found;
    }

    @Override public UUID requireUploadFamily(UUID ownerId) {
        return readable(ownerId,null).familyId();
    }

    @Override public void requireRead(UUID ownerId, UUID familyId) {
        readable(ownerId,familyId);
    }

    @Override public void requireDelete(UUID mediaId, UUID ownerId, UUID familyId, boolean uploader) {
        Event found = readable(ownerId,familyId);
        if (!uploader) access.requireManage(access.actor(),found);
    }
}
