package com.agentsupport.policy.entity;

import com.agentsupport.common.BaseAuditEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "policies")
@EntityListeners(AuditingEntityListener.class)
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

  @Column(name = "customer_id")
  private UUID customerId;

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

  public static Policy create(
      String policyNumber,
      String agentId,
      UUID customerId,
      String customerName,
      String productName,
      String insurerName,
      String status,
      LocalDate contractDate,
      BigDecimal monthlyPremium
  ) {
    Policy p = new Policy();
    p.policyNumber = policyNumber;
    p.agentId = agentId;
    p.customerId = customerId;
    p.customerName = customerName;
    p.productName = productName;
    p.insurerName = insurerName;
    p.status = status;
    p.contractDate = contractDate;
    p.monthlyPremium = monthlyPremium;
    return p;
  }

  public UUID getId() { return id; }
  public String getPolicyNumber() { return policyNumber; }
  public String getAgentId() { return agentId; }
  public String getCustomerName() { return customerName; }
  public UUID getCustomerId() { return customerId; }
  public String getProductName() { return productName; }
  public String getInsurerName() { return insurerName; }
  public String getStatus() { return status; }
  public LocalDate getContractDate() { return contractDate; }
  public BigDecimal getMonthlyPremium() { return monthlyPremium; }
}
