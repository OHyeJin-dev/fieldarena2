package com.agentsupport.policy.dto;

import com.agentsupport.policy.entity.Policy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyDto(
    UUID id,
    String policyNumber,
    String customerName,
    String productName,
    String insurerName,
    String status,
    LocalDate contractDate,
    BigDecimal monthlyPremium) {

  public static PolicyDto from(Policy p) {
    return new PolicyDto(
        p.getId(),
        p.getPolicyNumber(),
        p.getCustomerName(),
        p.getProductName(),
        p.getInsurerName(),
        p.getStatus(),
        p.getContractDate(),
        p.getMonthlyPremium());
  }
}
