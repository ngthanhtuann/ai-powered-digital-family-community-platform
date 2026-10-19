package com.familyconnect.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Request body của module auth, khớp schema trong docs/04-api/openapi/auth.yaml. */
public final class AuthRequests {

    private AuthRequests() {
    }

    /** POST /users (RegisterRequest). Tuổi tối thiểu 16 kiểm ở service (BR-AUTH-03). */
    public record Register(
            @NotBlank(message = "Email không được để trống")
            @Email(message = "Email không đúng định dạng")
            @Size(max = 255, message = "Email tối đa 255 ký tự")
            String email,

            @NotNull(message = "Mật khẩu không được để trống")
            @Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự")
            String password,

            @NotBlank(message = "Họ tên không được để trống")
            @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
            String fullName,

            @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$", message = "Số điện thoại không đúng định dạng")
            String phone,

            @NotNull(message = "Ngày sinh không được để trống")
            @Past(message = "Ngày sinh phải trước hôm nay")
            LocalDate dateOfBirth
    ) {
    }

    /** POST /auth/login (LoginRequest). */
    public record Login(
            @NotBlank(message = "Email không được để trống")
            @Email(message = "Email không đúng định dạng")
            String email,

            @NotBlank(message = "Mật khẩu không được để trống")
            String password
    ) {
    }

    /** POST /auth/refresh (RefreshRequest). */
    public record Refresh(
            @NotBlank(message = "Thiếu refresh token")
            String refreshToken
    ) {
    }

    /** PUT /users/me/password (ChangePasswordRequest). */
    public record ChangePassword(
            @NotBlank(message = "Mật khẩu hiện tại không được để trống")
            String currentPassword,

            @NotNull(message = "Mật khẩu mới không được để trống")
            @Size(min = 8, max = 72, message = "Mật khẩu mới từ 8 đến 72 ký tự")
            String newPassword
    ) {
    }
}
