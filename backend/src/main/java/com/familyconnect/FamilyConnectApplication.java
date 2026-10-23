package com.familyconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// Xác thực bằng JWT (SCRUM-35) nên tắt user mặc định của Spring Security (tránh in mật khẩu sinh sẵn ra log).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FamilyConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(FamilyConnectApplication.class, args);
    }
}
