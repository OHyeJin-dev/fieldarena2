package com.agentsupport.healthanalysis.entity;

import com.agentsupport.customer.entity.Customer;
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

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
      Customer customer, String source, String scenario,
      String payload, String collectedBy
  ) {
    HealthData h = new HealthData();
    h.customer = customer;
    h.source = source;
    h.scenario = scenario;
    h.payload = payload;
    h.collectedBy = collectedBy;
    return h;
  }

  public UUID getId() { return id; }
  public Customer getCustomer() { return customer; }
  public UUID getCustomerId() { return customer != null ? customer.getId() : null; }
  public String getSource() { return source; }
  public String getScenario() { return scenario; }
  public String getPayload() { return payload; }
  public LocalDateTime getCollectedAt() { return collectedAt; }
  public String getCollectedBy() { return collectedBy; }
}