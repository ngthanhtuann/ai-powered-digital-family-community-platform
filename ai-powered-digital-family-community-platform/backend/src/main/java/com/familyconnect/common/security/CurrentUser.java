package com.familyconnect.common.security;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Lấy người đang đăng nhập từ SecurityContext. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<AuthenticatedUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** Ném 401 AUTH_002 nếu chưa đăng nhập. */
    public static AuthenticatedUser require() {
        return find().orElseThrow(() -> new ApiException(ErrorCode.AUTH_002));
    }
}
