package com.agentsupport.claim.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimCreateRequest(
    @NotNull UUID customerId,
    @NotBlank @Size(max = 20) String policyNumber,
    @NotBlank @Size(max = 50) String insurerName,
    @NotBlank @Size(max = 50) String claimType,
    BigDecimal claimAmount,
    @NotNull LocalDate claimDate) {}
