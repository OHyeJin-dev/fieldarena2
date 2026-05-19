package com.agentsupport.claim;

import com.agentsupport.claim.dto.ClaimDto;
import com.agentsupport.claim.service.ClaimService;
import com.agentsupport.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "청구")
@RestController
@RequestMapping("/api/claims")
public class ClaimController {

  private final ClaimService claimService;

  public ClaimController(ClaimService claimService) {
    this.claimService = claimService;
  }

  @GetMapping
  public PageResponse<ClaimDto> list(
      Authentication auth,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return claimService.findClaims(auth.getName(), status, page, size);
  }
}
