package com.familyconnect.modules.genealogy.service;

import com.familyconnect.modules.genealogy.repository.FamilyTreeRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BR-GEN-25 (SCRUM-142): trưởng theo dòng và thứ tự kế nhiệm theo con trưởng, đích tôn thừa trọng, em trai kế.
 * Chỉ tính con trai ruột, xếp theo ngày sinh; không đòi tài khoản.
 */
@Service
public class LineageService {

    public static final String FAMILY_HEAD = "FAMILY_HEAD";
    public static final String BRANCH_HEAD = "BRANCH_HEAD";
    /** Chặn đệ quy bất thường; thuật toán quan hệ cũng xét tối đa 30 đời. */
    private static final int MAX_DEPTH = 30;
    private static final String BORN = "COALESCE(%1$s.birth_date, make_date(%1$s.birth_year::int, 1, 1))";

    private static final String SONS =
            "SELECT c.id, c.is_deceased FROM parent_child pc JOIN person c ON c.id = pc.child_id"
            + " WHERE pc.parent_id = :person AND pc.kind = 'BIOLOGICAL' AND pc.deleted_at IS NULL"
            + " AND c.deleted_at IS NULL AND c.gender = 'MALE'"
            + " ORDER BY " + BORN.formatted("c") + " NULLS LAST, c.full_name, c.id";
    /** Em trai ruột: cùng cha ruột, nam, sinh sau người đang xét. */
    private static final String YOUNGER_BROTHERS =
            "SELECT DISTINCT s.id, s.is_deceased, " + BORN.formatted("s") + " AS born, s.full_name FROM parent_child a"
            + " JOIN person f ON f.id = a.parent_id AND f.gender = 'MALE'"
            + " JOIN parent_child b ON b.parent_id = a.parent_id AND b.child_id <> a.child_id"
            + " AND b.kind = 'BIOLOGICAL' AND b.deleted_at IS NULL"
            + " JOIN person s ON s.id = b.child_id AND s.deleted_at IS NULL AND s.gender = 'MALE'"
            + " JOIN person me ON me.id = a.child_id"
            + " WHERE a.child_id = :person AND a.kind = 'BIOLOGICAL' AND a.deleted_at IS NULL"
            + " AND " + BORN.formatted("s") + " > " + BORN.formatted("me")
            + " ORDER BY born, s.full_name";

    private record Member(UUID id, boolean deceased) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final FamilyTreeRepository treeRepository;

    public LineageService(NamedParameterJdbcTemplate jdbc, FamilyTreeRepository treeRepository) {
        this.jdbc = jdbc;
        this.treeRepository = treeRepository;
    }

    /** Người kế nhiệm khi personId qua đời: dòng con trai (đích tôn thừa trọng), không có thì em trai kế tiếp và dòng của em. */
    public UUID heirOf(UUID personId) {
        UUID heir = lineHeir(personId, 0);
        if (heir != null) {
            return heir;
        }
        for (Member brother : members(YOUNGER_BROTHERS, personId)) {
            if (!brother.deceased()) {
                return brother.id();
            }
            UUID inLine = lineHeir(brother.id(), 0);
            if (inLine != null) {
                return inLine;
            }
        }
        return null;
    }

    /** Nam giới còn sống đầu tiên theo thứ tự trưởng: con cả, dòng con cả (nếu con cả mất), rồi con thứ... */
    UUID lineHeir(UUID personId, int depth) {
        if (depth > MAX_DEPTH) {
            return null;
        }
        for (Member son : members(SONS, personId)) {
            if (!son.deceased()) {
                return son.id();
            }
            UUID heir = lineHeir(son.id(), depth + 1);
            if (heir != null) {
                return heir;
            }
        }
        return null;
    }

    /** Người đang giữ dòng của người gốc (nam): còn sống thì là người gốc, đã mất thì là người kế nhiệm trong dòng. */
    UUID holderOf(UUID originId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT is_deceased FROM person WHERE id = :id AND deleted_at IS NULL AND gender = 'MALE'", Map.of("id", originId));
        if (rows.isEmpty()) {
            return null;
        }
        return Boolean.TRUE.equals(rows.get(0).get("is_deceased")) ? lineHeir(originId, 0) : originId;
    }

    /** person id -> FAMILY_HEAD (giữ dòng người tổ) hoặc BRANCH_HEAD (giữ dòng người lập chi). */
    public Map<UUID, String> lineageHeads(UUID familyId) {
        Map<UUID, String> heads = new HashMap<>();
        List<UUID> origins = jdbc.queryForList(
                "SELECT origin_person_id FROM branch WHERE family_id = :family AND deleted_at IS NULL AND origin_person_id IS NOT NULL",
                Map.of("family", familyId), UUID.class);
        for (UUID origin : origins) {
            UUID holder = holderOf(origin);
            if (holder != null) {
                heads.put(holder, BRANCH_HEAD);
            }
        }
        treeRepository.findTopAncestor(familyId).map(this::holderOf).ifPresent(holder -> heads.put(holder, FAMILY_HEAD));
        return heads;
    }

    private List<Member> members(String sql, UUID personId) {
        return jdbc.query(sql, Map.of("person", personId),
                (rs, i) -> new Member(rs.getObject("id", UUID.class), rs.getBoolean("is_deceased")));
    }
}
