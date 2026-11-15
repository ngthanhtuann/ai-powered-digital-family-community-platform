package com.familyconnect.modules.auth.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.common.web.ClientIp;
import com.familyconnect.modules.auth.dto.AuthRequests;
import com.familyconnect.modules.auth.dto.AuthResponses.LoginData;
import com.familyconnect.modules.auth.dto.AuthResponses.TokenData;
import com.familyconnect.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Đăng nhập, làm mới token, đăng xuất (auth.yaml: login, refreshToken, logout). */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(operationId = "login", summary = "Đăng nhập (UC-AUTH-02)")
    public ApiResponse<LoginData> login(@Valid @RequestBody AuthRequests.Login request, HttpServletRequest http) {
        return ApiResponse.ok(authService.login(request, ClientIp.of(http)));
    }

    @PostMapping("/refresh")
    @Operation(operationId = "refreshToken", summary = "Làm mới token (UC-AUTH-03)")
    public ApiResponse<TokenData> refresh(@Valid @RequestBody AuthRequests.Refresh request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @DeleteMapping("/sessions/current")
    @Operation(operationId = "logout", summary = "Đăng xuất (UC-AUTH-03)")
    public ApiResponse<Void> logout() {
        authService.logout(CurrentUser.require().id());
        return ApiResponse.empty();
    }
}
