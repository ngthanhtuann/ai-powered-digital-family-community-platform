package com.familyconnect.modules.genealogy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bộ ca kiểm thử thuật toán quan hệ họ hàng, lấy từ docs/02-design/algorithm-relationship.md mục 6 và 7.
 * Thuật toán chưa có trong repo: các test hiện tại kiểm tra bộ ca khớp với dữ liệu mẫu (số bước, tổ tiên chung);
 * khi TV2 cài đặt xong, bỏ @Disabled ở {@link #algorithmMatchesExpected} và nối {@link #calculator()}.
 * Ca #51 đến #53 (PERM_001, PERM_002, NOT_FOUND_001) viết ở tầng controller khi có endpoint GET /relationships.
 */
class RelationshipCaseCatalogTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_GENERATIONS = 6; // giới hạn của thuật toán, mục 3 bước 3.1

    /** Kết quả mong đợi, tên trường theo RelationshipResponse.data trong genealogy.yaml. */
    record Case(int no, String a, String b, Integer stepsFromA, Integer stepsFromB, boolean supported,
                String commonAncestorId, String relation, String explanation) {}

    /** Kết quả thuật toán trả về, cùng các trường được so sánh (mục 7.1). */
    record Result(boolean supported, String relation, Integer stepsFromA, Integer stepsFromB, String commonAncestorId) {}

    interface RelationshipCalculator {
        Result calculate(JsonNode fixture, String personA, String personB);
    }

    static Stream<Case> cases() {
        return Stream.of(
            c(1,"an","an",0,0,true,"an","cùng một người","A và B trùng ID."),
            c(2,"cuong","an",0,1,true,"cuong","cha","Cường là cha trực tiếp của An."),
            c(3,"mai","an",0,1,true,"mai","mẹ","Mai là mẹ trực tiếp của An."),
            c(4,"an","cuong",1,0,true,"cuong","con","An đi lên một bước tới Cường."),
            c(5,"an","bich",1,1,true,"cuong","anh","Cùng cha mẹ; An sinh trước Bích."),
            c(6,"bich","an",1,1,true,"cuong","em","Cùng cha mẹ; Bích sinh sau An."),
            c(7,"an","tuan",1,1,true,"cuong","anh (cùng cha khác mẹ)","Chung cha Cường, mẹ khác nhau; An lớn tuổi hơn."),
            c(8,"tuan","an",1,1,true,"cuong","em (cùng cha khác mẹ)","Chung cha Cường, mẹ khác nhau; Tuấn nhỏ tuổi hơn."),
            c(9,"phuc","ha",1,1,true,"dung","anh/chị/em","Hà thiếu năm sinh nên không phân định tuổi."),
            c(10,"bao","nhan",1,1,true,"an","anh (nuôi)","Nhân là con nuôi; Bảo lớn tuổi hơn."),
            c(11,"cuong","bao",0,2,true,"cuong","ông nội","Bảo đi lên qua cha An tới Cường."),
            c(12,"phat","an",0,2,true,"phat","ông ngoại","An đi lên qua mẹ Mai tới Phát."),
            c(13,"sen","an",0,2,true,"sen","bà ngoại","An đi lên qua mẹ Mai tới Sen."),
            c(14,"bao","cuong",2,0,true,"cuong","cháu nội","Bảo là con trai của An, con Cường."),
            c(15,"giang","cuong",2,0,true,"cuong","cháu ngoại","Giang đi xuống qua con gái Bích của Cường."),
            c(16,"to","bao",0,3,true,"to","ông cố nội","Bảo đi lên ba thế hệ theo nhánh cha."),
            c(17,"coi","giang",0,3,true,"coi","bà cố ngoại","Giang đi lên ba thế hệ theo nhánh mẹ."),
            c(18,"bao","to",3,0,true,"to","chắt nội","Bảo cách Tổ ba bước qua nhánh cha."),
            c(19,"giang","to",3,0,true,"to","chắt ngoại","Giang cách Tổ ba bước qua nhánh mẹ."),
            c(20,"hai","an",1,2,true,"to","bác","Hải là anh của cha An là Cường."),
            c(21,"dung","an",1,2,true,"to","chú","Dũng là em trai của cha An."),
            c(22,"hoa","an",1,2,true,"to","cô","Hoa là chị/em gái của cha An."),
            c(23,"quan","an",1,2,true,"phat","cậu","Quân là anh/em trai của mẹ An."),
            c(24,"thu","an",1,2,true,"phat","dì","Thu là chị/em gái của mẹ An."),
            c(25,"an","hai",2,1,true,"to","cháu","An là con của em Cường, người có quan hệ anh em với Hải."),
            c(26,"khai","an",2,2,true,"to","anh họ (bên nội)","Khải và An là con của hai anh em; Khải lớn hơn."),
            c(27,"linh","an",2,2,true,"to","em họ (bên nội)","Linh và An là con của hai anh em; Linh nhỏ hơn."),
            c(28,"vinh","an",2,2,true,"phat","em họ (bên ngoại)","Vinh cùng nhánh họ bên mẹ của An và nhỏ tuổi hơn."),
            c(29,"tam","an",2,2,true,"to","em họ (bên nội)","Tâm là con của cô Hoa; cách An hai bước tới tổ tiên chung."),
            c(30,"bao","giang",2,2,true,"cuong","anh họ (bên ngoại)","Bảo lớn hơn Giang; bên ngoại xét theo bước đầu của Giang."),
            c(31,"long","bao",3,3,false,"to",null,"Có tổ tiên chung nhưng cặp (3,3) chưa được hỗ trợ."),
            c(32,"hai","bao",1,3,false,"to",null,"Có tổ tiên chung nhưng cặp (1,3) chưa được hỗ trợ."),
            c(33,"huong","bich",null,null,false,null,null,"Không có huyết thống; vợ của An không thuộc lớp thông gia đã hỗ trợ với Bích."),
            c(34,"an","huong",null,null,true,null,"chồng","Hai người có hôn nhân MARRIED."),
            c(35,"huong","an",null,null,true,null,"vợ","Đảo chiều hôn nhân của An và Hương."),
            c(36,"lan","an",null,null,true,null,"bác","Lan là vợ của bác Hải."),
            c(37,"yen","an",null,null,true,null,"thím","Yến là vợ của chú Dũng."),
            c(38,"hong","an",null,null,true,null,"mợ","Hồng là vợ của cậu Quân."),
            c(39,"son","an",null,null,true,null,"dượng","Sơn là chồng của cô Hoa."),
            c(40,"lan","yen",null,null,false,null,null,"Không có quan hệ; extension không đệ quy qua bạn đời."),
            c(41,"huong","ngan",null,null,false,null,null,"Không có huyết thống hoặc quan hệ hôn nhân được hỗ trợ."),
            c(42,"son","phuong",null,null,false,null,null,"Hai nhánh không có tổ tiên chung hoặc quan hệ mở rộng."),
            c(43,"an","hanh",1,1,true,"mai","anh (cùng mẹ khác cha)","An và Hạnh cùng mẹ Mai, khác cha; An lớn tuổi hơn."),
            c(44,"hanh","an",1,1,true,"mai","em (cùng mẹ khác cha)","Đảo chiều của ca 43; Hạnh nhỏ tuổi hơn An."),
            c(45,"may","giang",1,1,true,"duc","anh/chị","Mây có giới tính OTHER, dùng tên trung tính."),
            c(46,"an","tam",2,2,true,"to","anh họ (bên ngoại)","Hai LCA ngang độ sâu; quy tắc ưu tiên cha chọn Tổ."),
            c(47,"coi","to",null,null,true,null,"vợ","Vợ chồng góa vẫn được coi là quan hệ vợ/chồng."),
            c(48,"cuong","mai",null,null,true,null,"chồng cũ","Hôn nhân DIVORCED trả tên quan hệ cũ."),
            c(49,"c","a1",2,0,true,"a1","cháu nội","Nhánh ngắn duy nhất từ C tới A1 đi qua B1."),
            c(50,"c","x",3,0,true,"x","chắt nội","Hai đường cùng độ dài; ưu tiên đường đầu qua cha B1.")
        );
    }

    private static Case c(int no, String a, String b, Integer sa, Integer sb, boolean supported,
                          String lca, String relation, String why) {
        return new Case(no, a, b, sa, sb, supported, lca, relation, why);
    }

    @ParameterizedTest(name = "SCRUM-42 case #{0}: {1} => {2}")
    @MethodSource("cases")
    void caseIsCompleteAndUsesKnownFixturePeople(Case testCase) throws Exception {
        JsonNode fixture = fixture();
        assertTrue(hasPerson(fixture, testCase.a()), "Unknown A: " + testCase.a());
        assertTrue(hasPerson(fixture, testCase.b()), "Unknown B: " + testCase.b());
        assertTrue(testCase.explanation() != null && !testCase.explanation().isBlank());
        // Dạng kết quả theo bảng mục 3.1
        assertEquals(testCase.supported(), testCase.relation() != null, "supported phải đi cùng relation");
        assertEquals(testCase.commonAncestorId() == null, testCase.stepsFromA() == null, "LCA và stepsFromA phải cùng có hoặc cùng null");
        assertEquals(testCase.stepsFromA() == null, testCase.stepsFromB() == null, "stepsFromA và stepsFromB phải cùng có hoặc cùng null");
    }

    /** Kiểm tra bộ ca khớp dữ liệu mẫu: (a, b) là đường ngắn nhất tới LCA và không có tổ tiên chung nào gần hơn. */
    @ParameterizedTest(name = "SCRUM-42 case #{0}: (a, b) và LCA khớp dữ liệu mẫu")
    @MethodSource("cases")
    void stepsAndCommonAncestorMatchFixture(Case testCase) throws Exception {
        JsonNode fixture = fixture();
        Map<String, Integer> ancA = ancestors(fixture, testCase.a());
        Map<String, Integer> ancB = ancestors(fixture, testCase.b());
        List<String> common = ancA.keySet().stream().filter(ancB::containsKey).toList();

        if (testCase.commonAncestorId() == null) {
            assertTrue(common.isEmpty(), "Ca không huyết thống nhưng có tổ tiên chung: " + common);
            return;
        }
        String lca = testCase.commonAncestorId();
        assertEquals(testCase.stepsFromA(), ancA.get(lca), "stepsFromA");
        assertEquals(testCase.stepsFromB(), ancB.get(lca), "stepsFromB");
        int best = common.stream().mapToInt(x -> ancA.get(x) + ancB.get(x)).min().orElseThrow();
        assertEquals(best, ancA.get(lca) + ancB.get(lca), "Có tổ tiên chung gần hơn LCA mong đợi");
        // Quy tắc hòa (ưu tiên qua cha, id nhỏ hơn) do test thuật toán bên dưới kiểm tra.
    }

    @Disabled("Chờ TV2 cài đặt thuật toán quan hệ họ hàng; nối calculator() rồi bỏ @Disabled")
    @ParameterizedTest(name = "SCRUM-42 algorithm #{0}: {1} => {2}")
    @MethodSource("cases")
    void algorithmMatchesExpected(Case testCase) throws Exception {
        Result actual = calculator().calculate(fixture(), testCase.a(), testCase.b());
        assertEquals(new Result(testCase.supported(), testCase.relation(), testCase.stepsFromA(),
                testCase.stepsFromB(), testCase.commonAncestorId()), actual, "Case #" + testCase.no());
    }

    private static RelationshipCalculator calculator() {
        throw new UnsupportedOperationException("Chưa có cài đặt thuật toán quan hệ họ hàng");
    }

    @Test
    void fixtureContainsFourGenerationSampleAndRequiredEdgeCases() throws Exception {
        JsonNode fixture = fixture();
        assertEquals(43, fixture.path("people").size());
        assertEquals(52, fixture.path("parentChild").size());
        assertEquals(12, fixture.path("marriages").size());
        assertTrue(fixture.path("people").findValuesAsText("id").containsAll(List.of("an", "stranger", "c")));
        assertEquals("OTHER", person(fixture, "may").path("gender").asText());
        assertTrue(person(fixture, "ha").path("deceased").asBoolean());
        assertFalse(person(fixture, "ha").has("birthDate"));
    }

    @Test
    void fixtureParentRolesMatchGenderAndOneActiveMarriagePerPerson() throws Exception {
        JsonNode fixture = fixture();
        for (JsonNode edge : fixture.path("parentChild")) {
            String gender = person(fixture, edge.path("parent").asText()).path("gender").asText();
            String role = edge.path("role").asText();
            assertFalse(role.equals("FATHER") && gender.equals("FEMALE"), "FATHER là nữ: " + edge);
            assertFalse(role.equals("MOTHER") && gender.equals("MALE"), "MOTHER là nam: " + edge);
        }
        // BR-GEN-07: mỗi người tối đa một hôn nhân MARRIED
        Map<String, Integer> married = new HashMap<>();
        for (JsonNode m : fixture.path("marriages")) {
            if (!"MARRIED".equals(m.path("status").asText())) continue;
            married.merge(m.path("person1").asText(), 1, Integer::sum);
            married.merge(m.path("person2").asText(), 1, Integer::sum);
        }
        married.forEach((id, n) -> assertEquals(1, n, "Nhiều hôn nhân MARRIED: " + id));
    }

    @Test
    void catalogHasFiftyAlgorithmCasesInOrder() {
        List<Case> all = cases().toList();
        assertEquals(50, all.size(), "Ca #1 đến #50 trong mục 7; #51-#53 thuộc tầng controller");
        for (int i = 0; i < all.size(); i++) assertEquals(i + 1, all.get(i).no());
    }

    /** Khoảng cách ngắn nhất từ p lên mỗi tổ tiên (p ở độ sâu 0), tối đa MAX_GENERATIONS đời. */
    private static Map<String, Integer> ancestors(JsonNode fixture, String p) {
        Map<String, Integer> depth = new HashMap<>(Map.of(p, 0));
        List<String> frontier = List.of(p);
        for (int d = 1; d <= MAX_GENERATIONS && !frontier.isEmpty(); d++) {
            List<String> next = new ArrayList<>();
            for (JsonNode edge : fixture.path("parentChild")) {
                String parent = edge.path("parent").asText();
                if (frontier.contains(edge.path("child").asText()) && !depth.containsKey(parent)) {
                    depth.put(parent, d);
                    next.add(parent);
                }
            }
            frontier = next;
        }
        return depth;
    }

    private static JsonNode fixture() throws Exception {
        try (InputStream input = RelationshipCaseCatalogTest.class.getResourceAsStream("/genealogy/relationship-fixture.json")) {
            assertNotNull(input, "Missing genealogy/relationship-fixture.json");
            return JSON.readTree(input);
        }
    }

    private static boolean hasPerson(JsonNode fixture, String id) {
        return fixture.path("people").findValuesAsText("id").contains(id);
    }

    private static JsonNode person(JsonNode fixture, String id) {
        for (JsonNode person : fixture.path("people")) if (id.equals(person.path("id").asText())) return person;
        return JSON.createObjectNode();
    }
}
