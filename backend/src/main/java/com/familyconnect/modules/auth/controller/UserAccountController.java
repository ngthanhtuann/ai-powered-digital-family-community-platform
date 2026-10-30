package com.familyconnect.modules.auth.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.auth.dto.AuthRequests;
import com.familyconnect.modules.auth.dto.AuthResponses.MembershipData;
import com.familyconnect.modules.auth.dto.AuthResponses.UserData;
import com.familyconnect.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Đăng ký, đổi mật khẩu, xem hồ sơ, trạng thái tham gia gia đình
 * (auth.yaml: registerUser, changePassword, getMyProfile, updateMyProfile, getMyMembership).
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Auth")
public class UserAccountController {

    private final AuthService authService;

    public UserAccountController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "registerUser", summary = "Đăng ký tài khoản (UC-AUTH-01)")
    public ApiResponse<UserData> register(@Valid @RequestBody AuthRequests.Register request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PutMapping("/me/password")
    @Operation(operationId = "changePassword", summary = "Đổi mật khẩu (UC-AUTH-04)")
    public ApiResponse<Void> changePassword(@Valid @RequestBody AuthRequests.ChangePassword request) {
        authService.changePassword(CurrentUser.require().id(), request);
        return ApiResponse.empty();
    }

    @GetMapping("/me")
    @Operation(operationId = "getMyProfile", summary = "Xem hồ sơ của tôi (UC-AUTH-05)")
    public ApiResponse<UserData> myProfile() {
        return ApiResponse.ok(authService.myProfile(CurrentUser.require().id()));
    }

    @PatchMapping("/me")
    @Operation(operationId = "updateMyProfile", summary = "Cập nhật hồ sơ của tôi (UC-AUTH-05)")
    public ApiResponse<UserData> updateMyProfile(@Valid @RequestBody AuthRequests.UpdateProfile request) {
        return ApiResponse.ok(authService.updateProfile(CurrentUser.require().id(), request));
    }

    @GetMapping("/me/membership")
    @Operation(operationId = "getMyMembership", summary = "Trạng thái tham gia gia đình của tôi (UC-AUTH-06)")
    public ApiResponse<MembershipData> myMembership() {
        return ApiResponse.ok(authService.myMembership(CurrentUser.require().id()));
    }
}
