package com.familyconnect.modules.genealogy.api;

import java.util.Optional;
import java.util.UUID;

/** Dữ liệu của module Gia phả cho module khác đọc (genealogy.md mục 9: GenealogyQuery). */
public interface GenealogyQuery {

    /** Gia đình của một thành viên; rỗng nếu không tồn tại hoặc đã xóa mềm. Heritage dùng để kiểm tra related_person_id. */
    Optional<UUID> findPersonFamilyId(UUID personId);
}
