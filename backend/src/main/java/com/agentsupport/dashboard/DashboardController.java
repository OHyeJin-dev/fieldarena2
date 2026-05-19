package com.agentsupport.dashboard;

import com.agentsupport.dashboard.dto.DashboardSummaryDto;
import com.agentsupport.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대시보드")
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

  private final DashboardService dashboardService;

  public DashboardController(DashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @Operation(summary = "대시보드 요약")
  @GetMapping("/summary")
  public DashboardSummaryDto summary(Authentication auth) {
    return dashboardService.getSummary(auth.getName());
  }
}
