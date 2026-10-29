package com.familyconnect.modules.community.api;

import java.util.UUID;

/** Module sở hữu quyết định quyền trên tài nguyên HERITAGE/PERSON; Community không đọc bảng của module khác. */
public interface MediaOwnerPolicy {
    String ownerType();

    /** Xác nhận tài nguyên và quyền upload, trả family_id thực tế. */
    UUID requireUploadFamily(UUID ownerId);

    /** Xác nhận quyền đọc tệp đã lưu và gia đình của tài nguyên. */
    void requireRead(UUID ownerId, UUID familyId);
}
