package com.agentsupport.healthanalysis.dto;

public record AnalysisSummaryDto(
    long total,
    long normal,
    long caution,
    long risk
) {}
