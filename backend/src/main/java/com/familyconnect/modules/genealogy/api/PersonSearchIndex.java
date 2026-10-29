package com.familyconnect.modules.genealogy.api;

import java.util.UUID;

/**
 * Module Gia phả là chủ nguồn PERSON trong chỉ mục AI (ai.md mục 6.1). Module khác đổi dữ liệu thuộc hồ sơ
 * (ví dụ Directory đổi nghề nghiệp) gọi hàm này để Gia phả dựng lại nội dung, thay vì tự gửi lên AI và ghi đè.
 */
public interface PersonSearchIndex {

    /** Dựng lại chunk PERSON sau khi giao dịch hiện tại commit; bỏ qua nếu hồ sơ không tồn tại. */
    void refresh(UUID personId);
}
