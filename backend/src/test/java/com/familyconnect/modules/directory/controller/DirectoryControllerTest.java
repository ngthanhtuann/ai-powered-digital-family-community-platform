package com.familyconnect.modules.directory.controller;

import com.familyconnect.modules.directory.dto.DirectoryResponses.EducationProfileData;
import com.familyconnect.modules.directory.dto.DirectoryResponses.ListResponse;
import com.familyconnect.modules.directory.dto.DirectoryResponses.PersonSearchResult;
import com.familyconnect.modules.directory.dto.DirectoryResponses.ProfessionProfileData;
import com.familyconnect.modules.directory.dto.DirectoryResponses.UpsertResult;
import com.familyconnect.modules.directory.service.DirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DirectoryControllerTest {

    private static final UUID PERSON_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock
    private DirectoryService directoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DirectoryController(directoryService)).build();
    }

    @Test
    void searchMembersReturnsPagedResponse() throws Exception {
        when(directoryService.searchMembers(null, null, null, null, 0, 20, "createdAt,desc"))
                .thenReturn(ListResponse.of(List.of(
                        new PersonSearchResult(PERSON_ID, "Thành viên", null, 1990, 1, null, null)),
                        0, 20, 1));

        mockMvc.perform(get("/api/v1/directory/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(PERSON_ID.toString()))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void firstProfessionProfileWriteReturnsCreatedAndSubsequentWriteReturnsOk() throws Exception {
        ProfessionProfileData data = new ProfessionProfileData(
                UUID.randomUUID(), PERSON_ID, "Kỹ sư", null, null, null, null);
        when(directoryService.updateProfessionProfile(eq(PERSON_ID), any()))
                .thenReturn(new UpsertResult<>(data, true))
                .thenReturn(new UpsertResult<>(data, false));
        String request = """
                {"jobTitle":"Kỹ sư","company":null,"workLocation":null}
                """;

        mockMvc.perform(put("/api/v1/directory/members/{personId}/profession-profile", PERSON_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.personId").value(PERSON_ID.toString()));

        mockMvc.perform(put("/api/v1/directory/members/{personId}/profession-profile", PERSON_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobTitle").value("Kỹ sư"));
    }

    @Test
    void firstEducationProfileWriteReturnsCreated() throws Exception {
        EducationProfileData data = new EducationProfileData(
                UUID.randomUUID(), PERSON_ID, "Đại học", "Cử nhân", 2024, null, null);
        when(directoryService.updateEducationProfile(eq(PERSON_ID), any()))
                .thenReturn(new UpsertResult<>(data, true));

        mockMvc.perform(put("/api/v1/directory/members/{personId}/education-profile", PERSON_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"school":"Đại học","degree":"Cử nhân","graduationYear":2024}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.school").value("Đại học"))
                .andExpect(jsonPath("$.data.graduationYear").value(2024));
    }
}
