package com.familyconnect.modules.heritage.api;

import java.util.Optional;
import java.util.UUID;

/** Read-only access to Genealogy people needed by Heritage. */
public interface GenealogyQuery {

    Optional<UUID> findPersonFamilyId(UUID personId);
}
