package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/** Phân trang theo conventions.md: page từ 0, size 1..100, sort "trườngJSON,asc|desc". */
final class Paging {

    private Paging() {
    }

    static Pageable of(int page, int size, String sort, Set<String> allowedFields) {
        if (page < 0) {
            throw ApiException.invalidField("page", "page phải bắt đầu từ 0");
        }
        if (size < 1 || size > 100) {
            throw ApiException.invalidField("size", "size phải từ 1 đến 100");
        }
        String[] parts = (sort == null || sort.isBlank() ? "createdAt,desc" : sort).split(",", -1);
        if (parts.length != 2 || !allowedFields.contains(parts[0])
                || !(parts[1].equalsIgnoreCase("asc") || parts[1].equalsIgnoreCase("desc"))) {
            throw ApiException.invalidField("sort", "Giá trị sort không hợp lệ");
        }
        Sort.Direction direction = parts[1].equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        // Thêm id để thứ tự ổn định giữa các trang khi giá trị sắp xếp trùng nhau.
        return PageRequest.of(page, size, Sort.by(direction, parts[0]).and(Sort.by(direction, "id")));
    }
}
