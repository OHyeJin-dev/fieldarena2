package com.agentsupport.proposal.service;

import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.proposal.dto.ProposalCreateRequest;
import com.agentsupport.proposal.dto.ProposalDto;
import com.agentsupport.proposal.entity.Proposal;
import com.agentsupport.proposal.repository.ProposalRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProposalService {

  private final ProposalRepository proposalRepository;

  public ProposalService(ProposalRepository proposalRepository) {
    this.proposalRepository = proposalRepository;
  }

  public PageResponse<ProposalDto> findProposals(String agentId, String status, int page, int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    String statusFilter = (status == null || status.isBlank()) ? null : status;
    return PageResponse.from(
        proposalRepository.findByCondition(agentId, statusFilter, pageable).map(ProposalDto::from));
  }

  @Transactional
  public ProposalDto createProposal(String agentId, ProposalCreateRequest req) {
    Proposal proposal = Proposal.create(
        agentId,
        req.customerName(),
        req.phoneNumber(),
        req.birthDate().toString(),
        req.productName(),
        req.insurerName(),
        req.monthlyPremium());
    return ProposalDto.from(proposalRepository.save(proposal));
  }
}
