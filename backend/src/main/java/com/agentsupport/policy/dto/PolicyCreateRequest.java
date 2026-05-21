package com.agentsupport.policy.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyCreateRequest(
    @NotNull UUID customerId,
    @NotBlank String productName,
    @NotBlank String insurerName,
    @NotNull LocalDate contractDate,
    @NotNull @Positive BigDecimal monthlyPremium
) {}
