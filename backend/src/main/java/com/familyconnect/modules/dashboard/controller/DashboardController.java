package com.familyconnect.modules.dashboard.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.modules.dashboard.dto.DashboardResponses.DashboardStatisticsData;
import com.familyconnect.modules.dashboard.service.DashboardService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
}
