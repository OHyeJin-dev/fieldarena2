package com.agentsupport.claim.service;

import com.agentsupport.claim.dto.ClaimCreateRequest;
import com.agentsupport.claim.dto.ClaimDto;
import com.agentsupport.claim.entity.Claim;
import com.agentsupport.claim.repository.ClaimRepository;
import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.customer.entity.Customer;
import com.agentsupport.customer.repository.CustomerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ClaimService {

  private final ClaimRepository claimRepository;
  private final CustomerRepository customerRepository;

  public ClaimService(ClaimRepository claimRepository, CustomerRepository customerRepository) {
    this.claimRepository = claimRepository;
    this.customerRepository = customerRepository;
  }

  public PageResponse<ClaimDto> findClaims(String agentId, String status, int page, int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    String statusFilter = (status == null || status.isBlank()) ? null : status;
    return PageResponse.from(
        claimRepository.findByCondition(agentId, statusFilter, pageable).map(ClaimDto::from));
  }

  @Transactional
  public ClaimDto create(String agentId, ClaimCreateRequest req) {
    Customer customer = customerRepository
        .findByIdAndAgentId(req.customerId(), agentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 고객이 아닙니다"));

    Claim claim = Claim.create(
        agentId,
        customer,
        req.policyNumber(),
        customer.getName(),
        req.insurerName(),
        req.claimType(),
        req.claimAmount(),
        "접수",
        req.claimDate());

    return ClaimDto.from(claimRepository.save(claim));
  }
}
