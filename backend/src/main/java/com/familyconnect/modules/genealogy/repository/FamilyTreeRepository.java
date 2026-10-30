package com.familyconnect.modules.genealogy.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Truy vấn cây gia phả (UC-GEN-06): chỉ trong một gia đình, bỏ bản ghi đã xóa mềm, số truy vấn cố định mỗi lần tải. */
@Repository
public class FamilyTreeRepository {

    public record EdgeRow(UUID id, UUID source, UUID target, String detail) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public FamilyTreeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Người tổ: người không có cha mẹ trong gia đình, sinh sớm nhất (UC-GEN-06 bước 2). */
    public Optional<UUID> findTopAncestor(UUID familyId) {
        return jdbc.queryForList("""
                SELECT p.id FROM person p
                WHERE p.family_id = :familyId AND p.deleted_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM parent_child pc JOIN person parent ON parent.id = pc.parent_id
                                  WHERE pc.child_id = p.id AND pc.deleted_at IS NULL
                                    AND parent.family_id = :familyId AND parent.deleted_at IS NULL)
                ORDER BY COALESCE(EXTRACT(YEAR FROM p.birth_date)::int, p.birth_year) NULLS LAST, p.created_at, p.id
                LIMIT 1
                """, new MapSqlParameterSource("familyId", familyId), UUID.class).stream().findFirst();
    }

    /** Hậu duệ của gốc, tối đa {@code depth} đời tính cả gốc: id -> cấp (gốc là 0). */
    public Map<UUID, Integer> findDescendants(UUID familyId, UUID rootId, int depth) {
        Map<UUID, Integer> levels = new HashMap<>();
        jdbc.query("""
                WITH RECURSIVE tree(id, level) AS (
                    SELECT p.id, 0 FROM person p
                    WHERE p.id = :rootId AND p.family_id = :familyId AND p.deleted_at IS NULL
                    UNION
                    SELECT child.id, tree.level + 1 FROM tree
                    JOIN parent_child pc ON pc.parent_id = tree.id AND pc.deleted_at IS NULL
                    JOIN person child ON child.id = pc.child_id
                        AND child.family_id = :familyId AND child.deleted_at IS NULL
                    WHERE tree.level < :lastLevel
                )
                SELECT id, MIN(level) AS level FROM tree GROUP BY id
                """, new MapSqlParameterSource().addValue("rootId", rootId).addValue("familyId", familyId)
                .addValue("lastLevel", depth - 1), rs -> {
            levels.put(rs.getObject("id", UUID.class), rs.getInt("level"));
        });
        return levels;
    }

    /** Vợ/chồng (mọi trạng thái hôn nhân chưa xóa) của những người đã có trên cây, cùng gia đình. */
    public Set<UUID> findSpouses(UUID familyId, Collection<UUID> ids) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT CASE WHEN m.person1_id IN (:ids) THEN m.person2_id ELSE m.person1_id END
                FROM marriage m
                JOIN person a ON a.id = m.person1_id AND a.family_id = :familyId AND a.deleted_at IS NULL
                JOIN person b ON b.id = m.person2_id AND b.family_id = :familyId AND b.deleted_at IS NULL
                WHERE m.deleted_at IS NULL AND (m.person1_id IN (:ids) OR m.person2_id IN (:ids))
                """, new MapSqlParameterSource().addValue("familyId", familyId).addValue("ids", ids), UUID.class));
    }

    /** Những người trong {@code ids} còn con trong gia đình (để đánh dấu hasMoreChildren). */
    public Set<UUID> findWithChildren(UUID familyId, Collection<UUID> ids) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT pc.parent_id FROM parent_child pc
                JOIN person child ON child.id = pc.child_id AND child.family_id = :familyId AND child.deleted_at IS NULL
                WHERE pc.deleted_at IS NULL AND pc.parent_id IN (:ids)
                """, new MapSqlParameterSource().addValue("familyId", familyId).addValue("ids", ids), UUID.class));
    }

    public List<EdgeRow> findParentEdges(Collection<UUID> ids) {
        return jdbc.query("""
                SELECT id, parent_id, child_id, kind FROM parent_child
                WHERE deleted_at IS NULL AND parent_id IN (:ids) AND child_id IN (:ids)
                """, new MapSqlParameterSource("ids", ids), (rs, row) -> new EdgeRow(rs.getObject("id", UUID.class),
                rs.getObject("parent_id", UUID.class), rs.getObject("child_id", UUID.class), rs.getString("kind")));
    }

    public List<EdgeRow> findMarriageEdges(Collection<UUID> ids) {
        return jdbc.query("""
                SELECT id, person1_id, person2_id, status FROM marriage
                WHERE deleted_at IS NULL AND person1_id IN (:ids) AND person2_id IN (:ids)
                """, new MapSqlParameterSource("ids", ids), (rs, row) -> new EdgeRow(rs.getObject("id", UUID.class),
                rs.getObject("person1_id", UUID.class), rs.getObject("person2_id", UUID.class), rs.getString("status")));
    }

    /** Thế hệ theo hàm chung GenerationSql (genealogy.md mục 6.8). */
    public Map<UUID, Integer> findGenerations(UUID familyId, Collection<UUID> ids) {
        Map<UUID, Integer> generations = new HashMap<>();
        jdbc.query(GenerationSql.CTE + "SELECT person_id, generation FROM generations WHERE person_id IN (:ids)",
                new MapSqlParameterSource().addValue("familyId", familyId).addValue("ids", ids), rs -> {
                    generations.put(rs.getObject("person_id", UUID.class), rs.getInt("generation"));
                });
        return generations;
    }
}
