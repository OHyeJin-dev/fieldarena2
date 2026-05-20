package com.agentsupport.dashboard.service;

import com.agentsupport.claim.repository.ClaimRepository;
import com.agentsupport.customer.repository.CustomerRepository;
import com.agentsupport.dashboard.dto.DashboardSummaryDto;
import com.agentsupport.policy.repository.PolicyRepository;
import com.agentsupport.proposal.dto.ProposalDto;
import com.agentsupport.proposal.repository.ProposalRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

  private final PolicyRepository policyRepository;
  private final ProposalRepository proposalRepository;
  private final ClaimRepository claimRepository;
  private final CustomerRepository customerRepository;

  public DashboardService(
      PolicyRepository policyRepository,
      ProposalRepository proposalRepository,
      ClaimRepository claimRepository,
      CustomerRepository customerRepository) {
    this.policyRepository = policyRepository;
    this.proposalRepository = proposalRepository;
    this.claimRepository = claimRepository;
    this.customerRepository = customerRepository;
  }

  public DashboardSummaryDto getSummary(String agentId) {
    LocalDate today = LocalDate.now();
    LocalDate firstOfMonth = today.withDayOfMonth(1);

    long activeProposals = proposalRepository.countActiveByAgentId(agentId);
    long underwritingPending = policyRepository.countPendingByAgentId(agentId);
    long claimsInProgress = claimRepository.countInProgressByAgentId(agentId);
    long monthlyProposals =
        proposalRepository.countByAgentIdAndProposedDateBetween(agentId, firstOfMonth, today);
    long myCustomers = customerRepository.countByAgentId(agentId);
    long monthlyClaims =
        claimRepository.countByAgentIdAndClaimDateBetween(agentId, firstOfMonth, today);

    List<ProposalDto> recentProposals =
        proposalRepository.findTop5ByAgentIdOrderByProposedDateDesc(agentId).stream()
            .map(ProposalDto::from)
            .toList();

    return new DashboardSummaryDto(
        activeProposals, underwritingPending, claimsInProgress, monthlyProposals,
        myCustomers, monthlyClaims, recentProposals);
  }
}
