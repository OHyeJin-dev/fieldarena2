package com.agentsupport.proposal.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProposalCreateRequest(
    @NotBlank String customerName,
    @NotBlank
        @Pattern(
            regexp = "^010-\\d{3,4}-\\d{4}$",
            message = "올바른 휴대폰번호 형식이 아닙니다 (예: 010-0000-0000)")
        String phoneNumber,
    @NotBlank
        @Pattern(
            regexp = "^\\d{4}-\\d{2}-\\d{2}$",
            message = "날짜 형식은 YYYY-MM-DD 이어야 합니다")
        String birthDate,
    @NotBlank String productName,
    @NotBlank String insurerName,
    @NotNull @Positive BigDecimal monthlyPremium) {}
