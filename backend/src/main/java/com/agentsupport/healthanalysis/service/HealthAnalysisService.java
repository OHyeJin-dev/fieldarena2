package com.agentsupport.healthanalysis.service;

import com.agentsupport.customer.repository.CustomerRepository;
import com.agentsupport.healthanalysis.ChronicConditions;
import com.agentsupport.healthanalysis.RiskGrade;
import com.agentsupport.healthanalysis.UnderwritingRecommendation;
import com.agentsupport.healthanalysis.dto.DiseaseDto;
import com.agentsupport.healthanalysis.dto.HealthDataPayload;
import com.agentsupport.healthanalysis.repository.HealthAnalysisRepository;
import com.agentsupport.healthanalysis.repository.HealthDataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class HealthAnalysisService {

  private final DummyHealthDataGenerator generator;
  private final HealthDataRepository healthDataRepository;
  private final HealthAnalysisRepository healthAnalysisRepository;
  private final CustomerRepository customerRepository;
  private final ObjectMapper objectMapper;

  public HealthAnalysisService(
      DummyHealthDataGenerator generator,
      HealthDataRepository healthDataRepository,
      HealthAnalysisRepository healthAnalysisRepository,
      CustomerRepository customerRepository,
      ObjectMapper objectMapper
  ) {
    this.generator = generator;
    this.healthDataRepository = healthDataRepository;
    this.healthAnalysisRepository = healthAnalysisRepository;
    this.customerRepository = customerRepository;
    this.objectMapper = objectMapper;
  }

  public record AnalysisOutcome(
      RiskGrade riskGrade,
      UnderwritingRecommendation recommendation,
      boolean hasDisease,
      List<DiseaseDto> diseases,
      String summary
  ) {}

  public AnalysisOutcome analyze(HealthDataPayload payload) {
    Set<String> diagnoses = payload.visits().stream()
        .map(HealthDataPayload.Visit::diagnosisCode)
        .collect(Collectors.toSet());
    Set<String> chronicHeld = diagnoses.stream()
        .filter(ChronicConditions::isChronic)
        .collect(Collectors.toSet());
    boolean hasBothMajorChronic = chronicHeld.contains("I10") && chronicHeld.contains("E11");
    int admissionCount = payload.admissions().size();

    RiskGrade grade;
    UnderwritingRecommendation rec;
    if (admissionCount > 0 || hasBothMajorChronic) {
      grade = RiskGrade.RISK;
      rec = UnderwritingRecommendation.DECLINE;
    } else if (!chronicHeld.isEmpty()) {
      grade = RiskGrade.CAUTION;
      rec = UnderwritingRecommendation.CONDITIONAL;
    } else {
      grade = RiskGrade.NORMAL;
      rec = UnderwritingRecommendation.APPROVE;
    }

    List<DiseaseDto> diseases = chronicHeld.stream()
        .map(code -> buildDisease(code, payload))
        .sorted(Comparator.comparing(DiseaseDto::code))
        .toList();

    String summary = composeSummary(grade, admissionCount, chronicHeld);
    return new AnalysisOutcome(grade, rec, !chronicHeld.isEmpty(), diseases, summary);
  }

  private DiseaseDto buildDisease(String code, HealthDataPayload payload) {
    List<HealthDataPayload.Visit> visitsForCode = payload.visits().stream()
        .filter(v -> code.equals(v.diagnosisCode()))
        .toList();
    LocalDate earliest = visitsForCode.stream()
        .map(HealthDataPayload.Visit::date)
        .min(Comparator.naturalOrder())
        .orElse(LocalDate.now());
    String diagnosedAt = earliest.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    String frequency = "월 " + Math.max(1, visitsForCode.size() / 12) + "회";
    return new DiseaseDto(code, ChronicConditions.CODES.get(code), diagnosedAt, frequency);
  }

  private String composeSummary(RiskGrade grade, int admissionCount, Set<String> chronicHeld) {
    if (grade == RiskGrade.NORMAL) {
      return "유의한 만성 질환 이력이 확인되지 않습니다.";
    }
    if (grade == RiskGrade.RISK && admissionCount > 0) {
      return "최근 입원 이력이 있어 인수 거절을 권고합니다.";
    }
    if (grade == RiskGrade.RISK) {
      return "복합 만성 질환 보유로 인수 거절을 권고합니다.";
    }
    return "만성질환 관리 양호하나 합병증 모니터링 필요";
  }
}
