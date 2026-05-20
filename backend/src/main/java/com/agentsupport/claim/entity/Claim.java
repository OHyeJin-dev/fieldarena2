package com.agentsupport.claim.entity;

import com.agentsupport.common.BaseAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "claims")
public class Claim extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "agent_id", nullable = false, length = 50)
  private String agentId;

  @Column(name = "customer_id")
  private UUID customerId;

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

  protected Claim() {}

  public static Claim create(
      String agentId, UUID customerId, String policyNumber, String customerName,
      String insurerName, String claimType, BigDecimal claimAmount,
      String status, LocalDate claimDate) {
    Claim c = new Claim();
    c.agentId = agentId;
    c.customerId = customerId;
    c.policyNumber = policyNumber;
    c.customerName = customerName;
    c.insurerName = insurerName;
    c.claimType = claimType;
    c.claimAmount = claimAmount;
    c.status = status;
    c.claimDate = claimDate;
    return c;
  }

  public UUID getId() { return id; }
  public String getAgentId() { return agentId; }
  public UUID getCustomerId() { return customerId; }
  public String getPolicyNumber() { return policyNumber; }
  public String getCustomerName() { return customerName; }
  public String getInsurerName() { return insurerName; }
  public String getClaimType() { return claimType; }
  public BigDecimal getClaimAmount() { return claimAmount; }
  public String getStatus() { return status; }
  public LocalDate getClaimDate() { return claimDate; }
}
