package com.familyconnect.modules.heritage.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.modules.heritage.dto.HeritageRequests.CreateDocument;
import com.familyconnect.modules.heritage.dto.HeritageRequests.CreateStory;
import com.familyconnect.modules.heritage.dto.HeritageResponses.DocumentData;
import com.familyconnect.modules.heritage.dto.HeritageResponses.ListResponse;
import com.familyconnect.modules.heritage.dto.HeritageResponses.StoryData;
import com.familyconnect.modules.heritage.service.HeritageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/heritage")
public class HeritageController {

    private final HeritageService heritageService;

    public HeritageController(HeritageService heritageService) {
        this.heritageService = heritageService;
    }

    @GetMapping("/documents")
    public ListResponse<DocumentData> listDocuments(
            @RequestParam(name = "familyId", required = false) UUID familyId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort) {
        return heritageService.listDocuments(familyId, category, page, size, ascending(sort));
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentData> createDocument(
            @RequestParam(name = "familyId", required = false) UUID familyId,
            @Valid @RequestBody CreateDocument request) {
        return ApiResponse.ok(heritageService.createDocument(familyId, request));
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<DocumentData> getDocument(@PathVariable UUID id) {
        return ApiResponse.ok(heritageService.getDocument(id));
    }

    @PatchMapping("/documents/{id}")
    public ApiResponse<DocumentData> updateDocument(@PathVariable UUID id, @RequestBody JsonNode patch) {
        return ApiResponse.ok(heritageService.updateDocument(id, patch));
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable UUID id) {
        heritageService.deleteDocument(id);
        return ApiResponse.empty();
    }

    @GetMapping("/stories")
    public ListResponse<StoryData> listStories(
            @RequestParam(name = "familyId", required = false) UUID familyId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", defaultValue = "createdAt,desc") String sort) {
        return heritageService.listStories(familyId, page, size, ascending(sort));
    }

    @PostMapping("/stories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StoryData> createStory(
            @RequestParam(name = "familyId", required = false) UUID familyId,
            @Valid @RequestBody CreateStory request) {
        return ApiResponse.ok(heritageService.createStory(familyId, request));
    }

    @GetMapping("/stories/{id}")
    public ApiResponse<StoryData> getStory(@PathVariable UUID id) {
        return ApiResponse.ok(heritageService.getStory(id));
    }

    @PatchMapping("/stories/{id}")
    public ApiResponse<StoryData> updateStory(@PathVariable UUID id, @RequestBody JsonNode patch) {
        return ApiResponse.ok(heritageService.updateStory(id, patch));
    }

    @DeleteMapping("/stories/{id}")
    public ApiResponse<Void> deleteStory(@PathVariable UUID id) {
        heritageService.deleteStory(id);
        return ApiResponse.empty();
    }

    private static boolean ascending(String sort) {
        return switch (sort) {
            case "createdAt,asc" -> true;
            case "createdAt,desc" -> false;
            default -> throw com.familyconnect.common.exception.ApiException
                    .invalidField("sort", "Chỉ hỗ trợ createdAt,asc hoặc createdAt,desc");
        };
    }
}
