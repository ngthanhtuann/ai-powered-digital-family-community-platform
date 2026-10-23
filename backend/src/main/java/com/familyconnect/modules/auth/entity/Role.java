package com.familyconnect.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Danh mục vai trò (bảng role). Dữ liệu khởi tạo bằng Flyway V2, không tạo qua API. */
@Entity
@Table(name = "role")
public class Role {

    public static final String MEMBER = "MEMBER";
    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    protected Role() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }
}
