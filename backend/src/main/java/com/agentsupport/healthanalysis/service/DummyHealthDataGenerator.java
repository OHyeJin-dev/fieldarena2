package com.agentsupport.healthanalysis.service;

import com.agentsupport.healthanalysis.Scenario;
import com.agentsupport.healthanalysis.dto.HealthDataPayload;
import com.agentsupport.healthanalysis.dto.HealthDataPayload.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DummyHealthDataGenerator {

  public HealthDataPayload generate(UUID customerId, Scenario scenario, int age, String gender) {
    long seed = scenario == Scenario.RANDOM
        ? customerId.hashCode() ^ System.currentTimeMillis()
        : customerId.hashCode() ^ scenario.ordinal();
    Random random = new Random(seed);

    Scenario effective = scenario == Scenario.RANDOM ? rollRandomScenario(random, age) : scenario;
    Demographics demographics = new Demographics(age, gender);

    return switch (effective) {
      case NORMAL -> normalPayload(demographics, random);
      case HYPERTENSION -> hypertensionPayload(demographics, random);
      case DIABETES -> diabetesPayload(demographics, random);
      case COMPLEX -> complexPayload(demographics, random);
      case RANDOM -> normalPayload(demographics, random); // unreachable but keeps switch exhaustive
    };
  }

  private Scenario rollRandomScenario(Random random, int age) {
    int roll = random.nextInt(100);
    int normalCutoff = age >= 50 ? 40 : 70;
    int singleChronicCutoff = age >= 50 ? 85 : 95;
    if (roll < normalCutoff) return Scenario.NORMAL;
    if (roll < singleChronicCutoff) return random.nextBoolean() ? Scenario.HYPERTENSION : Scenario.DIABETES;
    return Scenario.COMPLEX;
  }

  private HealthDataPayload normalPayload(Demographics d, Random random) {
    List<Visit> visits = new ArrayList<>();
    int count = random.nextInt(3);
    for (int i = 0; i < count; i++) {
      visits.add(new Visit(
          LocalDate.now().minusDays(random.nextInt(365)),
          "J00",
          "급성 비인두염",
          "내과"
      ));
    }
    return new HealthDataPayload(d, visits, List.of(), List.of());
  }

  private HealthDataPayload hypertensionPayload(Demographics d, Random random) {
    List<Visit> visits = new ArrayList<>();
    List<Prescription> rx = new ArrayList<>();
    for (int month = 0; month < 12; month++) {
      LocalDate date = LocalDate.now().minusMonths(month).withDayOfMonth(15);
      visits.add(new Visit(date, "I10", "본태성 고혈압", "내과"));
      rx.add(new Prescription(date, "ARB", 30));
    }
    return new HealthDataPayload(d, visits, rx, List.of());
  }

  private HealthDataPayload diabetesPayload(Demographics d, Random random) {
    List<Visit> visits = new ArrayList<>();
    List<Prescription> rx = new ArrayList<>();
    for (int month = 0; month < 12; month++) {
      LocalDate date = LocalDate.now().minusMonths(month).withDayOfMonth(20);
      visits.add(new Visit(date, "E11", "제2형 당뇨병", "내과"));
      rx.add(new Prescription(date, "Metformin", 30));
    }
    return new HealthDataPayload(d, visits, rx, List.of());
  }

  private HealthDataPayload complexPayload(Demographics d, Random random) {
    List<Visit> visits = new ArrayList<>();
    List<Prescription> rx = new ArrayList<>();
    for (int month = 0; month < 12; month++) {
      LocalDate date = LocalDate.now().minusMonths(month).withDayOfMonth(10);
      visits.add(new Visit(date, "I10", "본태성 고혈압", "내과"));
      visits.add(new Visit(date.plusDays(5), "E11", "제2형 당뇨병", "내과"));
      rx.add(new Prescription(date, "ARB", 30));
      rx.add(new Prescription(date, "Metformin", 30));
    }
    List<Admission> admissions = List.of(new Admission(
        LocalDate.now().minusMonths(6),
        LocalDate.now().minusMonths(6).plusDays(5),
        "E11.9",
        "당뇨 합병증"
    ));
    return new HealthDataPayload(d, visits, rx, admissions);
  }
}
