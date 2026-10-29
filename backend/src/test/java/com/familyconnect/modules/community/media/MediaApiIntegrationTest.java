package com.familyconnect.modules.community.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API media (UC-COM-05) qua HTTP với PostgreSQL thật; mỗi test tự rollback, dữ liệu và tệp đều giả.
 * Bao phủ nhóm test bắt buộc: tệp (định dạng, kích thước), IDOR giữa gia đình, phân quyền, quy ước API.
 * Tệp > 11 MB do Spring chặn trước controller nên MockMvc không tái hiện được; đã kiểm tra bằng backend chạy thật.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MediaApiIntegrationTest {

    private static final String PASSWORD = "Matkhau123";
    private static final byte[] JPG = bytes(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0}, 200);
    private static final byte[] PNG = bytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10}, 200);
    private static final byte[] WEBP = bytes("RIFF\0\0\0\0WEBPVP8 ".getBytes(), 200);
    private static final byte[] PDF = bytes("%PDF-1.4\n".getBytes(), 200);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    private static byte[] bytes(byte[] header, int padding) {
        byte[] result = Arrays.copyOf(header, header.length + padding);
        Arrays.fill(result, header.length, result.length, (byte) '0');
        return result;
    }

    private String newUser() throws Exception {
        String email = "media." + UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                        Map.of("email", email, "password", PASSWORD, "fullName", "Người thử", "dateOfBirth", "1990-01-01"))))
                .andExpect(status().isCreated());
        return email;
    }

    private String token(String email) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("accessToken").asText();
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private ResultActions upload(MockMultipartHttpServletRequestBuilder request, String token, String name, byte[] data)
            throws Exception {
        request.file(new MockMultipartFile("file", name, "application/octet-stream", data));
        return call(request, token, null);
    }

    private ResultActions uploadHeritage(String token, Object familyId, String name, byte[] data) throws Exception {
        return call(multipart("/api/v1/media").file(new MockMultipartFile("file", name, "application/octet-stream", data))
                .param("ownerType", "HERITAGE").param("ownerId", familyId.toString()), token, null);
    }

    private UUID id(ResultActions result) throws Exception {
        JsonNode body = json.readTree(result.andReturn().getResponse().getContentAsString());
        return UUID.fromString(body.get("data").get("id").asText());
    }

    /** Chủ gia đình mới (BRANCH_ADMIN toàn gia đình), trả về [token, familyId, postId]. */
    private Object[] familyWithPost() throws Exception {
        String token = token(newUser());
        UUID familyId = id(call(post("/api/v1/families"), token, Map.of("name", "Dòng họ thử"))
                .andExpect(status().isCreated()));
        UUID postId = id(call(post("/api/v1/posts"), token, Map.of("content", "Bài có ảnh", "type", "POST"))
                .andExpect(status().isCreated()));
        return new Object[]{token, familyId, postId};
    }

    private String member(UUID familyId) throws Exception {
        String email = newUser();
        jdbc.update("insert into member_verification(user_id, family_id, role, status) "
                + "select id, ?, 'MEMBER', 'APPROVED' from users where email = ?", familyId, email);
        return token(email);
    }

    @Test
    void postImagesAcceptValidFormatsAndRejectInvalidFiles() throws Exception {
        Object[] a = familyWithPost();
        String token = (String) a[0];
        String postMedia = "/api/v1/posts/" + a[2] + "/media";

        UUID jpg = id(upload(multipart(postMedia), token, "a.jpg", JPG).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contentType").value("image/jpeg"))
                .andExpect(content().string(not(containsString("storage")))));
        upload(multipart(postMedia), token, "b.png", PNG).andExpect(status().isCreated());
        upload(multipart(postMedia), token, "c.webp", WEBP).andExpect(status().isCreated());

        upload(multipart(postMedia), token, "fake.jpg", "day la van ban".getBytes())
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("FILE_002"));
        upload(multipart(postMedia), token, "x.png", JPG)
                .andExpect(status().isUnsupportedMediaType());
        upload(multipart(postMedia), token, "doc.pdf", PDF)
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("FILE_002"));
        upload(multipart(postMedia), token, "big.jpg", bytes(JPG, 5 * 1024 * 1024))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("FILE_001"));

        call(get(postMedia), token, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(3));
        call(get("/api/v1/media/" + jpg + "/content"), token, null)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(content().bytes(JPG));
        call(get("/api/v1/media/" + jpg + "/content"), null, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    @Test
    void otherFamilyCannotReadAttachOrDeleteMedia() throws Exception {
        Object[] a = familyWithPost();
        Object[] b = familyWithPost();
        String tokenA = (String) a[0];
        String tokenB = (String) b[0];
        String postMediaA = "/api/v1/posts/" + a[2] + "/media";
        UUID image = id(upload(multipart(postMediaA), tokenA, "a.jpg", JPG).andExpect(status().isCreated()));
        UUID pdf = id(uploadHeritage(tokenA, a[1], "tai-lieu.pdf", PDF).andExpect(status().isCreated()));

        call(get("/api/v1/media/" + image + "/content"), tokenB, null).andExpect(status().isForbidden());
        call(get(postMediaA), tokenB, null).andExpect(status().isForbidden());
        upload(multipart(postMediaA), tokenB, "b.jpg", JPG).andExpect(status().isForbidden());
        call(delete("/api/v1/media/" + image), tokenB, null).andExpect(status().isForbidden());
        call(get("/api/v1/media/" + pdf + "/content"), tokenB, null).andExpect(status().isForbidden());
        uploadHeritage(tokenB, a[1], "x.pdf", PDF).andExpect(status().isForbidden());
    }

    @Test
    void onlyUploaderOrModeratorDeletesAndDeletedMediaIsGone() throws Exception {
        Object[] a = familyWithPost();
        String owner = (String) a[0];
        String postMedia = "/api/v1/posts/" + a[2] + "/media";
        UUID image = id(upload(multipart(postMedia), owner, "a.jpg", JPG).andExpect(status().isCreated()));
        String otherMember = member((UUID) a[1]);

        call(get("/api/v1/media/" + image + "/content"), otherMember, null).andExpect(status().isOk());
        upload(multipart(postMedia), otherMember, "m.jpg", JPG).andExpect(status().isForbidden());
        call(delete("/api/v1/media/" + image), otherMember, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PERM_001"));

        call(delete("/api/v1/media/" + image), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
        call(get("/api/v1/media/" + image + "/content"), owner, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND_001"));
    }
}
