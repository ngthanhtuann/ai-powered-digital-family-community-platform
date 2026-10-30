package com.familyconnect.modules.genealogy.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Dữ liệu của module Gia phả cho module khác đọc (genealogy.md mục 9: GenealogyQuery). */
public interface GenealogyQuery {

    /** Gia đình của một thành viên; rỗng nếu không tồn tại hoặc đã xóa mềm. Heritage dùng để kiểm tra related_person_id. */
    Optional<UUID> findPersonFamilyId(UUID personId);

    Optional<DirectoryPerson> findDirectoryPerson(UUID personId);

    Optional<DirectoryPerson> findDirectoryPersonByUserId(UUID userId);

    boolean isParentOf(UUID parentPersonId, UUID childPersonId);

    boolean isBranchWithin(UUID familyId, UUID branchId, UUID ancestorBranchId);

    /** Thành viên còn sống dưới 16 tuổi tại {@code asOfDate} (privacy.md mục 4); AI loại khỏi kết quả của MEMBER. */
    List<UUID> findMinorPersonIds(UUID familyId, LocalDate asOfDate);

    DirectoryPage searchDirectoryPeople(UUID familyId, String location, Integer generation, LocalDate asOfDate,
                                        Set<UUID> personIds, boolean excludeChildren,
                                        String sortField, boolean ascending,
                                        int page, int size);

    record DirectoryPerson(UUID id, UUID familyId, UUID branchId, String fullName, LocalDate birthDate,
                           Integer birthYear, String currentProvince, boolean deceased, UUID userId,
                           Integer generation) {
    }

    record DirectoryPage(List<DirectoryPerson> people, long total) {
        public DirectoryPage {
            people = List.copyOf(people);
        }
    }
}
