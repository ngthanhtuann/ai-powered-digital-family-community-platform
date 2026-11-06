package com.familyconnect.modules.genealogy.service;

import com.familyconnect.modules.genealogy.dto.RelationshipResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class KinshipService {

    private final EntityManager entityManager;

    public KinshipService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public RelationshipResponse calculateRelationship(UUID personAId, UUID personBId, String userRole) {
        if (personAId.equals(personBId)) {
            return new RelationshipResponse("Bản thân", "Tôi", "Tôi", Collections.singletonList("Bản thân"));
        }

        // 1. Tìm tất cả tổ tiên của A và B kèm số bước (Distance) bằng SQL Recursive CTE ngắn chống lỗi chuỗi
        String ancestorSql = "WITH RECURSIVE AncestorCTE AS ( " +
                "  SELECT parent_id, child_id, 1 as steps FROM parent_child WHERE child_id = :personId AND deleted_at IS NULL " +
                "  UNION ALL " +
                "  SELECT pc.parent_id, pc.child_id, a.steps + 1 FROM parent_child pc " +
                "  JOIN AncestorCTE a ON pc.child_id = a.parent_id WHERE pc.deleted_at IS NULL " +
                ") SELECT parent_id, MIN(steps) FROM AncestorCTE GROUP BY parent_id";

        Map<UUID, Integer> ancestorsA = getAncestorsMap(ancestorSql, personAId);
        Map<UUID, Integer> ancestorsB = getAncestorsMap(ancestorSql, personBId);

        // 2. Tìm tổ tiên chung gần nhất (LCA)
        UUID commonAncestorId = null;
        int minTotalSteps = Integer.MAX_VALUE;

        for (UUID ancestorId : ancestorsA.keySet()) {
            if (ancestorsB.containsKey(ancestorId)) {
                int totalSteps = ancestorsA.get(ancestorId) + ancestorsB.get(ancestorId);
                if (totalSteps < minTotalSteps) {
                    minTotalSteps = totalSteps;
                    commonAncestorId = ancestorId;
                }
            }
        }

        int stepsA = commonAncestorId != null ? ancestorsA.get(commonAncestorId) : -1;
        int stepsB = commonAncestorId != null ? ancestorsB.get(commonAncestorId) : -1;

        String relation = "Chưa hỗ trợ hoặc quan hệ phức tạp";
        String callA = "Anh/Chị/Em";
        String callB = "Anh/Chị/Em";

        Object[] personAData = getPersonBasicData(personAId);
        Object[] personBData = getPersonBasicData(personBId);
        
        boolean isAOlder = false;
        if (personAData != null && personBData != null) {
            LocalDate birthA = personAData[3] != null ? ((java.sql.Date) personAData[3]).toLocalDate() : LocalDate.MIN;
            LocalDate birthB = personBData[3] != null ? ((java.sql.Date) personBData[3]).toLocalDate() : LocalDate.MIN;
            isAOlder = birthA.isBefore(birthB);
        }

        // 3. Ánh xạ quan hệ cơ bản theo khoảng cách bước đồ thị
        if (stepsA == 1 && stepsB == -1) {
            relation = "Cha/Mẹ ruột"; callA = "Con"; callB = "Cha/Mẹ";
        } else if (stepsA == -1 && stepsB == 1) {
            relation = "Con ruột"; callA = "Cha/Mẹ"; callB = "Con";
        } else if (stepsA == 1 && stepsB == 1) {
            relation = isAOlder ? "Anh/Chị ruột" : "Em ruột";
            callA = isAOlder ? "Anh/Chị" : "Em";
            callB = isAOlder ? "Em" : "Anh/Chị";
        } else if (stepsA == 2 && stepsB == -1) {
            relation = "Ông/Bà nội ngoại"; callA = "Cháu"; callB = "Ông/Bà";
        } else if (stepsA == -1 && stepsB == 2) {
            relation = "Cháu nội ngoại"; callA = "Ông/Bà"; callB = "Cháu";
        } else if (stepsA == 2 && stepsB == 1) {
            relation = "Chú/Bác/Cô/Dì"; callA = "Cháu"; callB = "Thúc Bá Cô Dì";
        } else if (stepsA == 1 && stepsB == 2) {
            relation = "Cháu (Gọi bằng Chú/Bác/Cô/Dì)"; callA = "Chú/Bác/Cô/Dì"; callB = "Cháu";
        }

        // 4. Kiểm tra luật bảo mật trẻ em dưới 16 tuổi đối với MEMBER
        List<String> pathList = new ArrayList<>();
        pathList.add(buildNodeName(personAData, userRole));
        pathList.add(buildNodeName(personBData, userRole));

        return new RelationshipResponse(relation, callA, callB, pathList);
    }

    private Map<UUID, Integer> getAncestorsMap(String sql, UUID personId) {
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("personId", personId);
        List<Object[]> rows = query.getResultList();
        Map<UUID, Integer> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((UUID) r[0], ((Number) r[1]).intValue());
        }
        return map;
    }

    private Object[] getPersonBasicData(UUID personId) {
        String sql = "SELECT id, first_name, last_name, birth_date, is_deceased FROM person WHERE id = :id";
        List<Object[]> result = entityManager.createNativeQuery(sql).setParameter("id", personId).getResultList();
        return result.isEmpty() ? null : result.get(0);
    }

    private String buildNodeName(Object[] personData, String userRole) {
        if (personData == null) return "Ẩn thông tin";
        String firstName = (String) personData[1];
        String lastName = (String) personData[2];
        java.sql.Date birthSql = (java.sql.Date) personData[3];
        boolean isDeceased = (Boolean) personData[4];

        if ("MEMBER".equalsIgnoreCase(userRole) && !isDeceased && birthSql != null) {
            LocalDate birthDate = birthSql.toLocalDate();
            if (birthDate.plusYears(16).isAfter(LocalDate.now())) {
                return "Người dưới 16 tuổi (MEMBER ẩn tên)";
            }
        }
        return lastName + " " + firstName;
    }
}

