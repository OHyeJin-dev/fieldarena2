package com.agentsupport.policy;

import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.policy.dto.PolicyDto;
import com.agentsupport.policy.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "계약")
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

  private final PolicyService policyService;

  public PolicyController(PolicyService policyService) {
    this.policyService = policyService;
  }

  @Operation(summary = "계약 목록 조회")
  @GetMapping
  public PageResponse<PolicyDto> list(
      Authentication auth,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate endDate,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    String agentId = auth.getName();
    String statusFilter = status.isBlank() ? null : status;
    return policyService.findPolicies(agentId, statusFilter, startDate, endDate, page, size);
  }
}
