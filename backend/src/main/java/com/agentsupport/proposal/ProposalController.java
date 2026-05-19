package com.agentsupport.proposal;

import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.proposal.dto.ProposalCreateRequest;
import com.agentsupport.proposal.dto.ProposalDto;
import com.agentsupport.proposal.service.ProposalService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "설계")
@RestController
@RequestMapping("/api/proposals")
public class ProposalController {

  private final ProposalService proposalService;

  public ProposalController(ProposalService proposalService) {
    this.proposalService = proposalService;
  }

  @GetMapping
  public PageResponse<ProposalDto> list(
      Authentication auth,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return proposalService.findProposals(auth.getName(), status, page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProposalDto create(
      Authentication auth,
      @Valid @RequestBody ProposalCreateRequest request) {
    return proposalService.createProposal(auth.getName(), request);
  }
}
