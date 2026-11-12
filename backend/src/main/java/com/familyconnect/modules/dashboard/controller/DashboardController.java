package com.familyconnect.modules.dashboard.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.modules.dashboard.dto.DashboardResponses.DashboardStatisticsData;
import com.familyconnect.modules.dashboard.service.DashboardService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/statistics")
    public ApiResponse<DashboardStatisticsData> statistics(
            @RequestParam(required = false) UUID familyId,
            @RequestParam(required = false) UUID branchId) {
        return ApiResponse.ok(dashboardService.getStatistics(familyId, branchId));
    }

    @GetMapping(value = "/reports/members", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportMembers(
            @RequestParam(required = false) UUID familyId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "csv") String format) {
        if (!"csv".equalsIgnoreCase(format)) {
            throw ApiException.invalidField("format", "Hiện chỉ hỗ trợ format=csv");
        }
        String fileName = "members-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(dashboardService.exportMembersCsv(familyId, branchId));
    }

    @GetMapping(value = "/reports/{reportType}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportStatisticsPdf(
            @PathVariable String reportType,
            @RequestParam(required = false) UUID familyId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String safeReportType = reportType.replaceAll("[^A-Za-z0-9_-]", "");
        String fileName = "report-" + safeReportType.toLowerCase(Locale.ROOT) + "-"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".pdf";
        byte[] report = dashboardService.exportStatisticsPdf(reportType, familyId, branchId, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(report);
    }
}
