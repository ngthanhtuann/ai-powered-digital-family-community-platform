package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.TreeData;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.TreeEdge;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.TreeNode;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.FamilyRepository;
import com.familyconnect.modules.genealogy.repository.FamilyTreeRepository;
import com.familyconnect.modules.genealogy.repository.FamilyTreeRepository.EdgeRow;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import com.familyconnect.modules.genealogy.service.GenealogyAccess.Viewer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * UC-GEN-06 cây gia phả cho React Flow: hậu duệ của người gốc trong {@code depth} đời, kèm vợ/chồng của họ.
 * Lazy-load bằng cách gọi lại với rootId là node có hasMoreChildren. Ẩn dữ liệu theo BR-GEN-10.
 */
@Service
public class FamilyTreeService {

    static final int DEFAULT_DEPTH = 3;
    static final int MAX_DEPTH = 10;
    private static final int CHILD_AGE = 16;

    private final FamilyTreeRepository treeRepository;
    private final PersonRepository personRepository;
    private final FamilyRepository familyRepository;
    private final GenealogyAccess access;
    private final Clock clock;

    public FamilyTreeService(FamilyTreeRepository treeRepository, PersonRepository personRepository,
                             FamilyRepository familyRepository, GenealogyAccess access, Clock clock) {
        this.treeRepository = treeRepository;
        this.personRepository = personRepository;
        this.familyRepository = familyRepository;
        this.access = access;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TreeData tree(UUID familyId, UUID rootId, Integer depth) {
        Viewer viewer = access.requireRead(familyId);
        int levels = depth == null ? DEFAULT_DEPTH : depth;
        if (levels < 1 || levels > MAX_DEPTH) {
            throw ApiException.invalidField("depth", "depth phải từ 1 đến " + MAX_DEPTH);
        }
        if (familyRepository.findByIdAndDeletedAtIsNull(familyId).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND_001);
        }
        UUID root = rootId != null ? requireInFamily(rootId, familyId) : defaultRoot(familyId, viewer);
        if (root == null) {
            return new TreeData(null, List.of(), List.of()); // UC-GEN-06 2a: gia đình chưa có thành viên
        }

        Map<UUID, Integer> descendants = treeRepository.findDescendants(familyId, root, levels);
        Set<UUID> ids = new HashSet<>(descendants.keySet());
        ids.addAll(treeRepository.findSpouses(familyId, descendants.keySet()));
        // Còn nhánh chưa tải: người ở đời cuối mà vẫn có con.
        Set<UUID> lastLevel = new HashSet<>();
        descendants.forEach((id, level) -> { if (level == levels - 1) lastLevel.add(id); });
        Set<UUID> hasMore = lastLevel.isEmpty() ? Set.of() : treeRepository.findWithChildren(familyId, lastLevel);
        Map<UUID, Integer> generations = treeRepository.findGenerations(familyId, ids);

        boolean fullData = viewer.seesFullData(familyId);
        LocalDate today = LocalDate.now(clock);
        List<TreeNode> nodes = personRepository.findAllById(ids).stream()
                .filter(p -> p.getDeletedAt() == null && familyId.equals(p.getFamilyId()))
                .sorted(Comparator.comparing((Person p) -> descendants.getOrDefault(p.getId(), Integer.MAX_VALUE))
                        .thenComparing(Person::getFullName))
                .map(p -> toNode(p, fullData, today, hasMore.contains(p.getId()), generations.get(p.getId())))
                .toList();

        List<TreeEdge> edges = new ArrayList<>();
        for (EdgeRow row : treeRepository.findParentEdges(ids)) {
            edges.add(new TreeEdge(row.id().toString(), row.source(), row.target(), "PARENT_CHILD", row.detail(), null));
        }
        for (EdgeRow row : treeRepository.findMarriageEdges(ids)) {
            edges.add(new TreeEdge(row.id().toString(), row.source(), row.target(), "MARRIAGE", null, row.detail()));
        }
        return new TreeData(root, nodes, edges);
    }

    /** genealogy.yaml: mặc định là hồ sơ gắn với tài khoản hiện tại, nếu không có thì người tổ. */
    private UUID defaultRoot(UUID familyId, Viewer viewer) {
        return personRepository.findByUserIdAndDeletedAtIsNull(viewer.userId())
                .filter(p -> familyId.equals(p.getFamilyId()))
                .map(Person::getId)
                .orElseGet(() -> treeRepository.findTopAncestor(familyId).orElse(null));
    }

    private UUID requireInFamily(UUID personId, UUID familyId) {
        return personRepository.findByIdAndDeletedAtIsNull(personId)
                .filter(p -> familyId.equals(p.getFamilyId()))
                .map(Person::getId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
    }

    /** BR-GEN-10: MEMBER thấy trẻ dưới 16 tuổi còn sống chỉ với tên, quan hệ (cạnh), năm sinh. */
    private static TreeNode toNode(Person p, boolean fullData, LocalDate today, boolean hasMoreChildren,
                                   Integer generation) {
        Integer birthYear = p.getBirthDate() != null ? Integer.valueOf(p.getBirthDate().getYear())
                : p.getBirthYear() == null ? null : p.getBirthYear().intValue();
        boolean child = !p.isDeceased() && p.getBirthDate() != null
                && p.getBirthDate().plusYears(CHILD_AGE).isAfter(today);
        boolean limited = child && !fullData;
        return new TreeNode(p.getId(), p.getFullName(), limited ? null : p.getGender(), birthYear, p.isDeceased(),
                hasMoreChildren, limited ? null : generation);
    }
}
