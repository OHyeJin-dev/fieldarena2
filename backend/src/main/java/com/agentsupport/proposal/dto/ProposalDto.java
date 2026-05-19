package com.agentsupport.proposal.dto;

import com.agentsupport.proposal.entity.Proposal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

public record ProposalDto(
    UUID id,
    String customerName,
    String phoneNumber,
    String age,
    String productName,
    String insurerName,
    BigDecimal monthlyPremium,
    String status,
    LocalDate proposedDate) {

  public static ProposalDto from(Proposal p) {
    return new ProposalDto(
        p.getId(),
        maskName(p.getCustomerName()),
        maskPhone(p.getPhoneNumber()),
        maskAge(p.getBirthDate()),
        p.getProductName(),
        p.getInsurerName(),
        p.getMonthlyPremium(),
        p.getStatus(),
        p.getProposedDate());
  }

  private static String maskName(String name) {
    if (name == null || name.isBlank()) return "-";
    return name.substring(0, 1) + "○".repeat(Math.max(1, name.length() - 1));
  }

  private static String maskPhone(String phone) {
    if (phone == null || phone.isBlank()) return "-";
    return phone.replaceAll("^(\\d{3}-)\\d{3,4}(-\\d{4})$", "$1****$2");
  }

  private static String maskAge(String birthDate) {
    if (birthDate == null || birthDate.isBlank()) return "-";
    try {
      int age = Period.between(LocalDate.parse(birthDate), LocalDate.now()).getYears();
      return String.valueOf(age).charAt(0) + "*세";
    } catch (Exception e) {
      return "-";
    }
  }
}
