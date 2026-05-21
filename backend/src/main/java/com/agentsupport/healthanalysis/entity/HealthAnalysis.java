package com.agentsupport.healthanalysis.entity;

import com.agentsupport.healthanalysis.RiskGrade;
import com.agentsupport.healthanalysis.UnderwritingRecommendation;
import com.agentsupport.security.PiiAttributeConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "health_analyses")
public class HealthAnalysis {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false, unique = true)
  private UUID customerId;

  @Column(name = "health_data_id", nullable = false)
  private UUID healthDataId;

  @Enumerated(EnumType.STRING)
  @Column(name = "risk_grade", nullable = false, length = 10)
  private RiskGrade riskGrade;

  @Column(name = "has_disease", nullable = false)
  private boolean hasDisease;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, columnDefinition = "TEXT")
  private String diseases;

  @Enumerated(EnumType.STRING)
  @Column(name = "underwriting_recommendation", nullable = false, length = 20)
  private UnderwritingRecommendation underwritingRecommendation;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String summary;

  @UpdateTimestamp
  @Column(name = "analyzed_at", nullable = false)
  private LocalDateTime analyzedAt;

  @Column(name = "analyzed_by", nullable = false, length = 50)
  private String analyzedBy;

  protected HealthAnalysis() {}

  public static HealthAnalysis create(
      UUID customerId, UUID healthDataId,
      RiskGrade riskGrade, boolean hasDisease, String diseases,
      UnderwritingRecommendation rec, String summary, String analyzedBy
  ) {
    HealthAnalysis a = new HealthAnalysis();
    a.customerId = customerId;
    a.healthDataId = healthDataId;
    a.riskGrade = riskGrade;
    a.hasDisease = hasDisease;
    a.diseases = diseases;
    a.underwritingRecommendation = rec;
    a.summary = summary;
    a.analyzedBy = analyzedBy;
    return a;
  }

  public void replaceWith(
      UUID healthDataId,
      RiskGrade riskGrade, boolean hasDisease, String diseases,
      UnderwritingRecommendation rec, String summary, String analyzedBy
  ) {
    this.healthDataId = healthDataId;
    this.riskGrade = riskGrade;
    this.hasDisease = hasDisease;
    this.diseases = diseases;
    this.underwritingRecommendation = rec;
    this.summary = summary;
    this.analyzedBy = analyzedBy;
  }

  public UUID getId() { return id; }
  public UUID getCustomerId() { return customerId; }
  public UUID getHealthDataId() { return healthDataId; }
  public RiskGrade getRiskGrade() { return riskGrade; }
  public boolean isHasDisease() { return hasDisease; }
  public String getDiseases() { return diseases; }
  public UnderwritingRecommendation getUnderwritingRecommendation() { return underwritingRecommendation; }
  public String getSummary() { return summary; }
  public LocalDateTime getAnalyzedAt() { return analyzedAt; }
  public String getAnalyzedBy() { return analyzedBy; }
}
