package com.familyconnect.modules.genealogy.repository;

import com.familyconnect.modules.genealogy.api.GenealogyQuery.DashboardDemographics;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.AgeCount;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.GenerationCount;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.LocationCount;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class DashboardDemographicsRepository {

    private static final String MINOR_CONDITION = """
            (p.birth_date IS NOT NULL AND p.birth_date + INTERVAL '16 years' > :asOfDate)
            OR (p.birth_date IS NULL AND p.birth_year IS NOT NULL
                AND p.birth_year > EXTRACT(YEAR FROM CAST(:asOfDate AS date)) - 16)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final BranchRepository branchRepository;

    public DashboardDemographicsRepository(NamedParameterJdbcTemplate jdbc, BranchRepository branchRepository) {
        this.jdbc = jdbc;
        this.branchRepository = branchRepository;
    }

    public DashboardDemographics get(UUID familyId, UUID branchId, LocalDate asOfDate) {
        List<UUID> branchIds = branchId == null
                ? List.of()
                : branchRepository.findBranchAndDescendants(familyId, branchId);
        if (branchId != null && branchIds.isEmpty()) {
            return new DashboardDemographics(0, List.of(), List.of(), 0, 0, 0,
                    List.of(), List.of(), List.of());
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("familyId", familyId)
                .addValue("asOfDate", asOfDate)
                .addValue("branchIds", branchIds);
        String scope = branchId == null ? "" : " AND p.branch_id IN (:branchIds)";

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM person p WHERE p.family_id=:familyId "
                + "AND p.deleted_at IS NULL" + scope, params, Long.class);

        List<GenerationCount> generations = jdbc.query(GenerationSql.CTE + """
                SELECT g.generation, COUNT(*) AS count
                FROM generations g
                JOIN person p ON p.id = g.person_id
                WHERE p.family_id=:familyId AND p.deleted_at IS NULL
                """ + scope + """
                GROUP BY g.generation
                ORDER BY g.generation
                """, params, (rs, row) -> new GenerationCount(rs.getInt("generation"), rs.getLong("count")));

        List<AgeCount> ages = jdbc.query("""
                SELECT CASE
                    WHEN age < 16 THEN 'Dưới 16'
                    WHEN age < 30 THEN '16-29'
                    WHEN age < 45 THEN '30-44'
                    WHEN age < 60 THEN '45-59'
                    ELSE '60+'
                END AS age_range, COUNT(*) AS count
                FROM (
                    SELECT CASE
                        WHEN p.birth_date IS NOT NULL THEN EXTRACT(YEAR FROM age(
                            CASE WHEN p.is_deceased AND p.death_date IS NOT NULL THEN p.death_date ELSE :asOfDate END,
                            p.birth_date))::int
                        WHEN p.birth_year IS NOT NULL THEN
                            EXTRACT(YEAR FROM CAST(CASE
                                WHEN p.is_deceased AND p.death_date IS NOT NULL THEN p.death_date
                                ELSE :asOfDate
                            END AS date))::int - p.birth_year
                        ELSE NULL
                    END AS age
                    FROM person p
                    WHERE p.family_id=:familyId AND p.deleted_at IS NULL
                """ + scope + """
                ) members
                WHERE age IS NOT NULL AND age >= 0
                GROUP BY age_range
                ORDER BY MIN(age)
                """, params, (rs, row) -> new AgeCount(rs.getString("age_range"), rs.getLong("count")));

        Map<String, Long> genders = jdbc.query("""
                SELECT p.gender, COUNT(*) AS count
                FROM person p
                WHERE p.family_id=:familyId AND p.deleted_at IS NULL
                """ + scope + """
                GROUP BY p.gender
                """, params, (rs, row) -> Map.entry(rs.getString("gender"), rs.getLong("count")))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<LocationCount> locations = jdbc.query("""
                SELECT TRIM(p.current_province) AS location, COUNT(*) AS count
                FROM person p
                WHERE p.family_id=:familyId AND p.deleted_at IS NULL
                  AND NOT p.is_deceased
                  AND NULLIF(TRIM(p.current_province), '') IS NOT NULL
                  AND NOT (
                """ + MINOR_CONDITION + """
                  )
                """ + scope + """
                GROUP BY TRIM(p.current_province)
                ORDER BY count DESC, location
                """, params, (rs, row) -> new LocationCount(rs.getString("location"), rs.getLong("count")));

        List<UUID> professionPersonIds = jdbc.query("""
                SELECT p.id
                FROM person p
                WHERE p.family_id=:familyId AND p.deleted_at IS NULL
                  AND NOT p.is_deceased
                  AND NOT (
                """ + MINOR_CONDITION + """
                  )
                """ + scope + """
                ORDER BY p.id
                """, params, (rs, row) -> rs.getObject("id", UUID.class));

        return new DashboardDemographics(total == null ? 0 : total, generations, ages,
                genders.getOrDefault("MALE", 0L), genders.getOrDefault("FEMALE", 0L),
                genders.getOrDefault("OTHER", 0L), locations, professionPersonIds, branchIds);
    }
}
