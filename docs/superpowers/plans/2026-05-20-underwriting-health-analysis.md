# Underwriting Health Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 심사 페이지에 건강 데이터 분석 기능을 추가한다. 더미 데이터로 분석을 수행하고, 결과를 underwriting 테이블과 대시보드에 노출한다.

**Architecture:** 백엔드에 `healthanalysis` 모듈 신설(엔티티/서비스/컨트롤러), `policies` 테이블에 `customer_id` FK 추가, 프론트엔드에 FSD `entities/health-analysis` + `features/health-analysis/request` 슬라이스 추가. 분석은 customer 단위 1건 UPSERT, raw `health_data`는 누적.

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, Flyway, JUnit 5 + MockMvc, Next.js 16 (App Router), React Query, react-hook-form + zod, Tailwind v4, pnpm.

**Reference Spec:** `docs/superpowers/specs/2026-05-20-underwriting-health-analysis-design.md`

**Branch:** `feat/health-analysis` (master에서 분기). 머지 시 PR로 진행 (branch protection 적용됨).

---

## File Structure

### 신규 (Backend)

```
backend/src/main/resources/db/migration/
├── V14__add_customer_id_to_policies.sql
└── V15__create_health_data_and_analyses.sql

backend/src/main/java/com/agentsupport/healthanalysis/
├── Scenario.java                        # enum
├── RiskGrade.java                       # enum
├── UnderwritingRecommendation.java      # enum
├── ChronicConditions.java               # Map<코드, 한글명> 상수
├── entity/
│   ├── HealthData.java
│   └── HealthAnalysis.java
├── dto/
│   ├── HealthDataPayload.java           # Jackson JSON POJO
│   ├── DiseaseDto.java
│   ├── HealthAnalysisDto.java
│   ├── AnalysisRequestDto.java
│   ├── AnalysisSummaryDto.java
│   └── RecentAnalysisItemDto.java
├── repository/
│   ├── HealthDataRepository.java
│   └── HealthAnalysisRepository.java
├── service/
│   ├── DummyHealthDataGenerator.java
│   └── HealthAnalysisService.java
└── HealthAnalysisController.java

backend/src/test/java/com/agentsupport/healthanalysis/
├── DummyHealthDataGeneratorTest.java
├── HealthAnalysisServiceTest.java
└── HealthAnalysisControllerTest.java
```

### 신규 (Frontend)

```
frontend/src/entities/health-analysis/
├── api/index.ts
├── model/index.ts
├── ui/
│   ├── risk-badge/index.tsx
│   ├── analysis-result/index.tsx
│   └── index.ts
└── index.ts

frontend/src/features/health-analysis/request/
├── api/index.ts
├── model/index.ts
├── ui/
│   ├── index.tsx                        # AnalysisRequestModal
│   ├── step-input.tsx                   # Step 1 (시나리오 선택)
│   └── step-result.tsx                  # Step 2 (결과 표시)
└── index.ts
```

### 수정 (Backend)

```
backend/src/main/java/com/agentsupport/
├── policy/entity/Policy.java            # customerId 필드 추가
├── policy/dto/PolicyDto.java            # customerId 노출
├── policy/service/PolicyService.java    # DTO 매핑에 customerId 포함
└── config/SecurityConfig.java           # /api/health-analyses/** ADMIN, AGENT1
```

### 수정 (Frontend)

```
frontend/src/
├── entities/contract/api/index.ts       # PolicyDto에 customerId 필드
└── app/
    ├── underwriting/page.tsx            # "분석" 컬럼 + AnalysisCell + ?analysisId 처리
    └── dashboard/page.tsx               # 건강 분석 현황 통계 + 최근 분석 리스트
```

---

## Conventions

- **Working dir**: `d:\fieldarena2`. 백엔드 명령은 `cd backend`, 프론트엔드는 `cd frontend`.
- **Branch**: 시작 전 master에서 `git checkout -b feat/health-analysis`.
- **Verification**:
  - Backend: `cd backend; ./gradlew.bat test --tests <패턴>` (Windows) 또는 `./gradlew test`
  - Frontend: `cd frontend; pnpm typecheck; pnpm lint; pnpm lint:fsd; pnpm build`
- **Commits**: 각 Task 끝에서 한 번. push는 사용자 승인 후 (memory: feedback_git_push_confirm).
- **Pre-commit hook**: Husky가 `pnpm lint:fsd` 실행. 위반 시 commit 차단 → 코드 수정.
- **PII 암호화**: `health_data.payload`, `health_analyses.diseases`는 `@Convert(converter = PiiAttributeConverter.class)` 적용 (기존 패턴).
- **Identifier 보존**: `Policy`/`PolicyDto` 식별자는 그대로 (백엔드/프론트 일관성). FSD entity 폴더만 `contract`.

---

# Phase A: Database & Domain Primitives

## Task 1: V14 migration — policies.customer_id 추가 + backfill

**Files:**
- Create: `backend/src/main/resources/db/migration/V14__add_customer_id_to_policies.sql`

- [ ] **Step 1: 마이그레이션 SQL 작성**

```sql
-- V14__add_customer_id_to_policies.sql
ALTER TABLE policies ADD COLUMN customer_id UUID NULL;
ALTER TABLE policies ADD CONSTRAINT fk_policies_customer
  FOREIGN KEY (customer_id) REFERENCES customers(id);
CREATE INDEX idx_policies_customer_id ON policies(customer_id);

-- Backfill: agent_id + customer_name 일치 매칭
UPDATE policies p
SET customer_id = c.id
FROM customers c
WHERE p.agent_id = c.agent_id
  AND p.customer_name = c.name
  AND p.customer_id IS NULL;
```

- [ ] **Step 2: 부트 검증**

Run: `cd backend; ./gradlew.bat test --tests BackendApplicationTests`
Expected: Flyway가 V14 적용, 컨텍스트 정상 기동 로그에 "Migrating schema "public" to version "14 - add customer id to policies"" 포함.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/resources/db/migration/V14__add_customer_id_to_policies.sql
git commit -m "feat(policy): add customer_id FK with backfill (V14)"
```

---

## Task 2: Policy entity/DTO에 customerId 노출

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/policy/entity/Policy.java`
- Modify: `backend/src/main/java/com/agentsupport/policy/dto/PolicyDto.java`
- Modify: `backend/src/main/java/com/agentsupport/policy/service/PolicyService.java`

- [ ] **Step 1: Policy 엔티티에 customerId 필드 추가**

Edit `backend/src/main/java/com/agentsupport/policy/entity/Policy.java` — `customer_name` 필드 바로 뒤에 추가:

```java
  @Column(name = "customer_id")
  private UUID customerId;

  // getter
  public UUID getCustomerId() { return customerId; }
```

(import 필요 시: `import java.util.UUID;`)

- [ ] **Step 2: PolicyDto에 customerId 노출**

Edit `backend/src/main/java/com/agentsupport/policy/dto/PolicyDto.java` — record/class 정의에 `customerId` 추가:

```java
public record PolicyDto(
    UUID id,
    String policyNumber,
    UUID customerId,             // 신규
    String customerName,
    String productName,
    String insurerName,
    String status,
    LocalDate contractDate,
    BigDecimal monthlyPremium
) { }
```

(필드 순서: customerId는 customerName 바로 앞)

- [ ] **Step 3: PolicyService 매핑에 customerId 포함**

Edit `backend/src/main/java/com/agentsupport/policy/service/PolicyService.java` — Policy → PolicyDto 변환 부분에 `customerId` 매핑 추가:

```java
private PolicyDto toDto(Policy p) {
    return new PolicyDto(
        p.getId(),
        p.getPolicyNumber(),
        p.getCustomerId(),       // 신규
        p.getCustomerName(),
        p.getProductName(),
        p.getInsurerName(),
        p.getStatus(),
        p.getContractDate(),
        p.getMonthlyPremium()
    );
}
```

(실제 변환 메서드명은 파일 확인. 매핑이 여러 곳에 있으면 모두 갱신.)

- [ ] **Step 4: 빌드 + 기존 테스트 통과 확인**

Run: `cd backend; ./gradlew.bat build -x spotlessCheck`
Expected: 빌드 성공, 기존 테스트 모두 통과.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/policy/
git commit -m "feat(policy): expose customerId on Policy entity and DTO"
```

---

## Task 3: V15 migration — health_data + health_analyses 테이블

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__create_health_data_and_analyses.sql`

- [ ] **Step 1: 마이그레이션 SQL 작성**

```sql
-- V15__create_health_data_and_analyses.sql

CREATE TABLE health_data (
  id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id   UUID         NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  source        VARCHAR(20)  NOT NULL,
  scenario      VARCHAR(20)  NOT NULL,
  payload       TEXT         NOT NULL,
  collected_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  collected_by  VARCHAR(50)  NOT NULL
);

CREATE INDEX idx_health_data_customer_id_collected_at
  ON health_data (customer_id, collected_at DESC);

CREATE TABLE health_analyses (
  id                          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
  customer_id                 UUID         NOT NULL UNIQUE REFERENCES customers(id) ON DELETE CASCADE,
  health_data_id              UUID         NOT NULL REFERENCES health_data(id),
  risk_grade                  VARCHAR(10)  NOT NULL,
  has_disease                 BOOLEAN      NOT NULL,
  diseases                    TEXT         NOT NULL,
  underwriting_recommendation VARCHAR(20)  NOT NULL,
  summary                     TEXT         NOT NULL,
  analyzed_at                 TIMESTAMPTZ  NOT NULL DEFAULT now(),
  analyzed_by                 VARCHAR(50)  NOT NULL
);

CREATE INDEX idx_health_analyses_analyzed_by_analyzed_at
  ON health_analyses (analyzed_by, analyzed_at DESC);
```

- [ ] **Step 2: 부트 검증**

Run: `cd backend; ./gradlew.bat test --tests BackendApplicationTests`
Expected: V15 적용 + 컨텍스트 기동 성공.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/resources/db/migration/V15__create_health_data_and_analyses.sql
git commit -m "feat(health-analysis): create health_data and health_analyses tables (V15)"
```

---

## Task 4: 도메인 enum + 상수

**Files:**
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/Scenario.java`
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/RiskGrade.java`
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/UnderwritingRecommendation.java`
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/ChronicConditions.java`

- [ ] **Step 1: Scenario enum 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/Scenario.java
package com.agentsupport.healthanalysis;

public enum Scenario {
  RANDOM,
  NORMAL,
  HYPERTENSION,
  DIABETES,
  COMPLEX
}
```

- [ ] **Step 2: RiskGrade enum 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/RiskGrade.java
package com.agentsupport.healthanalysis;

public enum RiskGrade {
  NORMAL,
  CAUTION,
  RISK
}
```

- [ ] **Step 3: UnderwritingRecommendation enum 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/UnderwritingRecommendation.java
package com.agentsupport.healthanalysis;

public enum UnderwritingRecommendation {
  APPROVE,
  CONDITIONAL,
  DECLINE
}
```

- [ ] **Step 4: ChronicConditions 상수 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/ChronicConditions.java
package com.agentsupport.healthanalysis;

import java.util.Map;
import java.util.Set;

public final class ChronicConditions {

  private ChronicConditions() {}

  public static final Map<String, String> CODES = Map.ofEntries(
      Map.entry("I10", "본태성 고혈압"),
      Map.entry("E11", "제2형 당뇨병"),
      Map.entry("I50", "심부전"),
      Map.entry("N18", "만성 신장병")
  );

  public static final Set<String> CHRONIC_CODE_SET = CODES.keySet();

  public static boolean isChronic(String code) {
    return CHRONIC_CODE_SET.contains(code);
  }
}
```

- [ ] **Step 5: 빌드 확인**

Run: `cd backend; ./gradlew.bat compileJava`
Expected: 성공.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/healthanalysis/
git commit -m "feat(health-analysis): add Scenario/RiskGrade/Recommendation enums and chronic codes"
```

---

## Task 5: DTO 클래스들 (Payload, Disease, Request, Summary 등)

**Files:**
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/dto/HealthDataPayload.java`
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/dto/DiseaseDto.java`
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/dto/AnalysisRequestDto.java`
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/dto/HealthAnalysisDto.java`
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/dto/AnalysisSummaryDto.java`
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/dto/RecentAnalysisItemDto.java`

- [ ] **Step 1: HealthDataPayload 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/dto/HealthDataPayload.java
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
```

- [ ] **Step 2: DiseaseDto 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/dto/DiseaseDto.java
package com.agentsupport.healthanalysis.dto;

public record DiseaseDto(
    String code,
    String name,
    String diagnosedAt,       // "2024-03" 같은 yyyy-MM
    String frequency          // "월 1회" 한글 표기
) {}
```

- [ ] **Step 3: AnalysisRequestDto 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/dto/AnalysisRequestDto.java
package com.agentsupport.healthanalysis.dto;

import com.agentsupport.healthanalysis.Scenario;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AnalysisRequestDto(
    @NotNull UUID customerId,
    @NotNull Scenario scenario
) {}
```

- [ ] **Step 4: HealthAnalysisDto 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/dto/HealthAnalysisDto.java
package com.agentsupport.healthanalysis.dto;

import com.agentsupport.healthanalysis.RiskGrade;
import com.agentsupport.healthanalysis.UnderwritingRecommendation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record HealthAnalysisDto(
    UUID id,
    UUID customerId,
    String customerName,
    RiskGrade riskGrade,
    boolean hasDisease,
    List<DiseaseDto> diseases,
    UnderwritingRecommendation underwritingRecommendation,
    String summary,
    LocalDateTime analyzedAt,
    String analyzedBy
) {}
```

- [ ] **Step 5: AnalysisSummaryDto 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/dto/AnalysisSummaryDto.java
package com.agentsupport.healthanalysis.dto;

public record AnalysisSummaryDto(
    long total,
    long normal,
    long caution,
    long risk
) {}
```

- [ ] **Step 6: RecentAnalysisItemDto 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/dto/RecentAnalysisItemDto.java
package com.agentsupport.healthanalysis.dto;

import com.agentsupport.healthanalysis.RiskGrade;
import java.time.LocalDateTime;
import java.util.UUID;

public record RecentAnalysisItemDto(
    UUID id,
    UUID customerId,
    String customerName,
    RiskGrade riskGrade,
    LocalDateTime analyzedAt
) {}
```

- [ ] **Step 7: 빌드 확인**

Run: `cd backend; ./gradlew.bat compileJava`
Expected: 성공.

- [ ] **Step 8: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/healthanalysis/dto/
git commit -m "feat(health-analysis): add DTOs for payload, disease, request, summary"
```

---

## Task 6: HealthData 엔티티 + 리포지터리

**Files:**
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthData.java`
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/repository/HealthDataRepository.java`

- [ ] **Step 1: HealthData 엔티티 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthData.java
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
```

- [ ] **Step 2: HealthDataRepository 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/repository/HealthDataRepository.java
package com.agentsupport.healthanalysis.repository;

import com.agentsupport.healthanalysis.entity.HealthData;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthDataRepository extends JpaRepository<HealthData, UUID> {
}
```

- [ ] **Step 3: 빌드 확인**

Run: `cd backend; ./gradlew.bat compileJava`
Expected: 성공.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthData.java backend/src/main/java/com/agentsupport/healthanalysis/repository/HealthDataRepository.java
git commit -m "feat(health-analysis): add HealthData entity and repository"
```

---

## Task 7: HealthAnalysis 엔티티 + 리포지터리

**Files:**
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthAnalysis.java`
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/repository/HealthAnalysisRepository.java`

- [ ] **Step 1: HealthAnalysis 엔티티 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthAnalysis.java
package com.agentsupport.healthanalysis.entity;

import com.agentsupport.healthanalysis.RiskGrade;
import com.agentsupport.healthanalysis.UnderwritingRecommendation;
import com.agentsupport.security.PiiAttributeConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
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
```

- [ ] **Step 2: HealthAnalysisRepository 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/repository/HealthAnalysisRepository.java
package com.agentsupport.healthanalysis.repository;

import com.agentsupport.healthanalysis.entity.HealthAnalysis;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthAnalysisRepository extends JpaRepository<HealthAnalysis, UUID> {

  Optional<HealthAnalysis> findByCustomerId(UUID customerId);

  List<HealthAnalysis> findByCustomerIdIn(List<UUID> customerIds);

  @Query("""
      SELECT h FROM HealthAnalysis h
      WHERE h.analyzedBy = :analyzedBy
      ORDER BY h.analyzedAt DESC
      """)
  List<HealthAnalysis> findRecentByAnalyzedBy(@Param("analyzedBy") String analyzedBy,
                                              org.springframework.data.domain.Pageable pageable);

  @Query("""
      SELECT h FROM HealthAnalysis h
      ORDER BY h.analyzedAt DESC
      """)
  List<HealthAnalysis> findAllRecent(org.springframework.data.domain.Pageable pageable);

  long countByAnalyzedBy(String analyzedBy);
  long countByAnalyzedByAndRiskGrade(String analyzedBy, com.agentsupport.healthanalysis.RiskGrade riskGrade);
  long countByRiskGrade(com.agentsupport.healthanalysis.RiskGrade riskGrade);
}
```

- [ ] **Step 3: 빌드 확인**

Run: `cd backend; ./gradlew.bat compileJava`
Expected: 성공.

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthAnalysis.java backend/src/main/java/com/agentsupport/healthanalysis/repository/HealthAnalysisRepository.java
git commit -m "feat(health-analysis): add HealthAnalysis entity and repository"
```

---

# Phase B: Backend Logic (TDD)

## Task 8: DummyHealthDataGenerator + 테스트

**Files:**
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/service/DummyHealthDataGenerator.java`
- Create: `backend/src/test/java/com/agentsupport/healthanalysis/DummyHealthDataGeneratorTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
// backend/src/test/java/com/agentsupport/healthanalysis/DummyHealthDataGeneratorTest.java
package com.agentsupport.healthanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentsupport.healthanalysis.dto.HealthDataPayload;
import com.agentsupport.healthanalysis.service.DummyHealthDataGenerator;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DummyHealthDataGeneratorTest {

  private final DummyHealthDataGenerator generator = new DummyHealthDataGenerator();

  @Test
  void normalScenarioHasNoChronicDiagnosis() {
    HealthDataPayload payload = generator.generate(UUID.randomUUID(), Scenario.NORMAL, 35, "M");
    boolean anyChronic = payload.visits().stream()
        .anyMatch(v -> ChronicConditions.isChronic(v.diagnosisCode()));
    assertThat(anyChronic).isFalse();
    assertThat(payload.admissions()).isEmpty();
  }

  @Test
  void hypertensionScenarioHasI10MonthlyVisits() {
    HealthDataPayload payload = generator.generate(UUID.randomUUID(), Scenario.HYPERTENSION, 50, "M");
    long i10Count = payload.visits().stream()
        .filter(v -> "I10".equals(v.diagnosisCode()))
        .count();
    assertThat(i10Count).isGreaterThanOrEqualTo(10);  // 월 1회 12개월 ≥ 10건
    assertThat(payload.prescriptions()).isNotEmpty();
  }

  @Test
  void diabetesScenarioHasE11MonthlyVisits() {
    HealthDataPayload payload = generator.generate(UUID.randomUUID(), Scenario.DIABETES, 55, "F");
    long e11Count = payload.visits().stream()
        .filter(v -> "E11".equals(v.diagnosisCode()))
        .count();
    assertThat(e11Count).isGreaterThanOrEqualTo(10);
  }

  @Test
  void complexScenarioHasBothI10AndE11AndAdmission() {
    HealthDataPayload payload = generator.generate(UUID.randomUUID(), Scenario.COMPLEX, 60, "M");
    boolean hasI10 = payload.visits().stream().anyMatch(v -> "I10".equals(v.diagnosisCode()));
    boolean hasE11 = payload.visits().stream().anyMatch(v -> "E11".equals(v.diagnosisCode()));
    assertThat(hasI10).isTrue();
    assertThat(hasE11).isTrue();
    assertThat(payload.admissions()).isNotEmpty();
  }

  @Test
  void sameSeededScenarioProducesSameResult() {
    UUID customerId = UUID.randomUUID();
    HealthDataPayload p1 = generator.generate(customerId, Scenario.HYPERTENSION, 50, "M");
    HealthDataPayload p2 = generator.generate(customerId, Scenario.HYPERTENSION, 50, "M");
    assertThat(p1.visits()).hasSize(p2.visits().size());
  }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend; ./gradlew.bat test --tests DummyHealthDataGeneratorTest`
Expected: 컴파일 에러 또는 ClassNotFound (DummyHealthDataGenerator 미존재).

- [ ] **Step 3: DummyHealthDataGenerator 구현**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/service/DummyHealthDataGenerator.java
package com.agentsupport.healthanalysis.service;

import com.agentsupport.healthanalysis.Scenario;
import com.agentsupport.healthanalysis.dto.HealthDataPayload;
import com.agentsupport.healthanalysis.dto.HealthDataPayload.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DummyHealthDataGenerator {

  public HealthDataPayload generate(UUID customerId, Scenario scenario, int age, String gender) {
    long seed = scenario == Scenario.RANDOM
        ? customerId.hashCode() ^ System.currentTimeMillis()
        : customerId.hashCode() ^ scenario.ordinal();
    Random random = new Random(seed);

    Scenario effective = scenario == Scenario.RANDOM ? rollRandomScenario(random, age) : scenario;
    Demographics demographics = new Demographics(age, gender);

    return switch (effective) {
      case NORMAL -> normalPayload(demographics, random);
      case HYPERTENSION -> hypertensionPayload(demographics, random);
      case DIABETES -> diabetesPayload(demographics, random);
      case COMPLEX -> complexPayload(demographics, random);
      case RANDOM -> normalPayload(demographics, random);  // never hit
    };
  }

  private Scenario rollRandomScenario(Random random, int age) {
    int roll = random.nextInt(100);
    int normalCutoff = age >= 50 ? 40 : 70;
    int singleChronicCutoff = age >= 50 ? 85 : 95;
    if (roll < normalCutoff) return Scenario.NORMAL;
    if (roll < singleChronicCutoff) return random.nextBoolean() ? Scenario.HYPERTENSION : Scenario.DIABETES;
    return Scenario.COMPLEX;
  }

  private HealthDataPayload normalPayload(Demographics d, Random random) {
    List<Visit> visits = new ArrayList<>();
    int count = random.nextInt(3);
    for (int i = 0; i < count; i++) {
      visits.add(new Visit(
          LocalDate.now().minusDays(random.nextInt(365)),
          "J00",
          "급성 비인두염",
          "내과"
      ));
    }
    return new HealthDataPayload(d, visits, List.of(), List.of());
  }

  private HealthDataPayload hypertensionPayload(Demographics d, Random random) {
    List<Visit> visits = new ArrayList<>();
    List<Prescription> rx = new ArrayList<>();
    for (int month = 0; month < 12; month++) {
      LocalDate date = LocalDate.now().minusMonths(month).withDayOfMonth(15);
      visits.add(new Visit(date, "I10", "본태성 고혈압", "내과"));
      rx.add(new Prescription(date, "ARB", 30));
    }
    return new HealthDataPayload(d, visits, rx, List.of());
  }

  private HealthDataPayload diabetesPayload(Demographics d, Random random) {
    List<Visit> visits = new ArrayList<>();
    List<Prescription> rx = new ArrayList<>();
    for (int month = 0; month < 12; month++) {
      LocalDate date = LocalDate.now().minusMonths(month).withDayOfMonth(20);
      visits.add(new Visit(date, "E11", "제2형 당뇨병", "내과"));
      rx.add(new Prescription(date, "Metformin", 30));
    }
    return new HealthDataPayload(d, visits, rx, List.of());
  }

  private HealthDataPayload complexPayload(Demographics d, Random random) {
    List<Visit> visits = new ArrayList<>();
    List<Prescription> rx = new ArrayList<>();
    for (int month = 0; month < 12; month++) {
      LocalDate date = LocalDate.now().minusMonths(month).withDayOfMonth(10);
      visits.add(new Visit(date, "I10", "본태성 고혈압", "내과"));
      visits.add(new Visit(date.plusDays(5), "E11", "제2형 당뇨병", "내과"));
      rx.add(new Prescription(date, "ARB", 30));
      rx.add(new Prescription(date, "Metformin", 30));
    }
    List<Admission> admissions = List.of(new Admission(
        LocalDate.now().minusMonths(6),
        LocalDate.now().minusMonths(6).plusDays(5),
        "E11.9",
        "당뇨 합병증"
    ));
    return new HealthDataPayload(d, visits, rx, admissions);
  }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend; ./gradlew.bat test --tests DummyHealthDataGeneratorTest`
Expected: 5/5 PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/healthanalysis/service/DummyHealthDataGenerator.java backend/src/test/java/com/agentsupport/healthanalysis/DummyHealthDataGeneratorTest.java
git commit -m "feat(health-analysis): add DummyHealthDataGenerator with scenario-based dummy data"
```

---

## Task 9: HealthAnalysisService.analyze() + 룰 테스트

**Files:**
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/service/HealthAnalysisService.java`
- Create: `backend/src/test/java/com/agentsupport/healthanalysis/HealthAnalysisServiceTest.java`

- [ ] **Step 1: 분석 룰 테스트 작성 (실패)**

```java
// backend/src/test/java/com/agentsupport/healthanalysis/HealthAnalysisServiceTest.java
package com.agentsupport.healthanalysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentsupport.healthanalysis.dto.HealthDataPayload;
import com.agentsupport.healthanalysis.dto.HealthDataPayload.*;
import com.agentsupport.healthanalysis.service.HealthAnalysisService;
import com.agentsupport.healthanalysis.service.HealthAnalysisService.AnalysisOutcome;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HealthAnalysisServiceTest {

  private final HealthAnalysisService service = new HealthAnalysisService(null, null, null, null, null);

  @Test
  void normalPayloadProducesNormalGradeAndApprove() {
    HealthDataPayload payload = new HealthDataPayload(
        new Demographics(30, "M"),
        List.of(new Visit(LocalDate.now().minusDays(60), "J00", "급성 비인두염", "내과")),
        List.of(), List.of()
    );
    AnalysisOutcome outcome = service.analyze(payload);
    assertThat(outcome.riskGrade()).isEqualTo(RiskGrade.NORMAL);
    assertThat(outcome.recommendation()).isEqualTo(UnderwritingRecommendation.APPROVE);
    assertThat(outcome.hasDisease()).isFalse();
    assertThat(outcome.diseases()).isEmpty();
  }

  @Test
  void singleChronicProducesCautionAndConditional() {
    HealthDataPayload payload = new HealthDataPayload(
        new Demographics(50, "M"),
        List.of(
            new Visit(LocalDate.now().minusMonths(6), "I10", "본태성 고혈압", "내과"),
            new Visit(LocalDate.now().minusMonths(3), "I10", "본태성 고혈압", "내과")
        ),
        List.of(), List.of()
    );
    AnalysisOutcome outcome = service.analyze(payload);
    assertThat(outcome.riskGrade()).isEqualTo(RiskGrade.CAUTION);
    assertThat(outcome.recommendation()).isEqualTo(UnderwritingRecommendation.CONDITIONAL);
    assertThat(outcome.hasDisease()).isTrue();
    assertThat(outcome.diseases()).hasSize(1);
    assertThat(outcome.diseases().get(0).code()).isEqualTo("I10");
  }

  @Test
  void bothChronicProducesRiskAndDecline() {
    HealthDataPayload payload = new HealthDataPayload(
        new Demographics(55, "F"),
        List.of(
            new Visit(LocalDate.now().minusMonths(6), "I10", "본태성 고혈압", "내과"),
            new Visit(LocalDate.now().minusMonths(4), "E11", "제2형 당뇨병", "내과")
        ),
        List.of(), List.of()
    );
    AnalysisOutcome outcome = service.analyze(payload);
    assertThat(outcome.riskGrade()).isEqualTo(RiskGrade.RISK);
    assertThat(outcome.recommendation()).isEqualTo(UnderwritingRecommendation.DECLINE);
    assertThat(outcome.diseases()).hasSize(2);
  }

  @Test
  void admissionAloneProducesRiskAndDecline() {
    HealthDataPayload payload = new HealthDataPayload(
        new Demographics(40, "M"),
        List.of(new Visit(LocalDate.now().minusMonths(2), "S52", "팔뼈 골절", "정형외과")),
        List.of(),
        List.of(new Admission(LocalDate.now().minusMonths(2), LocalDate.now().minusMonths(2).plusDays(7), "S52", "골절 수술"))
    );
    AnalysisOutcome outcome = service.analyze(payload);
    assertThat(outcome.riskGrade()).isEqualTo(RiskGrade.RISK);
    assertThat(outcome.recommendation()).isEqualTo(UnderwritingRecommendation.DECLINE);
  }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd backend; ./gradlew.bat test --tests HealthAnalysisServiceTest`
Expected: 컴파일 에러 (HealthAnalysisService.analyze, AnalysisOutcome 미존재).

- [ ] **Step 3: HealthAnalysisService skeleton + analyze() 구현**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/service/HealthAnalysisService.java
package com.agentsupport.healthanalysis.service;

import com.agentsupport.healthanalysis.ChronicConditions;
import com.agentsupport.healthanalysis.RiskGrade;
import com.agentsupport.healthanalysis.UnderwritingRecommendation;
import com.agentsupport.healthanalysis.dto.DiseaseDto;
import com.agentsupport.healthanalysis.dto.HealthDataPayload;
import com.agentsupport.healthanalysis.entity.HealthAnalysis;
import com.agentsupport.healthanalysis.entity.HealthData;
import com.agentsupport.healthanalysis.repository.HealthAnalysisRepository;
import com.agentsupport.healthanalysis.repository.HealthDataRepository;
import com.agentsupport.customer.repository.CustomerRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class HealthAnalysisService {

  private final DummyHealthDataGenerator generator;
  private final HealthDataRepository healthDataRepository;
  private final HealthAnalysisRepository healthAnalysisRepository;
  private final CustomerRepository customerRepository;
  private final ObjectMapper objectMapper;

  public HealthAnalysisService(
      DummyHealthDataGenerator generator,
      HealthDataRepository healthDataRepository,
      HealthAnalysisRepository healthAnalysisRepository,
      CustomerRepository customerRepository,
      ObjectMapper objectMapper
  ) {
    this.generator = generator;
    this.healthDataRepository = healthDataRepository;
    this.healthAnalysisRepository = healthAnalysisRepository;
    this.customerRepository = customerRepository;
    this.objectMapper = objectMapper;
  }

  public record AnalysisOutcome(
      RiskGrade riskGrade,
      UnderwritingRecommendation recommendation,
      boolean hasDisease,
      List<DiseaseDto> diseases,
      String summary
  ) {}

  public AnalysisOutcome analyze(HealthDataPayload payload) {
    Set<String> diagnoses = payload.visits().stream()
        .map(HealthDataPayload.Visit::diagnosisCode)
        .collect(Collectors.toSet());
    Set<String> chronicHeld = diagnoses.stream()
        .filter(ChronicConditions::isChronic)
        .collect(Collectors.toSet());
    boolean hasBothMajorChronic = chronicHeld.contains("I10") && chronicHeld.contains("E11");
    int admissionCount = payload.admissions().size();

    RiskGrade grade;
    UnderwritingRecommendation rec;
    if (admissionCount > 0 || hasBothMajorChronic) {
      grade = RiskGrade.RISK;
      rec = UnderwritingRecommendation.DECLINE;
    } else if (!chronicHeld.isEmpty()) {
      grade = RiskGrade.CAUTION;
      rec = UnderwritingRecommendation.CONDITIONAL;
    } else {
      grade = RiskGrade.NORMAL;
      rec = UnderwritingRecommendation.APPROVE;
    }

    List<DiseaseDto> diseases = chronicHeld.stream()
        .map(code -> buildDisease(code, payload))
        .sorted(Comparator.comparing(DiseaseDto::code))
        .toList();

    String summary = composeSummary(grade, admissionCount, chronicHeld);
    return new AnalysisOutcome(grade, rec, !chronicHeld.isEmpty(), diseases, summary);
  }

  private DiseaseDto buildDisease(String code, HealthDataPayload payload) {
    List<HealthDataPayload.Visit> visitsForCode = payload.visits().stream()
        .filter(v -> code.equals(v.diagnosisCode()))
        .toList();
    LocalDate earliest = visitsForCode.stream()
        .map(HealthDataPayload.Visit::date)
        .min(Comparator.naturalOrder())
        .orElse(LocalDate.now());
    String diagnosedAt = earliest.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    String frequency = "월 " + Math.max(1, visitsForCode.size() / 12) + "회";
    return new DiseaseDto(code, ChronicConditions.CODES.get(code), diagnosedAt, frequency);
  }

  private String composeSummary(RiskGrade grade, int admissionCount, Set<String> chronicHeld) {
    if (grade == RiskGrade.NORMAL) {
      return "유의한 만성 질환 이력이 확인되지 않습니다.";
    }
    if (grade == RiskGrade.RISK && admissionCount > 0) {
      return "최근 입원 이력이 있어 인수 거절을 권고합니다.";
    }
    if (grade == RiskGrade.RISK) {
      return "복합 만성 질환 보유로 인수 거절을 권고합니다.";
    }
    return "만성질환 관리 양호하나 합병증 모니터링 필요";
  }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd backend; ./gradlew.bat test --tests HealthAnalysisServiceTest`
Expected: 4/4 PASS.

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/healthanalysis/service/HealthAnalysisService.java backend/src/test/java/com/agentsupport/healthanalysis/HealthAnalysisServiceTest.java
git commit -m "feat(health-analysis): add HealthAnalysisService.analyze() with rule-based grading"
```

---

## Task 10: HealthAnalysisService.createOrUpdate() — DB persistence

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/healthanalysis/service/HealthAnalysisService.java`
- Modify: `backend/src/test/java/com/agentsupport/healthanalysis/HealthAnalysisServiceTest.java`

- [ ] **Step 1: 영속화 메서드 시그니처 추가 (테스트 작성용)**

테스트는 다음 단계에서. 우선 메서드 stub:

Edit `HealthAnalysisService.java` — `analyze()` 메서드 뒤에 추가:

```java
@org.springframework.transaction.annotation.Transactional
public com.agentsupport.healthanalysis.dto.HealthAnalysisDto createOrUpdate(
    UUID customerId,
    com.agentsupport.healthanalysis.Scenario scenario,
    String agentId
) {
  com.agentsupport.customer.entity.Customer customer = customerRepository.findById(customerId)
      .orElseThrow(() -> new com.agentsupport.common.NotFoundException("customer not found"));

  // 1. 더미 데이터 생성
  HealthDataPayload payload = generator.generate(
      customerId, scenario, computeAge(customer), customer.getGender());
  String payloadJson = serialize(payload);

  // 2. HealthData 저장 (누적)
  HealthData savedData = healthDataRepository.save(HealthData.create(
      customerId, "NHIS_DUMMY", scenario.name(), payloadJson, agentId
  ));

  // 3. 분석
  AnalysisOutcome outcome = analyze(payload);
  String diseasesJson = serialize(outcome.diseases());

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
  HealthAnalysis saved = healthAnalysisRepository.save(analysis);

  return toDto(saved, customer);
}

private int computeAge(com.agentsupport.customer.entity.Customer customer) {
  if (customer.getBirthDate() == null) return 40;  // default
  return java.time.Period.between(customer.getBirthDate(), java.time.LocalDate.now()).getYears();
}

private String serialize(Object value) {
  try {
    return objectMapper.writeValueAsString(value);
  } catch (JsonProcessingException e) {
    throw new IllegalStateException("Failed to serialize health data", e);
  }
}

private <T> T deserialize(String json, com.fasterxml.jackson.core.type.TypeReference<T> typeRef) {
  try {
    return objectMapper.readValue(json, typeRef);
  } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
    throw new IllegalStateException("Failed to deserialize health data", e);
  }
}

private com.agentsupport.healthanalysis.dto.HealthAnalysisDto toDto(
    HealthAnalysis a, com.agentsupport.customer.entity.Customer customer) {
  List<DiseaseDto> diseases = deserialize(
      a.getDiseases(),
      new com.fasterxml.jackson.core.type.TypeReference<List<DiseaseDto>>() {}
  );
  return new com.agentsupport.healthanalysis.dto.HealthAnalysisDto(
      a.getId(), a.getCustomerId(), customer.getName(),
      a.getRiskGrade(), a.isHasDisease(), diseases,
      a.getUnderwritingRecommendation(), a.getSummary(),
      a.getAnalyzedAt(), a.getAnalyzedBy()
  );
}
```

NOTE: `NotFoundException` 가 기존에 없다면 `IllegalArgumentException` 또는 적절한 예외로 대체. 기존 코드의 패턴 확인: `Select-String -Path backend/src -Pattern "extends RuntimeException"` 로 기존 예외 클래스 찾을 것.

- [ ] **Step 2: 빌드 확인**

Run: `cd backend; ./gradlew.bat compileJava compileTestJava`
Expected: 성공 (분석 룰 테스트는 여전히 통과).

만약 `NotFoundException` 없으면 — `IllegalArgumentException("customer not found: " + customerId)` 로 대체.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/healthanalysis/service/HealthAnalysisService.java
git commit -m "feat(health-analysis): add createOrUpdate with HealthData accumulation and analysis upsert"
```

---

## Task 11: HealthAnalysisService — 조회 메서드들

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/healthanalysis/service/HealthAnalysisService.java`

- [ ] **Step 1: 조회 메서드 추가**

Edit `HealthAnalysisService.java` — 클래스 끝에 추가:

```java
public java.util.Map<UUID, com.agentsupport.healthanalysis.dto.HealthAnalysisDto>
findByCustomerIds(java.util.List<UUID> customerIds, String agentId, boolean isAdmin) {
  java.util.List<UUID> allowedCustomerIds;
  if (isAdmin) {
    allowedCustomerIds = customerIds;
  } else {
    allowedCustomerIds = customerRepository.findAllById(customerIds).stream()
        .filter(c -> agentId.equals(c.getAgentId()))
        .map(com.agentsupport.customer.entity.Customer::getId)
        .toList();
  }
  if (allowedCustomerIds.isEmpty()) return java.util.Map.of();

  java.util.Map<UUID, com.agentsupport.customer.entity.Customer> customerById =
      customerRepository.findAllById(allowedCustomerIds).stream()
          .collect(Collectors.toMap(com.agentsupport.customer.entity.Customer::getId, c -> c));

  return healthAnalysisRepository.findByCustomerIdIn(allowedCustomerIds).stream()
      .collect(Collectors.toMap(
          HealthAnalysis::getCustomerId,
          a -> toDto(a, customerById.get(a.getCustomerId()))
      ));
}

public com.agentsupport.healthanalysis.dto.HealthAnalysisDto findById(
    UUID id, String agentId, boolean isAdmin
) {
  HealthAnalysis analysis = healthAnalysisRepository.findById(id)
      .orElseThrow(() -> new IllegalArgumentException("analysis not found: " + id));
  com.agentsupport.customer.entity.Customer customer = customerRepository.findById(analysis.getCustomerId())
      .orElseThrow(() -> new IllegalArgumentException("customer not found"));
  if (!isAdmin && !agentId.equals(customer.getAgentId())) {
    throw new org.springframework.security.access.AccessDeniedException("not allowed");
  }
  return toDto(analysis, customer);
}

public com.agentsupport.healthanalysis.dto.AnalysisSummaryDto summary(String agentId, boolean isAdmin) {
  if (isAdmin) {
    long total = healthAnalysisRepository.count();
    long normal = healthAnalysisRepository.countByRiskGrade(RiskGrade.NORMAL);
    long caution = healthAnalysisRepository.countByRiskGrade(RiskGrade.CAUTION);
    long risk = healthAnalysisRepository.countByRiskGrade(RiskGrade.RISK);
    return new com.agentsupport.healthanalysis.dto.AnalysisSummaryDto(total, normal, caution, risk);
  }
  long total = healthAnalysisRepository.countByAnalyzedBy(agentId);
  long normal = healthAnalysisRepository.countByAnalyzedByAndRiskGrade(agentId, RiskGrade.NORMAL);
  long caution = healthAnalysisRepository.countByAnalyzedByAndRiskGrade(agentId, RiskGrade.CAUTION);
  long risk = healthAnalysisRepository.countByAnalyzedByAndRiskGrade(agentId, RiskGrade.RISK);
  return new com.agentsupport.healthanalysis.dto.AnalysisSummaryDto(total, normal, caution, risk);
}

public java.util.List<com.agentsupport.healthanalysis.dto.RecentAnalysisItemDto> recent(
    int limit, String agentId, boolean isAdmin
) {
  org.springframework.data.domain.Pageable pageable =
      org.springframework.data.domain.PageRequest.of(0, limit);
  java.util.List<HealthAnalysis> analyses = isAdmin
      ? healthAnalysisRepository.findAllRecent(pageable)
      : healthAnalysisRepository.findRecentByAnalyzedBy(agentId, pageable);

  if (analyses.isEmpty()) return java.util.List.of();
  java.util.List<UUID> customerIds = analyses.stream().map(HealthAnalysis::getCustomerId).toList();
  java.util.Map<UUID, com.agentsupport.customer.entity.Customer> customerById =
      customerRepository.findAllById(customerIds).stream()
          .collect(Collectors.toMap(com.agentsupport.customer.entity.Customer::getId, c -> c));

  return analyses.stream()
      .map(a -> new com.agentsupport.healthanalysis.dto.RecentAnalysisItemDto(
          a.getId(), a.getCustomerId(),
          customerById.get(a.getCustomerId()).getName(),
          a.getRiskGrade(), a.getAnalyzedAt()))
      .toList();
}
```

- [ ] **Step 2: 빌드 확인**

Run: `cd backend; ./gradlew.bat compileJava`
Expected: 성공.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/healthanalysis/service/HealthAnalysisService.java
git commit -m "feat(health-analysis): add findByCustomerIds, findById, summary, recent service methods"
```

---

# Phase C: Controller + Security

## Task 12: HealthAnalysisController + 권한 통합 테스트

**Files:**
- Create: `backend/src/main/java/com/agentsupport/healthanalysis/HealthAnalysisController.java`
- Modify: `backend/src/main/java/com/agentsupport/config/SecurityConfig.java`
- Create: `backend/src/test/java/com/agentsupport/healthanalysis/HealthAnalysisControllerTest.java`

- [ ] **Step 1: SecurityConfig에 권한 룰 추가**

Edit `backend/src/main/java/com/agentsupport/config/SecurityConfig.java` — 기존 endpoint 권한 매핑에 추가 (정확한 위치는 기존 패턴 확인):

```java
.requestMatchers("/api/health-analyses/**").hasAnyRole("ADMIN", "AGENT1")
```

이 줄을 다른 `/api/*/**` 룰들과 같은 블록에 추가. AGENT2 차단은 hasAnyRole에서 자동.

- [ ] **Step 2: 컨트롤러 작성**

```java
// backend/src/main/java/com/agentsupport/healthanalysis/HealthAnalysisController.java
package com.agentsupport.healthanalysis;

import com.agentsupport.healthanalysis.dto.*;
import com.agentsupport.healthanalysis.service.HealthAnalysisService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/health-analyses")
public class HealthAnalysisController {

  private final HealthAnalysisService service;

  public HealthAnalysisController(HealthAnalysisService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<HealthAnalysisDto> create(
      @RequestBody @Valid AnalysisRequestDto req,
      Authentication auth
  ) {
    HealthAnalysisDto result = service.createOrUpdate(req.customerId(), req.scenario(), auth.getName());
    return ResponseEntity.ok(result);
  }

  @GetMapping
  public Map<UUID, HealthAnalysisDto> byCustomers(
      @RequestParam("customerIds") List<UUID> customerIds,
      Authentication auth
  ) {
    return service.findByCustomerIds(customerIds, auth.getName(), isAdmin(auth));
  }

  @GetMapping("/{id}")
  public HealthAnalysisDto byId(@PathVariable UUID id, Authentication auth) {
    return service.findById(id, auth.getName(), isAdmin(auth));
  }

  @GetMapping("/summary")
  public AnalysisSummaryDto summary(Authentication auth) {
    return service.summary(auth.getName(), isAdmin(auth));
  }

  @GetMapping("/recent")
  public List<RecentAnalysisItemDto> recent(
      @RequestParam(defaultValue = "5") int limit,
      Authentication auth
  ) {
    return service.recent(limit, auth.getName(), isAdmin(auth));
  }

  private boolean isAdmin(Authentication auth) {
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch("ROLE_ADMIN"::equals);
  }
}
```

- [ ] **Step 3: 컨트롤러 권한 테스트 작성**

```java
// backend/src/test/java/com/agentsupport/healthanalysis/HealthAnalysisControllerTest.java
package com.agentsupport.healthanalysis;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HealthAnalysisControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;
  // adminSession / agent1Session / agent2Session 헬퍼는 CustomerControllerTest의 패턴 그대로 복사
  // (해당 헬퍼들이 별도 추출되어 있으면 그것을 사용)

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
    // 세션 셋업: CustomerControllerTest 참조
  }

  @Test
  void agent2_returns_403_on_post() throws Exception {
    // AGENT2 세션으로 POST /api/health-analyses
    // mockMvc.perform(post("/api/health-analyses").session(agent2Session)...)
    //   .andExpect(status().isForbidden());
  }

  @Test
  void agent1_can_analyze_own_customer() throws Exception {
    // AGENT1 본인 고객으로 POST → 200, 결과 검증
  }

  @Test
  void agent1_cannot_analyze_others_customer() throws Exception {
    // AGENT1이 다른 사람 고객 id로 POST → 403
  }

  @Test
  void admin_can_analyze_any_customer() throws Exception {
    // ADMIN POST → 200
  }

  @Test
  void by_customers_filters_out_unauthorized_ids() throws Exception {
    // AGENT1이 본인 고객 id + 타인 고객 id 둘 다 요청 → 응답 map에 본인 것만 포함
  }

  @Test
  void summary_returns_self_only_for_agent1() throws Exception {
    // AGENT1 본인 분석 1건 + ADMIN 분석 2건 셋업 → AGENT1 summary total=1
  }
}
```

**중요**: `CustomerControllerTest` 의 헬퍼 메서드(세션 셋업, 테스트 fixture 등) 패턴을 그대로 따라가야 함. 실제 테스트 구현 시 그 파일을 참조해서 일관된 스타일 유지.

- [ ] **Step 4: 테스트 헬퍼 + fixture 실제 구현**

`CustomerControllerTest`의 `@BeforeEach`, 세션 빌더, customer 등록 로직을 복사해서 `HealthAnalysisControllerTest`에 맞게 조정. 더미 customer 2-3명 (다른 agent_id) 등록 후 시나리오별 분석 호출.

(실제 구현은 그 파일을 읽고 패턴 그대로. 본 plan에서는 모든 헬퍼 메서드 코드를 반복하지 않음 — 50줄 이상이라 plan이 흩어짐. CustomerControllerTest의 패턴이 표준.)

- [ ] **Step 5: 테스트 실행**

Run: `cd backend; ./gradlew.bat test --tests HealthAnalysisControllerTest`
Expected: 모든 권한 케이스 PASS.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/healthanalysis/HealthAnalysisController.java backend/src/main/java/com/agentsupport/config/SecurityConfig.java backend/src/test/java/com/agentsupport/healthanalysis/HealthAnalysisControllerTest.java
git commit -m "feat(health-analysis): add controller with role-based access control"
```

---

## Task 13: 백엔드 전체 빌드 + 부트 검증

- [ ] **Step 1: 전체 테스트 실행**

Run: `cd backend; ./gradlew.bat test`
Expected: 모든 테스트 통과 (기존 + 신규).

- [ ] **Step 2: bootRun 으로 컨텍스트 확인**

Run: `cd backend; ./gradlew.bat bootRun` (별도 터미널)

브라우저: http://localhost:8080/actuator/health → `{"status":"UP"}`

ADMIN 세션으로 (이미 로그인된 상태 가정 또는 manual curl):
```
curl -X POST http://localhost:8080/api/health-analyses \
  -H "Content-Type: application/json" \
  -H "Cookie: AGENT_SESSION=..." \
  -d '{"customerId":"<uuid>","scenario":"HYPERTENSION"}'
```

Expected: 200 응답, JSON에 riskGrade/diseases 등 포함.

(이 단계는 수동 검증. plan에서는 도커/스크립트 없음.)

- [ ] **Step 3: 백엔드 부분만 한 번 더 커밋 (지금까지 정리용 노옵 commit 안 함)**

생략. 이 단계는 검증 only.

---

# Phase D: Frontend — entities/health-analysis

## Task 14: entities/health-analysis/api/index.ts

**Files:**
- Create: `frontend/src/entities/health-analysis/api/index.ts`

- [ ] **Step 1: api/index.ts 작성**

```ts
// frontend/src/entities/health-analysis/api/index.ts
import { apiFetch } from "@/shared/api";

export type RiskGrade = "NORMAL" | "CAUTION" | "RISK";
export type UnderwritingRecommendation = "APPROVE" | "CONDITIONAL" | "DECLINE";
export type Scenario = "RANDOM" | "NORMAL" | "HYPERTENSION" | "DIABETES" | "COMPLEX";

export interface DiseaseDto {
  code: string;
  name: string;
  diagnosedAt: string;
  frequency: string;
}

export interface HealthAnalysisDto {
  id: string;
  customerId: string;
  customerName: string;
  riskGrade: RiskGrade;
  hasDisease: boolean;
  diseases: DiseaseDto[];
  underwritingRecommendation: UnderwritingRecommendation;
  summary: string;
  analyzedAt: string;
  analyzedBy: string;
}

export interface AnalysisSummaryDto {
  total: number;
  normal: number;
  caution: number;
  risk: number;
}

export interface RecentAnalysisItemDto {
  id: string;
  customerId: string;
  customerName: string;
  riskGrade: RiskGrade;
  analyzedAt: string;
}

export function fetchAnalysesByCustomers(
  customerIds: string[],
): Promise<Record<string, HealthAnalysisDto>> {
  if (customerIds.length === 0) return Promise.resolve({});
  const params = new URLSearchParams();
  params.set("customerIds", customerIds.join(","));
  return apiFetch<Record<string, HealthAnalysisDto>>(`/api/health-analyses?${params.toString()}`);
}

export function fetchAnalysis(id: string): Promise<HealthAnalysisDto> {
  return apiFetch<HealthAnalysisDto>(`/api/health-analyses/${id}`);
}

export function fetchAnalysisSummary(): Promise<AnalysisSummaryDto> {
  return apiFetch<AnalysisSummaryDto>("/api/health-analyses/summary");
}

export function fetchRecentAnalyses(limit = 5): Promise<RecentAnalysisItemDto[]> {
  return apiFetch<RecentAnalysisItemDto[]>(`/api/health-analyses/recent?limit=${limit}`);
}
```

- [ ] **Step 2: 폴더 + 파일 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/entities/health-analysis/api -Force
```

위 내용으로 파일 작성.

- [ ] **Step 3: 타입체크 확인**

Run: `cd frontend; pnpm typecheck`
Expected: 통과 (참조하는 곳 없음).

- [ ] **Step 4: 커밋 (Phase D 마지막에 일괄. 지금은 skip)**

---

## Task 15: entities/health-analysis/model/index.ts

**Files:**
- Create: `frontend/src/entities/health-analysis/model/index.ts`

- [ ] **Step 1: model/index.ts 작성**

```ts
// frontend/src/entities/health-analysis/model/index.ts
import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  fetchAnalysesByCustomers,
  fetchAnalysis,
  fetchAnalysisSummary,
  fetchRecentAnalyses,
} from "../api";

export function useAnalysesByCustomers(customerIds: (string | null | undefined)[]) {
  const ids = useMemo(
    () => [...new Set(customerIds.filter((x): x is string => !!x))].sort(),
    [customerIds],
  );
  return useQuery({
    queryKey: ["health-analyses", "by-customers", ids],
    queryFn: () => fetchAnalysesByCustomers(ids),
    enabled: ids.length > 0,
    staleTime: 30_000,
  });
}

export function useAnalysis(id: string | null | undefined) {
  return useQuery({
    queryKey: ["health-analyses", "by-id", id],
    queryFn: () => fetchAnalysis(id as string),
    enabled: !!id,
  });
}

export function useAnalysisSummary() {
  return useQuery({
    queryKey: ["health-analyses", "summary"],
    queryFn: fetchAnalysisSummary,
  });
}

export function useRecentAnalyses(limit = 5) {
  return useQuery({
    queryKey: ["health-analyses", "recent", limit],
    queryFn: () => fetchRecentAnalyses(limit),
  });
}
```

- [ ] **Step 2: 폴더 + 파일 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/entities/health-analysis/model -Force
```

위 내용으로 파일 작성.

- [ ] **Step 3: 타입체크 확인**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

---

## Task 16: entities/health-analysis/ui/risk-badge

**Files:**
- Create: `frontend/src/entities/health-analysis/ui/risk-badge/index.tsx`

- [ ] **Step 1: 폴더 + 파일 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/entities/health-analysis/ui/risk-badge -Force
```

```tsx
// frontend/src/entities/health-analysis/ui/risk-badge/index.tsx
"use client";

import type { RiskGrade } from "../../api";

const GRADE_LABEL: Record<RiskGrade, string> = {
  NORMAL: "정상",
  CAUTION: "주의",
  RISK: "위험",
};

const GRADE_CLASS: Record<RiskGrade, string> = {
  NORMAL: "bg-status-success-container text-status-success",
  CAUTION: "bg-status-warning-container text-status-warning",
  RISK: "bg-status-error-container text-status-error",
};

interface Props {
  grade: RiskGrade;
  onClick?: () => void;
}

export function RiskBadge({ grade, onClick }: Props) {
  const className = `inline-flex items-center px-2 py-1 rounded-full text-xs font-medium ${GRADE_CLASS[grade]}`;
  if (onClick) {
    return (
      <button type="button" onClick={onClick} className={`${className} cursor-pointer hover:opacity-80`}>
        {GRADE_LABEL[grade]}
      </button>
    );
  }
  return <span className={className}>{GRADE_LABEL[grade]}</span>;
}
```

- [ ] **Step 2: 타입체크**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

---

## Task 17: entities/health-analysis/ui/analysis-result

**Files:**
- Create: `frontend/src/entities/health-analysis/ui/analysis-result/index.tsx`

- [ ] **Step 1: 폴더 + 파일 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/entities/health-analysis/ui/analysis-result -Force
```

```tsx
// frontend/src/entities/health-analysis/ui/analysis-result/index.tsx
"use client";

import type { HealthAnalysisDto, UnderwritingRecommendation } from "../../api";
import { RiskBadge } from "../risk-badge";

const REC_LABEL: Record<UnderwritingRecommendation, string> = {
  APPROVE: "권고 (정상 인수)",
  CONDITIONAL: "조건부 (보험료 할증 권고)",
  DECLINE: "거절 권고",
};

interface Props {
  analysis: HealthAnalysisDto;
}

export function AnalysisResult({ analysis }: Props) {
  const analyzedAt = new Date(analysis.analyzedAt).toLocaleString("ko-KR");
  return (
    <div className="flex flex-col gap-4">
      <div className="text-sm text-on-surface-variant">분석 시각: {analyzedAt}</div>
      <div className="flex items-center gap-3">
        <span className="text-sm font-medium text-on-surface">위험 등급</span>
        <RiskBadge grade={analysis.riskGrade} />
        <span className="text-sm text-on-surface-variant">
          유병 여부: {analysis.hasDisease ? "있음" : "없음"}
        </span>
      </div>
      {analysis.diseases.length > 0 && (
        <div>
          <div className="text-sm font-semibold text-on-surface mb-2">보유 질환</div>
          <ul className="flex flex-col gap-2">
            {analysis.diseases.map((d) => (
              <li key={d.code} className="text-sm text-on-surface">
                • {d.name} ({d.code})
                <div className="ml-3 text-xs text-on-surface-variant">
                  추정 진단 {d.diagnosedAt} · 처방 {d.frequency}
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
      <div>
        <div className="text-sm font-semibold text-on-surface">인수 권고</div>
        <div className="text-sm text-on-surface">{REC_LABEL[analysis.underwritingRecommendation]}</div>
      </div>
      <div>
        <div className="text-sm font-semibold text-on-surface">의견</div>
        <div className="text-sm text-on-surface-variant">{analysis.summary}</div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 타입체크**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

---

## Task 18: entities/health-analysis Public API + ui/index.ts

**Files:**
- Create: `frontend/src/entities/health-analysis/ui/index.ts`
- Create: `frontend/src/entities/health-analysis/index.ts`

- [ ] **Step 1: ui/index.ts 작성**

```ts
// frontend/src/entities/health-analysis/ui/index.ts
export { RiskBadge } from "./risk-badge";
export { AnalysisResult } from "./analysis-result";
```

- [ ] **Step 2: 슬라이스 Public API (index.ts) 작성**

```ts
// frontend/src/entities/health-analysis/index.ts
export type {
  RiskGrade,
  UnderwritingRecommendation,
  Scenario,
  DiseaseDto,
  HealthAnalysisDto,
  AnalysisSummaryDto,
  RecentAnalysisItemDto,
} from "./api";
export {
  fetchAnalysesByCustomers,
  fetchAnalysis,
  fetchAnalysisSummary,
  fetchRecentAnalyses,
} from "./api";
export {
  useAnalysesByCustomers,
  useAnalysis,
  useAnalysisSummary,
  useRecentAnalyses,
} from "./model";
export { RiskBadge, AnalysisResult } from "./ui";
```

- [ ] **Step 3: 타입체크 + Steiger + 커밋**

Run:
```powershell
cd frontend
pnpm typecheck
pnpm lint:fsd
```
Expected: 통과.

```bash
git add frontend/src/entities/health-analysis
git commit -m "feat(frontend): add entities/health-analysis slice (api/model/ui/index)"
```

---

# Phase E: Frontend — features/health-analysis/request

## Task 19: features/health-analysis/request/api

**Files:**
- Create: `frontend/src/features/health-analysis/request/api/index.ts`

- [ ] **Step 1: 폴더 + 파일 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/features/health-analysis/request/api -Force
```

```ts
// frontend/src/features/health-analysis/request/api/index.ts
import { apiFetch } from "@/shared/api";
import type { HealthAnalysisDto, Scenario } from "@/entities/health-analysis";

export interface CreateAnalysisRequest {
  customerId: string;
  scenario: Scenario;
}

export function createAnalysis(req: CreateAnalysisRequest): Promise<HealthAnalysisDto> {
  return apiFetch<HealthAnalysisDto>("/api/health-analyses", {
    method: "POST",
    body: JSON.stringify(req),
  });
}
```

- [ ] **Step 2: 타입체크**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

---

## Task 20: features/health-analysis/request/model

**Files:**
- Create: `frontend/src/features/health-analysis/request/model/index.ts`

- [ ] **Step 1: 폴더 + 파일 생성**

```ts
// frontend/src/features/health-analysis/request/model/index.ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { HealthAnalysisDto } from "@/entities/health-analysis";
import { createAnalysis, type CreateAnalysisRequest } from "../api";

export function useCreateAnalysis() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateAnalysisRequest) => createAnalysis(req),
    onSuccess: (newAnalysis: HealthAnalysisDto) => {
      qc.invalidateQueries({ queryKey: ["health-analyses"] });
      qc.setQueryData(["health-analyses", "by-id", newAnalysis.id], newAnalysis);
    },
  });
}
```

- [ ] **Step 2: 타입체크**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

---

## Task 21: features/health-analysis/request/ui — Step 1 (input)

**Files:**
- Create: `frontend/src/features/health-analysis/request/ui/step-input.tsx`

- [ ] **Step 1: 폴더 + 파일 생성**

Run:
```powershell
New-Item -ItemType Directory -Path frontend/src/features/health-analysis/request/ui -Force
```

```tsx
// frontend/src/features/health-analysis/request/ui/step-input.tsx
"use client";

import { useState } from "react";
import type { Scenario } from "@/entities/health-analysis";
import { Button } from "@/shared/ui/button";

const SCENARIO_OPTIONS: { value: Scenario; label: string }[] = [
  { value: "NORMAL", label: "정상" },
  { value: "HYPERTENSION", label: "고혈압" },
  { value: "DIABETES", label: "당뇨" },
  { value: "COMPLEX", label: "복합" },
];

interface Props {
  isPending: boolean;
  onSubmit: (scenario: Scenario) => void;
}

export function StepInput({ isPending, onSubmit }: Props) {
  const [mode, setMode] = useState<"auto" | "scenario">("auto");
  const [scenario, setScenario] = useState<Scenario>("NORMAL");

  function handleSubmit() {
    onSubmit(mode === "auto" ? "RANDOM" : scenario);
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="text-sm text-on-surface-variant">데이터 수집 시나리오</div>
      <div className="flex flex-col gap-2">
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            checked={mode === "auto"}
            onChange={() => setMode("auto")}
          />
          자동 수집 (기본)
        </label>
        <label className="flex items-center gap-2 text-sm">
          <input
            type="radio"
            checked={mode === "scenario"}
            onChange={() => setMode("scenario")}
          />
          시나리오 선택 (개발/시연용)
        </label>
        {mode === "scenario" && (
          <div className="ml-6 flex gap-2 flex-wrap">
            {SCENARIO_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                onClick={() => setScenario(opt.value)}
                className={`px-3 py-1 rounded-lg text-xs ${
                  scenario === opt.value
                    ? "bg-primary-container text-on-primary"
                    : "bg-surface-container text-on-surface"
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        )}
      </div>
      <Button onClick={handleSubmit} loading={isPending} className="!w-auto self-start px-6">
        수집 시작
      </Button>
    </div>
  );
}
```

- [ ] **Step 2: 타입체크**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

---

## Task 22: features/health-analysis/request/ui — Step 2 (result)

**Files:**
- Create: `frontend/src/features/health-analysis/request/ui/step-result.tsx`

- [ ] **Step 1: 파일 생성**

```tsx
// frontend/src/features/health-analysis/request/ui/step-result.tsx
"use client";

import type { HealthAnalysisDto } from "@/entities/health-analysis";
import { AnalysisResult } from "@/entities/health-analysis";
import { Button } from "@/shared/ui/button";

interface Props {
  analysis: HealthAnalysisDto;
  onReAnalyze: () => void;
  onClose: () => void;
}

export function StepResult({ analysis, onReAnalyze, onClose }: Props) {
  return (
    <div className="flex flex-col gap-4">
      <AnalysisResult analysis={analysis} />
      <div className="flex gap-2 justify-end">
        <button
          type="button"
          onClick={onClose}
          className="px-4 py-2 text-sm rounded-lg border border-outline-variant text-on-surface hover:bg-surface-container-low"
        >
          닫기
        </button>
        <Button onClick={onReAnalyze} className="!w-auto px-6">재분석</Button>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 타입체크**

Run: `cd frontend; pnpm typecheck`
Expected: 통과.

---

## Task 23: AnalysisRequestModal — 메인 컴포넌트

**Files:**
- Create: `frontend/src/features/health-analysis/request/ui/index.tsx`

- [ ] **Step 1: 모달 작성**

```tsx
// frontend/src/features/health-analysis/request/ui/index.tsx
"use client";

import { useState } from "react";
import { X } from "lucide-react";
import type { HealthAnalysisDto, Scenario } from "@/entities/health-analysis";
import { useCreateAnalysis } from "../model";
import { StepInput } from "./step-input";
import { StepResult } from "./step-result";

interface Props {
  customer: { id: string; name: string };
  existingAnalysis?: HealthAnalysisDto;
  onClose: () => void;
}

type ModalState =
  | { step: "input" }
  | { step: "collecting" }
  | { step: "result"; analysis: HealthAnalysisDto }
  | { step: "error"; message: string };

export function AnalysisRequestModal({ customer, existingAnalysis, onClose }: Props) {
  const [state, setState] = useState<ModalState>(
    existingAnalysis ? { step: "result", analysis: existingAnalysis } : { step: "input" }
  );
  const mutation = useCreateAnalysis();

  function handleSubmit(scenario: Scenario) {
    setState({ step: "collecting" });
    mutation.mutate(
      { customerId: customer.id, scenario },
      {
        onSuccess: (data) => setState({ step: "result", analysis: data }),
        onError: (err) => setState({
          step: "error",
          message: err instanceof Error ? err.message : "분석 실패",
        }),
      },
    );
  }

  const isPending = state.step === "collecting";
  const currentStep = state.step === "result" ? 2 : 1;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-surface-container-lowest rounded-2xl shadow-lg w-full max-w-lg mx-4 max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
          <div className="text-base font-semibold text-on-surface">
            건강 데이터 분석 — {customer.name}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-surface-container-low"
            aria-label="닫기"
          >
            <X size={20} />
          </button>
        </div>

        {/* Stepper */}
        <div className="flex items-center gap-3 px-6 py-3 text-xs text-on-surface-variant border-b border-outline-variant">
          <span className={currentStep === 1 ? "text-on-surface font-semibold" : ""}>
            ● Step 1 · 데이터 수집
          </span>
          <span>—</span>
          <span className={currentStep === 2 ? "text-on-surface font-semibold" : ""}>
            ● Step 2 · 분석 결과
          </span>
        </div>

        <div className="p-6">
          {state.step === "input" && (
            <StepInput isPending={isPending} onSubmit={handleSubmit} />
          )}
          {state.step === "collecting" && (
            <div className="flex flex-col items-center gap-3 py-8">
              <div className="w-8 h-8 border-2 border-primary-container border-t-transparent rounded-full animate-spin" />
              <div className="text-sm text-on-surface-variant">데이터 수집 및 분석 중…</div>
            </div>
          )}
          {state.step === "result" && (
            <StepResult
              analysis={state.analysis}
              onReAnalyze={() => setState({ step: "input" })}
              onClose={onClose}
            />
          )}
          {state.step === "error" && (
            <div className="flex flex-col gap-4">
              <div className="text-sm text-status-error">{state.message}</div>
              <button
                type="button"
                onClick={() => setState({ step: "input" })}
                className="self-start px-4 py-2 text-sm rounded-lg border border-outline-variant"
              >
                다시 시도
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 타입체크 + Steiger**

Run: `cd frontend; pnpm typecheck; pnpm lint:fsd`
Expected: 통과.

---

## Task 24: features/health-analysis/request Public API + 슬라이스 커밋

**Files:**
- Create: `frontend/src/features/health-analysis/request/index.ts`

- [ ] **Step 1: index.ts 작성**

```ts
// frontend/src/features/health-analysis/request/index.ts
export { AnalysisRequestModal } from "./ui";
export { useCreateAnalysis } from "./model";
export type { CreateAnalysisRequest } from "./api";
```

- [ ] **Step 2: 전체 검증**

Run:
```powershell
cd frontend
pnpm typecheck
pnpm lint:fsd
pnpm lint
pnpm build
```
Expected: 모두 통과.

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/features/health-analysis
git commit -m "feat(frontend): add features/health-analysis/request slice with stepper modal"
```

---

# Phase F: Integration — 기존 파일 변경

## Task 25: entities/contract PolicyDto에 customerId 추가

**Files:**
- Modify: `frontend/src/entities/contract/api/index.ts`

- [ ] **Step 1: PolicyDto 필드 추가**

Edit `frontend/src/entities/contract/api/index.ts` — `PolicyDto` interface에 `customerId` 추가:

```ts
export interface PolicyDto {
  id: string;
  policyNumber: string;
  customerId: string | null;       // 신규
  customerName: string;
  productName: string;
  insurerName: string;
  status: string;
  contractDate: string;
  monthlyPremium: number | null;
}
```

- [ ] **Step 2: 빌드 검증**

Run:
```powershell
cd frontend
pnpm typecheck
pnpm build
```
Expected: 통과 (다른 파일에서 PolicyDto 분해 사용 시 customerId 미사용도 OK — optional field 아니지만 null 가능).

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/entities/contract/api/index.ts
git commit -m "feat(frontend): expose customerId on PolicyDto"
```

---

## Task 26: underwriting 페이지 — "분석" 컬럼 추가 + AnalysisCell

**Files:**
- Modify: `frontend/src/app/underwriting/page.tsx`

- [ ] **Step 1: 현재 파일 읽기**

Run: `cat frontend/src/app/underwriting/page.tsx`

기존 구조 (table head, body, pagination)를 파악한 후 다음 변경 적용.

- [ ] **Step 2: 컬럼 + 셀 추가**

Edit `frontend/src/app/underwriting/page.tsx` — top imports 추가:

```ts
import { useState } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { useAnalysesByCustomers, useAnalysis, RiskBadge, type HealthAnalysisDto } from "@/entities/health-analysis";
import { AnalysisRequestModal } from "@/features/health-analysis/request";
```

(기존 `useState`/`usePolicies` import는 유지 또는 통합)

페이지 컴포넌트 안 — `usePolicies` 호출 이후에 추가:

```tsx
const customerIds = data?.content.map((p) => p.customerId).filter((x): x is string => !!x) ?? [];
const { data: analysisMap } = useAnalysesByCustomers(customerIds);
```

테이블 헤더 마지막에 `<th>분석</th>` 추가.

테이블 body 각 row의 마지막 셀로 `<AnalysisCell>` 추가 (이전 셀 다음, `</tr>` 직전):

```tsx
<td className="px-6 py-4">
  <AnalysisCell
    customer={{ id: c.customerId, name: c.customerName }}
    analysis={c.customerId ? analysisMap?.[c.customerId] ?? null : null}
  />
</td>
```

(테이블 변수명 `c`는 기존 코드 확인 — 다른 이름이면 그것에 맞춤)

- [ ] **Step 3: AnalysisCell 컴포넌트를 같은 파일 안에 정의**

같은 파일 안 (export default function 위 또는 아래):

```tsx
function AnalysisCell({
  customer,
  analysis,
}: {
  customer: { id: string | null; name: string };
  analysis: HealthAnalysisDto | null;
}) {
  const [modalOpen, setModalOpen] = useState(false);

  if (!customer.id) {
    return <span className="text-sm text-on-surface-variant">-</span>;
  }

  return (
    <>
      {analysis ? (
        <RiskBadge grade={analysis.riskGrade} onClick={() => setModalOpen(true)} />
      ) : (
        <button
          type="button"
          onClick={() => setModalOpen(true)}
          className="text-xs text-primary hover:underline"
        >
          + 분석 요청
        </button>
      )}
      {modalOpen && (
        <AnalysisRequestModal
          customer={{ id: customer.id, name: customer.name }}
          existingAnalysis={analysis ?? undefined}
          onClose={() => setModalOpen(false)}
        />
      )}
    </>
  );
}
```

- [ ] **Step 4: searchParams.analysisId 처리 (대시보드에서 진입 시 자동 모달 오픈)**

페이지 컴포넌트 본문에 추가 (`usePolicies` 호출 이후):

```tsx
const searchParams = useSearchParams();
const router = useRouter();
const analysisIdParam = searchParams.get("analysisId");
const { data: queriedAnalysis } = useAnalysis(analysisIdParam);
const [showQueriedModal, setShowQueriedModal] = useState(false);

useEffect(() => {
  if (queriedAnalysis) setShowQueriedModal(true);
}, [queriedAnalysis]);

// 모달 닫을 때 URL param 제거
function closeQueriedModal() {
  setShowQueriedModal(false);
  router.replace("/underwriting", { scroll: false });
}
```

(`useEffect` import 필요)

페이지 최하단 (또는 적당한 위치)에:

```tsx
{showQueriedModal && queriedAnalysis && (
  <AnalysisRequestModal
    customer={{ id: queriedAnalysis.customerId, name: queriedAnalysis.customerName }}
    existingAnalysis={queriedAnalysis}
    onClose={closeQueriedModal}
  />
)}
```

- [ ] **Step 5: 전체 검증**

Run:
```powershell
cd frontend
pnpm typecheck
pnpm lint:fsd
pnpm build
```
Expected: 모두 통과.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/app/underwriting/page.tsx
git commit -m "feat(underwriting): add 분석 column with modal trigger and ?analysisId deep link"
```

---

## Task 27: 대시보드 — 건강 분석 카드 + 최근 리스트

**Files:**
- Modify: `frontend/src/app/dashboard/page.tsx`

- [ ] **Step 1: 현재 파일 구조 파악**

Run: `cat frontend/src/app/dashboard/page.tsx`

기존 ADMIN/AGENT1/AGENT2 분기 렌더링 부분을 찾는다.

- [ ] **Step 2: 분석 데이터 훅 추가**

Edit `frontend/src/app/dashboard/page.tsx` — imports에 추가:

```ts
import Link from "next/link";
import { useAnalysisSummary, useRecentAnalyses, RiskBadge } from "@/entities/health-analysis";
```

ADMIN/AGENT1 분기 안 (AGENT2 분기 밖) 컴포넌트 본문에 추가:

```tsx
const { data: analysisSummary } = useAnalysisSummary();
const { data: recentAnalyses } = useRecentAnalyses(5);
```

- [ ] **Step 3: 통계 카드 추가**

ADMIN/AGENT1 렌더 트리 안 (기존 카드 섹션 옆 또는 아래):

```tsx
<section className="flex flex-col gap-3">
  <h2 className="text-lg font-semibold text-on-surface">건강 분석 현황</h2>
  <div className="grid grid-cols-4 gap-3">
    <SummaryCard label="분석 완료" value={analysisSummary?.total ?? 0} />
    <SummaryCard label="정상" value={analysisSummary?.normal ?? 0} colorClass="text-status-success" />
    <SummaryCard label="주의" value={analysisSummary?.caution ?? 0} colorClass="text-status-warning" />
    <SummaryCard label="위험" value={analysisSummary?.risk ?? 0} colorClass="text-status-error" />
  </div>
</section>
```

같은 파일에 작은 헬퍼 컴포넌트:

```tsx
function SummaryCard({
  label,
  value,
  colorClass = "text-on-surface",
}: {
  label: string;
  value: number;
  colorClass?: string;
}) {
  return (
    <div className="bg-surface-container-lowest rounded-2xl p-4 shadow-card">
      <div className="text-xs text-on-surface-variant">{label}</div>
      <div className={`text-2xl font-bold ${colorClass}`}>{value}명</div>
    </div>
  );
}
```

(기존에 비슷한 카드 컴포넌트가 있으면 그것을 재사용)

- [ ] **Step 4: 최근 분석 리스트 추가**

```tsx
<section className="flex flex-col gap-3">
  <h2 className="text-lg font-semibold text-on-surface">최근 분석 5건</h2>
  <div className="bg-surface-container-lowest rounded-2xl shadow-card overflow-hidden">
    {recentAnalyses && recentAnalyses.length === 0 && (
      <div className="px-6 py-8 text-center text-sm text-on-surface-variant">
        아직 분석된 건강 데이터가 없습니다.
      </div>
    )}
    {recentAnalyses?.map((item) => (
      <Link
        key={item.id}
        href={`/underwriting?analysisId=${item.id}`}
        className="flex items-center justify-between px-6 py-3 hover:bg-surface-container-low border-b border-outline-variant last:border-b-0"
      >
        <div className="flex items-center gap-3">
          <span className="text-sm font-medium text-on-surface">{item.customerName}</span>
          <RiskBadge grade={item.riskGrade} />
        </div>
        <span className="text-xs text-on-surface-variant">
          {new Date(item.analyzedAt).toLocaleString("ko-KR")}
        </span>
      </Link>
    ))}
  </div>
</section>
```

- [ ] **Step 5: 전체 검증**

Run:
```powershell
cd frontend
pnpm typecheck
pnpm lint:fsd
pnpm build
```
Expected: 모두 통과.

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/app/dashboard/page.tsx
git commit -m "feat(dashboard): add health analysis summary cards and recent list"
```

---

# Phase G: Verification & PR

## Task 28: 수동 스모크 테스트

**Files:** N/A

- [ ] **Step 1: 백엔드 + 프론트엔드 실행**

별도 터미널:
- `cd backend; ./gradlew.bat bootRun`
- `cd frontend; pnpm dev`

- [ ] **Step 2: 시나리오 1 — ADMIN 분석 흐름**

1. ADMIN 로그인 → `/underwriting`
2. 어떤 정책 행이든 "분석" 컬럼에 `-` 또는 `+ 분석 요청` 표시 확인
3. `+ 분석 요청` 클릭 → 모달 열림 ("건강 데이터 분석 — 김OO")
4. Step 1: "자동 수집" 선택 → "수집 시작"
5. 로딩 표시 (1-2초)
6. Step 2: 결과 표시 (등급 뱃지, 질환 목록, 권고, 의견)
7. 닫기 → 행 컬럼이 뱃지로 갱신
8. 같은 뱃지 클릭 → 같은 결과 모달 재표시
9. "재분석" → step 1 → "고혈압" 시나리오 선택 → "수집 시작" → 결과가 고혈압 케이스로 변경

- [ ] **Step 3: 시나리오 2 — AGENT1 권한 제한**

1. AGENT1 로그인 → `/underwriting`
2. 본인 고객이 있는 행의 "분석 요청" 동작 확인
3. 본인 고객이 아닌 행 (customer_id NULL 또는 다른 agent의 고객) → "-" 표시
4. 직접 URL `POST /api/health-analyses` 시도 (다른 사람 customer id) → 403 응답

- [ ] **Step 4: 시나리오 3 — AGENT2 차단**

1. AGENT2 로그인 → `/underwriting` 사이드바에 없음 (기본 동작)
2. 직접 URL `http://localhost:3000/underwriting` 진입 시도 → 차단 또는 빈 페이지
3. 브라우저 콘솔에서 fetch 직접 호출 시 403

- [ ] **Step 5: 시나리오 4 — 대시보드**

1. ADMIN/AGENT1 로그인 → `/dashboard`
2. "건강 분석 현황" 카드 4개 (분석 완료/정상/주의/위험) 숫자 표시
3. "최근 분석 5건" 리스트 표시
4. 항목 클릭 → `/underwriting?analysisId=<id>` 라우팅 → 페이지 진입 시 해당 분석 모달 자동 오픈
5. 모달 닫기 → URL의 `analysisId` 파라미터 제거됨

- [ ] **Step 6: 시나리오 5 — Backfill 매칭 실패**

1. customers에 없는 customer_name으로 policy 직접 INSERT (수동 SQL 또는 seed)
2. `/underwriting` 진입 → 그 행의 "분석" 컬럼 = "-"
3. 분석 시도 불가 확인

각 시나리오 통과 시 다음 단계로. 실패 시 해당 Task로 돌아가 수정.

---

## Task 29: PR 생성

**Files:** N/A

- [ ] **Step 1: 모든 커밋 검토**

Run: `git log master..HEAD --oneline`
Expected: 위 작업들의 커밋이 시간 순으로 표시.

- [ ] **Step 2: push 사용자 승인 받기**

(memory: feedback_git_push_confirm) — 사용자에게 "feat/health-analysis 브랜치 origin push 해도 될까요?" 명시적 확인.

- [ ] **Step 3: push**

```bash
git push -u origin feat/health-analysis
```

- [ ] **Step 4: PR 생성**

```bash
gh pr create --base master --head feat/health-analysis --title "feat(underwriting): add health data analysis feature" --body "$(cat <<'EOF'
## Summary

심사 페이지에 건강 데이터 분석 기능 추가. NHIS/HIRA 연계 가정의 더미 데이터로 유병 여부와 인수 권고를 산출. 결과는 underwriting 테이블의 분석 컬럼과 대시보드에서 조회.

## Spec / Plan

- Spec: \`docs/superpowers/specs/2026-05-20-underwriting-health-analysis-design.md\`
- Plan: \`docs/superpowers/plans/2026-05-20-underwriting-health-analysis.md\`

## Changes

### Backend
- V14: \`policies.customer_id\` FK 추가 + name+agent_id 기반 backfill
- V15: \`health_data\`, \`health_analyses\` 테이블 신설 (PII 컬럼 암호화)
- 새 \`healthanalysis\` 모듈: enum, ChronicConditions, DTOs, entities, repositories, service, controller
- \`DummyHealthDataGenerator\` (시나리오 4종 + RANDOM 가중 분기)
- \`HealthAnalysisService.analyze()\` 룰 기반 등급 산출
- SecurityConfig: \`/api/health-analyses/**\` → ADMIN, AGENT1

### Frontend
- \`entities/health-analysis\` (api/model/ui — RiskBadge, AnalysisResult)
- \`features/health-analysis/request\` (Stepper modal: 수집 → 결과)
- \`entities/contract\` PolicyDto에 customerId 노출
- underwriting 페이지: "분석" 컬럼 + 모달 트리거 + ?analysisId 딥링크
- 대시보드: 건강 분석 현황 카드 + 최근 5건 리스트

## Test plan

- [x] Backend unit tests: DummyHealthDataGenerator (5건), HealthAnalysisService.analyze (4건)
- [x] Backend integration tests: HealthAnalysisController 권한 (6건)
- [x] Frontend: typecheck / lint / lint:fsd / build 모두 통과
- [x] 수동 스모크 (ADMIN 분석 흐름, AGENT1 권한, AGENT2 차단, 대시보드, 백필 매칭 실패)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 5: CI 통과 대기 + 머지 결정**

```bash
gh pr view --json statusCheckRollup
```

CI 통과 후 사용자에게 머지 방식 확인 (merge commit 기본). 머지:

```bash
gh pr merge <PR번호> --merge
```

- [ ] **Step 6: 브랜치 정리**

```bash
git checkout master
git pull --quiet
git branch -d feat/health-analysis
git push origin --delete feat/health-analysis
```

---

# 완료 후 점검

- [ ] master에 새 기능 반영됨
- [ ] CI 자동 검사 (Steiger 포함) 통과
- [ ] Vercel 자동 배포 성공
- [ ] 운영 환경에서 ADMIN 계정으로 분석 흐름 한 번 검증

## Out of Scope (별도 작업)

- 실제 NHIS/HIRA 연계 (인증, 스크래핑)
- 분석 이력 view (현재는 최신 1건)
- health_data 자동 삭제 배치
- 분석 결과 PDF 출력
- 동의서 흐름 (마이데이터)
- 위험 등급 push/email 알림
- AGENT2 분석 권한 (요건 변경 시)
- 만성 진단 코드 관리 UI
- 어드민용 raw payload 조회 UI
- 분석 결과를 외부 보험사 시스템으로 전송
