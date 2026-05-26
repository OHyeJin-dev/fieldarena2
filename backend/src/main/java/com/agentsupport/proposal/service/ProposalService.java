package com.agentsupport.proposal.service;

import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.customer.entity.Customer;
import com.agentsupport.customer.repository.CustomerRepository;
import com.agentsupport.proposal.dto.ProposalCreateRequest;
import com.agentsupport.proposal.dto.ProposalDto;
import com.agentsupport.proposal.entity.Proposal;
import com.agentsupport.proposal.repository.ProposalRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ProposalService {

  private final ProposalRepository proposalRepository;
  private final CustomerRepository customerRepository;

  public ProposalService(ProposalRepository proposalRepository, CustomerRepository customerRepository) {
    this.proposalRepository = proposalRepository;
    this.customerRepository = customerRepository;
  }

  public PageResponse<ProposalDto> findProposals(String agentId, String status, int page, int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    String statusFilter = (status == null || status.isBlank()) ? null : status;
    return PageResponse.from(
        proposalRepository.findByCondition(agentId, statusFilter, pageable).map(ProposalDto::from));
  }

  @Transactional
  public ProposalDto createProposal(String agentId, boolean isAdmin, ProposalCreateRequest req) {
    Customer customer = customerRepository.findById(req.customerId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found: " + req.customerId()));

    if (!isAdmin && !customer.getAgentId().equals(agentId)) {
      throw new AccessDeniedException("not allowed to create proposal for customer not owned by this agent");
    }

    Proposal proposal = Proposal.create(
        agentId,
        customer,
        customer.getName(),
        customer.getPhone(),
        customer.getBirthDate() == null ? null : customer.getBirthDate().toString(),
        req.productName(),
        req.insurerName(),
        req.monthlyPremium());
    return ProposalDto.from(proposalRepository.save(proposal));
  }
}
