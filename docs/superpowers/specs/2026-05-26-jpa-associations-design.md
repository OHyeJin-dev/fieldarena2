# JPA Associations on Existing DB FKs

- **Date**: 2026-05-26
- **Author**: <hj8279@coocon.net>
- **Status**: Draft

## Background

`fieldarena2` 백엔드는 `customers` ↔ `claims/policies/proposals/health_data/health_analyses` 사이의 외래키를 이미 DB 레벨에서 보유한다 (Flyway V12/V14/V15/V16). 그러나 JPA 엔티티는 모두 `private UUID customerId` 형태로만 FK 값을 들고 있어 association 이 명시적이지 않다.

이번 작업은 이 6 개 child→Customer 관계를 단방향 `@ManyToOne(LAZY)` JPA association 으로 끌어올린다. DB 스키마 변경은 없다.

## Goals

- `Claim`, `Policy`, `Proposal`, `HealthData`, `HealthAnalysis` 가 `Customer` 를 `@ManyToOne(LAZY) @JoinColumn(name = "customer_id")` 로 가리키도록 한다.
- `HealthAnalysis` 가 `HealthData` 도 `@ManyToOne(LAZY) @JoinColumn(name = "health_data_id")` 로 가리키도록 한다.
- 기존 `UUID customerId` / `UUID healthDataId` 필드는 제거하되 `getCustomerId()` / `getHealthDataId()` 는 association 을 위임하는 delegate 로 남겨 호출처(DTO/서비스/테스트) 영향 최소화.
- 모든 기존 테스트가 그린 상태로 통과한다.
- DTO 시그니처와 클라이언트 응답 형태는 그대로 유지한다.

## Non-Goals

- DB 스키마 변경 (Flyway V17 등). 기존 V12/V14/V16 의 FK 컬럼·인덱스를 그대로 사용한다.
- `agent_id → users.id` 관계 추가. DB 에 FK 가 없고 이번 스코프 밖이다.
- `claims.policy_number → policies.policy_number` 관계 추가. 동일 이유.
- Bidirectional 관계 (`Customer.claims` 등의 `@OneToMany` 추가). YAGNI — 현재 사용처 없음.
- `CascadeType` JPA cascade. V15 의 DB `ON DELETE CASCADE` 가 이미 health_data/health_analyses 삭제 전파를 처리한다.
- DTO 시그니처 변경 (e.g. `PolicyDto.customerId UUID`). 클라이언트 호환 유지.
- 엔티티 직접 `@RestController` 반환 허용. 현재 코드가 일관되게 DTO 변환을 통과하며, 이를 유지한다.

## Approach

### Branch / PR

- Base: `master`
- 단일 PR `feat/jpa-associations`. 5 개 엔티티 변경 + 서비스 호출처 정리 + 테스트 보강을 한 PR 안에 묶는다.
- 변경 자체는 엔티티별로 commit 분리 가능하지만, `Claim.create()` 시그니처가 바뀌면 같은 commit 안에서 `ClaimService` 도 함께 고쳐야 컴파일 그린이 유지됨 — 엔티티+서비스+테스트를 함께 묶는 단위 commit.

### Pattern (모든 자식 엔티티 공통)

**As-is** (`Claim.java` 예시):

```java
@Column(name = "customer_id")
private UUID customerId;

public static Claim create(String agentId, UUID customerId, ...) {
  Claim c = new Claim();
  c.customerId = customerId;
  ...
}

public UUID getCustomerId() { return customerId; }
```

**To-be**:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "customer_id")
private Customer customer;

public static Claim create(String agentId, Customer customer, ...) {
  Claim c = new Claim();
  c.customer = customer;
  ...
}

public UUID getCustomerId() {
  return customer != null ? customer.getId() : null;
}

public Customer getCustomer() { return customer; }
```

`getCustomerId()` delegate 가 핵심이다 — 호출처(DTO, 서비스, 테스트) 가 `claim.getCustomerId()` 을 계속 호출해도 동작이 동일하다. Hibernate 의 lazy proxy 가 `getId()` 호출 시 DB 조회 없이 ID 만 반환한다.

### 적용 매트릭스

| 엔티티 | 추가할 association | 제거할 필드 | 유지할 delegate |
|---|---|---|---|
| `Claim` | `@ManyToOne(LAZY) Customer customer` | `UUID customerId` | `getCustomerId()` |
| `Policy` | `@ManyToOne(LAZY) Customer customer` | `UUID customerId` | `getCustomerId()` |
| `Proposal` | `@ManyToOne(LAZY) Customer customer` | `UUID customerId` | `getCustomerId()` |
| `HealthData` | `@ManyToOne(LAZY) Customer customer` | `UUID customerId` | `getCustomerId()` |
| `HealthAnalysis` | `@ManyToOne(LAZY) Customer customer` + `@ManyToOne(LAZY) HealthData healthData` | `UUID customerId`, `UUID healthDataId` | `getCustomerId()`, `getHealthDataId()` |

### Factory / 서비스 시그니처 변경

엔티티 정적 팩토리(`create()`) 가 `UUID customerId` 대신 `Customer customer` 를 받는다. 서비스는 이미 `customerRepository.findById(...)` 로 Customer 인스턴스를 확보한 상태이므로 인수 한 줄만 바꾸면 된다.

**ClaimService (예시)**:

```java
// AS-IS
Customer customer = customerRepository
    .findByIdAndAgentId(req.customerId(), agentId)
    .orElseThrow(...);
Claim claim = Claim.create(agentId, customer.getId(), policyNumber, ...);
return claimRepository.save(claim);

// TO-BE
Customer customer = customerRepository
    .findByIdAndAgentId(req.customerId(), agentId)
    .orElseThrow(...);
Claim claim = Claim.create(agentId, customer, policyNumber, ...);
return claimRepository.save(claim);
```

`PolicyService`, `ProposalService`, `HealthAnalysisService` 도 같은 패턴.

`HealthAnalysisService` 는 추가로 `HealthData` 인스턴스도 `create()` 에 직접 전달하도록 변경 (현재는 `healthData.getId()` UUID 전달).

### `HealthAnalysis → HealthData` 는 왜 `@ManyToOne` 인가

DB 제약은 `health_analyses.customer_id UNIQUE` + `health_data_id` (UNIQUE 아님) — 의미상 1:N. 도메인 의미상 한 분석은 하나의 health_data 스냅샷만 참조. `@OneToOne` 도 가능하나 Hibernate `@OneToOne(fetch = LAZY)` 는 byte-code enhancement 없이 항상 EAGER 동작. `@ManyToOne(LAZY)` 가 안전한 default. 도메인 동작에 차이 없음.

## Data Flow (변경 없음)

- HTTP 요청 → Controller → Service.
- Service 는 여전히 `req.customerId()` (UUID) 로 Customer 조회.
- Customer 객체를 엔티티 factory 에 전달.
- 엔티티는 association 으로 customer 보관.
- 조회 시 `repository.findById()` → 엔티티 반환 → DTO 변환에서 `getCustomerId()` delegate 호출 → 클라이언트는 UUID 만 받음.

→ 클라이언트 응답 JSON, request payload 모두 변경 없음.

## Testing Strategy

| 영역 | 기대 |
|---|---|
| 기존 ClaimControllerTest / PolicyControllerTest / ProposalControllerTest / HealthAnalysisServiceTest | 회귀 없이 그대로 통과 — delegate getter 가 같은 UUID 반환 |
| 신규 association 매핑 검증 | 각 엔티티당 단위 테스트 1건: `claimRepository.findById(id)` 후 `claim.getCustomer().getName() == 등록한 고객 이름` 확인. lazy load 정상 + 매핑 정상. |
| ddl-auto: validate | 부팅 성공 확인 — association 컬럼이 기존 customer_id/health_data_id 와 정확히 일치해야 validate 통과 |

## Risks

1. **N+1 위험**: 향후 리스트 페이지에서 `claim.getCustomer().getName()` 같은 호출을 N 회 반복하면 N+1. 현재 코드는 denormalized `customer_name` 컬럼을 사용해 이 경로를 회피한다. spec 메모: *list 조회에서 association 을 통한 customer.getXxx() 접근 금지, 필요 시 fetch join 또는 denormalized 컬럼 사용*.
2. **LazyInitializationException**: 누군가 엔티티를 `@RestController` 에서 직접 반환하면 Jackson 이 lazy proxy 를 만나 transaction 밖에서 폭발. 현재 코드는 일관되게 DTO 변환을 거치므로 안전. spec 메모: *엔티티 직접 반환 금지, DTO 변환 필수*.
3. **delegate getter null 처리**: `customer != null ? customer.getId() : null` 패턴 일관 적용. customer_id NOT NULL 인 테이블 (HealthData, HealthAnalysis) 도 동일하게 안전.
4. **테스트 픽스처 깨짐 위험**: 테스트가 `Claim.create()` 를 직접 호출하는 경우 시그니처 변경에 영향. 검색 결과 서비스만 호출하며, 컨트롤러 테스트는 HTTP 요청 형태라 안전. 단위 테스트는 service mock 패턴이라 영향 없음.
5. **Hibernate proxy 의 equals/hashCode**: lazy proxy 와 실 엔티티 비교 시 주의. 현재 코드에 엔티티 equals 호출처 없음 — 안전.

## Open Questions

없음 (브레인스토밍에서 모두 해소).

## References

- 기존 FK 마이그레이션:
  - [V11__create_customers.sql](../../../backend/src/main/resources/db/migration/V11__create_customers.sql)
  - [V12__add_customer_id_to_claims.sql](../../../backend/src/main/resources/db/migration/V12__add_customer_id_to_claims.sql)
  - [V14__add_customer_id_to_policies.sql](../../../backend/src/main/resources/db/migration/V14__add_customer_id_to_policies.sql)
  - [V15__create_health_data_and_analyses.sql](../../../backend/src/main/resources/db/migration/V15__create_health_data_and_analyses.sql)
  - [V16__add_customer_id_to_proposals.sql](../../../backend/src/main/resources/db/migration/V16__add_customer_id_to_proposals.sql)
- 영향 받는 서비스/엔티티:
  - [Claim.java](../../../backend/src/main/java/com/agentsupport/claim/entity/Claim.java)
  - [Policy.java](../../../backend/src/main/java/com/agentsupport/policy/entity/Policy.java)
  - [Proposal.java](../../../backend/src/main/java/com/agentsupport/proposal/entity/Proposal.java)
  - [HealthData.java](../../../backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthData.java)
  - [HealthAnalysis.java](../../../backend/src/main/java/com/agentsupport/healthanalysis/entity/HealthAnalysis.java)