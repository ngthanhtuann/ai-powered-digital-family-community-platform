package com.familyconnect.common.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * SCRUM-43 bước 6: Swagger (/v3/api-docs) phải hiện x-roles cho mọi endpoint /api/v1 đã code,
 * và giá trị phải đúng x-roles trong docs/04-api/openapi/*.yaml (nguồn sự thật). Lệch là test fail.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EndpointRolesDocsSyncTest {

    private static final Path SPEC_DIR = Path.of("..", "docs", "04-api", "openapi");
    private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    /** "GET /users/me" -> [MEMBER, ...] đọc từ tài liệu; biến đường dẫn chuẩn hóa thành {}. */
    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> documentedRoles() throws IOException {
        Map<String, List<String>> roles = new HashMap<>();
        try (Stream<Path> files = Files.list(SPEC_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".yaml")).toList()) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    Map<String, Object> spec = new Yaml().load(reader);
                    Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) spec.get("paths");
                    paths.forEach((path, item) -> METHODS.forEach(method -> {
                        Object op = item.get(method);
                        if (op instanceof Map<?, ?> operation && operation.get("x-roles") instanceof List<?> list) {
                            roles.put(EndpointRoles.key(method, path), list.stream().map(String::valueOf).toList());
                        }
                    }));
                }
            }
        }
        return roles;
    }

    @Test
    void swaggerShowsDocumentedRolesForEveryImplementedEndpoint() throws Exception {
        Map<String, List<String>> documented = documentedRoles();
        JsonNode paths = json.readTree(mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString())
                .get("paths");

        List<String> problems = new ArrayList<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = paths.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            if (!entry.getKey().startsWith(EndpointRoles.API_PREFIX)) {
                continue;
            }
            String path = entry.getKey().substring(EndpointRoles.API_PREFIX.length());
            for (String method : METHODS) {
                JsonNode op = entry.getValue().get(method);
                if (op == null) {
                    continue;
                }
                String key = EndpointRoles.key(method, path);
                List<String> expected = documented.get(key);
                List<String> actual = op.has("x-roles")
                        ? json.convertValue(op.get("x-roles"), json.getTypeFactory().constructCollectionType(List.class, String.class))
                        : null;
                if (expected == null) {
                    problems.add(key + ": endpoint đã code nhưng tài liệu chưa có x-roles");
                } else if (!expected.equals(actual)) {
                    problems.add(key + ": Swagger " + actual + ", tài liệu " + expected);
                }
            }
        }
        assertThat(problems).as("x-roles trên Swagger lệch với docs/04-api/openapi").isEmpty();
    }
}
