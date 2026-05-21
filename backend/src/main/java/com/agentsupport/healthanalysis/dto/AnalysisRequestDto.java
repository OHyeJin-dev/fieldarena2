package com.agentsupport.healthanalysis.dto;

import com.agentsupport.healthanalysis.Scenario;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AnalysisRequestDto(
    @NotNull UUID customerId,
    @NotNull Scenario scenario
) {}
