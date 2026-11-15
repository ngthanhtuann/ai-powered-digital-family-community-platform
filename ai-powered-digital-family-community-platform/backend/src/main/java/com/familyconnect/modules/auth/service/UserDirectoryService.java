package com.familyconnect.modules.auth.service;

import com.familyconnect.modules.auth.api.UserDirectory;
import com.familyconnect.modules.auth.entity.User;
import com.familyconnect.modules.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserDirectoryService implements UserDirectory {

    private final UserRepository userRepository;

    public UserDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> getFullNames(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        // Một truy vấn cho cả trang dữ liệu, tránh gọi lặp theo từng tác giả.
        return userRepository.findActiveByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }
}
