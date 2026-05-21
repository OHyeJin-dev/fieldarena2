package com.agentsupport.healthanalysis.service;

import com.agentsupport.customer.repository.CustomerRepository;
import com.agentsupport.healthanalysis.ChronicConditions;
import com.agentsupport.healthanalysis.RiskGrade;
import com.agentsupport.healthanalysis.Scenario;
import com.agentsupport.healthanalysis.UnderwritingRecommendation;
import com.agentsupport.healthanalysis.dto.AnalysisSummaryDto;
import com.agentsupport.healthanalysis.dto.DiseaseDto;
import com.agentsupport.healthanalysis.dto.HealthAnalysisDto;
import com.agentsupport.healthanalysis.dto.HealthDataPayload;
import com.agentsupport.healthanalysis.dto.RecentAnalysisItemDto;
import com.agentsupport.healthanalysis.entity.HealthAnalysis;
import com.agentsupport.healthanalysis.entity.HealthData;
import com.agentsupport.healthanalysis.repository.HealthAnalysisRepository;
import com.agentsupport.healthanalysis.repository.HealthDataRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agentsupport.customer.entity.Customer;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  // ── Task 10: createOrUpdate ──────────────────────────────────────────────

  @Transactional
  public HealthAnalysisDto createOrUpdate(UUID customerId, Scenario scenario, String agentId, boolean isAdmin) {
    Customer customer = customerRepository.findById(customerId)
        .orElseThrow(() -> new IllegalArgumentException("customer not found: " + customerId));

    if (!isAdmin && !agentId.equals(customer.getAgentId())) {
      throw new AccessDeniedException("not allowed: customer belongs to another agent");
    }

    // 1. 더미 데이터 생성
    HealthDataPayload payload = generator.generate(
        customerId, scenario, computeAge(customer), customer.getGender());
    String payloadJson = serialize(payload);

    // 2. HealthData 저장 (누적)
    HealthData savedData = healthDataRepository.save(
        HealthData.create(customerId, "NHIS_DUMMY", scenario.name(), payloadJson, agentId)
    );

    // 3. 분석
    AnalysisOutcome outcome = analyze(payload);
    String diseasesJson = serialize(outcome.diseases());

    // 4. UPSERT
    HealthAnalysis analysis = healthAnalysisRepository.findByCustomerId(customerId).orElse(null);
    if (analysis == null) {
      analysis = HealthAnalysis.create(
          customerId, savedData.getId(),
          outcome.riskGrade(), outcome.hasDisease(), diseasesJson,
          outcome.recommendation(), outcome.summary(), agentId
      );
    } else {
      analysis.replaceWith(
          savedData.getId(),
          outcome.riskGrade(), outcome.hasDisease(), diseasesJson,
          outcome.recommendation(), outcome.summary(), agentId
      );
    }
    HealthAnalysis saved = healthAnalysisRepository.save(analysis);

    return toDto(saved, customer);
  }

  private int computeAge(Customer customer) {
    if (customer.getBirthDate() == null) return 40;
    return Period.between(customer.getBirthDate(), LocalDate.now()).getYears();
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize health data", e);
    }
  }

  private <T> T deserialize(String json, TypeReference<T> typeRef) {
    try {
      return objectMapper.readValue(json, typeRef);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Failed to deserialize health data", e);
    }
  }

  private HealthAnalysisDto toDto(HealthAnalysis a, Customer customer) {
    List<DiseaseDto> diseases = deserialize(
        a.getDiseases(),
        new TypeReference<List<DiseaseDto>>() {}
    );
    return new HealthAnalysisDto(
        a.getId(), a.getCustomerId(), customer.getName(),
        a.getRiskGrade(), a.isHasDisease(), diseases,
        a.getUnderwritingRecommendation(), a.getSummary(),
        a.getAnalyzedAt(), a.getAnalyzedBy()
    );
  }

  // ── Task 11: Query methods ───────────────────────────────────────────────

  public Map<UUID, HealthAnalysisDto> findByCustomerIds(
      List<UUID> customerIds, String agentId, boolean isAdmin
  ) {
    List<UUID> allowedCustomerIds;
    if (isAdmin) {
      allowedCustomerIds = customerIds;
    } else {
      allowedCustomerIds = customerRepository.findAllById(customerIds).stream()
          .filter(c -> agentId.equals(c.getAgentId()))
          .map(Customer::getId)
          .toList();
    }
    if (allowedCustomerIds.isEmpty()) return Map.of();

    Map<UUID, Customer> customerById = customerRepository.findAllById(allowedCustomerIds).stream()
        .collect(Collectors.toMap(Customer::getId, c -> c));

    return healthAnalysisRepository.findByCustomerIdIn(allowedCustomerIds).stream()
        .collect(Collectors.toMap(
            HealthAnalysis::getCustomerId,
            a -> toDto(a, customerById.get(a.getCustomerId()))
        ));
  }

  public HealthAnalysisDto findById(UUID id, String agentId, boolean isAdmin) {
    HealthAnalysis analysis = healthAnalysisRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("analysis not found: " + id));
    Customer customer = customerRepository.findById(analysis.getCustomerId())
        .orElseThrow(() -> new IllegalArgumentException("customer not found"));
    if (!isAdmin && !agentId.equals(customer.getAgentId())) {
      throw new AccessDeniedException("not allowed");
    }
    return toDto(analysis, customer);
  }

  public AnalysisSummaryDto summary(String agentId, boolean isAdmin) {
    if (isAdmin) {
      long total = healthAnalysisRepository.count();
      long normal = healthAnalysisRepository.countByRiskGrade(RiskGrade.NORMAL);
      long caution = healthAnalysisRepository.countByRiskGrade(RiskGrade.CAUTION);
      long risk = healthAnalysisRepository.countByRiskGrade(RiskGrade.RISK);
      return new AnalysisSummaryDto(total, normal, caution, risk);
    }
    long total = healthAnalysisRepository.countByAnalyzedBy(agentId);
    long normal = healthAnalysisRepository.countByAnalyzedByAndRiskGrade(agentId, RiskGrade.NORMAL);
    long caution = healthAnalysisRepository.countByAnalyzedByAndRiskGrade(agentId, RiskGrade.CAUTION);
    long risk = healthAnalysisRepository.countByAnalyzedByAndRiskGrade(agentId, RiskGrade.RISK);
    return new AnalysisSummaryDto(total, normal, caution, risk);
  }

  public List<RecentAnalysisItemDto> recent(int limit, String agentId, boolean isAdmin) {
    Pageable pageable = PageRequest.of(0, limit);
    List<HealthAnalysis> analyses = isAdmin
        ? healthAnalysisRepository.findAllRecent(pageable)
        : healthAnalysisRepository.findRecentByAnalyzedBy(agentId, pageable);

    if (analyses.isEmpty()) return List.of();
    List<UUID> customerIds = analyses.stream()
        .map(HealthAnalysis::getCustomerId).toList();
    Map<UUID, Customer> customerById = customerRepository.findAllById(customerIds).stream()
        .collect(Collectors.toMap(Customer::getId, c -> c));

    return analyses.stream()
        .map(a -> new RecentAnalysisItemDto(
            a.getId(), a.getCustomerId(),
            customerById.get(a.getCustomerId()).getName(),
            a.getRiskGrade(), a.getAnalyzedAt()))
        .toList();
  }
}
