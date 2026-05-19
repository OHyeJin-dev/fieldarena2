package com.agentsupport.proposal.entity;

import com.agentsupport.security.PiiAttributeConverter;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "proposals")
public class Proposal {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "agent_id", nullable = false, length = 50)
  private String agentId;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(name = "customer_name", nullable = false)
  private String customerName;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(name = "phone_number")
  private String phoneNumber;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(name = "birth_date")
  private String birthDate;

  @Column(name = "product_name", nullable = false, length = 100)
  private String productName;

  @Column(name = "insurer_name", nullable = false, length = 50)
  private String insurerName;

  @Column(name = "monthly_premium", precision = 12, scale = 2)
  private BigDecimal monthlyPremium;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "proposed_date", nullable = false)
  private LocalDate proposedDate;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected Proposal() {}

  public static Proposal create(
      String agentId,
      String customerName,
      String phoneNumber,
      String birthDate,
      String productName,
      String insurerName,
      BigDecimal monthlyPremium) {
    Proposal p = new Proposal();
    p.agentId = agentId;
    p.customerName = customerName;
    p.phoneNumber = phoneNumber;
    p.birthDate = birthDate;
    p.productName = productName;
    p.insurerName = insurerName;
    p.monthlyPremium = monthlyPremium;
    p.status = "작성 중";
    p.proposedDate = LocalDate.now();
    return p;
  }

  public UUID getId() { return id; }
  public String getAgentId() { return agentId; }
  public String getCustomerName() { return customerName; }
  public String getPhoneNumber() { return phoneNumber; }
  public String getBirthDate() { return birthDate; }
  public String getProductName() { return productName; }
  public String getInsurerName() { return insurerName; }
  public BigDecimal getMonthlyPremium() { return monthlyPremium; }
  public String getStatus() { return status; }
  public LocalDate getProposedDate() { return proposedDate; }
}
