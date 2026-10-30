package com.familyconnect.modules.genealogy.service;

import com.familyconnect.modules.genealogy.dto.FamilyTreeResponse;
import com.familyconnect.modules.genealogy.dto.FamilyTreeResponse.Edge;
import com.familyconnect.modules.genealogy.dto.FamilyTreeResponse.Node;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GenealogyTreeService {

    private final EntityManager entityManager;

    public GenealogyTreeService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    private String loadSqlFromFile() {
        try (InputStream is = getClass().getResourceAsStream("/sql/tree.sql");
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "SELECT id, first_name, last_name, birth_date, is_deceased, address, avatar_media_id, 0 as lvl FROM person WHERE id = :rootId";
        }
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public FamilyTreeResponse getTree(UUID familyId, UUID rootId, int depth, String userRole) {
        if (rootId == null) {
            String findRootSql = "SELECT id FROM person WHERE family_id = :familyId AND deleted_at IS NULL AND id NOT IN (SELECT child_id FROM parent_child WHERE deleted_at IS NULL) ORDER BY birth_date ASC LIMIT 1";
            List<UUID> roots = entityManager.createNativeQuery(findRootSql).setParameter("familyId", familyId).getResultList();
            if (roots.isEmpty()) {
                return new FamilyTreeResponse(Collections.emptyList(), Collections.emptyList());
            }
            rootId = roots.get(0);
        }

        String cteSql = loadSqlFromFile();
        Query query = entityManager.createNativeQuery(cteSql);
        query.setParameter("rootId", rootId);
        query.setParameter("depth", depth);
        List<Object[]> personRows = query.getResultList();

        List<Node> nodes = new ArrayList<>();
        List<UUID> collectedIds = new ArrayList<>();
        LocalDate now = LocalDate.now();

        for (Object[] row : personRows) {
            UUID id = (UUID) row[0];
            String firstName = (String) row[1];
            String lastName = (String) row[2];
            java.sql.Date birthDateSql = (java.sql.Date) row[3];
            boolean isDeceased = (Boolean) row[4];
            String address = (String) row[5];
            UUID avatarId = (UUID) row[6];
            int nodeLvl = ((Number) row[7]).intValue();

            collectedIds.add(id);

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("generation", nodeLvl);
            dataMap.put("isDeceased", isDeceased);
            dataMap.put("avatarMediaId", avatarId);

            LocalDate birthDate = birthDateSql != null ? birthDateSql.toLocalDate() : null;
            boolean isUnder16 = birthDate != null && birthDate.plusYears(16).isAfter(now);

            if ("MEMBER".equalsIgnoreCase(userRole) && !isDeceased) {
                if (isUnder16) {
                    dataMap.put("name", lastName + " " + firstName);
                    dataMap.put("birthYear", birthDate != null ? birthDate.getYear() : null);
                    dataMap.put("address", "Ẩn thông tin");
                } else {
                    dataMap.put("name", lastName + " " + firstName);
                    dataMap.put("birthDate", birthDate);
                    dataMap.put("address", "Ẩn thông tin");
                }
            } else {
                dataMap.put("name", lastName + " " + firstName);
                dataMap.put("birthDate", birthDate);
                dataMap.put("address", address);
            }

            nodes.add(new Node(id.toString(), dataMap));
        }

        if (collectedIds.isEmpty()) {
            return new FamilyTreeResponse(nodes, Collections.emptyList());
        }

        List<Edge> edges = new ArrayList<>();

        String bloodEdgesSql = "SELECT id, parent_id, child_id FROM parent_child WHERE child_id IN (:ids) AND deleted_at IS NULL";
        List<Object[]> bloodRows = entityManager.createNativeQuery(bloodEdgesSql).setParameter("ids", collectedIds).getResultList();

        for (Object[] row : bloodRows) {
            edges.add(new Edge(row[0].toString(), row[1].toString(), row[2].toString(), "blood"));
        }

        String marriageEdgesSql = "SELECT id, person1_id, person2_id FROM marriage WHERE person1_id IN (:ids) AND person2_id IN (:ids) AND deleted_at IS NULL";
        List<Object[]> marriageRows = entityManager.createNativeQuery(marriageEdgesSql).setParameter("ids", collectedIds).getResultList();

        for (Object[] row : marriageRows) {
            edges.add(new Edge(row[0].toString(), row[1].toString(), row[2].toString(), "marriage"));
        }

        return new FamilyTreeResponse(nodes, edges);
    }
}

