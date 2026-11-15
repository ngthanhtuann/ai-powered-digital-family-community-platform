package com.familyconnect.modules.genealogy.api;

import java.util.Optional;
import java.util.UUID;

public interface GenealogyQuery {

    /**
     * Tìm familyId của một person dựa vào personId.
     * Dùng cho module Heritage để kiểm tra related_person_id thuộc cùng gia đình.
     */
    Optional<UUID> findPersonFamilyId(UUID personId);
}
