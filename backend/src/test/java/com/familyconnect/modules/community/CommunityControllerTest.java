package com.familyconnect.modules.community;

import com.familyconnect.modules.community.controller.CommunityController;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.exception.GlobalExceptionHandler;
import com.familyconnect.modules.community.service.CommunityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommunityControllerTest {
    CommunityService service;
    MockMvc mvc;

    @BeforeEach void setUp() {
        service = mock(CommunityService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CommunityController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void deletePostUsesHttp200WithNullData() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(delete("/api/v1/posts/{id}",id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
        verify(service).deletePost(id);
    }

    @Test void invalidPageReturnsStandardValidationEnvelope() throws Exception {
        when(service.posts(isNull(),isNull(),isNull(),eq(-1),eq(20),eq("createdAt,desc")))
            .thenThrow(ApiException.invalidField("page","page phải bắt đầu từ 0"));
        mvc.perform(get("/api/v1/posts").param("page","-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALID_001"))
            .andExpect(jsonPath("$.error.details[0].field").value("page"));
    }

    @Test void malformedReactionBodyReturns400() throws Exception {
        mvc.perform(post("/api/v1/posts/{id}/reactions",UUID.randomUUID())
                .contentType("application/json").content("{\"type\":\"UNSUPPORTED\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALID_001"));
    }

    @Test void forbiddenPostIs403() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.post(id)).thenThrow(new ApiException(ErrorCode.PERM_001));
        mvc.perform(get("/api/v1/posts/{id}",id))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("PERM_001"));
    }
}
