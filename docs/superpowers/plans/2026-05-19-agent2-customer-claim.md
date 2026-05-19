# AGENT2 — 고객 관리 & 청구 등록/관리 구현 플랜

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AGENT2 역할이 본인 고객을 관리하고 그 고객에 대한 청구를 등록·조회하도록 고객 관리 모듈과 청구 등록 기능을 추가하고, 설계/심사 메뉴/API는 AGENT2에게 차단한다.

**Architecture:** Spring Boot 백엔드에 `customer` 모듈을 새로 추가하고 `claim` 모듈에 등록 API를 추가한다. PII 컬럼은 기존 `PiiAttributeConverter` 패턴을 그대로 사용한다. 프런트(Next.js)는 사이드바를 역할 기반으로 갱신하고, `/customers` 페이지를 신설하며, `/claims`에 청구 등록 모달을 추가하고, 대시보드는 `useMe()` 역할에 따라 분기 렌더링한다.

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, Flyway, Spring Security, JUnit 5 + MockMvc, Next.js 15 (App Router), React Query, react-hook-form + zod, Tailwind v4.

---

## 파일 구조

**생성**
- `backend/src/main/resources/db/migration/V11__create_customers.sql`
- `backend/src/main/resources/db/migration/V12__add_customer_id_to_claims.sql`
- `backend/src/main/java/com/agentsupport/customer/entity/Customer.java`
- `backend/src/main/java/com/agentsupport/customer/dto/CustomerDto.java`
- `backend/src/main/java/com/agentsupport/customer/dto/CustomerCreateRequest.java`
- `backend/src/main/java/com/agentsupport/customer/dto/CustomerUpdateRequest.java`
- `backend/src/main/java/com/agentsupport/customer/repository/CustomerRepository.java`
- `backend/src/main/java/com/agentsupport/customer/service/CustomerService.java`
- `backend/src/main/java/com/agentsupport/customer/CustomerController.java`
- `backend/src/main/java/com/agentsupport/claim/dto/ClaimCreateRequest.java`
- `backend/src/test/java/com/agentsupport/customer/CustomerControllerTest.java`
- `backend/src/test/java/com/agentsupport/claim/ClaimControllerTest.java`
- `backend/src/test/java/com/agentsupport/security/RoleAccessTest.java`
- `frontend/src/features/customers/api.ts`
- `frontend/src/features/customers/queries.ts`
- `frontend/src/features/customers/CustomerFormModal.tsx`
- `frontend/src/features/claims/ClaimFormModal.tsx`
- `frontend/src/app/customers/page.tsx`
- `frontend/src/app/customers/layout.tsx`

**수정**
- `backend/src/main/java/com/agentsupport/claim/entity/Claim.java`
- `backend/src/main/java/com/agentsupport/claim/dto/ClaimDto.java`
- `backend/src/main/java/com/agentsupport/claim/service/ClaimService.java`
- `backend/src/main/java/com/agentsupport/claim/ClaimController.java`
- `backend/src/main/java/com/agentsupport/claim/repository/ClaimRepository.java`
- `backend/src/main/java/com/agentsupport/config/SecurityConfig.java`
- `backend/src/main/java/com/agentsupport/dashboard/dto/DashboardSummaryDto.java`
- `backend/src/main/java/com/agentsupport/dashboard/service/DashboardService.java`
- `frontend/src/components/layout/sidebar.tsx`
- `frontend/src/app/dashboard/page.tsx`
- `frontend/src/app/claims/page.tsx`
- `frontend/src/features/claims/api.ts`
- `frontend/src/features/claims/queries.ts`
- `frontend/src/features/dashboard/api.ts`

---

## Task 1: V11 customers 테이블 마이그레이션

**Files:**
- Create: `backend/src/main/resources/db/migration/V11__create_customers.sql`

- [ ] **Step 1: 마이그레이션 SQL 작성**

```sql
CREATE TABLE customers (
  id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
  agent_id    VARCHAR(50)   NOT NULL,
  name        VARCHAR(500)  NOT NULL,
  phone       VARCHAR(500)  NOT NULL,
  birth_date  DATE,
  gender      VARCHAR(10),
  email       VARCHAR(500),
  address     VARCHAR(1000),
  memo        TEXT,
  created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_customers_agent_id ON customers (agent_id);
```

- [ ] **Step 2: 백엔드 부트 확인**

Run: `cd backend && ./gradlew bootRun` (다른 터미널) 또는 `./gradlew test --tests BackendApplicationTests`
Expected: Flyway가 V11을 적용하고 컨텍스트가 정상 기동 (`Migrating schema "public" to version "11 - create customers"` 로그)

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/resources/db/migration/V11__create_customers.sql
git commit -m "feat: add customers table migration (V11)"
```

---

## Task 2: V12 claims.customer_id 컬럼 추가

**Files:**
- Create: `backend/src/main/resources/db/migration/V12__add_customer_id_to_claims.sql`

- [ ] **Step 1: 마이그레이션 SQL 작성**

```sql
ALTER TABLE claims ADD COLUMN customer_id UUID;
ALTER TABLE claims ADD CONSTRAINT fk_claims_customer
  FOREIGN KEY (customer_id) REFERENCES customers(id);
CREATE INDEX idx_claims_customer_id ON claims (customer_id);
```

- [ ] **Step 2: 부트 확인**

Run: `cd backend && ./gradlew test --tests BackendApplicationTests`
Expected: V12 적용 후 컨텍스트 기동 성공

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/resources/db/migration/V12__add_customer_id_to_claims.sql
git commit -m "feat: add customer_id FK to claims (V12)"
```

---

## Task 3: Customer 엔티티 작성

**Files:**
- Create: `backend/src/main/java/com/agentsupport/customer/entity/Customer.java`

- [ ] **Step 1: 엔티티 작성**

```java
package com.agentsupport.customer.entity;

import com.agentsupport.security.PiiAttributeConverter;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "customers")
public class Customer {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "agent_id", nullable = false, length = 50)
  private String agentId;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String name;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String phone;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(length = 10)
  private String gender;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(length = 500)
  private String email;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(length = 1000)
  private String address;

  @Column(columnDefinition = "TEXT")
  private String memo;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  protected Customer() {}

  public static Customer create(
      String agentId, String name, String phone,
      LocalDate birthDate, String gender, String email, String address, String memo) {
    Customer c = new Customer();
    c.agentId = agentId;
    c.name = name;
    c.phone = phone;
    c.birthDate = birthDate;
    c.gender = gender;
    c.email = email;
    c.address = address;
    c.memo = memo;
    return c;
  }

  public void update(
      String name, String phone, LocalDate birthDate,
      String gender, String email, String address, String memo) {
    this.name = name;
    this.phone = phone;
    this.birthDate = birthDate;
    this.gender = gender;
    this.email = email;
    this.address = address;
    this.memo = memo;
  }

  public UUID getId() { return id; }
  public String getAgentId() { return agentId; }
  public String getName() { return name; }
  public String getPhone() { return phone; }
  public LocalDate getBirthDate() { return birthDate; }
  public String getGender() { return gender; }
  public String getEmail() { return email; }
  public String getAddress() { return address; }
  public String getMemo() { return memo; }
  public LocalDateTime getCreatedAt() { return createdAt; }
  public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/customer/entity/Customer.java
git commit -m "feat: add Customer entity"
```

---

## Task 4: CustomerRepository 작성

**Files:**
- Create: `backend/src/main/java/com/agentsupport/customer/repository/CustomerRepository.java`

- [ ] **Step 1: 리포지토리 작성**

```java
package com.agentsupport.customer.repository;

import com.agentsupport.customer.entity.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

  Page<Customer> findByAgentIdOrderByCreatedAtDesc(String agentId, Pageable pageable);

  Page<Customer> findAllByOrderByCreatedAtDesc(Pageable pageable);

  Optional<Customer> findByIdAndAgentId(UUID id, String agentId);

  long countByAgentId(String agentId);
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/customer/repository/CustomerRepository.java
git commit -m "feat: add CustomerRepository"
```

---

## Task 5: Customer DTO 3종 작성

**Files:**
- Create: `backend/src/main/java/com/agentsupport/customer/dto/CustomerDto.java`
- Create: `backend/src/main/java/com/agentsupport/customer/dto/CustomerCreateRequest.java`
- Create: `backend/src/main/java/com/agentsupport/customer/dto/CustomerUpdateRequest.java`

- [ ] **Step 1: CustomerDto 작성**

```java
package com.agentsupport.customer.dto;

import com.agentsupport.customer.entity.Customer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerDto(
    UUID id,
    String name,
    String phone,
    LocalDate birthDate,
    String gender,
    String email,
    String address,
    String memo,
    LocalDateTime createdAt) {

  public static CustomerDto from(Customer c) {
    return new CustomerDto(
        c.getId(), c.getName(), c.getPhone(),
        c.getBirthDate(), c.getGender(), c.getEmail(),
        c.getAddress(), c.getMemo(), c.getCreatedAt());
  }
}
```

- [ ] **Step 2: CustomerCreateRequest 작성**

```java
package com.agentsupport.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CustomerCreateRequest(
    @NotBlank @Size(max = 50) String name,
    @NotBlank @Size(max = 20) String phone,
    LocalDate birthDate,
    @Size(max = 10) String gender,
    @Size(max = 100) String email,
    @Size(max = 200) String address,
    @Size(max = 2000) String memo) {}
```

- [ ] **Step 3: CustomerUpdateRequest 작성**

```java
package com.agentsupport.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CustomerUpdateRequest(
    @NotBlank @Size(max = 50) String name,
    @NotBlank @Size(max = 20) String phone,
    LocalDate birthDate,
    @Size(max = 10) String gender,
    @Size(max = 100) String email,
    @Size(max = 200) String address,
    @Size(max = 2000) String memo) {}
```

- [ ] **Step 4: 컴파일 확인 + 커밋**

```bash
cd backend && ./gradlew compileJava
git add backend/src/main/java/com/agentsupport/customer/dto/
git commit -m "feat: add Customer DTOs"
```

---

## Task 6: CustomerService 작성

**Files:**
- Create: `backend/src/main/java/com/agentsupport/customer/service/CustomerService.java`

- [ ] **Step 1: 서비스 작성**

```java
package com.agentsupport.customer.service;

import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.customer.dto.CustomerCreateRequest;
import com.agentsupport.customer.dto.CustomerDto;
import com.agentsupport.customer.dto.CustomerUpdateRequest;
import com.agentsupport.customer.entity.Customer;
import com.agentsupport.customer.repository.CustomerRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CustomerService {

  private final CustomerRepository customerRepository;

  public CustomerService(CustomerRepository customerRepository) {
    this.customerRepository = customerRepository;
  }

  public PageResponse<CustomerDto> findCustomers(String agentId, boolean isAdmin, int page, int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    Page<Customer> result = isAdmin
        ? customerRepository.findAllByOrderByCreatedAtDesc(pageable)
        : customerRepository.findByAgentIdOrderByCreatedAtDesc(agentId, pageable);
    return PageResponse.from(result.map(CustomerDto::from));
  }

  public CustomerDto findOne(UUID id, String agentId, boolean isAdmin) {
    Customer c = loadOwned(id, agentId, isAdmin);
    return CustomerDto.from(c);
  }

  @Transactional
  public CustomerDto create(String agentId, CustomerCreateRequest req) {
    Customer c = Customer.create(
        agentId, req.name(), req.phone(),
        req.birthDate(), req.gender(), req.email(), req.address(), req.memo());
    return CustomerDto.from(customerRepository.save(c));
  }

  @Transactional
  public CustomerDto update(UUID id, String agentId, boolean isAdmin, CustomerUpdateRequest req) {
    Customer c = loadOwned(id, agentId, isAdmin);
    c.update(req.name(), req.phone(), req.birthDate(),
        req.gender(), req.email(), req.address(), req.memo());
    return CustomerDto.from(c);
  }

  @Transactional
  public void delete(UUID id, String agentId, boolean isAdmin) {
    Customer c = loadOwned(id, agentId, isAdmin);
    customerRepository.delete(c);
  }

  private Customer loadOwned(UUID id, String agentId, boolean isAdmin) {
    if (isAdmin) {
      return customerRepository.findById(id)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "고객을 찾을 수 없습니다"));
    }
    return customerRepository.findByIdAndAgentId(id, agentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "권한이 없습니다"));
  }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/customer/service/CustomerService.java
git commit -m "feat: add CustomerService"
```

---

## Task 7: CustomerController 작성

**Files:**
- Create: `backend/src/main/java/com/agentsupport/customer/CustomerController.java`

- [ ] **Step 1: 컨트롤러 작성**

```java
package com.agentsupport.customer;

import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.customer.dto.CustomerCreateRequest;
import com.agentsupport.customer.dto.CustomerDto;
import com.agentsupport.customer.dto.CustomerUpdateRequest;
import com.agentsupport.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

@Tag(name = "고객")
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

  private final CustomerService customerService;

  public CustomerController(CustomerService customerService) {
    this.customerService = customerService;
  }

  @GetMapping
  public PageResponse<CustomerDto> list(
      Authentication auth,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return customerService.findCustomers(auth.getName(), isAdmin(auth), page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CustomerDto create(Authentication auth, @Valid @RequestBody CustomerCreateRequest req) {
    return customerService.create(auth.getName(), req);
  }

  @GetMapping("/{id}")
  public CustomerDto get(Authentication auth, @PathVariable UUID id) {
    return customerService.findOne(id, auth.getName(), isAdmin(auth));
  }

  @PutMapping("/{id}")
  public CustomerDto update(
      Authentication auth, @PathVariable UUID id,
      @Valid @RequestBody CustomerUpdateRequest req) {
    return customerService.update(id, auth.getName(), isAdmin(auth), req);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(Authentication auth, @PathVariable UUID id) {
    customerService.delete(id, auth.getName(), isAdmin(auth));
  }

  private boolean isAdmin(Authentication auth) {
    for (GrantedAuthority a : auth.getAuthorities()) {
      if ("ROLE_ADMIN".equals(a.getAuthority())) return true;
    }
    return false;
  }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/customer/CustomerController.java
git commit -m "feat: add CustomerController"
```

---

## Task 8: CustomerController 통합 테스트

**Files:**
- Create: `backend/src/test/java/com/agentsupport/customer/CustomerControllerTest.java`

- [ ] **Step 1: 실패 테스트 작성**

```java
package com.agentsupport.customer;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CustomerControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;
  MockHttpSession adminSession;
  MockHttpSession agent1Session;
  MockHttpSession agent2Session;

  @BeforeEach
  void setUp() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();

    adminSession = login("admin", "Admin1234!");
    agent1Session = registerAndApprove("ag1", "AGENT1", "test1@x.com", "010-1111-1111");
    agent2Session = registerAndApprove("ag2", "AGENT2", "test2@x.com", "010-2222-2222");
  }

  private MockHttpSession login(String username, String password) throws Exception {
    MvcResult r = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk())
        .andReturn();
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  private MockHttpSession registerAndApprove(String id, String role, String email, String phone) throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"" + id + "\",\"password\":\"Test1234!\",\"name\":\"홍길동\","
                + "\"phone\":\"" + phone + "\",\"gaName\":\"GA\",\"email\":\"" + email + "\"}"))
        .andExpect(status().isCreated());
    mockMvc.perform(patch("/api/admin/users/" + id + "/approve")
            .session(adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"" + role + "\"}"))
        .andExpect(status().isOk());
    return login(id, "Test1234!");
  }

  private String createBody(String name) {
    return "{\"name\":\"" + name + "\",\"phone\":\"010-1234-5678\","
        + "\"birthDate\":\"1990-01-01\",\"gender\":\"M\","
        + "\"email\":\"a@b.com\",\"address\":\"서울\",\"memo\":\"\"}";
  }

  @Test
  void agent2_creates_customer_returns201() throws Exception {
    mockMvc.perform(post("/api/customers")
            .session(agent2Session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("김고객")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("김고객"));
  }

  @Test
  void agent1_lists_only_own_customers() throws Exception {
    mockMvc.perform(post("/api/customers")
            .session(agent1Session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("A고객")))
        .andExpect(status().isCreated());
    mockMvc.perform(post("/api/customers")
            .session(agent2Session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("B고객")))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/api/customers").session(agent1Session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].name").value("A고객"));
  }

  @Test
  void admin_lists_all_customers() throws Exception {
    mockMvc.perform(post("/api/customers")
            .session(agent1Session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("A고객")))
        .andExpect(status().isCreated());
    mockMvc.perform(post("/api/customers")
            .session(agent2Session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("B고객")))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/api/customers").session(adminSession))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));
  }

  @Test
  void agent_cannot_access_other_agent_customer_returns403() throws Exception {
    MvcResult r = mockMvc.perform(post("/api/customers")
            .session(agent1Session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(createBody("A고객")))
        .andExpect(status().isCreated())
        .andReturn();
    String id = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
        .readTree(r.getResponse().getContentAsString()).get("id").asText();

    mockMvc.perform(get("/api/customers/" + id).session(agent2Session))
        .andExpect(status().isForbidden());
  }

  @Test
  void list_without_auth_returns401() throws Exception {
    mockMvc.perform(get("/api/customers"))
        .andExpect(status().isUnauthorized());
  }
}
```

- [ ] **Step 2: 테스트 실행 (실패 확인)**

Run: `cd backend && ./gradlew test --tests CustomerControllerTest`
Expected: 모든 테스트 PASS (이미 Task 1~7로 구현이 완료된 상태이므로 이번엔 곧장 PASS). FAIL 시 컨트롤러/서비스 수정.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/test/java/com/agentsupport/customer/CustomerControllerTest.java
git commit -m "test: add CustomerController integration tests"
```

---

## Task 9: Claim 엔티티에 customerId 추가

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/claim/entity/Claim.java`

- [ ] **Step 1: 엔티티 수정**

기존 클래스 본문에 필드 + 정적 팩토리 + getter를 추가한다. 전체 파일 내용:

```java
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

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

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
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/claim/entity/Claim.java
git commit -m "feat: add customerId to Claim entity"
```

---

## Task 10: ClaimCreateRequest DTO + ClaimService.create

**Files:**
- Create: `backend/src/main/java/com/agentsupport/claim/dto/ClaimCreateRequest.java`
- Modify: `backend/src/main/java/com/agentsupport/claim/service/ClaimService.java`

- [ ] **Step 1: ClaimCreateRequest 작성**

```java
package com.agentsupport.claim.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ClaimCreateRequest(
    @NotNull UUID customerId,
    @NotBlank @Size(max = 20) String policyNumber,
    @NotBlank @Size(max = 50) String insurerName,
    @NotBlank @Size(max = 50) String claimType,
    BigDecimal claimAmount,
    @NotNull LocalDate claimDate) {}
```

- [ ] **Step 2: ClaimService 수정**

기존 `ClaimService`를 다음으로 교체:

```java
package com.agentsupport.claim.service;

import com.agentsupport.claim.dto.ClaimCreateRequest;
import com.agentsupport.claim.dto.ClaimDto;
import com.agentsupport.claim.entity.Claim;
import com.agentsupport.claim.repository.ClaimRepository;
import com.agentsupport.common.dto.PageResponse;
import com.agentsupport.customer.entity.Customer;
import com.agentsupport.customer.repository.CustomerRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class ClaimService {

  private final ClaimRepository claimRepository;
  private final CustomerRepository customerRepository;

  public ClaimService(ClaimRepository claimRepository, CustomerRepository customerRepository) {
    this.claimRepository = claimRepository;
    this.customerRepository = customerRepository;
  }

  public PageResponse<ClaimDto> findClaims(String agentId, String status, int page, int size) {
    Pageable pageable = PageRequest.of(page, Math.min(size, 100));
    String statusFilter = (status == null || status.isBlank()) ? null : status;
    return PageResponse.from(
        claimRepository.findByCondition(agentId, statusFilter, pageable).map(ClaimDto::from));
  }

  @Transactional
  public ClaimDto create(String agentId, ClaimCreateRequest req) {
    Customer customer = customerRepository
        .findByIdAndAgentId(req.customerId(), agentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "본인 고객이 아닙니다"));

    Claim claim = Claim.create(
        agentId,
        customer.getId(),
        req.policyNumber(),
        customer.getName(),
        req.insurerName(),
        req.claimType(),
        req.claimAmount(),
        "접수",
        req.claimDate());

    return ClaimDto.from(claimRepository.save(claim));
  }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/claim/dto/ClaimCreateRequest.java backend/src/main/java/com/agentsupport/claim/service/ClaimService.java
git commit -m "feat: add ClaimService.create with customer ownership check"
```

---

## Task 11: ClaimController에 POST 추가

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/claim/ClaimController.java`

- [ ] **Step 1: 컨트롤러 수정**

전체 파일 교체:

```java
package com.agentsupport.claim;

import com.agentsupport.claim.dto.ClaimCreateRequest;
import com.agentsupport.claim.dto.ClaimDto;
import com.agentsupport.claim.service.ClaimService;
import com.agentsupport.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "청구")
@RestController
@RequestMapping("/api/claims")
public class ClaimController {

  private final ClaimService claimService;

  public ClaimController(ClaimService claimService) {
    this.claimService = claimService;
  }

  @GetMapping
  public PageResponse<ClaimDto> list(
      Authentication auth,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return claimService.findClaims(auth.getName(), status, page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ClaimDto create(Authentication auth, @Valid @RequestBody ClaimCreateRequest req) {
    return claimService.create(auth.getName(), req);
  }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/claim/ClaimController.java
git commit -m "feat: add POST /api/claims endpoint"
```

---

## Task 12: ClaimController 통합 테스트

**Files:**
- Create: `backend/src/test/java/com/agentsupport/claim/ClaimControllerTest.java`

- [ ] **Step 1: 테스트 작성**

```java
package com.agentsupport.claim;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ClaimControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;
  MockHttpSession adminSession;
  MockHttpSession agent2Session;
  MockHttpSession otherAgentSession;

  @BeforeEach
  void setUp() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
    adminSession = login("admin", "Admin1234!");
    agent2Session = registerAndApprove("ag2", "AGENT2", "ag2@x.com", "010-2222-2222");
    otherAgentSession = registerAndApprove("ag3", "AGENT2", "ag3@x.com", "010-3333-3333");
  }

  private MockHttpSession login(String username, String password) throws Exception {
    MvcResult r = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk())
        .andReturn();
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  private MockHttpSession registerAndApprove(String id, String role, String email, String phone) throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"" + id + "\",\"password\":\"Test1234!\",\"name\":\"홍\","
                + "\"phone\":\"" + phone + "\",\"gaName\":\"GA\",\"email\":\"" + email + "\"}"))
        .andExpect(status().isCreated());
    mockMvc.perform(patch("/api/admin/users/" + id + "/approve")
            .session(adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"" + role + "\"}"))
        .andExpect(status().isOk());
    return login(id, "Test1234!");
  }

  private String createCustomer(MockHttpSession session, String name) throws Exception {
    MvcResult r = mockMvc.perform(post("/api/customers")
            .session(session)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + name + "\",\"phone\":\"010-1234-5678\","
                + "\"birthDate\":\"1990-01-01\",\"gender\":\"M\","
                + "\"email\":\"a@b.com\",\"address\":\"\",\"memo\":\"\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    return JsonMapper.builder().build()
        .readTree(r.getResponse().getContentAsString()).get("id").asText();
  }

  private String claimBody(String customerId) {
    return "{\"customerId\":\"" + customerId + "\",\"policyNumber\":\"P12345\","
        + "\"insurerName\":\"삼성생명\",\"claimType\":\"실손\","
        + "\"claimAmount\":100000,\"claimDate\":\"2026-05-19\"}";
  }

  @Test
  void agent2_creates_claim_with_own_customer_returns201() throws Exception {
    String cid = createCustomer(agent2Session, "김고객");
    mockMvc.perform(post("/api/claims")
            .session(agent2Session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(claimBody(cid)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.customerName").value("김고객"))
        .andExpect(jsonPath("$.status").value("접수"));
  }

  @Test
  void agent_cannot_create_claim_for_other_agent_customer_returns403() throws Exception {
    String cid = createCustomer(otherAgentSession, "타인고객");
    mockMvc.perform(post("/api/claims")
            .session(agent2Session)
            .contentType(MediaType.APPLICATION_JSON)
            .content(claimBody(cid)))
        .andExpect(status().isForbidden());
  }

  @Test
  void create_claim_without_auth_returns401() throws Exception {
    mockMvc.perform(post("/api/claims")
            .contentType(MediaType.APPLICATION_JSON)
            .content(claimBody("00000000-0000-0000-0000-000000000000")))
        .andExpect(status().isUnauthorized());
  }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `cd backend && ./gradlew test --tests ClaimControllerTest`
Expected: 모든 테스트 PASS.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/test/java/com/agentsupport/claim/ClaimControllerTest.java
git commit -m "test: add ClaimController create endpoint tests"
```

---

## Task 13: SecurityConfig — AGENT2 차단

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/config/SecurityConfig.java`

- [ ] **Step 1: 권한 규칙 추가**

`authorizeHttpRequests` 람다 내부, `.requestMatchers("/api/admin/**").hasRole("ADMIN")` 다음 줄에 다음 2개 라인을 삽입(`.anyRequest().authenticated()` 앞):

```java
.requestMatchers("/api/proposals/**", "/api/underwriting/**")
    .hasAnyRole("ADMIN", "AGENT1")
.requestMatchers("/api/customers/**", "/api/claims/**")
    .hasAnyRole("ADMIN", "AGENT1", "AGENT2")
```

수정 후 전체 블록은 다음과 같이 보여야 한다:

```java
.authorizeHttpRequests(
    auth ->
        auth.requestMatchers(
                "/api/auth/login", "/api/auth/register",
                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
            .permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/proposals/**", "/api/underwriting/**")
                .hasAnyRole("ADMIN", "AGENT1")
            .requestMatchers("/api/customers/**", "/api/claims/**")
                .hasAnyRole("ADMIN", "AGENT1", "AGENT2")
            .anyRequest()
            .authenticated())
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/config/SecurityConfig.java
git commit -m "feat: block AGENT2 from proposals/underwriting APIs"
```

---

## Task 14: 역할별 API 접근 통합 테스트

**Files:**
- Create: `backend/src/test/java/com/agentsupport/security/RoleAccessTest.java`

- [ ] **Step 1: 테스트 작성**

```java
package com.agentsupport.security;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RoleAccessTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;
  MockHttpSession adminSession;
  MockHttpSession agent1Session;
  MockHttpSession agent2Session;

  @BeforeEach
  void setUp() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
    adminSession = login("admin", "Admin1234!");
    agent1Session = registerAndApprove("ag1", "AGENT1");
    agent2Session = registerAndApprove("ag2", "AGENT2");
  }

  private MockHttpSession login(String username, String password) throws Exception {
    MvcResult r = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk())
        .andReturn();
    return (MockHttpSession) r.getRequest().getSession(false);
  }

  private MockHttpSession registerAndApprove(String id, String role) throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"" + id + "\",\"password\":\"Test1234!\",\"name\":\"홍\","
                + "\"phone\":\"010-0000-" + id.substring(2) + "\",\"gaName\":\"GA\",\"email\":\""
                + id + "@x.com\"}"))
        .andExpect(status().isCreated());
    mockMvc.perform(patch("/api/admin/users/" + id + "/approve")
            .session(adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"" + role + "\"}"))
        .andExpect(status().isOk());
    return login(id, "Test1234!");
  }

  @Test
  void agent2_proposals_get_returns403() throws Exception {
    mockMvc.perform(get("/api/proposals").session(agent2Session))
        .andExpect(status().isForbidden());
  }

  @Test
  void agent2_underwriting_get_returns403() throws Exception {
    mockMvc.perform(get("/api/underwriting").session(agent2Session))
        .andExpect(status().isForbidden());
  }

  @Test
  void agent1_proposals_get_returns200() throws Exception {
    mockMvc.perform(get("/api/proposals").session(agent1Session))
        .andExpect(status().isOk());
  }

  @Test
  void agent2_customers_get_returns200() throws Exception {
    mockMvc.perform(get("/api/customers").session(agent2Session))
        .andExpect(status().isOk());
  }

  @Test
  void agent2_claims_get_returns200() throws Exception {
    mockMvc.perform(get("/api/claims").session(agent2Session))
        .andExpect(status().isOk());
  }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `cd backend && ./gradlew test --tests RoleAccessTest`
Expected: 모든 테스트 PASS.

- [ ] **Step 3: 커밋**

```bash
git add backend/src/test/java/com/agentsupport/security/RoleAccessTest.java
git commit -m "test: verify AGENT2 cannot access proposals/underwriting APIs"
```

---

## Task 15: Dashboard — myCustomers, monthlyClaims 추가

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/dashboard/dto/DashboardSummaryDto.java`
- Modify: `backend/src/main/java/com/agentsupport/dashboard/service/DashboardService.java`
- Modify: `backend/src/main/java/com/agentsupport/claim/repository/ClaimRepository.java`

- [ ] **Step 1: ClaimRepository에 월 카운트 메서드 추가**

기존 인터페이스에 다음 메서드 추가:

```java
@Query(
    """
    SELECT COUNT(c) FROM Claim c
    WHERE c.agentId = :agentId
      AND c.claimDate BETWEEN :from AND :to
    """)
long countByAgentIdAndClaimDateBetween(
    @Param("agentId") String agentId,
    @Param("from") java.time.LocalDate from,
    @Param("to") java.time.LocalDate to);
```

- [ ] **Step 2: DashboardSummaryDto에 필드 추가**

전체 파일 교체:

```java
package com.agentsupport.dashboard.dto;

import com.agentsupport.proposal.dto.ProposalDto;
import java.util.List;

public record DashboardSummaryDto(
    long activeProposals,
    long underwritingPending,
    long claimsInProgress,
    long monthlyProposals,
    long myCustomers,
    long monthlyClaims,
    List<ProposalDto> recentProposals) {}
```

- [ ] **Step 3: DashboardService 수정**

전체 파일 교체:

```java
package com.agentsupport.dashboard.service;

import com.agentsupport.claim.repository.ClaimRepository;
import com.agentsupport.customer.repository.CustomerRepository;
import com.agentsupport.dashboard.dto.DashboardSummaryDto;
import com.agentsupport.policy.repository.PolicyRepository;
import com.agentsupport.proposal.dto.ProposalDto;
import com.agentsupport.proposal.repository.ProposalRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DashboardService {

  private final PolicyRepository policyRepository;
  private final ProposalRepository proposalRepository;
  private final ClaimRepository claimRepository;
  private final CustomerRepository customerRepository;

  public DashboardService(
      PolicyRepository policyRepository,
      ProposalRepository proposalRepository,
      ClaimRepository claimRepository,
      CustomerRepository customerRepository) {
    this.policyRepository = policyRepository;
    this.proposalRepository = proposalRepository;
    this.claimRepository = claimRepository;
    this.customerRepository = customerRepository;
  }

  public DashboardSummaryDto getSummary(String agentId) {
    LocalDate today = LocalDate.now();
    LocalDate firstOfMonth = today.withDayOfMonth(1);

    long activeProposals = proposalRepository.countActiveByAgentId(agentId);
    long underwritingPending = policyRepository.countPendingByAgentId(agentId);
    long claimsInProgress = claimRepository.countInProgressByAgentId(agentId);
    long monthlyProposals =
        proposalRepository.countByAgentIdAndProposedDateBetween(agentId, firstOfMonth, today);
    long myCustomers = customerRepository.countByAgentId(agentId);
    long monthlyClaims =
        claimRepository.countByAgentIdAndClaimDateBetween(agentId, firstOfMonth, today);

    List<ProposalDto> recentProposals =
        proposalRepository.findTop5ByAgentIdOrderByProposedDateDesc(agentId).stream()
            .map(ProposalDto::from)
            .toList();

    return new DashboardSummaryDto(
        activeProposals, underwritingPending, claimsInProgress, monthlyProposals,
        myCustomers, monthlyClaims, recentProposals);
  }
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 전체 테스트 실행**

Run: `cd backend && ./gradlew test`
Expected: 모든 테스트 PASS.

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/dashboard backend/src/main/java/com/agentsupport/claim/repository/ClaimRepository.java
git commit -m "feat: add myCustomers and monthlyClaims to dashboard summary"
```

---

## Task 16: 사이드바 — 고객 관리 메뉴 추가, 역할별 노출 조정

**Files:**
- Modify: `frontend/src/components/layout/sidebar.tsx`

- [ ] **Step 1: import 및 NAV_ITEMS 수정**

`import` 줄에서 `Users` 아이콘을 추가하고 `NAV_ITEMS`를 다음으로 교체:

```ts
import {
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  FileSearch,
  LayoutDashboard,
  Receipt,
  Settings,
  Users,
  X,
} from "lucide-react";
```

```ts
const NAV_ITEMS: NavItem[] = [
  { label: "대시보드", href: "/dashboard", icon: LayoutDashboard, roles: ["ADMIN", "AGENT1", "AGENT2"] },
  { label: "설계 관리", href: "/proposals", icon: ClipboardList, roles: ["ADMIN", "AGENT1"] },
  { label: "심사 현황", href: "/underwriting", icon: FileSearch, roles: ["ADMIN", "AGENT1"] },
  { label: "고객 관리", href: "/customers", icon: Users, roles: ["ADMIN", "AGENT1", "AGENT2"] },
  { label: "청구 관리", href: "/claims", icon: Receipt, roles: ["ADMIN", "AGENT1", "AGENT2"] },
  { label: "가입 관리", href: "/admin/users", icon: Settings, roles: ["ADMIN"] },
];
```

- [ ] **Step 2: 타입체크**

Run: `cd frontend && pnpm exec tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/components/layout/sidebar.tsx
git commit -m "feat: add 고객 관리 nav item, restrict 설계/심사 from AGENT2"
```

---

## Task 17: customers feature API & queries

**Files:**
- Create: `frontend/src/features/customers/api.ts`
- Create: `frontend/src/features/customers/queries.ts`

- [ ] **Step 1: api.ts 작성**

```ts
import { apiFetch } from "@/lib/api/csrf";
import type { PageResponse } from "@/features/contracts/api";

export interface CustomerDto {
  id: string;
  name: string;
  phone: string;
  birthDate: string | null;
  gender: string | null;
  email: string | null;
  address: string | null;
  memo: string | null;
  createdAt: string;
}

export interface CustomerWriteRequest {
  name: string;
  phone: string;
  birthDate?: string | null;
  gender?: string | null;
  email?: string | null;
  address?: string | null;
  memo?: string | null;
}

export interface CustomerQuery {
  page?: number;
  size?: number;
}

export function fetchCustomers(query: CustomerQuery = {}): Promise<PageResponse<CustomerDto>> {
  const params = new URLSearchParams();
  if (query.page !== undefined) params.set("page", String(query.page));
  if (query.size !== undefined) params.set("size", String(query.size));
  const qs = params.toString();
  return apiFetch<PageResponse<CustomerDto>>(`/api/customers${qs ? `?${qs}` : ""}`);
}

export function createCustomer(req: CustomerWriteRequest): Promise<CustomerDto> {
  return apiFetch<CustomerDto>("/api/customers", {
    method: "POST",
    body: JSON.stringify(req),
  });
}

export function updateCustomer(id: string, req: CustomerWriteRequest): Promise<CustomerDto> {
  return apiFetch<CustomerDto>(`/api/customers/${id}`, {
    method: "PUT",
    body: JSON.stringify(req),
  });
}

export function deleteCustomer(id: string): Promise<void> {
  return apiFetch<void>(`/api/customers/${id}`, { method: "DELETE" });
}
```

- [ ] **Step 2: queries.ts 작성**

```ts
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createCustomer,
  deleteCustomer,
  fetchCustomers,
  updateCustomer,
  type CustomerQuery,
  type CustomerWriteRequest,
} from "./api";

export function useCustomers(query: CustomerQuery = {}) {
  return useQuery({
    queryKey: ["customers", query],
    queryFn: () => fetchCustomers(query),
  });
}

export function useCreateCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CustomerWriteRequest) => createCustomer(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["customers"] }),
  });
}

export function useUpdateCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: CustomerWriteRequest }) => updateCustomer(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["customers"] }),
  });
}

export function useDeleteCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteCustomer(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["customers"] }),
  });
}
```

- [ ] **Step 3: 타입체크 + 커밋**

```bash
cd frontend && pnpm exec tsc --noEmit
cd .. && git add frontend/src/features/customers/
git commit -m "feat: add customers feature api/queries"
```

---

## Task 18: CustomerFormModal

**Files:**
- Create: `frontend/src/features/customers/CustomerFormModal.tsx`

- [ ] **Step 1: 모달 작성**

```tsx
"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { X } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { TextField } from "@/components/ui/text-field";
import { useCreateCustomer, useUpdateCustomer } from "./queries";
import type { CustomerDto } from "./api";

const schema = z.object({
  name: z.string().min(1, "이름을 입력하세요"),
  phone: z.string().regex(/^010-\d{3,4}-\d{4}$/, "올바른 형식: 010-0000-0000"),
  birthDate: z.string().optional(),
  gender: z.enum(["M", "F", ""]).optional(),
  email: z.string().email("올바른 이메일").or(z.literal("")).optional(),
  address: z.string().optional(),
  memo: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  onClose: () => void;
  initial?: CustomerDto;
}

export function CustomerFormModal({ onClose, initial }: Props) {
  const create = useCreateCustomer();
  const update = useUpdateCustomer();
  const isEdit = !!initial;
  const isPending = create.isPending || update.isPending;

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: initial?.name ?? "",
      phone: initial?.phone ?? "",
      birthDate: initial?.birthDate ?? "",
      gender: (initial?.gender as "M" | "F" | "") ?? "",
      email: initial?.email ?? "",
      address: initial?.address ?? "",
      memo: initial?.memo ?? "",
    },
  });

  function onSubmit(values: FormValues) {
    const payload = {
      name: values.name,
      phone: values.phone,
      birthDate: values.birthDate || null,
      gender: values.gender || null,
      email: values.email || null,
      address: values.address || null,
      memo: values.memo || null,
    };
    if (isEdit && initial) {
      update.mutate({ id: initial.id, req: payload }, { onSuccess: onClose });
    } else {
      create.mutate(payload, { onSuccess: onClose });
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-surface-container-lowest rounded-2xl shadow-lg w-full max-w-lg mx-4">
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
          <h2 className="text-base font-semibold text-on-surface">
            {isEdit ? "고객 수정" : "신규 고객 등록"}
          </h2>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-on-surface-variant hover:bg-surface-container-low transition-colors"
            aria-label="닫기"
          >
            <X size={18} />
          </button>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <div className="px-6 py-5 flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-4">
              <TextField label="이름" error={errors.name?.message} {...register("name")} />
              <TextField
                label="휴대폰번호"
                placeholder="010-0000-0000"
                error={errors.phone?.message}
                {...register("phone")}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <TextField
                label="생년월일"
                type="date"
                error={errors.birthDate?.message}
                {...register("birthDate")}
              />
              <div className="flex flex-col gap-1">
                <label className="text-sm text-on-surface-variant">성별</label>
                <select
                  className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest text-sm text-on-surface outline-none focus:border-primary-container"
                  {...register("gender")}
                >
                  <option value="">선택</option>
                  <option value="M">남</option>
                  <option value="F">여</option>
                </select>
              </div>
            </div>
            <TextField
              label="이메일"
              type="email"
              error={errors.email?.message}
              {...register("email")}
            />
            <TextField label="주소" error={errors.address?.message} {...register("address")} />
            <TextField label="메모" error={errors.memo?.message} {...register("memo")} />
          </div>
          <div className="px-6 pb-5 flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-3 px-6 rounded-xl border border-outline-variant text-sm font-semibold text-on-surface hover:bg-surface-container-low transition-colors"
            >
              취소
            </button>
            <Button type="submit" loading={isPending} className="flex-1">
              {isEdit ? "수정" : "등록"}
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 타입체크 + 커밋**

```bash
cd frontend && pnpm exec tsc --noEmit
cd .. && git add frontend/src/features/customers/CustomerFormModal.tsx
git commit -m "feat: add CustomerFormModal (create/edit)"
```

---

## Task 19: /customers 페이지 + layout

**Files:**
- Create: `frontend/src/app/customers/layout.tsx`
- Create: `frontend/src/app/customers/page.tsx`

- [ ] **Step 1: layout.tsx**

```tsx
import { AuthGuard } from "@/components/layout/auth-guard";

export default function CustomersLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard allowedRoles={["ADMIN", "AGENT1", "AGENT2"]}>{children}</AuthGuard>;
}
```

- [ ] **Step 2: page.tsx**

```tsx
"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { useCustomers, useDeleteCustomer } from "@/features/customers/queries";
import type { CustomerDto } from "@/features/customers/api";
import { CustomerFormModal } from "@/features/customers/CustomerFormModal";

export default function CustomersPage() {
  const [page, setPage] = useState(0);
  const [showModal, setShowModal] = useState(false);
  const [editing, setEditing] = useState<CustomerDto | null>(null);
  const { data, isLoading, isError } = useCustomers({ page, size: 20 });
  const del = useDeleteCustomer();

  function openCreate() {
    setEditing(null);
    setShowModal(true);
  }
  function openEdit(c: CustomerDto) {
    setEditing(c);
    setShowModal(true);
  }
  function handleDelete(c: CustomerDto) {
    if (!confirm(`'${c.name}' 고객을 삭제하시겠습니까?`)) return;
    del.mutate(c.id);
  }

  return (
    <div className="p-6 max-w-300 mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-on-surface">고객 관리</h1>
        <button
          type="button"
          onClick={openCreate}
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-primary-container text-on-primary-container text-sm font-semibold hover:opacity-90 transition-opacity"
        >
          <Plus size={16} /> 신규 고객 등록
        </button>
      </div>

      <div className="bg-surface-container-lowest rounded-2xl shadow-card overflow-hidden">
        <table className="w-full">
          <thead>
            <tr className="bg-surface-container-low text-sm text-on-surface-variant">
              <th className="text-left px-6 py-3 font-medium">이름</th>
              <th className="text-left px-6 py-3 font-medium">전화</th>
              <th className="text-left px-6 py-3 font-medium">생년월일</th>
              <th className="text-left px-6 py-3 font-medium">성별</th>
              <th className="text-left px-6 py-3 font-medium">이메일</th>
              <th className="text-left px-6 py-3 font-medium">등록일</th>
              <th className="text-right px-6 py-3 font-medium">액션</th>
            </tr>
          </thead>
          <tbody>
            {isLoading &&
              Array.from({ length: 5 }).map((_, i) => (
                <tr key={i} className="border-b border-outline-variant">
                  {Array.from({ length: 7 }).map((_, j) => (
                    <td key={j} className="px-6 py-4">
                      <div className="h-4 bg-surface-container rounded animate-pulse" />
                    </td>
                  ))}
                </tr>
              ))}
            {isError && (
              <tr>
                <td colSpan={7} className="px-6 py-12 text-center text-sm text-status-error">
                  데이터를 불러오지 못했습니다.
                </td>
              </tr>
            )}
            {!isLoading && !isError && data?.content.length === 0 && (
              <tr>
                <td colSpan={7} className="px-6 py-12 text-center text-sm text-on-surface-variant">
                  등록된 고객이 없습니다.
                </td>
              </tr>
            )}
            {!isLoading && !isError && data?.content.map((c, i) => (
              <tr key={c.id} className={i < (data.content.length - 1) ? "border-b border-outline-variant" : ""}>
                <td className="px-6 py-4 text-sm text-on-surface">{c.name}</td>
                <td className="px-6 py-4 text-sm text-on-surface">{c.phone}</td>
                <td className="px-6 py-4 text-sm text-on-surface">{c.birthDate ?? "-"}</td>
                <td className="px-6 py-4 text-sm text-on-surface">
                  {c.gender === "M" ? "남" : c.gender === "F" ? "여" : "-"}
                </td>
                <td className="px-6 py-4 text-sm text-on-surface">{c.email ?? "-"}</td>
                <td className="px-6 py-4 text-sm text-on-surface-variant">{c.createdAt.slice(0, 10)}</td>
                <td className="px-6 py-4 text-right text-sm">
                  <button
                    type="button"
                    onClick={() => openEdit(c)}
                    className="text-primary mr-3 hover:underline"
                  >
                    수정
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(c)}
                    className="text-status-error hover:underline"
                  >
                    삭제
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between px-6 py-4 border-t border-outline-variant">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-4 py-2 text-sm rounded-lg border border-outline-variant text-on-surface disabled:opacity-40 hover:bg-surface-container-low transition-colors"
            >
              이전
            </button>
            <span className="text-sm text-on-surface-variant">{page + 1} / {data.totalPages}</span>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
              disabled={page >= data.totalPages - 1}
              className="px-4 py-2 text-sm rounded-lg border border-outline-variant text-on-surface disabled:opacity-40 hover:bg-surface-container-low transition-colors"
            >
              다음
            </button>
          </div>
        )}
      </div>

      {showModal && (
        <CustomerFormModal
          onClose={() => setShowModal(false)}
          initial={editing ?? undefined}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 3: 빌드 확인**

Run: `cd frontend && pnpm exec tsc --noEmit && pnpm next build`
Expected: 빌드 성공 (또는 dev server에서 페이지 진입 시 에러 없음)

- [ ] **Step 4: 수동 동작 확인**

`pnpm dev`로 개발 서버 실행 → AGENT1 또는 AGENT2 계정으로 로그인 → `/customers` 진입 → 신규 등록 → 수정 → 삭제 흐름이 모두 작동하는지 확인.

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/app/customers/
git commit -m "feat: add /customers page with CRUD"
```

---

## Task 20: 청구 등록 — claims api/queries 확장 + ClaimFormModal

**Files:**
- Modify: `frontend/src/features/claims/api.ts`
- Modify: `frontend/src/features/claims/queries.ts`
- Create: `frontend/src/features/claims/ClaimFormModal.tsx`

- [ ] **Step 1: claims/api.ts 확장**

기존 파일에 다음을 추가:

```ts
export interface ClaimCreateRequest {
  customerId: string;
  policyNumber: string;
  insurerName: string;
  claimType: string;
  claimAmount: number | null;
  claimDate: string;
}

export function createClaim(req: ClaimCreateRequest): Promise<ClaimDto> {
  return apiFetch<ClaimDto>("/api/claims", {
    method: "POST",
    body: JSON.stringify(req),
  });
}
```

- [ ] **Step 2: claims/queries.ts 확장**

전체 파일 교체:

```ts
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createClaim, fetchClaims, type ClaimCreateRequest, type ClaimQuery } from "./api";

export function useClaims(query: ClaimQuery = {}) {
  return useQuery({
    queryKey: ["claims", query],
    queryFn: () => fetchClaims(query),
  });
}

export function useCreateClaim() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ClaimCreateRequest) => createClaim(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["claims"] }),
  });
}
```

- [ ] **Step 3: ClaimFormModal 작성**

```tsx
"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { X } from "lucide-react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { TextField } from "@/components/ui/text-field";
import { useCustomers } from "@/features/customers/queries";
import { useCreateClaim } from "./queries";

const schema = z.object({
  customerId: z.string().min(1, "고객을 선택하세요"),
  policyNumber: z.string().min(1, "계약번호를 입력하세요"),
  insurerName: z.string().min(1, "보험사를 입력하세요"),
  claimType: z.string().min(1, "청구 유형을 입력하세요"),
  claimAmount: z
    .string()
    .min(1, "청구 금액을 입력하세요")
    .refine((v) => !isNaN(Number(v)) && Number(v) >= 0, "올바른 금액을 입력하세요"),
  claimDate: z.string().min(1, "청구일을 입력하세요"),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  onClose: () => void;
}

export function ClaimFormModal({ onClose }: Props) {
  const { data: customers, isLoading: loadingCustomers } = useCustomers({ size: 100 });
  const create = useCreateClaim();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  function onSubmit(values: FormValues) {
    create.mutate(
      {
        customerId: values.customerId,
        policyNumber: values.policyNumber,
        insurerName: values.insurerName,
        claimType: values.claimType,
        claimAmount: Number(values.claimAmount),
        claimDate: values.claimDate,
      },
      { onSuccess: onClose },
    );
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
      onClick={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="bg-surface-container-lowest rounded-2xl shadow-lg w-full max-w-lg mx-4">
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
          <h2 className="text-base font-semibold text-on-surface">청구 등록</h2>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 rounded-lg text-on-surface-variant hover:bg-surface-container-low transition-colors"
            aria-label="닫기"
          >
            <X size={18} />
          </button>
        </div>
        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <div className="px-6 py-5 flex flex-col gap-4">
            <div className="flex flex-col gap-1">
              <label className="text-sm text-on-surface-variant">고객 선택</label>
              <select
                className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest text-sm text-on-surface outline-none focus:border-primary-container"
                disabled={loadingCustomers}
                {...register("customerId")}
              >
                <option value="">{loadingCustomers ? "로딩 중..." : "고객을 선택하세요"}</option>
                {customers?.content.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name} ({c.phone})
                  </option>
                ))}
              </select>
              {errors.customerId && (
                <p className="text-xs text-status-error">{errors.customerId.message}</p>
              )}
            </div>
            <div className="grid grid-cols-2 gap-4">
              <TextField
                label="계약번호"
                placeholder="P12345"
                error={errors.policyNumber?.message}
                {...register("policyNumber")}
              />
              <TextField
                label="보험사"
                placeholder="삼성생명"
                error={errors.insurerName?.message}
                {...register("insurerName")}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <TextField
                label="청구 유형"
                placeholder="실손"
                error={errors.claimType?.message}
                {...register("claimType")}
              />
              <TextField
                label="청구 금액 (원)"
                type="number"
                min="0"
                placeholder="100000"
                error={errors.claimAmount?.message}
                {...register("claimAmount")}
              />
            </div>
            <TextField
              label="청구일"
              type="date"
              error={errors.claimDate?.message}
              {...register("claimDate")}
            />
          </div>
          <div className="px-6 pb-5 flex gap-3">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-3 px-6 rounded-xl border border-outline-variant text-sm font-semibold text-on-surface hover:bg-surface-container-low transition-colors"
            >
              취소
            </button>
            <Button type="submit" loading={create.isPending} className="flex-1">
              등록
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: 타입체크 + 커밋**

```bash
cd frontend && pnpm exec tsc --noEmit
cd .. && git add frontend/src/features/claims/
git commit -m "feat: add claim create API and ClaimFormModal"
```

---

## Task 21: /claims 페이지에 청구 등록 버튼 통합

**Files:**
- Modify: `frontend/src/app/claims/page.tsx`

- [ ] **Step 1: import + 헤더 + 모달 추가**

기존 파일을 다음과 같이 수정:

```tsx
"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { useClaims } from "@/features/claims/queries";
import { ClaimFormModal } from "@/features/claims/ClaimFormModal";

const STATUS_OPTIONS = ["전체", "접수", "심사 중", "추가 서류 요청", "지급 완료", "부지급"] as const;

const STATUS_CLASS: Record<string, string> = {
  "접수": "bg-status-info-container text-status-info",
  "심사 중": "bg-status-info-container text-status-info",
  "추가 서류 요청": "bg-status-warning-container text-status-warning",
  "지급 완료": "bg-status-success-container text-status-success",
  "부지급": "bg-status-error-container text-status-error",
};

function formatAmount(value: number | null): string {
  if (value == null) return "-";
  return value.toLocaleString("ko-KR") + "원";
}

export default function ClaimsPage() {
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [showModal, setShowModal] = useState(false);

  const { data, isLoading, isError } = useClaims({
    page,
    size: 20,
    status: status || undefined,
  });

  function handleStatusChange(value: string) {
    setStatus(value === "전체" ? "" : value);
    setPage(0);
  }

  return (
    <div className="p-6 max-w-300 mx-auto">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-on-surface">청구 관리</h1>
        <button
          type="button"
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-4 py-2 rounded-xl bg-primary-container text-on-primary-container text-sm font-semibold hover:opacity-90 transition-opacity"
        >
          <Plus size={16} /> 청구 등록
        </button>
      </div>

      <div className="mb-4">
        <select
          aria-label="상태 필터"
          value={status || "전체"}
          onChange={(e) => handleStatusChange(e.target.value)}
          className="px-3 py-2 rounded-lg border border-outline-variant bg-surface-container-lowest text-sm text-on-surface outline-none focus:border-primary-container"
        >
          {STATUS_OPTIONS.map((opt) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
      </div>

      <div className="bg-surface-container-lowest rounded-2xl shadow-card overflow-hidden">
        <table className="w-full">
          <thead>
            <tr className="bg-surface-container-low text-sm text-on-surface-variant">
              <th className="text-left px-6 py-3 font-medium">계약번호</th>
              <th className="text-left px-6 py-3 font-medium">고객명</th>
              <th className="text-left px-6 py-3 font-medium">보험사</th>
              <th className="text-left px-6 py-3 font-medium">청구 유형</th>
              <th className="text-left px-6 py-3 font-medium">청구 금액</th>
              <th className="text-left px-6 py-3 font-medium">상태</th>
              <th className="text-left px-6 py-3 font-medium">청구일</th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <>
                {Array.from({ length: 5 }).map((_, i) => (
                  <tr key={i} className="border-b border-outline-variant">
                    {Array.from({ length: 7 }).map((_, j) => (
                      <td key={j} className="px-6 py-4">
                        <div className="h-4 bg-surface-container rounded animate-pulse" />
                      </td>
                    ))}
                  </tr>
                ))}
              </>
            )}
            {isError && (
              <tr>
                <td colSpan={7} className="px-6 py-12 text-center text-sm text-status-error">
                  데이터를 불러오지 못했습니다.
                </td>
              </tr>
            )}
            {!isLoading && !isError && data?.content.length === 0 && (
              <tr>
                <td colSpan={7} className="px-6 py-12 text-center text-sm text-on-surface-variant">
                  청구 내역이 없습니다.
                </td>
              </tr>
            )}
            {!isLoading &&
              !isError &&
              data?.content.map((c, i) => (
                <tr
                  key={c.id}
                  className={i < (data.content.length - 1) ? "border-b border-outline-variant" : ""}
                >
                  <td className="px-6 py-4 text-sm font-mono text-on-surface">{c.policyNumber}</td>
                  <td className="px-6 py-4 text-sm text-on-surface">{c.customerName}</td>
                  <td className="px-6 py-4 text-sm text-on-surface">{c.insurerName}</td>
                  <td className="px-6 py-4 text-sm text-on-surface">{c.claimType}</td>
                  <td className="px-6 py-4 text-sm font-mono text-on-surface">
                    {formatAmount(c.claimAmount)}
                  </td>
                  <td className="px-6 py-4">
                    <span
                      className={`text-xs font-medium px-2 py-1 rounded-full ${
                        STATUS_CLASS[c.status] ?? "bg-surface-container text-on-surface-variant"
                      }`}
                    >
                      {c.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-sm text-on-surface-variant">{c.claimDate}</td>
                </tr>
              ))}
          </tbody>
        </table>

        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between px-6 py-4 border-t border-outline-variant">
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-4 py-2 text-sm rounded-lg border border-outline-variant text-on-surface disabled:opacity-40 hover:bg-surface-container-low transition-colors"
            >
              이전
            </button>
            <span className="text-sm text-on-surface-variant">
              {page + 1} / {data.totalPages}
            </span>
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
              disabled={page >= data.totalPages - 1}
              className="px-4 py-2 text-sm rounded-lg border border-outline-variant text-on-surface disabled:opacity-40 hover:bg-surface-container-low transition-colors"
            >
              다음
            </button>
          </div>
        )}
      </div>

      {showModal && <ClaimFormModal onClose={() => setShowModal(false)} />}
    </div>
  );
}
```

- [ ] **Step 2: 빌드 + 동작 확인**

Run: `cd frontend && pnpm exec tsc --noEmit`
수동: `/claims` 진입 → 청구 등록 → 본인 고객 선택 → 제출 → 테이블에 추가됨

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/app/claims/page.tsx
git commit -m "feat: add 청구 등록 button to /claims page"
```

---

## Task 22: Dashboard — AGENT2 분기 렌더링

**Files:**
- Modify: `frontend/src/features/dashboard/api.ts`
- Modify: `frontend/src/app/dashboard/page.tsx`

- [ ] **Step 1: api.ts — 새 필드 타입 추가**

```ts
import { apiFetch } from "@/lib/api/csrf";
import type { ProposalDto } from "@/features/proposals/api";

export interface DashboardSummaryDto {
  activeProposals: number;
  underwritingPending: number;
  claimsInProgress: number;
  monthlyProposals: number;
  myCustomers: number;
  monthlyClaims: number;
  recentProposals: ProposalDto[];
}

export function fetchDashboardSummary(): Promise<DashboardSummaryDto> {
  return apiFetch<DashboardSummaryDto>("/api/dashboard/summary");
}
```

- [ ] **Step 2: dashboard/page.tsx — useMe 추가 및 분기**

기존 파일을 다음과 같이 수정:

```tsx
"use client";

import { useState } from "react";
import Link from "next/link";
import {
  AlertCircle,
  ClipboardList,
  FileSearch,
  Plus,
  Receipt,
  TrendingUp,
  Users,
} from "lucide-react";
import { useDashboardSummary } from "@/features/dashboard/queries";
import { useMe } from "@/features/auth/queries";
import { ProposalFormModal } from "@/features/proposals/ProposalFormModal";

const MONTHLY_TARGET = 10;

const TABS_DEFAULT = ["공지사항", "최근 설계", "일정"] as const;
const TABS_AGENT2 = ["공지사항", "일정"] as const;
type Tab = string;

const NOTICES = [
  { id: 1, title: "실손보험 청구 서류 제출 기준 변경 안내", date: "2026-05-15", isNew: true },
  { id: 2, title: "5월 GA 월례 교육 일정 공지", date: "2026-05-10", isNew: true },
  { id: 3, title: "암보험 심사 기준 강화 안내 (DB손보, 삼성생명)", date: "2026-05-02", isNew: false },
  { id: 4, title: "2026년 상반기 수수료 정산 일정 및 기준 업데이트", date: "2026-04-25", isNew: false },
  { id: 5, title: "변액보험 판매 자격 갱신 대상자 안내", date: "2026-04-18", isNew: false },
];

const SCHEDULE_TYPE_CLASS: Record<string, string> = {
  상담: "bg-status-info-container text-status-info",
  서류: "bg-status-warning-container text-status-warning",
  교육: "bg-status-success-container text-status-success",
  청구: "bg-status-error-container text-status-error",
  세미나: "bg-surface-container-high text-on-surface-variant",
};

const SCHEDULES = [
  { id: 1, title: "류○○ 고객 설계 상담", date: "2026-05-20", time: "14:00", type: "상담" },
  { id: 2, title: "삼성생명 심사 서류 제출", date: "2026-05-21", time: "09:00", type: "서류" },
  { id: 3, title: "GA 월례 교육", date: "2026-05-22", time: "13:00", type: "교육" },
  { id: 4, title: "이○○ 청구 서류 확인 미팅", date: "2026-05-26", time: "10:30", type: "청구" },
  { id: 5, title: "DB손보 신상품 설계 세미나", date: "2026-05-27", time: "14:00", type: "세미나" },
];

const PROPOSAL_STATUS_CLASS: Record<string, string> = {
  "작성 중": "bg-status-info-container text-status-info",
  "설계 완료": "bg-status-success-container text-status-success",
  "취소": "bg-surface-container-high text-on-surface-variant",
};

export default function DashboardPage() {
  const { data } = useDashboardSummary();
  const { data: me } = useMe();
  const isAgent2 = me?.role === "AGENT2";
  const tabs = isAgent2 ? TABS_AGENT2 : TABS_DEFAULT;
  const [activeTab, setActiveTab] = useState<Tab>(tabs[0]);
  const [showModal, setShowModal] = useState(false);

  const isLoading = !data;
  const activeProposals = data?.activeProposals ?? 0;
  const underwritingPending = data?.underwritingPending ?? 0;
  const claimsInProgress = data?.claimsInProgress ?? 0;
  const monthlyProposals = data?.monthlyProposals ?? 0;
  const myCustomers = data?.myCustomers ?? 0;
  const monthlyClaims = data?.monthlyClaims ?? 0;
  const progressPct = Math.min(100, Math.round((monthlyProposals / MONTHLY_TARGET) * 100));

  return (
    <div className="p-6 max-w-300 mx-auto space-y-6">
      <h1 className="text-2xl font-bold text-on-surface">대시보드</h1>

      {/* 요약 카드 3개 — role별 분기 */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {isAgent2 ? (
          <>
            <SummaryCard
              label="본인 고객"
              value={myCustomers}
              unit="명"
              caption="등록한 고객"
              Icon={Users}
              iconClass="bg-status-info-container text-status-info"
              borderClass="border-status-info"
              isLoading={isLoading}
            />
            <SummaryCard
              label="청구 처리 중"
              value={claimsInProgress}
              unit="건"
              caption="접수 + 심사 중"
              Icon={AlertCircle}
              iconClass="bg-status-error-container text-status-error"
              borderClass="border-status-error"
              isLoading={isLoading}
            />
            <SummaryCard
              label="이번 달 청구 등록"
              value={monthlyClaims}
              unit="건"
              caption="이번 달 등록"
              Icon={Receipt}
              iconClass="bg-status-success-container text-status-success"
              borderClass="border-status-success"
              isLoading={isLoading}
            />
          </>
        ) : (
          <>
            <SummaryCard
              label="진행 중인 설계"
              value={activeProposals}
              unit="건"
              caption="작성 중 + 설계 완료"
              Icon={ClipboardList}
              iconClass="bg-status-info-container text-status-info"
              borderClass="border-status-info"
              isLoading={isLoading}
            />
            <SummaryCard
              label="심사 대기 건수"
              value={underwritingPending}
              unit="건"
              caption="심사 중 + 서류 보완"
              Icon={FileSearch}
              iconClass="bg-status-warning-container text-status-warning"
              borderClass="border-status-warning"
              isLoading={isLoading}
            />
            <SummaryCard
              label="청구 처리 중"
              value={claimsInProgress}
              unit="건"
              caption="접수 + 심사 중"
              Icon={AlertCircle}
              iconClass="bg-status-error-container text-status-error"
              borderClass="border-status-error"
              isLoading={isLoading}
            />
          </>
        )}
      </div>

      {/* 2단 레이아웃 */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-7 flex flex-col gap-6">
          {/* 빠른 작업 */}
          <div className="bg-surface-container-lowest rounded-2xl shadow-card p-6">
            <h2 className="text-base font-semibold text-on-surface mb-4">빠른 작업</h2>
            <div className="grid grid-cols-3 gap-3">
              {isAgent2 ? (
                <>
                  <Link
                    href="/customers"
                    className="flex flex-col items-center justify-center gap-1.5 px-3 py-4 rounded-xl bg-primary-container text-on-primary-container text-xs font-medium hover:opacity-90 transition-opacity"
                  >
                    <Plus size={18} /> 새 고객 등록
                  </Link>
                  <Link
                    href="/claims"
                    className="flex flex-col items-center justify-center gap-1.5 px-3 py-4 rounded-xl border border-outline-variant text-on-surface text-xs font-medium hover:bg-surface-container-low transition-colors"
                  >
                    <Plus size={18} /> 청구 등록
                  </Link>
                  <Link
                    href="/claims"
                    className="flex flex-col items-center justify-center gap-1.5 px-3 py-4 rounded-xl border border-outline-variant text-on-surface text-xs font-medium hover:bg-surface-container-low transition-colors"
                  >
                    <Receipt size={18} /> 청구 관리
                  </Link>
                </>
              ) : (
                <>
                  <button
                    type="button"
                    onClick={() => setShowModal(true)}
                    className="flex flex-col items-center justify-center gap-1.5 px-3 py-4 rounded-xl bg-primary-container text-on-primary-container text-xs font-medium hover:opacity-90 transition-opacity"
                  >
                    <Plus size={18} /> 새 설계 작성
                  </button>
                  <Link
                    href="/underwriting"
                    className="flex flex-col items-center justify-center gap-1.5 px-3 py-4 rounded-xl border border-outline-variant text-on-surface text-xs font-medium hover:bg-surface-container-low transition-colors"
                  >
                    <FileSearch size={18} /> 심사 현황
                  </Link>
                  <Link
                    href="/claims"
                    className="flex flex-col items-center justify-center gap-1.5 px-3 py-4 rounded-xl border border-outline-variant text-on-surface text-xs font-medium hover:bg-surface-container-low transition-colors"
                  >
                    <Receipt size={18} /> 청구 관리
                  </Link>
                </>
              )}
            </div>
          </div>

          {/* 업무 현황 */}
          <div className="bg-surface-container-lowest rounded-2xl shadow-card p-6">
            <h2 className="text-base font-semibold text-on-surface mb-4">업무 현황</h2>
            <div className="flex flex-col">
              {(isAgent2
                ? [
                    {
                      label: "청구 처리 중",
                      value: claimsInProgress,
                      iconClass: "bg-status-error-container text-status-error",
                      badgeClass: "bg-status-error-container text-status-error",
                      Icon: AlertCircle,
                    },
                  ]
                : [
                    {
                      label: "설계 진행 중",
                      value: activeProposals,
                      iconClass: "bg-status-info-container text-status-info",
                      badgeClass: "bg-status-info-container text-status-info",
                      Icon: ClipboardList,
                    },
                    {
                      label: "심사 대기",
                      value: underwritingPending,
                      iconClass: "bg-status-warning-container text-status-warning",
                      badgeClass: "bg-status-warning-container text-status-warning",
                      Icon: FileSearch,
                    },
                    {
                      label: "청구 처리 중",
                      value: claimsInProgress,
                      iconClass: "bg-status-error-container text-status-error",
                      badgeClass: "bg-status-error-container text-status-error",
                      Icon: AlertCircle,
                    },
                  ]
              ).map(({ label, value, iconClass, badgeClass, Icon }) => (
                <div
                  key={label}
                  className="flex items-center justify-between py-3 border-b border-outline-variant last:border-0"
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-9 h-9 rounded-full flex items-center justify-center shrink-0 ${iconClass}`}
                    >
                      <Icon size={16} />
                    </div>
                    <span className="text-sm text-on-surface">{label}</span>
                  </div>
                  {isLoading ? (
                    <div className="h-6 w-10 bg-surface-container rounded-full animate-pulse" />
                  ) : (
                    <span className={`text-xs font-semibold px-2.5 py-1 rounded-full ${badgeClass}`}>
                      {value}건
                    </span>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 오른쪽: 탭 패널 */}
        <div className="lg:col-span-5">
          <div className="bg-surface-container-lowest rounded-2xl shadow-card overflow-hidden h-full flex flex-col">
            <div className="flex border-b border-outline-variant shrink-0">
              {tabs.map((tab) => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => setActiveTab(tab)}
                  className={[
                    "flex-1 py-3 text-sm font-medium border-b-2 -mb-px transition-colors",
                    activeTab === tab
                      ? "border-primary text-on-surface"
                      : "border-transparent text-on-surface-variant hover:text-on-surface",
                  ].join(" ")}
                >
                  {tab}
                </button>
              ))}
            </div>
            <div className="flex-1 overflow-y-auto p-5">
              {activeTab === "공지사항" && (
                <div className="flex flex-col">
                  {NOTICES.map((n, i) => (
                    <div
                      key={n.id}
                      className={[
                        "flex items-start gap-3 py-3 cursor-default hover:bg-surface-container-low -mx-2 px-2 rounded-lg transition-colors",
                        i < NOTICES.length - 1 ? "border-b border-outline-variant" : "",
                      ].join(" ")}
                    >
                      <div className="flex-1 min-w-0">
                        <p className="text-sm text-on-surface leading-snug line-clamp-2">{n.title}</p>
                        <p className="text-xs text-on-surface-variant mt-1">{n.date}</p>
                      </div>
                      {n.isNew && (
                        <span className="shrink-0 text-xs font-bold text-status-error bg-status-error-container px-1.5 py-0.5 rounded mt-0.5">
                          N
                        </span>
                      )}
                    </div>
                  ))}
                </div>
              )}

              {activeTab === "최근 설계" && !isAgent2 && (
                <div className="flex flex-col gap-1">
                  {isLoading &&
                    Array.from({ length: 5 }).map((_, i) => (
                      <div key={i} className="flex items-center justify-between p-3 rounded-xl">
                        <div className="flex flex-col gap-1.5 flex-1">
                          <div className="h-3 w-20 bg-surface-container rounded animate-pulse" />
                          <div className="h-3.5 w-32 bg-surface-container rounded animate-pulse" />
                        </div>
                        <div className="h-5 w-14 bg-surface-container rounded-full animate-pulse shrink-0" />
                      </div>
                    ))}
                  {!isLoading && (data?.recentProposals ?? []).length === 0 && (
                    <p className="text-sm text-on-surface-variant text-center py-8">
                      최근 설계 내역이 없습니다.
                    </p>
                  )}
                  {!isLoading &&
                    (data?.recentProposals ?? []).map((p) => (
                      <div
                        key={p.id}
                        className="flex items-center justify-between p-3 rounded-xl hover:bg-surface-container-low transition-colors"
                      >
                        <div className="flex flex-col gap-0.5 min-w-0 flex-1 mr-3">
                          <span className="text-xs text-on-surface-variant">{p.insurerName}</span>
                          <span className="text-sm text-on-surface truncate">
                            {p.customerName} · {p.productName}
                          </span>
                        </div>
                        <span
                          className={`shrink-0 text-xs font-medium px-2 py-1 rounded-full ${
                            PROPOSAL_STATUS_CLASS[p.status] ??
                            "bg-surface-container text-on-surface-variant"
                          }`}
                        >
                          {p.status}
                        </span>
                      </div>
                    ))}
                </div>
              )}

              {activeTab === "일정" && (
                <div className="flex flex-col">
                  {SCHEDULES.map((s, i) => (
                    <div
                      key={s.id}
                      className={[
                        "flex items-center gap-3 py-3",
                        i < SCHEDULES.length - 1 ? "border-b border-outline-variant" : "",
                      ].join(" ")}
                    >
                      <div className="shrink-0 w-10 text-center">
                        <p className="text-xs text-on-surface-variant leading-tight">
                          {s.date.slice(5).replace("-", "/")}
                        </p>
                        <p className="text-base font-bold text-on-surface leading-tight">
                          {new Date(s.date).getDate()}
                        </p>
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm text-on-surface truncate">{s.title}</p>
                        <p className="text-xs text-on-surface-variant">{s.time}</p>
                      </div>
                      <span
                        className={`shrink-0 text-xs font-medium px-2 py-1 rounded-full ${
                          SCHEDULE_TYPE_CLASS[s.type] ??
                          "bg-surface-container text-on-surface-variant"
                        }`}
                      >
                        {s.type}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {showModal && <ProposalFormModal onClose={() => setShowModal(false)} />}

      {/* 이번 달 설계 작성 현황 — AGENT2는 숨김 */}
      {!isAgent2 && (
        <div className="bg-primary-container rounded-2xl p-6">
          <div className="flex items-center gap-2 mb-3">
            <TrendingUp size={20} className="text-on-primary-container" />
            <h2 className="text-base font-semibold text-on-primary-container">
              이번 달 설계 작성 현황
            </h2>
          </div>
          <div className="flex items-end justify-between mb-3">
            {isLoading ? (
              <div className="h-7 w-36 bg-white/20 rounded animate-pulse" />
            ) : (
              <p className="text-on-primary-container">
                <span className="text-2xl font-bold">{monthlyProposals}</span>
                <span className="text-sm ml-1 text-on-primary-container">
                  / 목표 {MONTHLY_TARGET}건
                </span>
              </p>
            )}
            <span className="text-sm font-semibold text-on-primary-container">
              {isLoading ? "…" : `${progressPct}%`}
            </span>
          </div>
          <progress
            className="growth-progress"
            max={MONTHLY_TARGET}
            value={isLoading ? 0 : monthlyProposals}
          />
        </div>
      )}
    </div>
  );
}

interface SummaryCardProps {
  label: string;
  value: number;
  unit: string;
  caption: string;
  Icon: React.ComponentType<{ size?: number; className?: string }>;
  iconClass: string;
  borderClass: string;
  isLoading: boolean;
}

function SummaryCard({
  label, value, unit, caption, Icon, iconClass, borderClass, isLoading,
}: SummaryCardProps) {
  return (
    <div
      className={`bg-surface-container-lowest rounded-2xl shadow-card p-6 flex items-start justify-between border-l-4 ${borderClass}`}
    >
      <div className="min-w-0">
        <p className="text-sm text-on-surface-variant mb-1">{label}</p>
        {isLoading ? (
          <div className="h-9 w-20 bg-surface-container rounded animate-pulse mt-1" />
        ) : (
          <p className="text-3xl font-bold text-on-surface leading-tight">
            {value}
            <span className="text-base font-normal ml-1 text-on-surface-variant">{unit}</span>
          </p>
        )}
        <p className="text-xs text-on-surface-variant mt-1.5">{caption}</p>
      </div>
      <div className={`w-11 h-11 rounded-full flex items-center justify-center shrink-0 ml-4 ${iconClass}`}>
        <Icon size={20} className="" />
      </div>
    </div>
  );
}
```

- [ ] **Step 2: 타입체크**

Run: `cd frontend && pnpm exec tsc --noEmit`
Expected: 에러 없음

- [ ] **Step 3: 수동 동작 확인**

`pnpm dev`로 실행, ADMIN/AGENT1/AGENT2 각 계정으로 로그인하여:
- ADMIN/AGENT1: 기존 대시보드와 동일 표시 (단, `myCustomers` 등 새 필드 응답은 무시됨)
- AGENT2: 본인 고객/청구 처리 중/이번 달 청구 등록 카드 노출, 빠른 작업이 고객/청구 위주, 업무 현황은 청구만, 탭은 공지/일정 2개, 하단 설계 진행률 영역 미노출

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/features/dashboard/api.ts frontend/src/app/dashboard/page.tsx
git commit -m "feat: branch dashboard rendering for AGENT2 role"
```

---

## Task 23: 최종 검증

- [ ] **Step 1: 전체 백엔드 테스트**

Run: `cd backend && ./gradlew test`
Expected: 모든 테스트 PASS.

- [ ] **Step 2: 프런트 타입체크 및 빌드**

Run: `cd frontend && pnpm exec tsc --noEmit && pnpm next build`
Expected: 에러 없이 빌드 성공.

- [ ] **Step 3: 수동 시나리오 점검**

ADMIN/AGENT1/AGENT2 각 계정으로 다음 플로우 확인:

| 계정 | 시나리오 | 기대 |
| --- | --- | --- |
| AGENT2 | 사이드바 메뉴 | 대시보드, 고객 관리, 청구 관리만 노출 |
| AGENT2 | 대시보드 | 본인 고객/청구 처리 중/이번 달 청구 등록 카드, 청구 위주 업무, 공지/일정 탭만 |
| AGENT2 | `/customers` | 등록·수정·삭제 가능 |
| AGENT2 | `/claims` | 청구 등록 가능, 본인 청구만 보임 |
| AGENT2 | `/proposals` 직접 URL 입력 | `/dashboard`로 리다이렉트 (AuthGuard) |
| AGENT2 | `/api/proposals` 직접 호출 | 403 |
| AGENT1 | 사이드바 | 대시보드, 설계, 심사, 고객 관리, 청구 관리 |
| AGENT1 | 대시보드 | 기존과 동일 + 신규 필드 무시 |
| AGENT1 | `/customers` | 본인 고객만 보임 |
| ADMIN | `/customers` | 모든 고객 조회 가능 |

- [ ] **Step 4: 머지 준비**

```bash
git log --oneline | head -25
```

이상 모든 커밋이 의미 있는 단위로 분리되어 있는지 확인.

---

## 범위 외 (이번 작업에서 제외)

- 일정 데이터 동적화 / 역할별 필터
- 청구 수정·삭제·상태 변경
- 인라인 고객 등록(청구 등록 모달 안에서)
- 고객 검색(이름 부분일치)
- 기존 시드 청구의 `customer_id` 백필
