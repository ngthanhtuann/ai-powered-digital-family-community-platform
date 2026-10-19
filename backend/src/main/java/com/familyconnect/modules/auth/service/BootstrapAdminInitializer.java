package com.familyconnect.modules.auth.service;

import com.familyconnect.modules.auth.entity.Role;
import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.entity.UserRole;
import com.familyconnect.modules.auth.repository.RoleRepository;
import com.familyconnect.modules.auth.repository.UserRepository;
import com.familyconnect.modules.auth.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * BR-AUTH-20: khi chưa có SYSTEM_ADMIN và có BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD
 * thì tạo tài khoản quản trị đầu tiên. Không có mật khẩu mặc định trong mã nguồn hay migration.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);
    /** Ngày sinh giữ chỗ cho tài khoản kỹ thuật; quản trị tự sửa ở trang hồ sơ. */
    private static final LocalDate PLACEHOLDER_BIRTH_DATE = LocalDate.of(2000, 1, 1);

    private final String email;
    private final String password;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(@Value("${app.auth.bootstrap-admin.email:}") String email,
                                     @Value("${app.auth.bootstrap-admin.password:}") String password,
                                     UserRepository userRepository, RoleRepository roleRepository,
                                     UserRoleRepository userRoleRepository, PasswordEncoder passwordEncoder) {
        this.email = email;
        this.password = password;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank() || userRoleRepository.existsByRoleCode(Role.SYSTEM_ADMIN)) {
            return;
        }
        if (password.length() < 8 || password.length() > 72) {
            log.warn("Bỏ qua tạo quản trị viên đầu tiên: BOOTSTRAP_ADMIN_PASSWORD phải từ 8 đến 72 ký tự");
            return;
        }
        String normalized = AuthService.normalizeEmail(email);
        User admin = userRepository.findActiveByEmail(normalized).orElseGet(() -> userRepository.save(
                new User(normalized, passwordEncoder.encode(password), "Quản trị hệ thống", null,
                        PLACEHOLDER_BIRTH_DATE)));
        Role memberRole = roleRepository.findByCode(Role.MEMBER).orElseThrow();
        Role adminRole = roleRepository.findByCode(Role.SYSTEM_ADMIN).orElseThrow();
        var current = userRoleRepository.findRoleCodes(admin.getId());
        if (!current.contains(Role.MEMBER)) {
            userRoleRepository.save(new UserRole(admin.getId(), memberRole));
        }
        userRoleRepository.save(new UserRole(admin.getId(), adminRole));
        log.info("Đã tạo quản trị viên đầu tiên từ BOOTSTRAP_ADMIN_EMAIL");
    }
}
