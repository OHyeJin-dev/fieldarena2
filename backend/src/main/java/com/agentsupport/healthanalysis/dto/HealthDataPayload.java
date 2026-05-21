package com.agentsupport.healthanalysis.dto;

import java.time.LocalDate;
import java.util.List;

public record HealthDataPayload(
    Demographics demographics,
    List<Visit> visits,
    List<Prescription> prescriptions,
    List<Admission> admissions
) {
  public record Demographics(int age, String gender) {}
  public record Visit(LocalDate date, String diagnosisCode, String diagnosisName, String department) {}
  public record Prescription(LocalDate date, String drugClass, int days) {}
  public record Admission(LocalDate fromDate, LocalDate toDate, String diagnosisCode, String reason) {}
}
