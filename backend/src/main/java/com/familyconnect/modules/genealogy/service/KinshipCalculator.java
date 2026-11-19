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

    /** Đủ cho cả cây của một dòng họ (mục 3.3); trước giới hạn 6 đời nên quan hệ xa báo "chưa hỗ trợ". */
    static final int MAX_GENERATIONS = 30;
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
        // (1) A là vợ/chồng của một người có huyết thống với B (con rể, chị dâu, thím, dượng...).
        for (MarriageLink m : marriages) {
            if (DIVORCED.equals(m.status())) {
                continue;
            }
            Result viaSpouse = bloodRelation(m.spouseId(), b);
            if (viaSpouse != null && viaSpouse.supported()) {
                List<String> path = new ArrayList<>(List.of(a));
                path.addAll(viaSpouse.path());
                return new Result(true, spouseOfRelative(gender, viaSpouse.relation()), null, null, null,
                        List.copyOf(path)); // mục 3.1: quan hệ qua hôn nhân không có số bước huyết thống
            }
        }
        // (2) A có huyết thống với vợ/chồng của B (bố vợ, anh chồng, bác vợ...).
        Person pb = graph.person(b);
        for (MarriageLink m : graph.marriagesOf(b)) {
            if (DIVORCED.equals(m.status())) {
                continue;
            }
            Result toSpouse = bloodRelation(a, m.spouseId());
            if (toSpouse != null && toSpouse.supported()) {
                List<String> path = new ArrayList<>(toSpouse.path());
                path.add(b);
                return new Result(true, toSpouse.relation() + " " + pick(pb.gender(), "vợ", "chồng", "vợ/chồng"),
                        null, null, null, List.copyOf(path));
            }
        }
        // (3) Vợ/chồng hai bên có huyết thống với nhau (thông gia, anh em cột chèo...).
        for (MarriageLink ma : marriages) {
            for (MarriageLink mb : graph.marriagesOf(b)) {
                if (DIVORCED.equals(ma.status()) || DIVORCED.equals(mb.status())) {
                    continue;
                }
                Result between = bloodRelation(ma.spouseId(), mb.spouseId());
                if (between != null && between.supported()) {
                    List<String> path = new ArrayList<>(List.of(a));
                    path.addAll(between.path());
                    path.add(b);
                    String rel = between.relation();
                    boolean siblings = rel.startsWith("anh") || rel.startsWith("chị") || rel.startsWith("em");
                    String genderB = graph.person(b).gender();
                    String name = !siblings ? "họ hàng bên vợ/chồng"
                            : ("FEMALE".equals(gender) && "FEMALE".equals(genderB) ? "chị em dâu"
                            : "MALE".equals(gender) && "MALE".equals(genderB) ? "anh em cột chèo"
                            : "anh chị em bên vợ/chồng") + (rel.contains(" họ") ? " họ" : "");
                    return new Result(true, name, null, null, null, List.copyOf(path));
                }
            }
        }
        // (4) Qua hôn nhân đã ly hôn: chỉ xét khi không có quan hệ nào ở trên (mục 2.4, SCRUM-117).
        for (MarriageLink m : marriages) {
            if (!DIVORCED.equals(m.status())) {
                continue;
            }
            Result viaEx = bloodRelation(m.spouseId(), b);
            if (viaEx != null && viaEx.supported()) {
                List<String> path = new ArrayList<>(List.of(a));
                path.addAll(viaEx.path());
                return new Result(true, pick(gender, "chồng cũ", "vợ cũ", "vợ/chồng cũ") + " của " + viaEx.relation(),
                        null, null, null, List.copyOf(path));
            }
        }
        for (MarriageLink m : graph.marriagesOf(b)) {
            if (!DIVORCED.equals(m.status())) {
                continue;
            }
            Result toEx = bloodRelation(a, m.spouseId());
            if (toEx != null && toEx.supported()) {
                List<String> path = new ArrayList<>(toEx.path());
                path.add(b);
                return new Result(true, toEx.relation() + " " + pick(pb.gender(), "vợ cũ", "chồng cũ", "vợ/chồng cũ"),
                        null, null, null, List.copyOf(path));
            }
        }
        return Result.none();
    }

    /** A là vợ/chồng của người mà B gọi là {@code relative}: tên gọi theo cách xưng hô phổ biến (mục 2.4). */
    static String spouseOfRelative(String gender, String relative) {
        String mapped = IN_LAW.get(relative);
        if (mapped != null) {
            return mapped;
        }
        boolean male = "MALE".equals(gender);
        String tail = relative.contains(" họ") ? " họ" : "";
        if (relative.startsWith("con")) return pick(gender, "con rể", "con dâu", "con rể/dâu");
        if (relative.startsWith("cháu")) return pick(gender, "cháu rể", "cháu dâu", "cháu rể/dâu") + tail;
        if (relative.startsWith("chắt")) return pick(gender, "chắt rể", "chắt dâu", "chắt rể/dâu") + tail;
        if (relative.startsWith("cha")) return male ? "cha" : "mẹ kế";
        if (relative.startsWith("mẹ")) return male ? "cha dượng" : "mẹ";
        if (relative.startsWith("anh") || relative.startsWith("chị")) return (male ? "anh rể" : "chị dâu") + tail;
        if (relative.startsWith("em")) return (male ? "em rể" : "em dâu") + tail;
        if (relative.startsWith("ông") || relative.startsWith("bà")) {
            int space = relative.indexOf(' ');
            return pick(gender, "ông", "bà", "ông/bà") + (space < 0 ? "" : relative.substring(space));
        }
        if (relative.startsWith("bác")) return male ? "bác" + tail : "bác gái" + tail;
        if (relative.startsWith("chú")) return "thím" + tail;
        if (relative.startsWith("cậu")) return "mợ" + tail;
        if (relative.startsWith("cô") || relative.startsWith("dì")) return "dượng" + tail;
        return pick(gender, "chồng", "vợ", "vợ/chồng") + " của " + relative;
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
            default -> generalName(pa, pb, sa, sb, fromA, fromB);
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

    /**
     * Mọi khoảng cách thế hệ còn lại (mục 3.3): trực hệ xa (kỵ, chút, chít...) và bàng hệ xa (ông/bà họ, cháu họ,
     * anh/chị/em họ nhiều đời). sa, sb là số đời từ A, B lên tổ tiên chung.
     */
    private static String generalName(Person a, Person b, int sa, int sb, Ancestor fromA, Ancestor fromB) {
        String sideOfB = " (bên " + side(fromB.firstRole()) + ")";
        if (sa == 0) {
            String[] up = {"", "cha", "ông", "cụ", "kỵ", "tiên tổ"};
            return sb < up.length ? pick(a.gender(), "", "", "") + up[sb] + sideOfB : "tổ tiên " + sb + " đời trên";
        }
        if (sb == 0) {
            String[] down = {"", "con", "cháu", "chắt", "chút", "chít"};
            return sa < down.length ? down[sa] + " (bên " + side(fromA.firstRole()) + ")" : "hậu duệ đời thứ " + sa;
        }
        int diff = sb - sa; // > 0: A ở đời trên B
        if (diff == 0) {
            Boolean o = older(a, b);
            String base = o == null ? "anh/chị/em" : o ? pick(a.gender(), "anh", "chị", "anh/chị") : "em";
            return base + " họ đời thứ " + sa + sideOfB;
        }
        if (diff == 1) return pick(a.gender(), "bác/chú", "cô/dì", "bác/chú/cô") + " họ" + sideOfB;
        if (diff == 2) return pick(a.gender(), "ông", "bà", "ông/bà") + " họ" + sideOfB;
        if (diff == 3) return "cụ họ" + sideOfB;
        if (diff == -1) return "cháu họ" + sideOfB;
        if (diff == -2) return "cháu họ (gọi bằng ông/bà)" + sideOfB;
        if (diff == -3) return "chắt họ" + sideOfB;
        return diff > 0 ? "bậc trên " + diff + " đời (họ)" : "bậc dưới " + (-diff) + " đời (họ)";
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
