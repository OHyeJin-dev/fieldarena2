package com.agentsupport.claim.service;

import com.agentsupport.claim.dto.ClaimDto;
import com.agentsupport.claim.repository.ClaimRepository;
import com.agentsupport.common.dto.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClaimService {

  private final ClaimRepository claimRepository;

  public ClaimService(ClaimRepository claimRepository) {
    this.claimRepository = claimRepository;
  }

  public PageResponse<ClaimDto> findClaims(String agentId, String status, int page, int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    String statusFilter = (status == null || status.isBlank()) ? null : status;
    return PageResponse.from(
        claimRepository.findByCondition(agentId, statusFilter, pageable).map(ClaimDto::from));
  }
}
