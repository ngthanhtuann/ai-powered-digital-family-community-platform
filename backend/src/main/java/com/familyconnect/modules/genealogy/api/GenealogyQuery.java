package com.familyconnect.modules.genealogy.api;

import java.util.Optional;
import java.util.UUID;

/** Dữ liệu của module Gia phả cho module khác đọc (genealogy.md mục 9: GenealogyQuery). */
public interface GenealogyQuery {

    /** Gia đình của một thành viên; rỗng nếu thành viên không tồn tại. */
    Optional<UUID> findPersonFamilyId(UUID personId);
}
