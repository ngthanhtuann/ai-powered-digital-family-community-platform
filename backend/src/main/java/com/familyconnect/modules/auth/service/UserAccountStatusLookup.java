package com.familyconnect.modules.auth.service;

import com.familyconnect.common.security.AccountStatusLookup;
import com.familyconnect.modules.auth.entity.UserStatus;
import com.familyconnect.modules.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Cài AccountStatusLookup cho bộ lọc JWT: tài khoản bị khóa bị chặn ngay ở request kế tiếp. */
@Service
public class UserAccountStatusLookup implements AccountStatusLookup {

    private final UserRepository userRepository;

    public UserAccountStatusLookup(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AccountState find(UUID userId) {
        return userRepository.findStatusById(userId)
                .map(status -> status == UserStatus.ACTIVE ? AccountState.ACTIVE : AccountState.LOCKED)
                .orElse(AccountState.NOT_FOUND);
    }
}
