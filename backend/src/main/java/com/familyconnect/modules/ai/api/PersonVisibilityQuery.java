package com.familyconnect.modules.ai.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Privacy-filtered person IDs supplied by genealogy without exposing its tables to AI. */
public interface PersonVisibilityQuery {
    List<UUID> findMinorPersonIds(UUID familyId, LocalDate asOfDate);
}
