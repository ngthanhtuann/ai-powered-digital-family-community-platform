package com.familyconnect.modules.auth.api;

import java.util.UUID;

/**
 * Cổng ra do auth khai báo (BR-AUTH-25, SCRUM-140): khi thay tài khoản cho hồ sơ, mỗi module có nội dung công khai chuyển
 * người tạo từ tài khoản cũ sang tài khoản mới trên bảng của chính mình; trùng (một người một bản) thì giữ bản của tài khoản mới.
 */
public interface AccountContentTransfer {

    void transferContent(UUID fromUserId, UUID toUserId);
}
