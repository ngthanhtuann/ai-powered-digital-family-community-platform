package com.familyconnect.modules.heritage.controller;

import com.familyconnect.modules.heritage.dto.HeritageResponses;
import com.familyconnect.modules.heritage.service.HeritageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HeritageControllerTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID FAMILY_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID AUTHOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    private HeritageService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(HeritageService.class);
        mvc = MockMvcBuilders.standaloneSetup(new HeritageController(service)).build();
    }

    @Test
    void deletingDocumentReturns200WithNullData() throws Exception {
        mvc.perform(delete("/api/v1/heritage/documents/{id}", DOCUMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(service).deleteDocument(DOCUMENT_ID);
    }

    @Test
    void documentListAcceptsCategoryAndPagination() throws Exception {
        when(service.listDocuments(eq(FAMILY_ID), eq("gia-pha"), eq(1), eq(10), eq(false)))
                .thenReturn(HeritageResponses.ListResponse.of(List.of(), 1, 10, 0));

        mvc.perform(get("/api/v1/heritage/documents")
                        .queryParam("familyId", FAMILY_ID.toString())
                        .queryParam("category", "gia-pha")
                        .queryParam("page", "1")
                        .queryParam("size", "10")
                        .queryParam("sort", "createdAt,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.size").value(10));
    }

    @Test
    void creatingStoryReturns201AndSuccessEnvelope() throws Exception {
        HeritageResponses.StoryData story = new HeritageResponses.StoryData(
                DOCUMENT_ID, "Ký ức", "Ngày sum họp", "Người đăng", AUTHOR_ID, null,
                OffsetDateTime.parse("2026-10-23T10:00:00Z"),
                OffsetDateTime.parse("2026-10-23T10:00:00Z"));
        when(service.createStory(eq(FAMILY_ID), any())).thenReturn(story);

        mvc.perform(post("/api/v1/heritage/stories")
                        .queryParam("familyId", FAMILY_ID.toString())
                        .contentType("application/json")
                        .content("""
                                {"title":"Ký ức","content":"Ngày sum họp"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authorName").value("Người đăng"))
                .andExpect(jsonPath("$.data.authorId").value(AUTHOR_ID.toString()));
    }
}
