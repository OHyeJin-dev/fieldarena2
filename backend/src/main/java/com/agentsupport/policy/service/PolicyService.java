package com.agentsupport.policy.service;

import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.policy.dto.PolicyDto;
import com.agentsupport.policy.repository.PolicyRepository;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PolicyService {

  private final PolicyRepository policyRepository;

  public PolicyService(PolicyRepository policyRepository) {
    this.policyRepository = policyRepository;
  }

  public PageResponse<PolicyDto> findPolicies(
      String agentId,
      String status,
      LocalDate startDate,
      LocalDate endDate,
      int page,
      int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    return PageResponse.from(
        policyRepository
            .findByCondition(agentId, status, startDate, endDate, pageable)
            .map(PolicyDto::from));
  }
}
