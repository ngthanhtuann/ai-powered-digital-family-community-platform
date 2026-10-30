package com.familyconnect.modules.directory.api;

import java.util.Optional;
import java.util.UUID;

/** Dữ liệu của module Directory cho module khác đọc (heritage-directory-dashboard.md mục 5), không join bảng. */
public interface DirectoryQuery {

    /** Chức danh trong hồ sơ nghề nghiệp đang hoạt động; rỗng nếu chưa có. Gia phả dùng để dựng chunk PERSON cho AI. */
    Optional<String> findJobTitle(UUID personId);
}
