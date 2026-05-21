package com.agentsupport.healthanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentsupport.healthanalysis.dto.HealthDataPayload;
import com.agentsupport.healthanalysis.service.DummyHealthDataGenerator;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DummyHealthDataGeneratorTest {

  private final DummyHealthDataGenerator generator = new DummyHealthDataGenerator();

  @Test
  void normalScenarioHasNoChronicDiagnosis() {
    HealthDataPayload payload = generator.generate(UUID.randomUUID(), Scenario.NORMAL, 35, "M");
    boolean anyChronic = payload.visits().stream()
        .anyMatch(v -> ChronicConditions.isChronic(v.diagnosisCode()));
    assertThat(anyChronic).isFalse();
    assertThat(payload.admissions()).isEmpty();
  }

  @Test
  void hypertensionScenarioHasI10MonthlyVisits() {
    HealthDataPayload payload = generator.generate(UUID.randomUUID(), Scenario.HYPERTENSION, 50, "M");
    long i10Count = payload.visits().stream()
        .filter(v -> "I10".equals(v.diagnosisCode()))
        .count();
    assertThat(i10Count).isGreaterThanOrEqualTo(10);
    assertThat(payload.prescriptions()).isNotEmpty();
  }

  @Test
  void diabetesScenarioHasE11MonthlyVisits() {
    HealthDataPayload payload = generator.generate(UUID.randomUUID(), Scenario.DIABETES, 55, "F");
    long e11Count = payload.visits().stream()
        .filter(v -> "E11".equals(v.diagnosisCode()))
        .count();
    assertThat(e11Count).isGreaterThanOrEqualTo(10);
  }

  @Test
  void complexScenarioHasBothI10AndE11AndAdmission() {
    HealthDataPayload payload = generator.generate(UUID.randomUUID(), Scenario.COMPLEX, 60, "M");
    boolean hasI10 = payload.visits().stream().anyMatch(v -> "I10".equals(v.diagnosisCode()));
    boolean hasE11 = payload.visits().stream().anyMatch(v -> "E11".equals(v.diagnosisCode()));
    assertThat(hasI10).isTrue();
    assertThat(hasE11).isTrue();
    assertThat(payload.admissions()).isNotEmpty();
  }

  @Test
  void sameSeededScenarioProducesSameResult() {
    UUID customerId = UUID.randomUUID();
    HealthDataPayload p1 = generator.generate(customerId, Scenario.HYPERTENSION, 50, "M");
    HealthDataPayload p2 = generator.generate(customerId, Scenario.HYPERTENSION, 50, "M");
    assertThat(p1.visits()).hasSize(p2.visits().size());
  }
}
