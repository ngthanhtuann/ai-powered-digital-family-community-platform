package com.familyconnect.common.util;

/**
 * Mẫu LIKE "chứa" dùng chung cho các tìm kiếm có {@code escape '!'}: ký tự {@code !}, {@code %}, {@code _} người
 * dùng gõ được hiểu theo nghĩa đen, không thành ký tự đại diện.
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    /** {@code "%" + giá trị đã escape + "%"}; null giữ null để truy vấn bỏ qua điều kiện. */
    public static String contains(String value) {
        if (value == null) return null;
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
