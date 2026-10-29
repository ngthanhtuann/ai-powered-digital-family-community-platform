package com.familyconnect.modules.directory.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.modules.directory.dto.DirectoryRequests.EducationProfileRequest;
import com.familyconnect.modules.directory.dto.DirectoryRequests.ProfessionProfileRequest;
import com.familyconnect.modules.directory.dto.DirectoryResponses.EducationProfileData;
import com.familyconnect.modules.directory.dto.DirectoryResponses.ListResponse;
import com.familyconnect.modules.directory.dto.DirectoryResponses.PersonSearchResult;
import com.familyconnect.modules.directory.dto.DirectoryResponses.ProfessionProfileData;
import com.familyconnect.modules.directory.dto.DirectoryResponses.UpsertResult;
import com.familyconnect.modules.directory.service.DirectoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/directory")
public class DirectoryController {

    private final DirectoryService directoryService;

    public DirectoryController(DirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @GetMapping("/members")
    public ListResponse<PersonSearchResult> searchMembers(
            @RequestParam(required = false) UUID familyId,
            @RequestParam(required = false) String job,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer generation,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return directoryService.searchMembers(familyId, job, location, generation, page, size, sort);
    }

    @GetMapping("/members/{personId}/profession-profile")
    public ApiResponse<ProfessionProfileData> getProfessionProfile(@PathVariable UUID personId) {
        return ApiResponse.ok(directoryService.getProfessionProfile(personId));
    }

    @PutMapping("/members/{personId}/profession-profile")
    public ResponseEntity<ApiResponse<ProfessionProfileData>> updateProfessionProfile(
            @PathVariable UUID personId, @Valid @RequestBody ProfessionProfileRequest request) {
        UpsertResult<ProfessionProfileData> result =
                directoryService.updateProfessionProfile(personId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.data()));
    }

    @GetMapping("/members/{personId}/education-profile")
    public ApiResponse<EducationProfileData> getEducationProfile(@PathVariable UUID personId) {
        return ApiResponse.ok(directoryService.getEducationProfile(personId));
    }

    @PutMapping("/members/{personId}/education-profile")
    public ResponseEntity<ApiResponse<EducationProfileData>> updateEducationProfile(
            @PathVariable UUID personId, @Valid @RequestBody EducationProfileRequest request) {
        UpsertResult<EducationProfileData> result =
                directoryService.updateEducationProfile(personId, request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.data()));
    }
}
