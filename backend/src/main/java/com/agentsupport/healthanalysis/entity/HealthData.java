package com.agentsupport.healthanalysis.entity;

import com.agentsupport.security.PiiAttributeConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "health_data")
public class HealthData {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(nullable = false, length = 20)
  private String source;

  @Column(nullable = false, length = 20)
  private String scenario;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, columnDefinition = "TEXT")
  private String payload;

  @CreationTimestamp
  @Column(name = "collected_at", nullable = false, updatable = false)
  private LocalDateTime collectedAt;

  @Column(name = "collected_by", nullable = false, length = 50)
  private String collectedBy;

  protected HealthData() {}

  public static HealthData create(
      UUID customerId, String source, String scenario,
      String payload, String collectedBy
  ) {
    HealthData h = new HealthData();
    h.customerId = customerId;
    h.source = source;
    h.scenario = scenario;
    h.payload = payload;
    h.collectedBy = collectedBy;
    return h;
  }

  public UUID getId() { return id; }
  public UUID getCustomerId() { return customerId; }
  public String getSource() { return source; }
  public String getScenario() { return scenario; }
  public String getPayload() { return payload; }
  public LocalDateTime getCollectedAt() { return collectedAt; }
  public String getCollectedBy() { return collectedBy; }
}
