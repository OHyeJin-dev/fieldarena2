package com.agentsupport.healthanalysis;

import com.agentsupport.healthanalysis.dto.*;
import com.agentsupport.healthanalysis.service.HealthAnalysisService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/health-analyses")
public class HealthAnalysisController {

  private final HealthAnalysisService service;

  public HealthAnalysisController(HealthAnalysisService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<HealthAnalysisDto> create(
      @RequestBody @Valid AnalysisRequestDto req,
      Authentication auth
  ) {
    return ResponseEntity.ok(
        service.createOrUpdate(req.customerId(), req.scenario(), auth.getName(), isAdmin(auth)));
  }

  @GetMapping
  public Map<UUID, HealthAnalysisDto> byCustomers(
      @RequestParam("customerIds") List<UUID> customerIds,
      Authentication auth
  ) {
    return service.findByCustomerIds(customerIds, auth.getName(), isAdmin(auth));
  }

  @GetMapping("/{id}")
  public HealthAnalysisDto byId(@PathVariable UUID id, Authentication auth) {
    return service.findById(id, auth.getName(), isAdmin(auth));
  }

  @GetMapping("/summary")
  public AnalysisSummaryDto summary(Authentication auth) {
    return service.summary(auth.getName(), isAdmin(auth));
  }

  @GetMapping("/recent")
  public List<RecentAnalysisItemDto> recent(
      @RequestParam(defaultValue = "5") int limit,
      Authentication auth
  ) {
    return service.recent(limit, auth.getName(), isAdmin(auth));
  }

  private boolean isAdmin(Authentication auth) {
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch("ROLE_ADMIN"::equals);
  }
}
