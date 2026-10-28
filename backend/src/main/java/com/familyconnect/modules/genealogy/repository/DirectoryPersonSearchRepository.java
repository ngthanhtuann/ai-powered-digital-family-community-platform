package com.familyconnect.modules.genealogy.repository;

import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPage;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public interface DirectoryPersonSearchRepository {

    DirectoryPage search(UUID familyId, String location, Integer generation, LocalDate asOfDate,
                         Set<UUID> personIds,
                         boolean excludeChildren, String sortField, boolean ascending, int page, int size);
}
