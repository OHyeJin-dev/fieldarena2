package com.agentsupport.dashboard.dto;

import com.agentsupport.proposal.dto.ProposalDto;
import java.util.List;

public record DashboardSummaryDto(
    long activeProposals,
    long underwritingPending,
    long claimsInProgress,
    long monthlyProposals,
    long myCustomers,
    long monthlyClaims,
    List<ProposalDto> recentProposals) {}