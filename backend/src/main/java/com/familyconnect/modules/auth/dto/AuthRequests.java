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

    private static String trim(String value) {
        return value == null ? null : value.trim();
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
        /** BR-AUTH-01: cắt khoảng trắng trước khi kiểm tra định dạng, để " a@b.vn " không bị báo sai. */
        public Register {
            email = trim(email);
        }
    }

    /** POST /auth/login (LoginRequest). */
    public record Login(
            @NotBlank(message = "Email không được để trống")
            @Email(message = "Email không đúng định dạng")
            String email,

            @NotBlank(message = "Mật khẩu không được để trống")
            String password
    ) {
        /** BR-AUTH-01: như Register. */
        public Login {
            email = trim(email);
        }
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

    /**
     * PATCH /users/me (UpdateProfileRequest). Trường không gửi là không đổi. Riêng phone cho phép null (xóa số),
     * nên dùng setter để phân biệt "không gửi" với "gửi null". Ít nhất một trường và tuổi 16 kiểm ở service.
     */
    public static final class UpdateProfile {
        @Pattern(regexp = "(?s).*\\S.*", message = "Họ tên không được để trống")
        @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
        private String fullName;

        @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$", message = "Số điện thoại không đúng định dạng")
        private String phone;
        private boolean phoneSent;

        @Past(message = "Ngày sinh phải trước hôm nay")
        private LocalDate dateOfBirth;

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public void setPhone(String phone) {
            this.phone = phone;
            this.phoneSent = true;
        }

        public void setDateOfBirth(LocalDate dateOfBirth) {
            this.dateOfBirth = dateOfBirth;
        }

        public String fullName() {
            return fullName;
        }

        public String phone() {
            return phone;
        }

        public boolean phoneSent() {
            return phoneSent;
        }

        public LocalDate dateOfBirth() {
            return dateOfBirth;
        }

        public boolean nothingSent() {
            return fullName == null && !phoneSent && dateOfBirth == null;
        }
    }
}
