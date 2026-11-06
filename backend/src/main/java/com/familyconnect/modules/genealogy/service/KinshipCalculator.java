package com.familyconnect.modules.genealogy.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thuật toán "A là gì của B" theo docs/02-design/algorithm-relationship.md mục 3 và 4.
 * Không đọc CSDL trực tiếp mà đi qua {@link Graph}, để chạy được cả trên dữ liệu mẫu của bộ ca kiểm thử.
 */
public final class KinshipCalculator {

    static final int MAX_GENERATIONS = 6;
    private static final String FATHER = "FATHER";
    private static final String DIVORCED = "DIVORCED";
    /** Mục 2.4: vợ/chồng của A là ... của B thì A là ... */
    private static final Map<String, String> IN_LAW = Map.of(
            "bác", "bác", "chú", "thím", "cậu", "mợ", "cô", "dượng", "dì", "dượng");

    public record Person(String id, String fullName, String gender, LocalDate birthDate, Integer birthYear) {
    }

    /** Một cạnh đi lên: cha/mẹ của người đang xét, vai trò FATHER/MOTHER, loại BIOLOGICAL/ADOPTED/STEP. */
    public record ParentLink(String parentId, String role, String kind) {
    }

    public record MarriageLink(String spouseId, String status) {
    }

    public interface Graph {
        Person person(String id);

        List<ParentLink> parentsOf(String id);

        List<MarriageLink> marriagesOf(String id);
    }

    /** Mục 3.1; path là danh sách id từ A lên tổ tiên chung rồi xuống B. */
    public record Result(boolean supported, String relation, Integer stepsFromA, Integer stepsFromB,
                         String commonAncestorId, List<String> path) {
        static Result none() {
            return new Result(false, null, null, null, null, List.of());
        }
    }

    private record Ancestor(int depth, String firstRole, String lastRole, List<String> path, Set<String> kinds) {
    }

    private final Graph graph;

    public KinshipCalculator(Graph graph) {
        this.graph = graph;
    }

    public Result relationship(String a, String b) {
        if (a.equals(b)) {
            return new Result(true, "cùng một người", 0, 0, a, List.of(a));
        }
        Result blood = bloodRelation(a, b);
        return blood != null ? blood : extension(a, b);
    }

    /** Lớp mở rộng qua hôn nhân; chỉ gọi bloodRelation nên không thể đệ quy vô hạn. */
    private Result extension(String a, String b) {
        String gender = graph.person(a).gender();
        List<MarriageLink> marriages = graph.marriagesOf(a);
        MarriageLink direct = marriages.stream().filter(m -> m.spouseId().equals(b))
                .min(Comparator.comparing(m -> DIVORCED.equals(m.status()))) // còn hôn nhân thì ưu tiên hơn đã ly hôn
                .orElse(null);
        if (direct != null) {
            String name = pick(gender, "chồng", "vợ", "vợ/chồng") + (DIVORCED.equals(direct.status()) ? " cũ" : "");
            return new Result(true, name, null, null, null, List.of(a, b));
        }
        for (MarriageLink m : marriages) {
            if (DIVORCED.equals(m.status())) {
                continue;
            }
            Result viaSpouse = bloodRelation(m.spouseId(), b);
            String name = viaSpouse == null || !viaSpouse.supported() ? null : IN_LAW.get(viaSpouse.relation());
            if (name != null) {
                return new Result(true, name, null, null, null, List.of(a, m.spouseId()));
            }
        }
        return Result.none();
    }

    /** null khi A và B không có tổ tiên chung trong 6 đời. */
    private Result bloodRelation(String a, String b) {
        Map<String, Ancestor> ancA = ancestors(a);
        Map<String, Ancestor> ancB = ancestors(b);
        List<String> common = ancA.keySet().stream().filter(ancB::containsKey).toList();
        if (common.isEmpty()) {
            return null;
        }
        String lca = common.stream().min(Comparator
                        .comparingInt((String x) -> ancA.get(x).depth() + ancB.get(x).depth())
                        .thenComparingInt(x -> FATHER.equals(lastRole(x, ancA, ancB)) ? 0 : 1)
                        .thenComparing(Comparator.naturalOrder()))
                .orElseThrow();
        Ancestor fromA = ancA.get(lca);
        Ancestor fromB = ancB.get(lca);
        int sa = fromA.depth();
        int sb = fromB.depth();
        Person pa = graph.person(a);
        Person pb = graph.person(b);
        String elder = pick(pa.gender(), "ông ", "bà ", "ông/bà ");
        String name = switch (sa + "," + sb) {
            case "0,1" -> pick(pa.gender(), "cha", "mẹ", "cha/mẹ");
            case "1,0" -> "con";
            case "1,1" -> siblingName(pa, pb, common, ancA, ancB, lca);
            case "0,2" -> elder + side(fromB.firstRole());
            case "2,0" -> "cháu " + side(fromA.firstRole());
            case "0,3" -> elder + "cố " + side(fromB.firstRole());
            case "3,0" -> "chắt " + side(fromA.firstRole());
            case "2,1" -> "cháu";
            case "1,2" -> uncleAuntName(pa, fromB.firstRole(), graph.person(fromB.path().get(1)));
            case "2,2" -> cousinName(pa, pb, fromB.firstRole());
            default -> null;
        };
        if (name == null) {
            return new Result(false, null, sa, sb, lca, List.of());
        }
        Set<String> kinds = new HashSet<>(fromA.kinds());
        kinds.addAll(fromB.kinds());
        String tag = kinds.contains("ADOPTED") ? " (nuôi)" : kinds.contains("STEP") ? " (kế)" : "";
        List<String> path = new ArrayList<>(fromA.path());
        List<String> down = new ArrayList<>(fromB.path());
        Collections.reverse(down);
        path.addAll(down.subList(1, down.size()));
        return new Result(true, name + tag, sa, sb, lca, List.copyOf(path));
    }

    /** BFS lên tối đa 6 đời, cha trước mẹ; tổ tiên gặp ở độ sâu nhỏ hơn thì giữ nguyên. */
    private Map<String, Ancestor> ancestors(String p) {
        Map<String, Ancestor> result = new HashMap<>();
        result.put(p, new Ancestor(0, null, null, List.of(p), Set.of()));
        List<String> frontier = List.of(p);
        for (int d = 1; d <= MAX_GENERATIONS && !frontier.isEmpty(); d++) {
            List<String> next = new ArrayList<>();
            for (String x : frontier) {
                Ancestor from = result.get(x);
                List<ParentLink> parents = graph.parentsOf(x).stream()
                        .sorted(Comparator.comparing((ParentLink l) -> !FATHER.equals(l.role()))
                                .thenComparing(ParentLink::parentId))
                        .toList();
                for (ParentLink link : parents) {
                    if (result.containsKey(link.parentId())) {
                        continue;
                    }
                    List<String> path = new ArrayList<>(from.path());
                    path.add(link.parentId());
                    Set<String> kinds = new HashSet<>(from.kinds());
                    kinds.add(link.kind());
                    String first = from.firstRole() == null ? link.role() : from.firstRole();
                    result.put(link.parentId(), new Ancestor(d, first, link.role(), List.copyOf(path), Set.copyOf(kinds)));
                    next.add(link.parentId());
                }
            }
            frontier = next;
        }
        return result;
    }

    /** Vai trò ở bước cuối vào x, tính phía A; nếu A chính là x thì tính phía B. */
    private static String lastRole(String x, Map<String, Ancestor> ancA, Map<String, Ancestor> ancB) {
        return ancA.get(x).depth() == 0 ? ancB.get(x).lastRole() : ancA.get(x).lastRole();
    }

    private static String siblingName(Person a, Person b, List<String> common, Map<String, Ancestor> ancA,
                                      Map<String, Ancestor> ancB, String lca) {
        Boolean o = older(a, b);
        String base = o == null ? "anh/chị/em" : o ? pick(a.gender(), "anh", "chị", "anh/chị") : "em";
        long sharedParents = common.stream().filter(x -> ancA.get(x).depth() == 1 && ancB.get(x).depth() == 1).count();
        if (sharedParents >= 2) {
            return base;
        }
        return base + (FATHER.equals(lastRole(lca, ancA, ancB)) ? " (cùng cha khác mẹ)" : " (cùng mẹ khác cha)");
    }

    private static String uncleAuntName(Person a, String firstRoleOfB, Person parentOfB) {
        if (!FATHER.equals(firstRoleOfB)) {
            return pick(a.gender(), "cậu", "dì", "cậu/dì");
        }
        if ("FEMALE".equals(a.gender())) {
            return "cô";
        }
        if (!"MALE".equals(a.gender())) {
            return "bác/chú/cô";
        }
        Boolean o = older(a, parentOfB);
        return o == null ? "bác/chú" : o ? "bác" : "chú";
    }

    private static String cousinName(Person a, Person b, String firstRoleOfB) {
        Boolean o = older(a, b);
        String base = o == null ? "anh/chị/em họ" : (o ? pick(a.gender(), "anh", "chị", "anh/chị") : "em") + " họ";
        return base + " (bên " + side(firstRoleOfB) + ")";
    }

    /** X lớn tuổi hơn Y không; null khi thiếu năm sinh hoặc bằng nhau. Có đủ ngày sinh thì so ngày, không thì so năm. */
    static Boolean older(Person x, Person y) {
        if (x.birthDate() != null && y.birthDate() != null) {
            int c = x.birthDate().compareTo(y.birthDate());
            return c == 0 ? null : c < 0;
        }
        Integer yx = x.birthDate() != null ? Integer.valueOf(x.birthDate().getYear()) : x.birthYear();
        Integer yy = y.birthDate() != null ? Integer.valueOf(y.birthDate().getYear()) : y.birthYear();
        if (yx == null || yy == null || yx.equals(yy)) {
            return null;
        }
        return yx < yy;
    }

    private static String side(String role) {
        return FATHER.equals(role) ? "nội" : "ngoại";
    }

    private static String pick(String gender, String male, String female, String other) {
        return "MALE".equals(gender) ? male : "FEMALE".equals(gender) ? female : other;
    }
}
