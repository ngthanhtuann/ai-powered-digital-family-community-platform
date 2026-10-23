package com.familyconnect.common.security;

import java.util.UUID;

/**
 * Tra trạng thái tài khoản ở mỗi request để tài khoản bị khóa bị chặn ngay (auth.md mục 4.3, bước 2).
 * Module auth cài đặt.
 */
public interface AccountStatusLookup {

    AccountState find(UUID userId);

    enum AccountState { ACTIVE, LOCKED, NOT_FOUND }
}
