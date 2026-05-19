# 회원가입 & 역할 기반 접근 제어 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원가입(가입 신청 → 관리자 승인 → 로그인) 흐름과 역할(ADMIN/AGENT1/AGENT2)별 메뉴 접근 제어를 구현한다.

**Architecture:**
- 백엔드: `users` 테이블 추가, `MockUserDetailsService` → DB 기반 `UserDetailsServiceImpl` 교체. `AuthController`에서 status 체크. `AdminUserController`로 승인/거절.
- 프론트엔드: `MeResponse`에 role 추가. `AuthGuard`에 role 체크 추가. `Sidebar`에서 role별 메뉴 필터링. `/register`, `/pending`, `/admin/users` 페이지 추가.
- PII(이름/연락처/이메일): 기존 `PiiAttributeConverter` 재사용. 이메일 중복은 `email_hash`(SHA-256) + DB UNIQUE로 보장.

**Tech Stack:** Spring Boot 3.5.x, Spring Security, Spring Data JPA, Flyway, Next.js 15 App Router, TanStack Query, React Hook Form + Zod, H2(test)

---

## 파일 맵

### 백엔드 — 생성
- `backend/src/main/resources/db/migration/V10__create_users.sql`
- `backend/src/main/java/com/agentsupport/user/entity/User.java`
- `backend/src/main/java/com/agentsupport/user/repository/UserRepository.java`
- `backend/src/main/java/com/agentsupport/user/service/UserService.java`
- `backend/src/main/java/com/agentsupport/user/dto/RegisterRequest.java`
- `backend/src/main/java/com/agentsupport/user/dto/UserSummaryDto.java`
- `backend/src/main/java/com/agentsupport/user/dto/ApproveRequest.java`
- `backend/src/main/java/com/agentsupport/auth/UserDetailsServiceImpl.java`
- `backend/src/main/java/com/agentsupport/auth/AdminInitializer.java`
- `backend/src/main/java/com/agentsupport/admin/AdminUserController.java`
- `backend/src/test/java/com/agentsupport/user/RegisterControllerTest.java`
- `backend/src/test/java/com/agentsupport/admin/AdminUserControllerTest.java`

### 백엔드 — 수정/삭제
- DELETE: `backend/src/main/java/com/agentsupport/auth/MockUserDetailsService.java`
- MODIFY: `backend/src/main/java/com/agentsupport/auth/AuthController.java`
- MODIFY: `backend/src/main/java/com/agentsupport/auth/dto/MeResponse.java`
- MODIFY: `backend/src/main/java/com/agentsupport/config/SecurityConfig.java`
- MODIFY: `backend/src/test/java/com/agentsupport/auth/AuthControllerTest.java`

### 프론트엔드 — 생성
- `frontend/src/features/admin/api.ts`
- `frontend/src/features/admin/queries.ts`
- `frontend/src/app/register/page.tsx`
- `frontend/src/app/pending/page.tsx`
- `frontend/src/app/admin/layout.tsx`
- `frontend/src/app/admin/users/page.tsx`

### 프론트엔드 — 수정
- MODIFY: `frontend/src/features/auth/api.ts`
- MODIFY: `frontend/src/features/auth/queries.ts`
- MODIFY: `frontend/src/components/layout/auth-guard.tsx`
- MODIFY: `frontend/src/components/layout/app-shell.tsx`
- MODIFY: `frontend/src/components/layout/sidebar.tsx`
- MODIFY: `frontend/src/app/login/page.tsx`
- MODIFY: `frontend/src/app/dashboard/layout.tsx`
- MODIFY: `frontend/src/app/proposals/layout.tsx`
- MODIFY: `frontend/src/app/underwriting/layout.tsx`
- MODIFY: `frontend/src/app/claims/layout.tsx`

---

## Task 1: Flyway V10 — users 테이블 생성

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__create_users.sql`

- [ ] **Step 1: SQL 파일 생성**

```sql
CREATE TABLE users (
  id         VARCHAR(50)  PRIMARY KEY,
  password   VARCHAR(255) NOT NULL,
  name       VARCHAR(500) NOT NULL,
  phone      VARCHAR(500) NOT NULL,
  ga_name    VARCHAR(100) NOT NULL,
  email      VARCHAR(500) NOT NULL,
  email_hash VARCHAR(64)  NOT NULL UNIQUE,
  role       VARCHAR(20),
  status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_status ON users (status);
```

- [ ] **Step 2: 빌드로 Flyway 파싱 오류 없는지 확인**

Run: `cd backend && ./gradlew compileJava --no-daemon -q`
Expected: BUILD SUCCESSFUL (컴파일 에러 없음)

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/resources/db/migration/V10__create_users.sql
git commit -m "feat: add users table migration (V10)"
```

---

## Task 2: User 엔티티 + UserRepository

**Files:**
- Create: `backend/src/main/java/com/agentsupport/user/entity/User.java`
- Create: `backend/src/main/java/com/agentsupport/user/repository/UserRepository.java`

- [ ] **Step 1: User 엔티티 작성**

```java
package com.agentsupport.user.entity;

import com.agentsupport.security.PiiAttributeConverter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "users")
public class User {

  @Id
  @Column(length = 50)
  private String id;

  @Column(nullable = false, length = 255)
  private String password;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String name;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String phone;

  @Column(name = "ga_name", nullable = false, length = 100)
  private String gaName;

  @Convert(converter = PiiAttributeConverter.class)
  @Column(nullable = false, length = 500)
  private String email;

  @Column(name = "email_hash", nullable = false, length = 64, unique = true)
  private String emailHash;

  @Column(length = 20)
  private String role;

  @Column(nullable = false, length = 20)
  private String status;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  protected User() {}

  public static User create(
      String id, String password, String name, String phone,
      String gaName, String email, String emailHash) {
    User u = new User();
    u.id = id;
    u.password = password;
    u.name = name;
    u.phone = phone;
    u.gaName = gaName;
    u.email = email;
    u.emailHash = emailHash;
    u.role = null;
    u.status = "PENDING";
    return u;
  }

  public static User createAdmin(
      String id, String password, String name, String phone,
      String gaName, String email, String emailHash) {
    User u = create(id, password, name, phone, gaName, email, emailHash);
    u.role = "ADMIN";
    u.status = "ACTIVE";
    return u;
  }

  public void approve(String role) {
    this.role = role;
    this.status = "ACTIVE";
  }

  public void reject() {
    this.status = "REJECTED";
  }

  public String getId() { return id; }
  public String getPassword() { return password; }
  public String getName() { return name; }
  public String getPhone() { return phone; }
  public String getGaName() { return gaName; }
  public String getEmail() { return email; }
  public String getEmailHash() { return emailHash; }
  public String getRole() { return role; }
  public String getStatus() { return status; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 2: UserRepository 작성**

```java
package com.agentsupport.user.repository;

import com.agentsupport.user.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
  boolean existsByEmailHash(String emailHash);
  List<User> findByStatus(String status);
  List<User> findAll();
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava --no-daemon -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/user/
git commit -m "feat: add User entity and UserRepository"
```

---

## Task 3: DTOs — RegisterRequest / UserSummaryDto / ApproveRequest

**Files:**
- Create: `backend/src/main/java/com/agentsupport/user/dto/RegisterRequest.java`
- Create: `backend/src/main/java/com/agentsupport/user/dto/UserSummaryDto.java`
- Create: `backend/src/main/java/com/agentsupport/user/dto/ApproveRequest.java`

- [ ] **Step 1: RegisterRequest 작성**

```java
package com.agentsupport.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "아이디를 입력해주세요") @Size(max = 50) String id,
    @NotBlank(message = "비밀번호를 입력해주세요") @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다") String password,
    @NotBlank(message = "이름을 입력해주세요") String name,
    @NotBlank(message = "연락처를 입력해주세요") String phone,
    @NotBlank(message = "소속 GA를 입력해주세요") String gaName,
    @NotBlank(message = "이메일을 입력해주세요") @Email(message = "이메일 형식이 올바르지 않습니다") String email) {}
```

- [ ] **Step 2: UserSummaryDto 작성**

```java
package com.agentsupport.user.dto;

import com.agentsupport.user.entity.User;
import java.time.LocalDateTime;

public record UserSummaryDto(
    String id,
    String name,
    String phone,
    String gaName,
    String email,
    String role,
    String status,
    LocalDateTime createdAt) {

  public static UserSummaryDto from(User u) {
    return new UserSummaryDto(
        u.getId(), u.getName(), u.getPhone(), u.getGaName(),
        u.getEmail(), u.getRole(), u.getStatus(), u.getCreatedAt());
  }
}
```

- [ ] **Step 3: ApproveRequest 작성**

```java
package com.agentsupport.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ApproveRequest(
    @NotBlank
    @Pattern(regexp = "AGENT1|AGENT2|ADMIN", message = "유효하지 않은 역할입니다")
    String role) {}
```

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava --no-daemon -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/user/dto/
git commit -m "feat: add user DTOs (RegisterRequest, UserSummaryDto, ApproveRequest)"
```

---

## Task 4: UserService

**Files:**
- Create: `backend/src/main/java/com/agentsupport/user/service/UserService.java`

- [ ] **Step 1: UserService 작성**

```java
package com.agentsupport.user.service;

import com.agentsupport.user.dto.ApproveRequest;
import com.agentsupport.user.dto.RegisterRequest;
import com.agentsupport.user.dto.UserSummaryDto;
import com.agentsupport.user.entity.User;
import com.agentsupport.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  public void register(RegisterRequest req) {
    if (userRepository.existsById(req.id())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "ID_TAKEN");
    }
    String emailHash = sha256Hex(req.email().toLowerCase());
    if (userRepository.existsByEmailHash(emailHash)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "EMAIL_TAKEN");
    }
    User user = User.create(
        req.id(),
        passwordEncoder.encode(req.password()),
        req.name(),
        req.phone(),
        req.gaName(),
        req.email(),
        emailHash);
    userRepository.save(user);
  }

  public User findById(String id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  public List<UserSummaryDto> findAll(String status) {
    List<User> users = (status == null || status.isBlank())
        ? userRepository.findAll()
        : userRepository.findByStatus(status);
    return users.stream().map(UserSummaryDto::from).toList();
  }

  @Transactional
  public void approve(String id, ApproveRequest req) {
    User user = findById(id);
    user.approve(req.role());
  }

  @Transactional
  public void reject(String id) {
    User user = findById(id);
    user.reject();
  }

  @Transactional
  public void createAdminIfAbsent(String adminPassword) {
    if (!userRepository.existsById("admin")) {
      String emailHash = sha256Hex("admin@agentsupport.internal");
      User admin = User.createAdmin(
          "admin",
          passwordEncoder.encode(adminPassword),
          "관리자",
          "000-0000-0000",
          "system",
          "admin@agentsupport.internal",
          emailHash);
      userRepository.save(admin);
    }
  }

  public static String sha256Hex(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : hash) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava --no-daemon -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/user/service/UserService.java
git commit -m "feat: add UserService with register, approve, reject, email hash"
```

---

## Task 5: UserDetailsServiceImpl + AdminInitializer (MockUserDetailsService 교체)

**Files:**
- Create: `backend/src/main/java/com/agentsupport/auth/UserDetailsServiceImpl.java`
- Create: `backend/src/main/java/com/agentsupport/auth/AdminInitializer.java`
- Delete: `backend/src/main/java/com/agentsupport/auth/MockUserDetailsService.java`
- Modify: `backend/src/main/java/com/agentsupport/config/SecurityConfig.java`

- [ ] **Step 1: UserDetailsServiceImpl 작성**

```java
package com.agentsupport.auth;

import com.agentsupport.user.entity.User;
import com.agentsupport.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

  private final UserRepository userRepository;

  public UserDetailsServiceImpl(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user = userRepository.findById(username)
        .orElseThrow(() -> new UsernameNotFoundException(username));
    String role = user.getRole() != null ? user.getRole() : "PENDING";
    return org.springframework.security.core.userdetails.User.builder()
        .username(user.getId())
        .password(user.getPassword())
        .roles(role)
        .build();
  }
}
```

- [ ] **Step 2: AdminInitializer 작성**

```java
package com.agentsupport.auth;

import com.agentsupport.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminInitializer implements CommandLineRunner {

  private final UserService userService;

  @Value("${ADMIN_INITIAL_PASSWORD:Admin1234!}")
  private String adminPassword;

  public AdminInitializer(UserService userService) {
    this.userService = userService;
  }

  @Override
  public void run(String... args) {
    userService.createAdminIfAbsent(adminPassword);
  }
}
```

- [ ] **Step 3: MockUserDetailsService.java 삭제**

```bash
rm backend/src/main/java/com/agentsupport/auth/MockUserDetailsService.java
```

- [ ] **Step 4: SecurityConfig에 PasswordEncoder 빈 추가**

`MockUserDetailsService`에 있던 `PasswordEncoder` 빈을 `SecurityConfig`로 이동한다.
`SecurityConfig.java`에 다음을 추가:

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// filterChain, corsSource, authenticationManager 빈 옆에 추가:
@Bean
PasswordEncoder passwordEncoder() {
  return new BCryptPasswordEncoder();
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava --no-daemon -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/auth/UserDetailsServiceImpl.java
git add backend/src/main/java/com/agentsupport/auth/AdminInitializer.java
git add backend/src/main/java/com/agentsupport/config/SecurityConfig.java
git rm backend/src/main/java/com/agentsupport/auth/MockUserDetailsService.java
git commit -m "feat: replace MockUserDetailsService with DB-backed UserDetailsServiceImpl"
```

---

## Task 6: SecurityConfig — 경로 권한 설정

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/config/SecurityConfig.java`

- [ ] **Step 1: authorizeHttpRequests 수정**

`filterChain` 내 `authorizeHttpRequests` 블록을:

```java
auth.requestMatchers(
        "/api/auth/login", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
    .permitAll()
    .anyRequest()
    .authenticated()
```

다음으로 교체:

```java
auth.requestMatchers(
        "/api/auth/login", "/api/auth/register",
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
    .permitAll()
    .requestMatchers("/api/admin/**").hasRole("ADMIN")
    .anyRequest()
    .authenticated()
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava --no-daemon -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/config/SecurityConfig.java
git commit -m "feat: allow /api/auth/register public, restrict /api/admin/** to ADMIN"
```

---

## Task 7: AuthController 업데이트 + MeResponse 변경

**Files:**
- Modify: `backend/src/main/java/com/agentsupport/auth/dto/MeResponse.java`
- Modify: `backend/src/main/java/com/agentsupport/auth/AuthController.java`

- [ ] **Step 1: MeResponse에 role 추가**

```java
package com.agentsupport.auth.dto;

public record MeResponse(String id, String role) {}
```

- [ ] **Step 2: AuthController 수정**

`AuthController.java` 전체 내용을 다음으로 교체:

```java
package com.agentsupport.auth;

import com.agentsupport.auth.dto.LoginRequest;
import com.agentsupport.auth.dto.MeResponse;
import com.agentsupport.user.entity.User;
import com.agentsupport.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthenticationManager authenticationManager;
  private final UserService userService;
  private final HttpSessionSecurityContextRepository securityContextRepository =
      new HttpSessionSecurityContextRepository();

  public AuthController(AuthenticationManager authenticationManager, UserService userService) {
    this.authenticationManager = authenticationManager;
    this.userService = userService;
  }

  @Operation(summary = "로그인")
  @PostMapping("/login")
  public ResponseEntity<?> login(
      @Valid @RequestBody LoginRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    try {
      Authentication auth =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(request.username(), request.password()));

      User user = userService.findById(auth.getName());
      if ("PENDING".equals(user.getStatus())) {
        return ResponseEntity.status(401).body(Map.of("code", "PENDING_APPROVAL"));
      }
      if ("REJECTED".equals(user.getStatus())) {
        return ResponseEntity.status(401).body(Map.of("code", "ACCOUNT_REJECTED"));
      }

      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(auth);
      SecurityContextHolder.setContext(context);
      securityContextRepository.saveContext(context, httpRequest, httpResponse);
      return ResponseEntity.ok(new MeResponse(auth.getName(), user.getRole()));
    } catch (AuthenticationException e) {
      return ResponseEntity.status(401).build();
    }
  }

  @Operation(summary = "로그아웃")
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    var session = request.getSession(false);
    if (session != null) session.invalidate();
    SecurityContextHolder.clearContext();
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "현재 세션 사용자 확인")
  @GetMapping("/me")
  public ResponseEntity<MeResponse> me(Authentication auth) {
    if (auth == null || !auth.isAuthenticated()) {
      return ResponseEntity.status(401).build();
    }
    User user = userService.findById(auth.getName());
    return ResponseEntity.ok(new MeResponse(auth.getName(), user.getRole()));
  }
}
```

- [ ] **Step 3: RegisterController 추가 (AuthController와 별도 클래스)**

```java
package com.agentsupport.auth;

import com.agentsupport.user.dto.RegisterRequest;
import com.agentsupport.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
public class RegisterController {

  private final UserService userService;

  public RegisterController(UserService userService) {
    this.userService = userService;
  }

  @Operation(summary = "회원 가입 신청")
  @PostMapping("/register")
  public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
    userService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }
}
```

파일 경로: `backend/src/main/java/com/agentsupport/auth/RegisterController.java`

- [ ] **Step 4: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava --no-daemon -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/auth/
git commit -m "feat: update AuthController with status check and role, add RegisterController"
```

---

## Task 8: AdminUserController

**Files:**
- Create: `backend/src/main/java/com/agentsupport/admin/AdminUserController.java`

- [ ] **Step 1: AdminUserController 작성**

```java
package com.agentsupport.admin;

import com.agentsupport.user.dto.ApproveRequest;
import com.agentsupport.user.dto.UserSummaryDto;
import com.agentsupport.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin")
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

  private final UserService userService;

  public AdminUserController(UserService userService) {
    this.userService = userService;
  }

  @Operation(summary = "사용자 목록 조회")
  @GetMapping
  public ResponseEntity<List<UserSummaryDto>> list(
      @RequestParam(required = false) String status) {
    return ResponseEntity.ok(userService.findAll(status));
  }

  @Operation(summary = "가입 승인 및 역할 지정")
  @PatchMapping("/{id}/approve")
  public ResponseEntity<Void> approve(
      @PathVariable String id,
      @Valid @RequestBody ApproveRequest request) {
    userService.approve(id, request);
    return ResponseEntity.ok().build();
  }

  @Operation(summary = "가입 거절")
  @PatchMapping("/{id}/reject")
  public ResponseEntity<Void> reject(@PathVariable String id) {
    userService.reject(id);
    return ResponseEntity.ok().build();
  }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `cd backend && ./gradlew compileJava --no-daemon -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add backend/src/main/java/com/agentsupport/admin/AdminUserController.java
git commit -m "feat: add AdminUserController for user approval"
```

---

## Task 9: 테스트 업데이트 + 신규 테스트

**Files:**
- Modify: `backend/src/test/java/com/agentsupport/auth/AuthControllerTest.java`
- Create: `backend/src/test/java/com/agentsupport/user/RegisterControllerTest.java`
- Create: `backend/src/test/java/com/agentsupport/admin/AdminUserControllerTest.java`

- [ ] **Step 1: AuthControllerTest 업데이트**

기존 테스트를 모두 교체한다. `agent01/password` → `admin/Admin1234!`, `$.username` → `$.id`.

```java
package com.agentsupport.auth;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AuthControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
  }

  @Test
  void login_success_returns200_with_id() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin\",\"password\":\"Admin1234!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("admin"))
        .andExpect(jsonPath("$.role").value("ADMIN"));
  }

  @Test
  void login_wrong_password_returns401() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void login_blank_fields_returns400() throws Exception {
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"\",\"password\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void me_without_session_returns401() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void me_with_valid_session_returns_id() throws Exception {
    MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin\",\"password\":\"Admin1234!\"}"))
        .andReturn();

    MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

    mockMvc.perform(get("/api/auth/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("admin"));
  }
}
```

- [ ] **Step 2: 기존 테스트가 통과하는지 확인**

Run: `cd backend && ./gradlew test --tests "com.agentsupport.auth.AuthControllerTest" --no-daemon`
Expected: 5 tests passed

- [ ] **Step 3: RegisterControllerTest 작성**

```java
package com.agentsupport.user;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RegisterControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
  }

  private static final String VALID_REGISTER = """
      {
        "id": "testuser01",
        "password": "Test1234!",
        "name": "홍길동",
        "phone": "010-1234-5678",
        "gaName": "테스트GA",
        "email": "test@example.com"
      }
      """;

  @Test
  void register_with_valid_data_returns201() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_REGISTER))
        .andExpect(status().isCreated());
  }

  @Test
  void register_duplicate_id_returns409() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_REGISTER))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_REGISTER))
        .andExpect(status().isConflict());
  }

  @Test
  void register_duplicate_email_returns409() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_REGISTER))
        .andExpect(status().isCreated());

    String differentId = VALID_REGISTER.replace("testuser01", "testuser02");
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(differentId))
        .andExpect(status().isConflict());
  }

  @Test
  void login_with_pending_user_returns401_with_code() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(VALID_REGISTER))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"testuser01\",\"password\":\"Test1234!\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void register_blank_fields_returns400() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"\",\"password\":\"\",\"name\":\"\",\"phone\":\"\",\"gaName\":\"\",\"email\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
```

- [ ] **Step 4: RegisterControllerTest 실행**

Run: `cd backend && ./gradlew test --tests "com.agentsupport.user.RegisterControllerTest" --no-daemon`
Expected: 5 tests passed

- [ ] **Step 5: AdminUserControllerTest 작성**

```java
package com.agentsupport.admin;

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
class AdminUserControllerTest {

  @Autowired WebApplicationContext webApplicationContext;
  MockMvc mockMvc;
  MockHttpSession adminSession;

  @BeforeEach
  void setUp() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();

    MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"admin\",\"password\":\"Admin1234!\"}"))
        .andReturn();
    adminSession = (MockHttpSession) loginResult.getRequest().getSession(false);
  }

  @Test
  void list_users_as_admin_returns200() throws Exception {
    mockMvc.perform(get("/api/admin/users").session(adminSession))
        .andExpect(status().isOk());
  }

  @Test
  void list_users_without_auth_returns401() throws Exception {
    mockMvc.perform(get("/api/admin/users"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void approve_pending_user_returns200() throws Exception {
    // 가입 신청
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"newagent\",\"password\":\"Test1234!\",\"name\":\"김설계\","
                + "\"phone\":\"010-9999-8888\",\"gaName\":\"테스트GA\",\"email\":\"agent@test.com\"}"))
        .andExpect(status().isCreated());

    // 승인
    mockMvc.perform(patch("/api/admin/users/newagent/approve")
            .session(adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"role\":\"AGENT1\"}"))
        .andExpect(status().isOk());

    // 로그인 성공 확인
    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"newagent\",\"password\":\"Test1234!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("AGENT1"));
  }

  @Test
  void reject_pending_user_returns200() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":\"rejectme\",\"password\":\"Test1234!\",\"name\":\"이거절\","
                + "\"phone\":\"010-1111-2222\",\"gaName\":\"GA\",\"email\":\"reject@test.com\"}"))
        .andExpect(status().isCreated());

    mockMvc.perform(patch("/api/admin/users/rejectme/reject")
            .session(adminSession))
        .andExpect(status().isOk());
  }
}
```

- [ ] **Step 6: AdminUserControllerTest 실행**

Run: `cd backend && ./gradlew test --tests "com.agentsupport.admin.AdminUserControllerTest" --no-daemon`
Expected: 4 tests passed

- [ ] **Step 7: 전체 테스트 실행**

Run: `cd backend && ./gradlew test --no-daemon`
Expected: 모든 테스트 GREEN

- [ ] **Step 8: 커밋**

```bash
git add backend/src/test/
git commit -m "test: update AuthControllerTest, add RegisterControllerTest and AdminUserControllerTest"
```

---

## Task 10: 프론트엔드 — 타입 + API + 쿼리 업데이트

**Files:**
- Modify: `frontend/src/features/auth/api.ts`
- Modify: `frontend/src/features/auth/queries.ts`
- Create: `frontend/src/features/admin/api.ts`
- Create: `frontend/src/features/admin/queries.ts`

- [ ] **Step 1: auth/api.ts 업데이트**

```typescript
import { apiFetch } from "@/lib/api/csrf";

export interface LoginRequest {
  username: string;
  password: string;
}

export interface MeResponse {
  id: string;
  role: string;
}

export interface RegisterRequest {
  id: string;
  password: string;
  name: string;
  phone: string;
  gaName: string;
  email: string;
}

export function login(body: LoginRequest): Promise<MeResponse> {
  return apiFetch<MeResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function logout(): Promise<void> {
  return apiFetch<void>("/api/auth/logout", { method: "POST" });
}

export function me(): Promise<MeResponse> {
  return apiFetch<MeResponse>("/api/auth/me");
}

export function register(body: RegisterRequest): Promise<void> {
  return apiFetch<void>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
```

- [ ] **Step 2: auth/queries.ts 업데이트 (useRegisterMutation 추가)**

```typescript
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { login, logout, me, register, type LoginRequest, type RegisterRequest } from "./api";

export function useMe() {
  return useQuery({
    queryKey: ["me"],
    queryFn: me,
    retry: false,
  });
}

export function useLoginMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: LoginRequest) => login(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["me"] });
    },
  });
}

export function useLogoutMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ["me"] });
    },
  });
}

export function useRegisterMutation() {
  return useMutation({
    mutationFn: (body: RegisterRequest) => register(body),
  });
}
```

- [ ] **Step 3: features/admin/api.ts 생성**

```typescript
import { apiFetch } from "@/lib/api/csrf";

export interface UserSummary {
  id: string;
  name: string;
  phone: string;
  gaName: string;
  email: string;
  role: string | null;
  status: string;
  createdAt: string;
}

export function listUsers(status?: string): Promise<UserSummary[]> {
  const qs = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiFetch<UserSummary[]>(`/api/admin/users${qs}`);
}

export function approveUser(id: string, role: string): Promise<void> {
  return apiFetch<void>(`/api/admin/users/${encodeURIComponent(id)}/approve`, {
    method: "PATCH",
    body: JSON.stringify({ role }),
  });
}

export function rejectUser(id: string): Promise<void> {
  return apiFetch<void>(`/api/admin/users/${encodeURIComponent(id)}/reject`, {
    method: "PATCH",
  });
}
```

- [ ] **Step 4: features/admin/queries.ts 생성**

```typescript
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { approveUser, listUsers, rejectUser } from "./api";

export function useUsers(status?: string) {
  return useQuery({
    queryKey: ["admin", "users", status],
    queryFn: () => listUsers(status),
  });
}

export function useApproveMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, role }: { id: string; role: string }) => approveUser(id, role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
  });
}

export function useRejectMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => rejectUser(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
  });
}
```

- [ ] **Step 5: 타입 확인**

Run: `cd frontend && pnpm typecheck`
Expected: 에러 없음 (MeResponse.username → MeResponse.id 참조 오류가 있으면 다음 태스크에서 수정)

- [ ] **Step 6: 커밋**

```bash
git add frontend/src/features/
git commit -m "feat: update auth types with role, add register and admin API"
```

---

## Task 11: 로그인 페이지 수정 (PENDING 처리 + 회원가입 링크)

**Files:**
- Modify: `frontend/src/app/login/page.tsx`

- [ ] **Step 1: onError에 PENDING_APPROVAL 처리 추가, 회원가입 링크 연결**

`login/page.tsx`에서 `onError` 블록을:

```typescript
onError: (err) => {
  if (err instanceof ApiError && err.status === 401) {
    setServerError("아이디 또는 비밀번호가 일치하지 않습니다");
  } else {
    setServerError("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요");
  }
},
```

다음으로 교체:

```typescript
onError: (err) => {
  if (err instanceof ApiError && err.status === 401) {
    const code = (err.body as { code?: string } | null)?.code;
    if (code === "PENDING_APPROVAL") {
      router.push("/pending");
      return;
    }
    if (code === "ACCOUNT_REJECTED") {
      setServerError("가입이 거절된 계정입니다. 관리자에게 문의해주세요");
      return;
    }
    setServerError("아이디 또는 비밀번호가 일치하지 않습니다");
  } else {
    setServerError("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요");
  }
},
```

그리고 하단 `<a href="#">회원가입</a>`을:

```tsx
<Link href="/register" className="hover:text-primary transition-colors">회원가입</Link>
```

으로 교체하고 파일 상단에 `import Link from "next/link";` 추가.

- [ ] **Step 2: 타입 확인**

Run: `cd frontend && pnpm typecheck`
Expected: 에러 없음

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/app/login/page.tsx
git commit -m "feat: handle PENDING_APPROVAL/ACCOUNT_REJECTED on login, wire register link"
```

---

## Task 12: /register 페이지

**Files:**
- Create: `frontend/src/app/register/page.tsx`

- [ ] **Step 1: 가입 신청 폼 페이지 작성**

```tsx
"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useRegisterMutation } from "@/features/auth/queries";
import { ApiError } from "@/lib/api/csrf";

const schema = z.object({
  id: z.string().min(1, "아이디를 입력해주세요").max(50, "아이디는 50자 이하여야 합니다"),
  password: z.string().min(8, "비밀번호는 8자 이상이어야 합니다"),
  name: z.string().min(1, "이름을 입력해주세요"),
  phone: z.string().min(1, "연락처를 입력해주세요"),
  gaName: z.string().min(1, "소속 GA를 입력해주세요"),
  email: z.string().min(1, "이메일을 입력해주세요").email("이메일 형식이 올바르지 않습니다"),
});

type RegisterForm = z.infer<typeof schema>;

const INPUT_CLASS =
  "w-full h-12 px-4 bg-surface-container-lowest border border-outline-variant rounded-lg " +
  "text-base text-on-surface placeholder:text-on-surface-variant/50 " +
  "outline-none focus:border-primary-container transition-colors";

export default function RegisterPage() {
  const router = useRouter();
  const [serverError, setServerError] = useState<string | null>(null);
  const registerMutation = useRegisterMutation();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterForm>({ resolver: zodResolver(schema) });

  function onSubmit(data: RegisterForm) {
    setServerError(null);
    registerMutation.mutate(data, {
      onSuccess: () => router.push("/pending"),
      onError: (err) => {
        if (err instanceof ApiError && err.status === 409) {
          const code = (err.body as { message?: string } | null)?.message;
          if (code === "ID_TAKEN") {
            setServerError("이미 사용 중인 아이디입니다");
          } else if (code === "EMAIL_TAKEN") {
            setServerError("이미 등록된 이메일입니다");
          } else {
            setServerError("이미 사용 중인 아이디 또는 이메일입니다");
          }
        } else {
          setServerError("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요");
        }
      },
    });
  }

  const fields: {
    name: keyof RegisterForm;
    label: string;
    type: string;
    placeholder: string;
    autoComplete?: string;
  }[] = [
    { name: "id", label: "아이디", type: "text", placeholder: "아이디를 입력해주세요", autoComplete: "username" },
    { name: "password", label: "비밀번호", type: "password", placeholder: "8자 이상", autoComplete: "new-password" },
    { name: "name", label: "이름", type: "text", placeholder: "이름을 입력해주세요" },
    { name: "phone", label: "연락처", type: "tel", placeholder: "010-0000-0000" },
    { name: "gaName", label: "소속 GA", type: "text", placeholder: "소속 GA명을 입력해주세요" },
    { name: "email", label: "이메일", type: "email", placeholder: "example@email.com", autoComplete: "email" },
  ];

  return (
    <main className="min-h-screen flex items-center justify-center bg-surface px-6 py-16">
      <div className="w-full max-w-[480px] flex flex-col items-center">
        <div className="mb-10 text-center">
          <span className="block text-2xl font-bold text-primary tracking-tight">AgentSupport</span>
          <span className="block text-sm font-semibold text-on-surface-variant tracking-widest mt-2">
            가입 신청
          </span>
        </div>

        <div className="w-full bg-surface-container-lowest rounded-2xl p-10 shadow-card">
          {serverError && (
            <div className="mb-6 px-4 py-3 rounded-xl bg-status-error-container text-sm text-status-error">
              {serverError}
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-5">
            {fields.map((f) => (
              <div key={f.name} className="flex flex-col gap-1.5">
                <label className="text-sm font-semibold text-on-surface-variant px-1">
                  {f.label}
                </label>
                <input
                  type={f.type}
                  placeholder={f.placeholder}
                  autoComplete={f.autoComplete}
                  className={[INPUT_CLASS, errors[f.name] ? "border-status-error" : ""].join(" ")}
                  {...register(f.name)}
                />
                {errors[f.name] && (
                  <p className="text-xs text-status-error px-1">{errors[f.name]?.message}</p>
                )}
              </div>
            ))}

            <button
              type="submit"
              disabled={registerMutation.isPending}
              className="mt-2 w-full h-12 bg-primary-container text-on-primary text-sm font-semibold rounded-xl hover:opacity-90 active:scale-[0.99] transition-all disabled:opacity-50"
            >
              {registerMutation.isPending ? "처리 중…" : "가입 신청"}
            </button>
          </form>
        </div>

        <footer className="mt-6 text-sm text-on-surface-variant">
          이미 계정이 있으신가요?{" "}
          <Link href="/login" className="text-primary hover:underline font-medium">
            로그인
          </Link>
        </footer>
      </div>
    </main>
  );
}
```

- [ ] **Step 2: 타입 확인**

Run: `cd frontend && pnpm typecheck`
Expected: 에러 없음

- [ ] **Step 3: 커밋**

```bash
git add frontend/src/app/register/
git commit -m "feat: add /register page with signup form"
```

---

## Task 13: /pending 페이지

**Files:**
- Create: `frontend/src/app/pending/page.tsx`

- [ ] **Step 1: 승인 대기 안내 페이지 작성**

```tsx
import Link from "next/link";
import { Clock } from "lucide-react";

export default function PendingPage() {
  return (
    <main className="min-h-screen flex items-center justify-center bg-surface px-6">
      <div className="max-w-md w-full text-center flex flex-col items-center gap-6">
        <div className="w-20 h-20 rounded-full bg-primary-container/20 flex items-center justify-center">
          <Clock size={40} className="text-primary-container" />
        </div>

        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-bold text-on-surface">승인 대기 중입니다</h1>
          <p className="text-on-surface-variant leading-relaxed">
            가입 신청이 완료되었습니다.
            <br />
            관리자 검토 후 승인되면 로그인하실 수 있습니다.
          </p>
        </div>

        <Link
          href="/login"
          className="text-sm text-primary hover:underline font-medium"
        >
          로그인 페이지로 돌아가기
        </Link>
      </div>
    </main>
  );
}
```

- [ ] **Step 2: 커밋**

```bash
git add frontend/src/app/pending/
git commit -m "feat: add /pending page for awaiting approval"
```

---

## Task 14: AuthGuard — role 기반 접근 제어

**Files:**
- Modify: `frontend/src/components/layout/auth-guard.tsx`
- Modify: `frontend/src/app/dashboard/layout.tsx`
- Modify: `frontend/src/app/proposals/layout.tsx`
- Modify: `frontend/src/app/underwriting/layout.tsx`
- Modify: `frontend/src/app/claims/layout.tsx`

- [ ] **Step 1: AuthGuard에 allowedRoles prop 추가**

```tsx
"use client";

import { useMe } from "@/features/auth/queries";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { AppShell } from "./app-shell";

interface AuthGuardProps {
  children: React.ReactNode;
  allowedRoles?: string[];
}

export function AuthGuard({ children, allowedRoles }: AuthGuardProps) {
  const { data: user, isLoading } = useMe();
  const router = useRouter();

  useEffect(() => {
    if (isLoading) return;
    if (!user) {
      router.replace("/login");
      return;
    }
    if (allowedRoles && !allowedRoles.includes(user.role)) {
      router.replace("/dashboard");
    }
  }, [user, isLoading, router, allowedRoles]);

  if (isLoading) return null;
  if (!user) return null;
  if (allowedRoles && !allowedRoles.includes(user.role)) return null;

  return <AppShell userId={user.id} role={user.role}>{children}</AppShell>;
}
```

- [ ] **Step 2: dashboard layout — 모든 인증 유저 허용**

```tsx
import { AuthGuard } from "@/components/layout/auth-guard";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard>{children}</AuthGuard>;
}
```

- [ ] **Step 3: proposals layout — ADMIN, AGENT1 허용**

```tsx
import { AuthGuard } from "@/components/layout/auth-guard";

export default function ProposalsLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard allowedRoles={["ADMIN", "AGENT1"]}>{children}</AuthGuard>;
}
```

- [ ] **Step 4: underwriting layout — ADMIN, AGENT1 허용**

```tsx
import { AuthGuard } from "@/components/layout/auth-guard";

export default function UnderwritingLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard allowedRoles={["ADMIN", "AGENT1"]}>{children}</AuthGuard>;
}
```

- [ ] **Step 5: claims layout — ADMIN, AGENT1, AGENT2 허용**

```tsx
import { AuthGuard } from "@/components/layout/auth-guard";

export default function ClaimsLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard allowedRoles={["ADMIN", "AGENT1", "AGENT2"]}>{children}</AuthGuard>;
}
```

- [ ] **Step 6: 타입 확인**

Run: `cd frontend && pnpm typecheck`
Expected: AppShell props 오류 있을 수 있음 — 다음 태스크에서 수정

- [ ] **Step 7: 커밋**

```bash
git add frontend/src/components/layout/auth-guard.tsx
git add frontend/src/app/dashboard/layout.tsx
git add frontend/src/app/proposals/layout.tsx
git add frontend/src/app/underwriting/layout.tsx
git add frontend/src/app/claims/layout.tsx
git commit -m "feat: add role-based route guard to AuthGuard and layouts"
```

---

## Task 15: AppShell + Sidebar — role 기반 메뉴 필터링

**Files:**
- Modify: `frontend/src/components/layout/app-shell.tsx`
- Modify: `frontend/src/components/layout/sidebar.tsx`

- [ ] **Step 1: AppShell — username → userId, role 추가**

```tsx
"use client";

import { useState } from "react";
import { Sidebar } from "./sidebar";
import { TopBar } from "./top-bar";

interface AppShellProps {
  userId: string;
  role: string;
  children: React.ReactNode;
}

export function AppShell({ userId, role, children }: AppShellProps) {
  const [desktopCollapsed, setDesktopCollapsed] = useState(false);
  const [mobileDrawerOpen, setMobileDrawerOpen] = useState(false);

  function handleMenuClick() {
    if (typeof window !== "undefined" && window.innerWidth < 1024) {
      setMobileDrawerOpen((o) => !o);
    } else {
      setDesktopCollapsed((c) => !c);
    }
  }

  return (
    <div className="flex h-screen overflow-hidden">
      {mobileDrawerOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={() => setMobileDrawerOpen(false)}
        />
      )}

      <Sidebar
        role={role}
        desktopCollapsed={desktopCollapsed}
        mobileDrawerOpen={mobileDrawerOpen}
        onDesktopToggle={() => setDesktopCollapsed((c) => !c)}
        onMobileClose={() => setMobileDrawerOpen(false)}
      />

      <div className="flex flex-col flex-1 overflow-hidden min-w-0">
        <TopBar username={userId} onMenuClick={handleMenuClick} />
        <main className="flex-1 overflow-auto bg-surface">{children}</main>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Sidebar — role prop + 메뉴 필터링**

`NAV_ITEMS` 위에 역할별 허용 메뉴 맵을 추가하고, `Sidebar`에 `role` prop을 추가:

```tsx
"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  FileSearch,
  LayoutDashboard,
  Receipt,
  Settings,
  X,
} from "lucide-react";

interface NavItem {
  label: string;
  href: string;
  icon: React.ComponentType<{ size?: number; className?: string }>;
  disabled?: boolean;
  roles: string[];
}

const NAV_ITEMS: NavItem[] = [
  { label: "대시보드", href: "/dashboard", icon: LayoutDashboard, roles: ["ADMIN", "AGENT1", "AGENT2"] },
  { label: "설계 관리", href: "/proposals", icon: ClipboardList, roles: ["ADMIN", "AGENT1"] },
  { label: "심사 현황", href: "/underwriting", icon: FileSearch, roles: ["ADMIN", "AGENT1"] },
  { label: "청구 관리", href: "/claims", icon: Receipt, roles: ["ADMIN", "AGENT1", "AGENT2"] },
  { label: "가입 관리", href: "/admin/users", icon: Settings, roles: ["ADMIN"] },
];

interface SidebarProps {
  role: string;
  desktopCollapsed: boolean;
  mobileDrawerOpen: boolean;
  onDesktopToggle: () => void;
  onMobileClose: () => void;
}

export function Sidebar({
  role,
  desktopCollapsed,
  mobileDrawerOpen,
  onDesktopToggle,
  onMobileClose,
}: SidebarProps) {
  const pathname = usePathname();
  const visibleItems = NAV_ITEMS.filter((item) => item.roles.includes(role));

  return (
    <aside
      className={[
        "flex flex-col bg-primary-container overflow-hidden",
        "fixed inset-y-0 left-0 z-50 w-[256px]",
        "transition-transform duration-200",
        mobileDrawerOpen ? "translate-x-0" : "-translate-x-full",
        "lg:static lg:z-auto lg:translate-x-0",
        "lg:transition-[width] lg:duration-200",
        desktopCollapsed ? "lg:w-[80px]" : "lg:w-[256px]",
      ].join(" ")}
    >
      <div className="flex items-center h-16 px-4 border-b border-white/10 gap-2 shrink-0">
        <button
          type="button"
          onClick={onMobileClose}
          className="p-2 rounded-lg text-on-primary hover:bg-white/10 transition-colors shrink-0 lg:hidden"
          aria-label="메뉴 닫기"
        >
          <X size={20} />
        </button>

        {(!desktopCollapsed || mobileDrawerOpen) && (
          <span className="flex-1 text-on-primary font-bold text-base tracking-tight truncate hidden lg:block">
            AgentSupport
          </span>
        )}
        <span className="flex-1 text-on-primary font-bold text-base tracking-tight truncate lg:hidden">
          AgentSupport
        </span>

        <button
          type="button"
          onClick={onDesktopToggle}
          className={[
            "p-2 rounded-lg text-on-primary hover:bg-white/10 transition-colors shrink-0 hidden lg:flex",
            desktopCollapsed && "mx-auto",
          ].join(" ")}
          aria-label={desktopCollapsed ? "사이드바 펼치기" : "사이드바 접기"}
        >
          {desktopCollapsed ? <ChevronRight size={20} /> : <ChevronLeft size={20} />}
        </button>
      </div>

      <nav className="flex flex-col gap-1 p-2 flex-1">
        {visibleItems.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;
          const collapsed = desktopCollapsed && !mobileDrawerOpen;
          const base = [
            "flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors",
            collapsed && "lg:justify-center",
          ].join(" ");

          if (item.disabled) {
            return (
              <div
                key={item.href}
                className={`${base} opacity-40 cursor-not-allowed`}
                title={collapsed ? `${item.label} (준비 중)` : undefined}
              >
                <Icon size={20} className="text-on-primary shrink-0" />
                {!collapsed && (
                  <span className="text-sm text-on-primary truncate">
                    {item.label}
                    <span className="ml-1 text-xs opacity-70">준비 중</span>
                  </span>
                )}
              </div>
            );
          }

          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onMobileClose}
              className={[
                base,
                isActive
                  ? "bg-white/20 text-on-primary"
                  : "text-on-primary hover:bg-white/10",
              ].join(" ")}
              title={collapsed ? item.label : undefined}
            >
              <Icon size={20} className="shrink-0" />
              {!collapsed && (
                <span className="text-sm font-medium truncate">{item.label}</span>
              )}
            </Link>
          );
        })}
      </nav>
    </aside>
  );
}
```

- [ ] **Step 3: 타입 확인**

Run: `cd frontend && pnpm typecheck`
Expected: 에러 없음

- [ ] **Step 4: 커밋**

```bash
git add frontend/src/components/layout/
git commit -m "feat: role-based sidebar menu filtering, AppShell accepts role prop"
```

---

## Task 16: /admin/users 페이지

**Files:**
- Create: `frontend/src/app/admin/layout.tsx`
- Create: `frontend/src/app/admin/users/page.tsx`

- [ ] **Step 1: admin layout — ADMIN 전용 AuthGuard**

```tsx
import { AuthGuard } from "@/components/layout/auth-guard";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard allowedRoles={["ADMIN"]}>{children}</AuthGuard>;
}
```

- [ ] **Step 2: /admin/users 페이지 작성**

```tsx
"use client";

import { useState } from "react";
import { useUsers, useApproveMutation, useRejectMutation } from "@/features/admin/queries";

const STATUS_LABELS: Record<string, string> = {
  PENDING: "대기",
  ACTIVE: "승인",
  REJECTED: "거절",
};

const ROLE_LABELS: Record<string, string> = {
  ADMIN: "관리자",
  AGENT1: "설계사1",
  AGENT2: "설계사2",
};

export default function AdminUsersPage() {
  const [statusFilter, setStatusFilter] = useState("PENDING");
  const { data: users = [], isLoading } = useUsers(statusFilter);
  const approveMutation = useApproveMutation();
  const rejectMutation = useRejectMutation();
  const [selectedRole, setSelectedRole] = useState<Record<string, string>>({});

  return (
    <div className="p-6 max-w-5xl mx-auto">
      <h1 className="text-xl font-bold text-on-surface mb-6">가입 신청 관리</h1>

      <div className="flex gap-2 mb-6">
        {["PENDING", "ACTIVE", "REJECTED"].map((s) => (
          <button
            key={s}
            onClick={() => setStatusFilter(s)}
            className={[
              "px-4 py-2 rounded-lg text-sm font-medium transition-colors",
              statusFilter === s
                ? "bg-primary-container text-on-primary"
                : "bg-surface-container text-on-surface-variant hover:bg-surface-container-high",
            ].join(" ")}
          >
            {STATUS_LABELS[s]}
          </button>
        ))}
      </div>

      {isLoading && <p className="text-on-surface-variant">로딩 중…</p>}

      {!isLoading && users.length === 0 && (
        <p className="text-on-surface-variant">해당 상태의 사용자가 없습니다.</p>
      )}

      <div className="flex flex-col gap-3">
        {users.map((user) => (
          <div
            key={user.id}
            className="bg-surface-container-lowest rounded-xl p-5 flex flex-col sm:flex-row sm:items-center gap-4 shadow-sm"
          >
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1">
                <span className="font-semibold text-on-surface">{user.name}</span>
                <span className="text-xs text-on-surface-variant">({user.id})</span>
              </div>
              <div className="text-sm text-on-surface-variant flex flex-wrap gap-x-4 gap-y-1">
                <span>{user.phone}</span>
                <span>{user.email}</span>
                <span>{user.gaName}</span>
                {user.role && (
                  <span className="font-medium text-primary">{ROLE_LABELS[user.role] ?? user.role}</span>
                )}
              </div>
            </div>

            {user.status === "PENDING" && (
              <div className="flex items-center gap-2 shrink-0">
                <select
                  value={selectedRole[user.id] ?? "AGENT1"}
                  onChange={(e) =>
                    setSelectedRole((prev) => ({ ...prev, [user.id]: e.target.value }))
                  }
                  className="h-9 px-3 rounded-lg border border-outline-variant bg-surface text-sm text-on-surface outline-none"
                >
                  <option value="AGENT1">설계사1</option>
                  <option value="AGENT2">설계사2</option>
                  <option value="ADMIN">관리자</option>
                </select>
                <button
                  onClick={() =>
                    approveMutation.mutate({ id: user.id, role: selectedRole[user.id] ?? "AGENT1" })
                  }
                  disabled={approveMutation.isPending}
                  className="h-9 px-4 rounded-lg bg-primary-container text-on-primary text-sm font-medium hover:opacity-90 disabled:opacity-50"
                >
                  승인
                </button>
                <button
                  onClick={() => rejectMutation.mutate(user.id)}
                  disabled={rejectMutation.isPending}
                  className="h-9 px-4 rounded-lg bg-status-error-container text-status-error text-sm font-medium hover:opacity-90 disabled:opacity-50"
                >
                  거절
                </button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: 타입 확인**

Run: `cd frontend && pnpm typecheck`
Expected: 에러 없음

- [ ] **Step 4: 빌드 확인**

Run: `cd frontend && pnpm build`
Expected: 빌드 성공

- [ ] **Step 5: 커밋**

```bash
git add frontend/src/app/admin/
git commit -m "feat: add /admin/users page for approval management"
```

---

## Task 17: 최종 통합 검증

- [ ] **Step 1: 백엔드 전체 테스트**

Run: `cd backend && ./gradlew test --no-daemon`
Expected: 모든 테스트 GREEN

- [ ] **Step 2: 프론트엔드 타입 + 빌드**

Run: `cd frontend && pnpm typecheck && pnpm build`
Expected: 에러 없음

- [ ] **Step 3: 로컬 동작 확인**

1. `cd backend && ./gradlew bootRun` 실행
2. `cd frontend && pnpm dev` 실행
3. `http://localhost:3000/register` 접속 → 가입 신청 → `/pending` 이동 확인
4. `http://localhost:3000/login` 에서 `admin/Admin1234!` 로그인 → 사이드바에 "가입 관리" 메뉴 확인
5. `/admin/users` 에서 신청자 승인(AGENT1) → 해당 계정으로 로그인 → 대시보드/설계/심사/청구 접근 가능 확인
6. AGENT2 승인 후 로그인 → 청구만 접근 가능 확인

- [ ] **Step 4: 최종 커밋 + 푸시**

```bash
git push origin master
```
