package com.agentsupport.policy.entity;

import com.agentsupport.common.BaseAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "policies")
public class Policy extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "policy_number", nullable = false, unique = true, length = 20)
  private String policyNumber;

  @Column(name = "agent_id", nullable = false, length = 50)
  private String agentId;

  @Column(name = "customer_name", nullable = false, length = 50)
  private String customerName;

  @Column(name = "product_name", nullable = false, length = 100)
  private String productName;

  @Column(name = "insurer_name", nullable = false, length = 50)
  private String insurerName;

  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "contract_date", nullable = false)
  private LocalDate contractDate;

  @Column(name = "monthly_premium", precision = 12, scale = 2)
  private BigDecimal monthlyPremium;

  protected Policy() {}

  public UUID getId() { return id; }
  public String getPolicyNumber() { return policyNumber; }
  public String getAgentId() { return agentId; }
  public String getCustomerName() { return customerName; }
  public String getProductName() { return productName; }
  public String getInsurerName() { return insurerName; }
  public String getStatus() { return status; }
  public LocalDate getContractDate() { return contractDate; }
  public BigDecimal getMonthlyPremium() { return monthlyPremium; }
}
