package com.agentsupport.healthanalysis.dto;

public record DiseaseDto(
    String code,
    String name,
    String diagnosedAt,
    String frequency
) {}
