package com.agentsupport.claim.dto;

import com.agentsupport.claim.entity.Claim;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimDto(
    UUID id,
    String policyNumber,
    String customerName,
    String insurerName,
    String claimType,
    BigDecimal claimAmount,
    String status,
    LocalDate claimDate) {

  public static ClaimDto from(Claim c) {
    return new ClaimDto(
        c.getId(),
        c.getPolicyNumber(),
        c.getCustomerName(),
        c.getInsurerName(),
        c.getClaimType(),
        c.getClaimAmount(),
        c.getStatus(),
        c.getClaimDate());
  }
}
