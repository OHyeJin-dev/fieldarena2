# JPA Associations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce unidirectional `@ManyToOne(LAZY)` JPA associations on 5 child entities (`Claim`, `Policy`, `Proposal`, `HealthData`, `HealthAnalysis`) that already have DB-level FK columns to `customers` (and `HealthAnalysis` to `health_data`), without altering DB schema, DTOs, or client contracts.

**Architecture:** Replace `private UUID customerId` field with `@ManyToOne(LAZY) @JoinColumn(name = "customer_id") Customer customer`, retain `getCustomerId()` as a null-safe delegate that returns `customer.getId()`. Entity factories accept `Customer` (and `HealthData` where applicable) instead of UUID. Services already hold `Customer` instances via `customerRepository.findById(...)`, so the change is a one-line argument swap. Spring Data derived queries that referenced `customerId` directly are renamed to traversal form (`findByCustomer_Id`) because Spring Data resolves against JPA-mapped fields, not Java getters.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Hibernate 6, JUnit 5 + MockMvc, Gradle.

**Spec:** [docs/superpowers/specs/2026-05-26-jpa-associations-design.md](../specs/2026-05-26-jpa-associations-design.md)

**Branch:** `feat/jpa-associations` from `master`.

---

## Task 1: Create branch

**Files:** (none)

- [ ] **Step 1: Create branch**

```bash
cd d:/fieldarena2
git checkout master
git pull origin master
git checkout -b feat/jpa-associations
git status
```

Expected: clean working tree on `feat/jpa-associations`.

- [ ] **Step 2: Baseline tests pass**

```bash
cd d:/fieldarena2/backend && ./gradlew test 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`. (If failing on baseline, stop and report — do not proceed with the migration.)

---

## Task 2: Migrate `Claim` to `@ManyToOne(LAZY) Customer`

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/claim/entity/Claim.java`
- Modify: `backend/src/main/java/com/agentsupport/claim/service/ClaimService.java`

- [ ] **Step 1: Replace `Claim.java` contents**

Write `backend/src/main/java/com/agentsupport/claim/entity/Claim.java` with:

```java
package com.agentsupport.claim.entity;

import com.agentsupport.common.BaseAuditEntity;
import com.agentsupport.customer.entity.Customer;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "claims")
@EntityListeners(AuditingEntityListener.class)
public class Claim extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "agent_id", nullable = false, length = 50)
  private String agentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id")
  private Customer customer;

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
      String agentId, Customer customer, String policyNumber, String customerName,
      String insurerName, String claimType, BigDecimal claimAmount,
      String status, LocalDate claimDate) {
    Claim c = new Claim();
    c.agentId = agentId;
    c.customer = customer;
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
  public Customer getCustomer() { return customer; }
  public UUID getCustomerId() { return customer != null ? customer.getId() : null; }
  public String getPolicyNumber() { return policyNumber; }
  public String getCustomerName() { return customerName; }
  public String getInsurerName() { return insurerName; }
  public String getClaimType() { return claimType; }
  public BigDecimal getClaimAmount() { return claimAmount; }
  public String getStatus() { return status; }
  public LocalDate getClaimDate() { return claimDate; }
}
```

- [ ] **Step 2: Update `ClaimService.java`**

In `backend/src/main/java/com/agentsupport/claim/service/ClaimService.java` line 44, replace `customer.getId(),` with `customer,`. Use Edit tool with this old/new pair:

Old:
```java
    Claim claim = Claim.create(
        agentId,
        customer.getId(),
        req.policyNumber(),
        customer.getName(),
```

New:
```java
    Claim claim = Claim.create(
        agentId,
        customer,
        req.policyNumber(),
        customer.getName(),
```

- [ ] **Step 3: Run full tests**

```bash
cd d:/fieldarena2/backend && ./gradlew test 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`. All existing tests pass — `claim.getCustomerId()` delegate returns the same UUID via Hibernate proxy without DB roundtrip.

If tests fail, do NOT commit. Inspect failure and report.

- [ ] **Step 4: Commit**

```bash
cd d:/fieldarena2
git add backend/src/main/java/com/agentsupport/claim/entity/Claim.java \
        backend/src/main/java/com/agentsupport/claim/service/ClaimService.java
git commit -m "refactor(claim): UUID customerId -> @ManyToOne(LAZY) Customer

Claim now holds a Customer association at the JPA layer. The DB column
customer_id is unchanged; @JoinColumn(name=\"customer_id\") binds to it.
getCustomerId() is retained as a null-safe delegate so DTO/test code
that already calls it keeps working. ClaimService passes Customer to
the factory instead of UUID."
```

---

## Task 3: Migrate `Policy` to `@ManyToOne(LAZY) Customer`

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/policy/entity/Policy.java`
- Modify: `backend/src/main/java/com/agentsupport/policy/service/PolicyService.java`

- [ ] **Step 1: Replace `Policy.java` contents**

Write `backend/src/main/java/com/agentsupport/policy/entity/Policy.java` with:

```java
package com.agentsupport.policy.entity;

import com.agentsupport.common.BaseAuditEntity;
import com.agentsupport.customer.entity.Customer;
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id")
  private Customer customer;

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
      Customer customer,
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
    p.customer = customer;
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
  public Customer getCustomer() { return customer; }
  public UUID getCustomerId() { return customer != null ? customer.getId() : null; }
  public String getProductName() { return productName; }
  public String getInsurerName() { return insurerName; }
  public String getStatus() { return status; }
  public LocalDate getContractDate() { return contractDate; }
  public BigDecimal getMonthlyPremium() { return monthlyPremium; }
}
```

- [ ] **Step 2: Update `PolicyService.java`**

In `backend/src/main/java/com/agentsupport/policy/service/PolicyService.java`, replace the `Policy.create(...)` invocation. Use Edit tool with this old/new pair:

Old:
```java
    Policy policy = Policy.create(
        policyNumber,
        agentId,
        customer.getId(),
        customer.getName(),
        req.productName(),
```

New:
```java
    Policy policy = Policy.create(
        policyNumber,
        agentId,
        customer,
        customer.getName(),
        req.productName(),
```

- [ ] **Step 3: Run full tests**

```bash
cd d:/fieldarena2/backend && ./gradlew test 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd d:/fieldarena2
git add backend/src/main/java/com/agentsupport/policy/entity/Policy.java \
        backend/src/main/java/com/agentsupport/policy/service/PolicyService.java
git commit -m "refactor(policy): UUID customerId -> @ManyToOne(LAZY) Customer

Same pattern as Claim: @JoinColumn(name=\"customer_id\") association,
getCustomerId() delegate retained for DTO compatibility. PolicyService
passes Customer to factory."
```

---

## Task 4: Migrate `Proposal` to `@ManyToOne(LAZY) Customer`

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/proposal/entity/Proposal.java`
- Modify: `backend/src/main/java/com/agentsupport/proposal/service/ProposalService.java`

- [ ] **Step 1: Replace `Proposal.java` contents**

Write `backend/src/main/java/com/agentsupport/proposal/entity/Proposal.java` with:

```java
package com.agentsupport.proposal.entity;

import com.agentsupport.common.BaseAuditEntity;
import com.agentsupport.customer.entity.Customer;
import com.agentsupport.security.PiiAttributeConverter;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "proposals")
@EntityListeners(AuditingEntityListener.class)
public class Proposal extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "agent_id", nullable = false, length = 50)
  private String agentId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id")
  private Customer customer;

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

  protected Proposal() {}

  public static Proposal create(
      String agentId,
      Customer customer,
      String customerName,
      String phoneNumber,
      String birthDate,
      String productName,
      String insurerName,
      BigDecimal monthlyPremium) {
    Proposal p = new Proposal();
    p.agentId = agentId;
    p.customer = customer;
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
  public Customer getCustomer() { return customer; }
  public UUID getCustomerId() { return customer != null ? customer.getId() : null; }
  public String getCustomerName() { return customerName; }
  public String getPhoneNumber() { return phoneNumber; }
  public String getBirthDate() { return birthDate; }
  public String getProductName() { return productName; }
  public String getInsurerName() { return insurerName; }
  public BigDecimal getMonthlyPremium() { return monthlyPremium; }
  public String getStatus() { return status; }
  public LocalDate getProposedDate() { return proposedDate; }
}
```

- [ ] **Step 2: Update `ProposalService.java`**

In `backend/src/main/java/com/agentsupport/proposal/service/ProposalService.java`, replace the `Proposal.create(...)` invocation. Use Edit tool with this old/new pair:

Old:
```java
    Proposal proposal = Proposal.create(
        agentId,
        customer.getId(),
        customer.getName(),
        customer.getPhone(),
```

New:
```java
    Proposal proposal = Proposal.create(
        agentId,
        customer,
        customer.getName(),
        customer.getPhone(),
```

- [ ] **Step 3: Run full tests**

```bash
cd d:/fieldarena2/backend && ./gradlew test 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd d:/fieldarena2
git add backend/src/main/java/com/agentsupport/proposal/entity/Proposal.java \
        backend/src/main/java/com/agentsupport/proposal/service/ProposalService.java
git commit -m "refactor(proposal): UUID customerId -> @ManyToOne(LAZY) Customer

Same pattern. ProposalService passes Customer to factory."
```

---

## Task 5: Migrate `HealthData` to `@ManyToOne(LAZY) Customer`

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthData.java`

- [ ] **Step 1: Replace `HealthData.java` contents**

Write `backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthData.java` with:

```java
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
```

- [ ] **Step 2: Update `HealthAnalysisService.java` — `HealthData.create(...)` call**

In `backend/src/main/java/com/agentsupport/healthanalysis/service/HealthAnalysisService.java`, change `HealthData.create(customerId, ...)` to `HealthData.create(customer, ...)`. Use Edit tool with this old/new pair:

Old:
```java
    // 2. HealthData 저장 (누적)
    HealthData savedData = healthDataRepository.save(
        HealthData.create(customerId, "NHIS_DUMMY", scenario.name(), payloadJson, agentId)
    );
```

New:
```java
    // 2. HealthData 저장 (누적)
    HealthData savedData = healthDataRepository.save(
        HealthData.create(customer, "NHIS_DUMMY", scenario.name(), payloadJson, agentId)
    );
```

(`customer` is already in scope at line 128 — `Customer customer = customerRepository.findById(customerId).orElseThrow(...)`.)

- [ ] **Step 3: Run full tests**

```bash
cd d:/fieldarena2/backend && ./gradlew test 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
cd d:/fieldarena2
git add backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthData.java \
        backend/src/main/java/com/agentsupport/healthanalysis/service/HealthAnalysisService.java
git commit -m "refactor(health-data): UUID customerId -> @ManyToOne(LAZY) Customer

Same pattern. HealthAnalysisService passes the already-fetched Customer
to HealthData.create."
```

---

## Task 6: Migrate `HealthAnalysis` to `@ManyToOne(LAZY)` for both Customer and HealthData

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthAnalysis.java`
- Modify: `backend/src/main/java/com/agentsupport/healthanalysis/repository/HealthAnalysisRepository.java`
- Modify: `backend/src/main/java/com/agentsupport/healthanalysis/service/HealthAnalysisService.java`

This task has additional surgery because Spring Data derived queries on the repository reference `customerId` — they must be renamed to traverse the new association.

- [ ] **Step 1: Replace `HealthAnalysis.java` contents**

Write `backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthAnalysis.java` with:

```java
package com.agentsupport.healthanalysis.entity;

import com.agentsupport.customer.entity.Customer;
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

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false, unique = true)
  private Customer customer;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "health_data_id", nullable = false)
  private HealthData healthData;

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
      Customer customer, HealthData healthData,
      RiskGrade riskGrade, boolean hasDisease, String diseases,
      UnderwritingRecommendation rec, String summary, String analyzedBy
  ) {
    HealthAnalysis a = new HealthAnalysis();
    a.customer = customer;
    a.healthData = healthData;
    a.riskGrade = riskGrade;
    a.hasDisease = hasDisease;
    a.diseases = diseases;
    a.underwritingRecommendation = rec;
    a.summary = summary;
    a.analyzedBy = analyzedBy;
    return a;
  }

  public void replaceWith(
      HealthData healthData,
      RiskGrade riskGrade, boolean hasDisease, String diseases,
      UnderwritingRecommendation rec, String summary, String analyzedBy
  ) {
    this.healthData = healthData;
    this.riskGrade = riskGrade;
    this.hasDisease = hasDisease;
    this.diseases = diseases;
    this.underwritingRecommendation = rec;
    this.summary = summary;
    this.analyzedBy = analyzedBy;
  }

  public UUID getId() { return id; }
  public Customer getCustomer() { return customer; }
  public UUID getCustomerId() { return customer != null ? customer.getId() : null; }
  public HealthData getHealthData() { return healthData; }
  public UUID getHealthDataId() { return healthData != null ? healthData.getId() : null; }
  public RiskGrade getRiskGrade() { return riskGrade; }
  public boolean isHasDisease() { return hasDisease; }
  public String getDiseases() { return diseases; }
  public UnderwritingRecommendation getUnderwritingRecommendation() { return underwritingRecommendation; }
  public String getSummary() { return summary; }
  public LocalDateTime getAnalyzedAt() { return analyzedAt; }
  public String getAnalyzedBy() { return analyzedBy; }
}
```

- [ ] **Step 2: Read current `HealthAnalysisRepository.java` to confirm structure**

```bash
cat d:/fieldarena2/backend/src/main/java/com/agentsupport/healthanalysis/repository/HealthAnalysisRepository.java
```

Note the existing method signatures — you will rename `findByCustomerId` and `findByCustomerIdIn`.

- [ ] **Step 3: Update `HealthAnalysisRepository.java` method names**

Edit `backend/src/main/java/com/agentsupport/healthanalysis/repository/HealthAnalysisRepository.java`:

Old:
```java
  Optional<HealthAnalysis> findByCustomerId(UUID customerId);
```

New:
```java
  Optional<HealthAnalysis> findByCustomer_Id(UUID customerId);
```

Old:
```java
  List<HealthAnalysis> findByCustomerIdIn(List<UUID> customerIds);
```

New:
```java
  List<HealthAnalysis> findByCustomer_IdIn(List<UUID> customerIds);
```

`Customer_Id` traversal (with explicit underscore) tells Spring Data to navigate the `customer` association and then read its `id`. After the entity change, the property `customerId` no longer exists as a direct field, so the underscore form is required.

- [ ] **Step 4: Update `HealthAnalysisService.java` — `findByCustomerId` callers**

Edit `backend/src/main/java/com/agentsupport/healthanalysis/service/HealthAnalysisService.java`:

Old:
```java
    // 4. UPSERT
    HealthAnalysis analysis = healthAnalysisRepository.findByCustomerId(customerId).orElse(null);
    if (analysis == null) {
      analysis = HealthAnalysis.create(
          customerId, savedData.getId(),
          outcome.riskGrade(), outcome.hasDisease(), diseasesJson,
          outcome.recommendation(), outcome.summary(), agentId
      );
    } else {
      analysis.replaceWith(
          savedData.getId(),
          outcome.riskGrade(), outcome.hasDisease(), diseasesJson,
          outcome.recommendation(), outcome.summary(), agentId
      );
    }
```

New:
```java
    // 4. UPSERT
    HealthAnalysis analysis = healthAnalysisRepository.findByCustomer_Id(customerId).orElse(null);
    if (analysis == null) {
      analysis = HealthAnalysis.create(
          customer, savedData,
          outcome.riskGrade(), outcome.hasDisease(), diseasesJson,
          outcome.recommendation(), outcome.summary(), agentId
      );
    } else {
      analysis.replaceWith(
          savedData,
          outcome.riskGrade(), outcome.hasDisease(), diseasesJson,
          outcome.recommendation(), outcome.summary(), agentId
      );
    }
```

- [ ] **Step 5: Update the `findByCustomerIdIn` caller in `HealthAnalysisService.java`**

Old:
```java
    return healthAnalysisRepository.findByCustomerIdIn(allowedCustomerIds).stream()
        .collect(Collectors.toMap(
            HealthAnalysis::getCustomerId,
            a -> toDto(a, customerById.get(a.getCustomerId()))
        ));
```

New:
```java
    return healthAnalysisRepository.findByCustomer_IdIn(allowedCustomerIds).stream()
        .collect(Collectors.toMap(
            HealthAnalysis::getCustomerId,
            a -> toDto(a, customerById.get(a.getCustomerId()))
        ));
```

(`HealthAnalysis::getCustomerId` is now the delegate — same return type and value, no callsite change needed.)

- [ ] **Step 6: Run full tests**

```bash
cd d:/fieldarena2/backend && ./gradlew test 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`. The `findByCustomer_Id` derived queries should resolve cleanly; `getCustomerId()` delegate on entity returns same UUID via lazy proxy.

If you see `PropertyReferenceException: No property 'customerId' found for type 'HealthAnalysis'`, that means Step 3 was missed — go back and rename the repository methods.

- [ ] **Step 7: Commit**

```bash
cd d:/fieldarena2
git add backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthAnalysis.java \
        backend/src/main/java/com/agentsupport/healthanalysis/repository/HealthAnalysisRepository.java \
        backend/src/main/java/com/agentsupport/healthanalysis/service/HealthAnalysisService.java
git commit -m "refactor(health-analysis): UUID FKs -> @ManyToOne(LAZY) Customer + HealthData

HealthAnalysis now holds Customer and HealthData associations. Spring
Data derived queries findByCustomerId(In) renamed to findByCustomer_Id(In)
so the traversal path resolves against the new property. Service passes
Customer/HealthData instances to the factory; getCustomerId() and
getHealthDataId() delegates keep DTO conversion code unchanged."
```

---

## Task 7: Add association integration test

**Files:**
- Create: `backend/src/test/java/com/agentsupport/claim/ClaimAssociationTest.java`

This test directly verifies that the new association mappings work end-to-end (write, then read back, then traverse). It catches regressions like wrong `@JoinColumn` name or missing import that the existing test suite might silently mask.

- [ ] **Step 1: Create the test file**

Write `backend/src/test/java/com/agentsupport/claim/ClaimAssociationTest.java`:

```java
package com.agentsupport.claim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.agentsupport.claim.entity.Claim;
import com.agentsupport.claim.repository.ClaimRepository;
import com.agentsupport.customer.entity.Customer;
import com.agentsupport.customer.repository.CustomerRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClaimAssociationTest {

  @Autowired ClaimRepository claimRepository;
  @Autowired CustomerRepository customerRepository;

  @Test
  @Transactional
  void claim_persists_customer_association_and_getCustomerId_delegates() {
    Customer customer = customerRepository.save(
        Customer.create("admin", "관계테스트", "010-1111-2222",
            LocalDate.of(1990, 1, 1), "M", null, null, null));

    Claim claim = Claim.create(
        "admin", customer, "P-ASSOC-001", "관계테스트",
        "삼성생명", "실손", BigDecimal.valueOf(1_000_000), "접수", LocalDate.now());

    Claim saved = claimRepository.save(claim);
    UUID savedId = saved.getId();

    Claim reloaded = claimRepository.findById(savedId).orElseThrow();
    assertNotNull(reloaded.getCustomer(), "association must be populated");
    assertEquals(customer.getId(), reloaded.getCustomer().getId());
    assertEquals(customer.getId(), reloaded.getCustomerId(),
        "delegate getCustomerId() must match association id");
    assertEquals("관계테스트", reloaded.getCustomer().getName(),
        "lazy load through association should return real customer");
  }
}
```

- [ ] **Step 2: Run the new test**

```bash
cd d:/fieldarena2/backend && ./gradlew test --tests "com.agentsupport.claim.ClaimAssociationTest" 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`. The test exercises persist → reload → association traversal in one transaction; lazy proxy resolves on `getName()` call.

- [ ] **Step 3: Run full test suite once more to ensure no regression from any prior task**

```bash
cd d:/fieldarena2/backend && ./gradlew test 2>&1 | tail -3
```

Expected: `BUILD SUCCESSFUL`. Total test count should be original count + 1.

- [ ] **Step 4: Commit**

```bash
cd d:/fieldarena2
git add backend/src/test/java/com/agentsupport/claim/ClaimAssociationTest.java
git commit -m "test(claim): verify @ManyToOne Customer association round-trips

Saves a customer, creates a claim referencing it, reloads, then asserts
both the navigation property (claim.getCustomer().getName()) and the
delegate (claim.getCustomerId()) return the right values. One canonical
test for the association pattern — the other 4 entities follow the same
pattern and are covered indirectly by existing controller/service tests."
```

---

## Task 8: Final verification

**Files:** (none)

- [ ] **Step 1: Run full test suite**

```bash
cd d:/fieldarena2/backend && ./gradlew test 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`. All existing controller/service tests pass alongside the new association test.

- [ ] **Step 2: Sanity-check boot (DDL validate)**

```bash
cd d:/fieldarena2/backend && ./gradlew bootRun --args='--spring.main.web-application-type=none' 2>&1 | head -40
```

(If `--web-application-type=none` is not supported in your setup, run `./gradlew bootRun` and Ctrl+C as soon as you see `Started BackendApplication`.)

Expected: see `Migrating schema "public" to version 16 - add customer id to proposals` (or no migration if DB is already on V16) followed by `Started BackendApplication`. No `SchemaManagementException` or `Hibernate validation` errors — confirms the new annotations align with existing columns.

- [ ] **Step 3: Confirm git history**

```bash
cd d:/fieldarena2 && git log --oneline master..HEAD
```

Expected: 6 commits in this order (oldest first):
1. `refactor(claim): UUID customerId -> @ManyToOne(LAZY) Customer`
2. `refactor(policy): UUID customerId -> @ManyToOne(LAZY) Customer`
3. `refactor(proposal): UUID customerId -> @ManyToOne(LAZY) Customer`
4. `refactor(health-data): UUID customerId -> @ManyToOne(LAZY) Customer`
5. `refactor(health-analysis): UUID FKs -> @ManyToOne(LAZY) Customer + HealthData`
6. `test(claim): verify @ManyToOne Customer association round-trips`

- [ ] **Step 4: Confirm no accidental files**

```bash
cd d:/fieldarena2 && git status
```

Expected: `nothing to commit, working tree clean`. If anything is unstaged (e.g. stray scratch file), investigate.

---

## Push and PR (requires explicit user authorization)

Per project convention, do NOT push or open a PR without the user's explicit go-ahead.

When the user authorizes:

- [ ] **Push**

```bash
cd d:/fieldarena2
git push -u origin feat/jpa-associations
```

- [ ] **Open PR**

```bash
gh pr create --title "feat(backend): JPA @ManyToOne associations on existing DB FKs" --body "$(cat <<'EOF'
## Summary

- 5 entities (Claim, Policy, Proposal, HealthData, HealthAnalysis) now hold `@ManyToOne(LAZY) @JoinColumn` associations instead of raw `UUID customerId` fields
- HealthAnalysis also gains a `@ManyToOne(LAZY) HealthData healthData` association
- DB schema is unchanged — `customer_id`/`health_data_id` columns + existing FK constraints (V12/V14/V15/V16) are reused
- `getCustomerId()` / `getHealthDataId()` retained as null-safe delegates so DTOs/services/tests continue to work without changes
- Spring Data derived queries `findByCustomerId(In)` on `HealthAnalysisRepository` renamed to `findByCustomer_Id(In)` so the traversal path resolves
- New `ClaimAssociationTest` directly verifies the persist → reload → traverse round-trip on one representative entity

## Test plan

- [x] `./gradlew test` — all existing + new test green
- [x] Boot succeeds with `ddl-auto: validate` against existing schema
- [ ] Reviewer: create a claim via the API, verify response unchanged
- [ ] Reviewer: list claims, verify `customer_name` denormalized column still surfaces

Spec: docs/superpowers/specs/2026-05-26-jpa-associations-design.md
Plan: docs/superpowers/plans/2026-05-26-jpa-associations.md

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```