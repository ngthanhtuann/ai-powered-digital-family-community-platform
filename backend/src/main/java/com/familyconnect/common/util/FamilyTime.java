package com.familyconnect.common.util;

import java.time.ZoneId;

/**
 * Múi giờ của dòng họ: ngày sinh tính tuổi, thống kê theo tuần, giờ sự kiện gửi AI. Web dùng cùng giá trị
 * ({@code frontend/src/lib/datetime.ts}); truy vấn SQL thống kê trong {@code JdbcCommunityStore} ghi trực tiếp chuỗi này.
 */
public final class FamilyTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private FamilyTime() {
    }
}
