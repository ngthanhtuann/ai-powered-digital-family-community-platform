package com.familyconnect.modules.community.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.modules.community.dto.CommunityDto.ReportInput;
import com.familyconnect.modules.community.dto.CommunityDto.ReportView;
import com.familyconnect.modules.community.service.ContentReportService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** BR-COM-12: báo cáo nội dung vi phạm; quản trị xử lý ở /admin/content-reports (UC-AUTH-09). */
@RestController
@RequestMapping("/api/v1/content-reports")
public class ContentReportController {

    private final ContentReportService reports;

    public ContentReportController(ContentReportService reports) {
        this.reports = reports;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createContentReport", summary = "Báo cáo nội dung vi phạm")
    public ApiResponse<ReportView> report(@Valid @RequestBody ReportInput body) {
        return ApiResponse.ok(reports.report(body));
    }
}
