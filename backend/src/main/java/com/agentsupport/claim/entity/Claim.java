package com.agentsupport.claim.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "claims")
public class Claim {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "agent_id", nullable = false, length = 50)
  private String agentId;

  @Column(name = "policy_number", nullable = false, length = 20)
  private String policyNumber;

  @Column(name = "customer_name", nullable = false, length = 50)
  private String customerName;

  @Column(name = "insurer_name", nullable = false, length = 50)
  private String insurerName;

  @Column(name = "claim_type", nullable = false, length = 50)
  private String claimType;

  @Column(name = "claim_amount", precision = 12, scale = 2)
  private BigDecimal claimAmount;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "claim_date", nullable = false)
  private LocalDate claimDate;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected Claim() {}

  public UUID getId() { return id; }
  public String getAgentId() { return agentId; }
  public String getPolicyNumber() { return policyNumber; }
  public String getCustomerName() { return customerName; }
  public String getInsurerName() { return insurerName; }
  public String getClaimType() { return claimType; }
  public BigDecimal getClaimAmount() { return claimAmount; }
  public String getStatus() { return status; }
  public LocalDate getClaimDate() { return claimDate; }
}
