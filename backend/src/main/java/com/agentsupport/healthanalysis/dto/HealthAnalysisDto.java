package com.agentsupport.healthanalysis.dto;

import com.agentsupport.healthanalysis.RiskGrade;
import com.agentsupport.healthanalysis.UnderwritingRecommendation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record HealthAnalysisDto(
    UUID id,
    UUID customerId,
    String customerName,
    RiskGrade riskGrade,
    boolean hasDisease,
    List<DiseaseDto> diseases,
    UnderwritingRecommendation underwritingRecommendation,
    String summary,
    LocalDateTime analyzedAt,
    String analyzedBy
) {}
