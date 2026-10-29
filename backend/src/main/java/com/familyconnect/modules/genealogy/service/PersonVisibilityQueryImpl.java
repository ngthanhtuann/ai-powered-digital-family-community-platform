package com.familyconnect.modules.genealogy.service;

import com.familyconnect.modules.ai.api.PersonVisibilityQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Owns the privacy query against person; the AI module only receives opaque IDs. */
@Service
public class PersonVisibilityQueryImpl implements PersonVisibilityQuery {
    private final JdbcTemplate jdbcTemplate;

    public PersonVisibilityQueryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findMinorPersonIds(UUID familyId, LocalDate asOfDate) {
        return jdbcTemplate.queryForList("""
                SELECT id FROM person
                WHERE family_id = ? AND deleted_at IS NULL AND NOT is_deceased
                  AND COALESCE(birth_date, CASE WHEN birth_year IS NOT NULL THEN make_date(birth_year, 6, 15) END) > ? - INTERVAL '16 years'
                """, UUID.class, familyId, asOfDate);
    }
}
