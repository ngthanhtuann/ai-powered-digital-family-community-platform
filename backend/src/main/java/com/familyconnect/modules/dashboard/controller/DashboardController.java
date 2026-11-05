package com.familyconnect.modules.dashboard.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.modules.dashboard.dto.DashboardResponses.DashboardStatisticsData;
import com.familyconnect.modules.dashboard.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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
}
