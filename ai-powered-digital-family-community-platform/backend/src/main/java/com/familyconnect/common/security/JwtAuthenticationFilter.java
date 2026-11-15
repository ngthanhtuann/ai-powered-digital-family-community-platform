package com.familyconnect.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Đọc header "Authorization: Bearer ...", kiểm tra token và trạng thái tài khoản.
 * Token sai hoặc hết hạn: không đăng nhập, endpoint cần đăng nhập sẽ trả 401 AUTH_002.
 * Tài khoản bị khóa: đánh dấu request để entry point trả 403 AUTH_003.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Thuộc tính request báo tài khoản bị khóa (RestAuthenticationEntryPoint đọc). */
    public static final String ACCOUNT_LOCKED_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".LOCKED";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AccountStatusLookup accountStatusLookup;

    public JwtAuthenticationFilter(JwtService jwtService, AccountStatusLookup accountStatusLookup) {
        this.jwtService = jwtService;
        this.accountStatusLookup = accountStatusLookup;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            jwtService.parse(token).ifPresent(user -> {
                switch (accountStatusLookup.find(user.id())) {
                    case ACTIVE -> {
                        var authorities = user.roles().stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                .toList();
                        var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                    case LOCKED -> request.setAttribute(ACCOUNT_LOCKED_ATTRIBUTE, Boolean.TRUE);
                    case NOT_FOUND -> { /* tài khoản đã xóa: coi như token không hợp lệ */ }
                }
            });
        }
        chain.doFilter(request, response);
    }
}
