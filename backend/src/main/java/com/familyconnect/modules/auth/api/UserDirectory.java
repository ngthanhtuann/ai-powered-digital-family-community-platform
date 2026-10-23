package com.familyconnect.modules.auth.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Tra tên hiển thị của người dùng cho module khác (auth.md mục 6.3a). */
public interface UserDirectory {

    /**
     * Trả họ tên theo id, chỉ gồm tài khoản chưa xóa; id không có trong kết quả thì module gọi tự hiển thị
     * tên thay thế. Không trả email, số điện thoại.
     */
    Map<UUID, String> getFullNames(Collection<UUID> userIds);
}
