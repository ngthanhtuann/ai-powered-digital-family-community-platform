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

    private static final String SEED = "/*seed*/";

    /** Tính đời cho cả gia đình (Danh bạ, Dashboard). */
    public static final String CTE = template("");

    /**
     * Chỉ tính đời cho những người trong tham số {@code :ids} (cây gia phả). Đời của một người chỉ phụ thuộc tổ tiên
     * của người đó và của vợ/chồng, nên chỉ cần đi lên từ {@code :ids} cùng vợ/chồng của họ; kết quả cho {@code :ids}
     * giống {@link #CTE} nhưng không phải duyệt cả gia đình mỗi lần tải một nhánh (SCRUM-59).
     */
    public static final String CTE_FOR_IDS = template("""
             AND (relation.child_id IN (:ids) OR relation.child_id IN (
                    SELECT CASE WHEN m.person1_id IN (:ids) THEN m.person2_id ELSE m.person1_id END
                    FROM marriage m
                    WHERE m.deleted_at IS NULL AND (m.person1_id IN (:ids) OR m.person2_id IN (:ids))))""");

    private static String template(String seedFilter) {
        return TEMPLATE.replace(SEED, seedFilter);
    }

    private static final String TEMPLATE = """
            WITH RECURSIVE ancestors(person_id, ancestor_id, depth) AS (
                SELECT child.id, relation.parent_id, 1
                FROM parent_child relation
                JOIN person child ON child.id = relation.child_id
                    AND child.family_id = :familyId AND child.deleted_at IS NULL
                JOIN person parent ON parent.id = relation.parent_id
                    AND parent.family_id = :familyId AND parent.deleted_at IS NULL
                WHERE relation.deleted_at IS NULL /*seed*/
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
            -- genealogy.md mục 6.8: người không có cha mẹ trong gia đình nhưng có vợ/chồng thì lấy đời của vợ/chồng.
            -- Gom một lần bằng JOIN; subquery tương quan cũ quét lại cả CTE blood cho mỗi người (SCRUM-59:
            -- 5.000 người mất khoảng 950 ms, cách này khoảng 120 ms, cùng kết quả).
            spouse_generation AS (
                SELECT spouse_id AS person_id, MAX(generation) AS generation
                FROM (
                    SELECT m.person2_id AS spouse_id, blood.generation
                    FROM marriage m JOIN blood ON blood.person_id = m.person1_id
                    WHERE m.deleted_at IS NULL
                    UNION ALL
                    SELECT m.person1_id, blood.generation
                    FROM marriage m JOIN blood ON blood.person_id = m.person2_id
                    WHERE m.deleted_at IS NULL
                ) spouse_sides
                GROUP BY spouse_id
            ),
            generations AS (
                SELECT p.id AS person_id,
                       COALESCE(blood.generation, spouse_generation.generation, 1) AS generation
                FROM person p
                LEFT JOIN blood ON blood.person_id = p.id
                LEFT JOIN spouse_generation ON spouse_generation.person_id = p.id
                WHERE p.family_id = :familyId AND p.deleted_at IS NULL
            )
            """;
}
