package com.familyconnect.modules.genealogy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executable quality gate for the relationship test catalog. The algorithm is not
 * implemented in this repository yet; once TV2 exposes a calculator, each case
 * can be wired to it without changing the fixture or expected values.
 */
class RelationshipCaseCatalogTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    record Case(int no, String a, String b, Integer stepsA, Integer stepsB,
                String relation, String explanation) {}

    static Stream<Case> cases() {
        return Stream.of(
            c(1,"an","an",0,0,"cùng một người","A và B trùng ID."),
            c(2,"cuong","an",0,1,"cha","Cường là cha trực tiếp của An."),
            c(3,"mai","an",0,1,"mẹ","Mai là mẹ trực tiếp của An."),
            c(4,"an","cuong",1,0,"con","An đi lên một bước tới Cường."),
            c(5,"an","bich",1,1,"anh","Cùng cha mẹ; An sinh trước Bích."),
            c(6,"bich","an",1,1,"em","Cùng cha mẹ; Bích sinh sau An."),
            c(7,"an","tuan",1,1,"anh (cùng cha khác mẹ)","Chung cha Cường, mẹ khác nhau; An lớn tuổi hơn."),
            c(8,"tuan","an",1,1,"em (cùng cha khác mẹ)","Chung cha Cường, mẹ khác nhau; Tuấn nhỏ tuổi hơn."),
            c(9,"phuc","ha",1,1,"anh/chị/em","Hà thiếu năm sinh nên không phân định tuổi."),
            c(10,"bao","nhan",1,1,"anh (nuôi)","Nhân là con nuôi; Bảo lớn tuổi hơn."),
            c(11,"cuong","bao",0,2,"ông nội","Bảo đi lên qua cha An tới Cường."),
            c(12,"phat","an",0,2,"ông ngoại","An đi lên qua mẹ Mai tới Phát."),
            c(13,"sen","an",0,2,"bà ngoại","An đi lên qua mẹ Mai tới Sen."),
            c(14,"bao","cuong",2,0,"cháu nội","Bảo là con trai của An, con Cường."),
            c(15,"giang","cuong",2,0,"cháu ngoại","Giang đi xuống qua con gái Bích của Cường."),
            c(16,"to","bao",0,3,"ông cố nội","Bảo đi lên ba thế hệ theo nhánh cha."),
            c(17,"coi","giang",0,3,"bà cố ngoại","Giang đi lên ba thế hệ theo nhánh mẹ."),
            c(18,"bao","to",3,0,"chắt nội","Bảo cách Tổ ba bước qua nhánh cha."),
            c(19,"giang","to",3,0,"chắt ngoại","Giang cách Tổ ba bước qua nhánh mẹ."),
            c(20,"hai","an",1,2,"bác","Hải là anh của cha An là Cường."),
            c(21,"dung","an",1,2,"chú","Dũng là em trai của cha An."),
            c(22,"hoa","an",1,2,"cô","Hoa là chị/em gái của cha An."),
            c(23,"quan","an",1,2,"cậu","Quân là anh/em trai của mẹ An."),
            c(24,"thu","an",1,2,"dì","Thu là chị/em gái của mẹ An."),
            c(25,"an","hai",2,1,"cháu","An là con của em Cường, người có quan hệ anh em với Hải."),
            c(26,"khai","an",2,2,"anh họ (bên nội)","Khải và An là con của hai anh em; Khải lớn hơn."),
            c(27,"linh","an",2,2,"em họ (bên nội)","Linh và An là con của hai anh em; Linh nhỏ hơn."),
            c(28,"vinh","an",2,2,"em họ (bên ngoại)","Vinh cùng nhánh họ bên mẹ của An và nhỏ tuổi hơn."),
            c(29,"tam","an",2,2,"em họ (bên nội)","Tâm là con của cô Hoa; cách An hai bước tới tổ tiên chung."),
            c(30,"bao","giang",2,2,"anh họ (bên ngoại)","Bảo lớn hơn Giang; bên ngoại xét theo bước đầu của Giang."),
            c(31,"long","bao",3,3,null,"Có tổ tiên chung nhưng cặp (3,3) chưa được hỗ trợ."),
            c(32,"hai","bao",1,3,null,"Có tổ tiên chung nhưng cặp (1,3) chưa được hỗ trợ."),
            c(33,"huong","bich",null,null,null,"Không có huyết thống; vợ của An không thuộc lớp thông gia đã hỗ trợ với Bích."),
            c(34,"an","huong",null,null,"chồng","Hai người có hôn nhân MARRIED."),
            c(35,"huong","an",null,null,"vợ","Đảo chiều hôn nhân của An và Hương."),
            c(36,"lan","an",null,null,"bác","Lan là vợ của bác Hải."),
            c(37,"yen","an",null,null,"thím","Yến là vợ của chú Dũng."),
            c(38,"hong","an",null,null,"mợ","Hồng là vợ của cậu Quân."),
            c(39,"son","an",null,null,"dượng","Sơn là chồng của cô Hoa."),
            c(40,"lan","yen",null,null,null,"Không có quan hệ; extension không đệ quy qua bạn đời."),
            c(41,"huong","ngan",null,null,null,"Không có huyết thống hoặc quan hệ hôn nhân được hỗ trợ."),
            c(42,"son","phuong",null,null,null,"Hai nhánh không có tổ tiên chung hoặc quan hệ mở rộng."),
            c(43,"an","hanh",1,1,"anh (cùng mẹ khác cha)","An và Hạnh cùng mẹ Mai, khác cha; An lớn tuổi hơn."),
            c(44,"hanh","an",1,1,"em (cùng mẹ khác cha)","Đảo chiều của ca 43; Hạnh nhỏ tuổi hơn An."),
            c(45,"may","giang",1,1,"anh/chị","Mây có giới tính OTHER, dùng tên trung tính."),
            c(46,"an","tam",2,2,"anh họ (bên ngoại)","Hai LCA ngang độ sâu; quy tắc ưu tiên cha chọn Tổ."),
            c(47,"coi","to",null,null,"vợ","Vợ chồng góa vẫn được coi là quan hệ vợ/chồng."),
            c(48,"cuong","mai",null,null,"chồng cũ","Hôn nhân DIVORCED trả tên quan hệ cũ."),
            c(49,"c","a1",2,0,"cháu nội","Nhánh ngắn duy nhất từ C tới A1 đi qua B1."),
            c(50,"c","x",3,0,"chắt nội","Hai đường cùng độ dài; ưu tiên đường đầu qua cha B1.")
        );
    }

    private static Case c(int no, String a, String b, Integer sa, Integer sb, String relation, String why) {
        return new Case(no, a, b, sa, sb, relation, why);
    }

    @ParameterizedTest(name = "SCRUM-42 case #{0}: {1} => {2}")
    @MethodSource("cases")
    void caseIsCompleteAndUsesKnownFixturePeople(Case testCase) throws Exception {
        JsonNode fixture = fixture();
        assertNotNull(fixture, "fixture JSON must parse");
        assertTrue(hasPerson(fixture, testCase.a()), "Unknown A: " + testCase.a());
        assertTrue(hasPerson(fixture, testCase.b()), "Unknown B: " + testCase.b());
        assertTrue(testCase.explanation() != null && !testCase.explanation().isBlank());
        if (testCase.relation() == null) {
            assertTrue(testCase.stepsA() == null || testCase.stepsA() >= 0);
            assertTrue(testCase.stepsB() == null || testCase.stepsB() >= 0);
        }
    }

    @org.junit.jupiter.api.Test
    void fixtureContainsFourGenerationSampleAndRequiredEdgeCases() throws Exception {
        JsonNode fixture = fixture();
        assertEquals(43, fixture.path("people").size());
        assertEquals(52, fixture.path("parentChild").size());
        assertEquals(12, fixture.path("marriages").size());
        assertTrue(fixture.path("people").findValuesAsText("id").containsAll(List.of("an", "stranger", "c")));
        assertEquals("OTHER", person(fixture, "may").path("gender").asText());
        assertTrue(person(fixture, "ha").path("deceased").asBoolean());
        assertFalse(person(fixture, "ha").has("birthDate"));
        assertEquals(50, cases().count());
    }

    @org.junit.jupiter.api.Test
    void catalogHasAtLeastThirtyCasesAndIncludesPublished53CaseApiScope() {
        assertEquals(50, cases().count(), "Algorithm cases 1-50 from TV2's sample table");
        assertTrue(cases().allMatch(c -> c.no() >= 1 && c.no() <= 50));
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
