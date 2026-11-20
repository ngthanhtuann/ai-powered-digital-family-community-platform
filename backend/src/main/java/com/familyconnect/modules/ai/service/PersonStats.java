package com.familyconnect.modules.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.familyconnect.modules.ai.dto.ChatResponse.SourceReference;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPerson;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * BR-AI-18 (SCRUM-133): câu hỏi thống kê về thành viên (đếm, liệt kê, lớn/nhỏ tuổi nhất) được trả lời bằng dữ liệu
 * gia phả có cấu trúc. LLM chỉ chuyển câu hỏi thành {@link Query} trong danh sách bộ lọc cho phép; máy chủ tự đếm và
 * dựng câu trả lời, nên con số không do LLM sinh ra.
 */
final class PersonStats {

    enum Intent { COUNT, LIST, OLDEST, YOUNGEST }

    record Query(Intent intent, Boolean deceased, String gender, String province, Integer generation,
                 Integer bornFrom, Integer bornTo) {
        boolean hasFilter() {
            return deceased != null || gender != null || province != null || generation != null
                    || bornFrom != null || bornTo != null;
        }
    }

    record Answer(String text, List<SourceReference> sources) {
    }

    private static final int MAX_NAMES = 30;
    private static final int MAX_SOURCES = 10;

    /** Câu hỏi tổng số thành viên viết rõ ràng: trả lời ngay, không gọi LLM. Có điều kiện lọc thì không khớp. */
    private static final Pattern PLAIN_TOTAL = Pattern.compile(
            "^(?:(?:ca )?(?:gia dinh|dong ho|gia pha|ho|nha minh)(?: (?:minh|nha minh|chung ta|toi))?"
                    + " (?:hien |hien nay |hien tai )?(?:co )?(?:tat ca |tong cong )?(?:may|bao nhieu) (?:thanh vien|nguoi)"
                    + "(?: (?:tat ca|ca thay|hien nay|hien tai|vay))?"
                    + "|(?:co )?(?:may|bao nhieu) (?:thanh vien|nguoi) trong (?:gia dinh|dong ho|gia pha|ho|nha minh))$");

    /** Dấu hiệu câu hỏi tổng hợp; chỉ những câu này mới được gửi kèm lựa chọn trả về truy vấn có cấu trúc. */
    private static final Pattern AGGREGATE = Pattern.compile(
            "(?<![a-z])(?:bao nhieu|may nguoi|may thanh vien|co may|liet ke|danh sach|nhung ai|nhung nguoi nao|tong so"
                    + "|(?:lon tuoi|nhieu tuoi|it tuoi|tre|gia|sinh som|sinh muon) nhat)(?![a-z])");

    /** Từ khóa rõ nghĩa trên câu hỏi còn dấu; "nam" viết thường để không nhầm "Nam Định", "năm". */
    private static final Pattern MALE = Pattern.compile("(?<!\\p{L})(?:nam|đàn ông|con trai|nam giới)(?!\\p{L})");
    private static final Pattern FEMALE = Pattern.compile("(?<!\\p{L})(?:nữ|phụ nữ|đàn bà|con gái|nữ giới)(?!\\p{L})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DEAD = Pattern.compile("(?<!\\p{L})(?:đã mất|qua đời|đã chết|đã khuất|quá cố)(?!\\p{L})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ALIVE = Pattern.compile("(?<!\\p{L})(?:còn sống|còn tại thế)(?!\\p{L})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private PersonStats() {
    }

    /**
     * LLM đôi khi bỏ sót bộ lọc (đánh giá 21/11/2026: "người nam" ra tổng số). Câu hỏi có từ khóa rõ nghĩa mà truy vấn
     * để trống thì máy chủ tự điền; không bao giờ ghi đè giá trị LLM đã điền.
     */
    static Query reconcile(Query q, String question) {
        if (q == null || question == null) return q;
        String gender = q.gender();
        if (gender == null) {
            boolean male = MALE.matcher(question).find(), female = FEMALE.matcher(question).find();
            if (male != female) gender = male ? "MALE" : "FEMALE";
        }
        Boolean deceased = q.deceased();
        if (deceased == null) {
            boolean dead = DEAD.matcher(question).find(), alive = ALIVE.matcher(question).find();
            if (dead != alive) deceased = dead;
        }
        return new Query(q.intent(), deceased, gender, q.province(), q.generation(), q.bornFrom(), q.bornTo());
    }

    /** Chữ thường, bỏ dấu, gộp khoảng trắng, bỏ dấu câu cuối: khóa so khớp và khóa bộ nhớ đệm. */
    static String normalize(String text) {
        String plain = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        return plain.replaceAll("[?!.,;:]+", " ").replaceAll("\\s+", " ").trim();
    }

    static Query fastPath(String normalized) {
        return PLAIN_TOTAL.matcher(normalized).matches()
                ? new Query(Intent.COUNT, null, null, null, null, null, null) : null;
    }

    static boolean looksAggregate(String normalized) {
        return AGGREGATE.matcher(normalized).find();
    }

    /** Schema JSON ép Gemini trả về (responseSchema): hoặc câu trả lời từ ngữ cảnh, hoặc truy vấn có cấu trúc. */
    static Map<String, Object> responseSchema() {
        return Map.of("type", "OBJECT",
                "properties", Map.of(
                        "kind", Map.of("type", "STRING", "enum", List.of("ANSWER", "QUERY")),
                        "text", Map.of("type", "STRING", "nullable", true),
                        "intent", Map.of("type", "STRING", "nullable", true,
                                "enum", List.of("COUNT", "LIST", "OLDEST", "YOUNGEST")),
                        "deceased", Map.of("type", "BOOLEAN", "nullable", true),
                        "gender", Map.of("type", "STRING", "nullable", true, "enum", List.of("MALE", "FEMALE")),
                        "province", Map.of("type", "STRING", "nullable", true),
                        "generation", Map.of("type", "INTEGER", "nullable", true),
                        "bornFrom", Map.of("type", "INTEGER", "nullable", true),
                        "bornTo", Map.of("type", "INTEGER", "nullable", true)),
                "required", List.of("kind"));
    }

    /** Hướng dẫn thêm vào prompt khi câu hỏi có dấu hiệu tổng hợp. */
    static String promptRules() {
        return "10. Trả về JSON đúng schema. Nếu câu hỏi CHỈ yêu cầu đếm, liệt kê, hoặc tìm người lớn tuổi nhất/trẻ tuổi nhất "
                + "trong các THÀNH VIÊN gia phả, và mọi điều kiện đều thuộc danh sách: còn sống/đã mất (deceased), giới tính "
                + "(gender MALE/FEMALE), tỉnh/thành đang sống (province), đời thứ mấy (generation), năm sinh từ/đến "
                + "(bornFrom/bornTo), thì trả kind=QUERY với intent COUNT, LIST, OLDEST hoặc YOUNGEST và các bộ lọc tương ứng; "
                + "để null bộ lọc không có trong câu hỏi; không trả text. Ví dụ: 'Dòng họ có bao nhiêu người đã mất?' -> "
                + "{kind:QUERY,intent:COUNT,deceased:true}; 'Liệt kê những người nữ sống ở Hà Nội' -> "
                + "{kind:QUERY,intent:LIST,gender:FEMALE,province:'Hà Nội'}. Mọi trường hợp khác (người tham dự sự kiện, tiền, "
                + "nghề nghiệp, quan hệ với một người cụ thể, điều kiện ngoài danh sách, yêu cầu bịa hoặc xác nhận số liệu) "
                + "trả kind=ANSWER và đặt câu trả lời theo các nguyên tắc trên vào text.\n";
    }

    /** Kiểm JSON của LLM theo danh sách cho phép; sai kiểu hoặc giá trị lạ thì trả null (không đoán). */
    static Query parse(JsonNode node) {
        if (node == null || !"QUERY".equals(node.path("kind").asText())) return null;
        Intent intent;
        try {
            intent = Intent.valueOf(node.path("intent").asText(""));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        Boolean deceased = node.path("deceased").isBoolean() ? node.path("deceased").asBoolean() : null;
        String gender = text(node, "gender");
        if (gender != null && !gender.equals("MALE") && !gender.equals("FEMALE")) return null;
        String province = text(node, "province");
        if (province != null && province.length() > 60) return null;
        Integer generation = integer(node, "generation");
        if (generation != null && (generation < 1 || generation > 50)) return null;
        Integer from = integer(node, "bornFrom");
        Integer to = integer(node, "bornTo");
        if (from != null && (from < 1500 || from > 2100) || to != null && (to < 1500 || to > 2100)) return null;
        if (from != null && to != null && from > to) return null;
        return new Query(intent, deceased, gender, province, generation, from, to);
    }

    static boolean isAnswer(JsonNode node) {
        return node != null && "ANSWER".equals(node.path("kind").asText());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isIntegralNumber() ? value.asInt() : null;
    }

    /**
     * Chạy truy vấn trên toàn bộ thành viên của gia đình (đã xóa mềm thì không có). Đếm tính cả trẻ em; danh sách tên
     * và người trẻ nhất không nêu tên trẻ dưới 16 tuổi (privacy.md mục 6, giống ngữ cảnh gửi LLM).
     */
    static Answer answer(GenealogyQuery genealogy, UUID familyId, LocalDate today, Collection<UUID> hiddenMinors,
                         Query query) {
        List<DirectoryPerson> matched = new ArrayList<>();
        int page = 0;
        GenealogyQuery.DirectoryPage result;
        do {
            result = genealogy.searchDirectoryPeople(familyId, null, null, null, today, null,
                    false, "fullName", true, page++, 100);
            result.people().stream().filter(p -> matches(p, query)).forEach(matched::add);
        } while (!result.people().isEmpty() && (long) page * 100 < result.total());

        String scope = describe(query);
        List<DirectoryPerson> named = matched.stream().filter(p -> !hiddenMinors.contains(p.id())).toList();
        int hidden = matched.size() - named.size();
        return switch (query.intent()) {
            case COUNT -> new Answer(query.hasFilter()
                    ? "Gia phả có " + matched.size() + " người" + scope + "."
                    : "Gia phả của dòng họ hiện có " + matched.size() + " thành viên.", List.of());
            case LIST -> list(named, hidden, scope);
            case OLDEST, YOUNGEST -> extreme(named, query.intent() == Intent.OLDEST, hidden, scope);
        };
    }

    private static Answer list(List<DirectoryPerson> named, int hidden, String scope) {
        if (named.isEmpty() && hidden == 0) return new Answer("Gia phả không có người nào" + scope + ".", List.of());
        String names = named.stream().limit(MAX_NAMES).map(PersonStats::label).reduce((a, b) -> a + ", " + b).orElse("");
        StringBuilder text = new StringBuilder("Có ").append(named.size() + hidden).append(" người").append(scope);
        if (!names.isEmpty()) text.append(": ").append(names);
        if (named.size() > MAX_NAMES) text.append(" và ").append(named.size() - MAX_NAMES).append(" người khác");
        text.append(".");
        if (hidden > 0) text.append(" Không hiển thị tên ").append(hidden).append(" trẻ dưới 16 tuổi.");
        return new Answer(text.toString(), sources(named));
    }

    private static Answer extreme(List<DirectoryPerson> named, boolean oldest, int hidden, String scope) {
        List<DirectoryPerson> known = named.stream().filter(p -> year(p) != null).toList();
        if (known.isEmpty()) return new Answer(ChatService.NOT_FOUND, List.of());
        Comparator<DirectoryPerson> byBirth = Comparator.comparing(PersonStats::birthKey);
        DirectoryPerson pick = oldest ? known.stream().min(byBirth).orElseThrow() : known.stream().max(byBirth).orElseThrow();
        String text = "Người " + (oldest ? "lớn tuổi nhất" : "trẻ tuổi nhất") + scope + " là " + label(pick) + ".";
        if (!oldest && hidden > 0) text += " Không tính trẻ dưới 16 tuổi.";
        return new Answer(text, List.of(new SourceReference("PERSON", pick.id())));
    }

    private static boolean matches(DirectoryPerson p, Query q) {
        if (q.deceased() != null && p.deceased() != q.deceased()) return false;
        if (q.gender() != null && !q.gender().equals(p.gender())) return false;
        if (q.generation() != null && !q.generation().equals(p.generation())) return false;
        if (q.province() != null && (p.currentProvince() == null
                || !normalize(p.currentProvince()).contains(normalize(q.province())))) return false;
        Integer year = year(p);
        if ((q.bornFrom() != null || q.bornTo() != null) && year == null) return false;
        if (q.bornFrom() != null && year < q.bornFrom()) return false;
        return q.bornTo() == null || year <= q.bornTo();
    }

    private static String describe(Query q) {
        StringBuilder s = new StringBuilder();
        if (q.gender() != null) s.append(q.gender().equals("MALE") ? " là nam" : " là nữ");
        if (Boolean.TRUE.equals(q.deceased())) s.append(" đã mất");
        if (Boolean.FALSE.equals(q.deceased())) s.append(" còn sống");
        if (q.province() != null) s.append(" đang sống ở ").append(q.province());
        if (q.generation() != null) s.append(" thuộc đời thứ ").append(q.generation());
        if (q.bornFrom() != null && q.bornTo() != null) {
            s.append(q.bornFrom().equals(q.bornTo()) ? " sinh năm " + q.bornFrom()
                    : " sinh từ năm " + q.bornFrom() + " đến " + q.bornTo());
        } else if (q.bornFrom() != null) s.append(" sinh từ năm ").append(q.bornFrom()).append(" trở về sau");
        else if (q.bornTo() != null) s.append(" sinh từ năm ").append(q.bornTo()).append(" trở về trước");
        return s.toString();
    }

    private static Integer year(DirectoryPerson p) {
        return p.birthDate() != null ? Integer.valueOf(p.birthDate().getYear()) : p.birthYear();
    }

    private static LocalDate birthKey(DirectoryPerson p) {
        return p.birthDate() != null ? p.birthDate() : LocalDate.of(p.birthYear(), 7, 1);
    }

    private static String label(DirectoryPerson p) {
        Integer year = year(p);
        return year == null ? p.fullName() : p.fullName() + " (sinh năm " + year + ")";
    }

    private static List<SourceReference> sources(List<DirectoryPerson> people) {
        return people.stream().limit(MAX_SOURCES).map(p -> new SourceReference("PERSON", p.id())).toList();
    }
}
