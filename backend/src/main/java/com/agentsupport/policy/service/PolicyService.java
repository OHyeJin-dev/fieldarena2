package com.agentsupport.policy.service;

import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.customer.entity.Customer;
import com.agentsupport.customer.repository.CustomerRepository;
import com.agentsupport.policy.dto.PolicyCreateRequest;
import com.agentsupport.policy.dto.PolicyDto;
import com.agentsupport.policy.entity.Policy;
import com.agentsupport.policy.repository.PolicyRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PolicyService {

  private final PolicyRepository policyRepository;
  private final CustomerRepository customerRepository;

  public PolicyService(PolicyRepository policyRepository, CustomerRepository customerRepository) {
    this.policyRepository = policyRepository;
    this.customerRepository = customerRepository;
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

  @Transactional
  public PolicyDto createPolicy(String agentId, boolean isAdmin, PolicyCreateRequest req) {
    Customer customer = customerRepository.findById(req.customerId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found"));

    if (!isAdmin && !customer.getAgentId().equals(agentId)) {
      throw new AccessDeniedException("not allowed");
    }

    String policyNumber = generatePolicyNumber(req.contractDate());

    Policy policy = Policy.create(
        policyNumber,
        agentId,
        customer,
        customer.getName(),
        req.productName(),
        req.insurerName(),
        "심사 중",
        req.contractDate(),
        req.monthlyPremium()
    );

    return PolicyDto.from(policyRepository.save(policy));
  }

  private String generatePolicyNumber(LocalDate contractDate) {
    String prefix = "C-" + contractDate.format(DateTimeFormatter.ofPattern("yyyy-MMdd")) + "-";
    long count = policyRepository.countByPolicyNumberStartingWith(prefix);
    return prefix + String.format("%04d", count + 1);
  }
}
