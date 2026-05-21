package com.agentsupport.proposal.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record ProposalCreateRequest(
    @NotNull UUID customerId,
    @NotBlank String productName,
    @NotBlank String insurerName,
    @NotNull @Positive BigDecimal monthlyPremium) {}
