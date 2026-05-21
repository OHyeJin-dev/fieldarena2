package com.agentsupport.healthanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentsupport.healthanalysis.dto.HealthDataPayload;
import com.agentsupport.healthanalysis.dto.HealthDataPayload.*;
import com.agentsupport.healthanalysis.service.HealthAnalysisService;
import com.agentsupport.healthanalysis.service.HealthAnalysisService.AnalysisOutcome;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HealthAnalysisServiceTest {

  private final HealthAnalysisService service = new HealthAnalysisService(null, null, null, null, null);

  @Test
  void normalPayloadProducesNormalGradeAndApprove() {
    HealthDataPayload payload = new HealthDataPayload(
        new Demographics(30, "M"),
        List.of(new Visit(LocalDate.now().minusDays(60), "J00", "급성 비인두염", "내과")),
        List.of(), List.of()
    );
    AnalysisOutcome outcome = service.analyze(payload);
    assertThat(outcome.riskGrade()).isEqualTo(RiskGrade.NORMAL);
    assertThat(outcome.recommendation()).isEqualTo(UnderwritingRecommendation.APPROVE);
    assertThat(outcome.hasDisease()).isFalse();
    assertThat(outcome.diseases()).isEmpty();
  }

  @Test
  void singleChronicProducesCautionAndConditional() {
    HealthDataPayload payload = new HealthDataPayload(
        new Demographics(50, "M"),
        List.of(
            new Visit(LocalDate.now().minusMonths(6), "I10", "본태성 고혈압", "내과"),
            new Visit(LocalDate.now().minusMonths(3), "I10", "본태성 고혈압", "내과")
        ),
        List.of(), List.of()
    );
    AnalysisOutcome outcome = service.analyze(payload);
    assertThat(outcome.riskGrade()).isEqualTo(RiskGrade.CAUTION);
    assertThat(outcome.recommendation()).isEqualTo(UnderwritingRecommendation.CONDITIONAL);
    assertThat(outcome.hasDisease()).isTrue();
    assertThat(outcome.diseases()).hasSize(1);
    assertThat(outcome.diseases().get(0).code()).isEqualTo("I10");
  }

  @Test
  void bothChronicProducesRiskAndDecline() {
    HealthDataPayload payload = new HealthDataPayload(
        new Demographics(55, "F"),
        List.of(
            new Visit(LocalDate.now().minusMonths(6), "I10", "본태성 고혈압", "내과"),
            new Visit(LocalDate.now().minusMonths(4), "E11", "제2형 당뇨병", "내과")
        ),
        List.of(), List.of()
    );
    AnalysisOutcome outcome = service.analyze(payload);
    assertThat(outcome.riskGrade()).isEqualTo(RiskGrade.RISK);
    assertThat(outcome.recommendation()).isEqualTo(UnderwritingRecommendation.DECLINE);
    assertThat(outcome.diseases()).hasSize(2);
  }

  @Test
  void admissionAloneProducesRiskAndDecline() {
    HealthDataPayload payload = new HealthDataPayload(
        new Demographics(40, "M"),
        List.of(new Visit(LocalDate.now().minusMonths(2), "S52", "팔뼈 골절", "정형외과")),
        List.of(),
        List.of(new Admission(LocalDate.now().minusMonths(2), LocalDate.now().minusMonths(2).plusDays(7), "S52", "골절 수술"))
    );
    AnalysisOutcome outcome = service.analyze(payload);
    assertThat(outcome.riskGrade()).isEqualTo(RiskGrade.RISK);
    assertThat(outcome.recommendation()).isEqualTo(UnderwritingRecommendation.DECLINE);
  }
}
