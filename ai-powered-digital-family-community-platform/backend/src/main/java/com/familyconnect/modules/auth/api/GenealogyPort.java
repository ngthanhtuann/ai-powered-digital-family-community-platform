package com.familyconnect.modules.auth.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng ra do module auth khai báo, module Gia phả (TV2, SCRUM-37) cài đặt (auth.md mục 6.3b).
 * Khi chưa có bản cài đặt, auth vẫn chạy nhưng không hiện được tên gia đình, tên chi.
 */
public interface GenealogyPort {

    /** Rỗng nếu gia đình không tồn tại. */
    Optional<String> findFamilyName(UUID familyId);

    Optional<String> findBranchName(UUID familyId, UUID branchId);

    /** branchId là chính ancestorBranchId hoặc chi con của nó. */
    boolean isBranchWithin(UUID familyId, UUID branchId, UUID ancestorBranchId);

    /** Đặt person.user_id = NULL ở hồ sơ đang gắn tài khoản này (nếu có). */
    void unlinkAccount(UUID userId);

    /** Đặt person.user_id; person phải thuộc familyId (BR-AUTH-11); ném lỗi GEN_xxx nếu không hợp lệ. */
    void linkAccount(UUID familyId, UUID personId, UUID userId);
}
