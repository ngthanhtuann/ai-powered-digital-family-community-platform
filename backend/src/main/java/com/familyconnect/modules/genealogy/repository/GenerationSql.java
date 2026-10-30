package com.familyconnect.modules.genealogy.repository;

/**
 * Cách tính thế hệ dùng chung của module Gia phả (genealogy.md mục 6.8): đời 1 là người không có cha mẹ trong gia đình,
 * người có cha mẹ lấy đời lớn nhất của cha mẹ cộng 1, người vào bằng hôn nhân lấy đời của vợ/chồng.
 * Đầu ra là CTE {@code generations(person_id, generation)} theo tham số {@code :familyId}; Danh bạ, cây gia phả và
 * thống kê ghép thêm SELECT phía sau, không viết lại truy vấn đệ quy.
 */
public final class GenerationSql {

    private GenerationSql() {
    }

    public static final String CTE = """
            WITH RECURSIVE ancestors(person_id, ancestor_id, depth) AS (
                SELECT child.id, relation.parent_id, 1
                FROM parent_child relation
                JOIN person child ON child.id = relation.child_id
                    AND child.family_id = :familyId AND child.deleted_at IS NULL
                JOIN person parent ON parent.id = relation.parent_id
                    AND parent.family_id = :familyId AND parent.deleted_at IS NULL
                WHERE relation.deleted_at IS NULL
                UNION
                SELECT ancestry.person_id, relation.parent_id, ancestry.depth + 1
                FROM ancestors ancestry
                JOIN parent_child relation ON relation.child_id = ancestry.ancestor_id
                    AND relation.deleted_at IS NULL
                JOIN person parent ON parent.id = relation.parent_id
                    AND parent.family_id = :familyId AND parent.deleted_at IS NULL
                WHERE ancestry.depth < 100
            ),
            blood AS (
                SELECT person_id, MAX(depth) + 1 AS generation
                FROM ancestors
                GROUP BY person_id
            ),
            -- genealogy.md mục 6.8: người không có cha mẹ trong gia đình nhưng có vợ/chồng thì lấy đời của vợ/chồng
            generations AS (
                SELECT p.id AS person_id,
                       COALESCE(blood.generation,
                                (SELECT MAX(spouse.generation)
                                 FROM marriage m
                                 JOIN blood spouse ON spouse.person_id =
                                      CASE WHEN m.person1_id = p.id THEN m.person2_id ELSE m.person1_id END
                                 WHERE m.deleted_at IS NULL AND (m.person1_id = p.id OR m.person2_id = p.id)),
                                1) AS generation
                FROM person p
                LEFT JOIN blood ON blood.person_id = p.id
                WHERE p.family_id = :familyId AND p.deleted_at IS NULL
            )
            """;
}
