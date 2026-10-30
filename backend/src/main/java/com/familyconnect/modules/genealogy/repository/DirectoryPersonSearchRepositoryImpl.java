package com.familyconnect.modules.genealogy.repository;

import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPage;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPerson;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.hibernate.query.NativeQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class DirectoryPersonSearchRepositoryImpl implements DirectoryPersonSearchRepository {

    private static final String GENERATIONS = GenerationSql.CTE;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public DirectoryPage search(UUID familyId, String location, Integer generation, LocalDate asOfDate,
                                Set<UUID> personIds,
                                boolean excludeChildren, String sortField, boolean ascending, int page, int size) {
        if (personIds != null && personIds.isEmpty()) {
            return new DirectoryPage(List.of(), 0);
        }
        String personFilter = personIds == null ? "" : " AND person.id IN (:personIds) ";
        String where = """
                person.family_id = :familyId
                AND person.deleted_at IS NULL
                AND (CAST(:location AS text) IS NULL
                    OR lower(person.current_province) LIKE lower(concat('%', CAST(:location AS text), '%')))
                AND (CAST(:generation AS integer) IS NULL
                    OR COALESCE(generations.generation, 1) = CAST(:generation AS integer))
                AND (:excludeChildren = FALSE OR person.is_deceased = TRUE
                    OR (person.birth_date IS NOT NULL AND person.birth_date + INTERVAL '16 years' <= :asOfDate)
                    OR (person.birth_date IS NULL AND person.birth_year <= :adultBirthYear))
                """ + personFilter;
        String from = """
                FROM person
                LEFT JOIN generations ON generations.person_id = person.id
                WHERE
                """ + where;

        String orderColumn = switch (sortField) {
            case "fullName" -> "person.full_name";
            case "birthYear" -> "COALESCE(EXTRACT(YEAR FROM person.birth_date)::int, person.birth_year)";
            default -> "person.created_at";
        };
        String direction = ascending ? "ASC" : "DESC";
        String sql = GENERATIONS + """
                SELECT person.id, person.family_id, person.branch_id, person.full_name,
                       person.birth_date, person.birth_year, person.current_province,
                       person.is_deceased, person.user_id, COALESCE(generations.generation, 1)
                """ + from + " ORDER BY " + orderColumn + " " + direction
                + ", person.id " + direction + " LIMIT :limit OFFSET :offset";
        Query dataQuery = entityManager.createNativeQuery(sql);
        setParameters(dataQuery, familyId, location, generation, asOfDate, personIds, excludeChildren);
        dataQuery.setParameter("limit", size);
        dataQuery.setParameter("offset", (long) page * size);

        Query countQuery = entityManager.createNativeQuery(GENERATIONS + "SELECT COUNT(*) " + from);
        setParameters(countQuery, familyId, location, generation, asOfDate, personIds, excludeChildren);
        List<?> rows = dataQuery.getResultList();
        List<DirectoryPerson> people = new ArrayList<>(rows.size());
        for (Object value : rows) {
            Object[] row = (Object[]) value;
            LocalDate birthDate = row[4] instanceof LocalDate date
                    ? date : row[4] instanceof java.sql.Date date ? date.toLocalDate() : null;
            Number year = birthDate == null ? (Number) row[5] : birthDate.getYear();
            people.add(new DirectoryPerson((UUID) row[0], (UUID) row[1], (UUID) row[2],
                    (String) row[3], birthDate, year == null ? null : year.intValue(),
                    (String) row[6], (Boolean) row[7], (UUID) row[8], ((Number) row[9]).intValue()));
        }
        return new DirectoryPage(people, ((Number) countQuery.getSingleResult()).longValue());
    }

    private static void setParameters(Query query, UUID familyId, String location, Integer generation,
                                      LocalDate asOfDate, Set<UUID> personIds, boolean excludeChildren) {
        query.setParameter("familyId", familyId);
        query.setParameter("location", location);
        query.setParameter("generation", generation);
        query.setParameter("asOfDate", asOfDate);
        query.setParameter("adultBirthYear", asOfDate.getYear() - 16);
        query.setParameter("excludeChildren", excludeChildren);
        if (personIds != null) {
            query.unwrap(NativeQuery.class).setParameterList("personIds", personIds);
        }
    }
}
