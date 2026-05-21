package com.agentsupport.healthanalysis.dto;

import com.agentsupport.healthanalysis.RiskGrade;
import java.time.LocalDateTime;
import java.util.UUID;

public record RecentAnalysisItemDto(
    UUID id,
    UUID customerId,
    String customerName,
    RiskGrade riskGrade,
    LocalDateTime analyzedAt
) {}
