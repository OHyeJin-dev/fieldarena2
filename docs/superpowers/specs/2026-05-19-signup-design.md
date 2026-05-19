# 회원가입 & 역할 기반 접근 제어 설계

## 개요

보험 설계사(AGENT1/AGENT2)가 가입 신청을 하면, 관리자(ADMIN)가 승인 후 역할을 지정한다.
승인 전까지 로그인 불가. 역할에 따라 접근 가능한 메뉴가 제한된다.

---

## 역할 정의

| 역할 | 접근 메뉴 |
| --- | --- |
| `ADMIN` | 전체 + `/admin/users` |
| `AGENT1` | 대시보드, 설계(`/proposals`), 심사(`/underwriting`), 청구(`/claims`) |
| `AGENT2` | 대시보드, 청구(`/claims`) |

---

## 데이터 구조

### Flyway V10: `users` 테이블

```sql
CREATE TABLE users (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username   VARCHAR(50)  NOT NULL UNIQUE,
  password   VARCHAR(255) NOT NULL,
  name       VARCHAR(50)  NOT NULL,
  phone      VARCHAR(20)  NOT NULL,
  ga_name    VARCHAR(100) NOT NULL,
  email      VARCHAR(255) NOT NULL UNIQUE,
  role       VARCHAR(20),
  status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

- `role`: `ADMIN` / `AGENT1` / `AGENT2` — 승인 시 관리자가 지정, PENDING 상태에서는 NULL
- `status`: `PENDING` / `ACTIVE` / `REJECTED`

### Flyway V11: 초기 ADMIN seed

```sql
INSERT INTO users (username, password, name, phone, ga_name, email, role, status)
VALUES ('admin', '{bcrypt_hash}', '관리자', '000-0000-0000', '-', 'admin@example.com', 'ADMIN', 'ACTIVE');
```

실제 해시값은 BCrypt로 생성해서 넣는다.

---

## 백엔드

### 교체: MockUserDetailsService → DB 기반

`MockUserDetailsService` 삭제. `User` 엔티티 + `UserRepository` + `UserDetailsService` 구현체로 교체.
Spring Security가 `users` 테이블에서 사용자를 조회해 인증한다.

### 신규 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/register` | 없음 | 가입 신청 |
| `GET` | `/api/admin/users` | ADMIN | 사용자 목록 (status 필터) |
| `PATCH` | `/api/admin/users/{id}/approve` | ADMIN | 승인 + 역할 지정 |
| `PATCH` | `/api/admin/users/{id}/reject` | ADMIN | 거절 |

### 로그인 status 체크

`AuthController.login()`에서 인증 성공 후 status 확인:

- `PENDING` → 401 + `{"code": "PENDING_APPROVAL"}`
- `REJECTED` → 401 + `{"code": "ACCOUNT_REJECTED"}`
- `ACTIVE` → 세션 발급

### MeResponse 변경

```json
{ "username": "agent01", "role": "AGENT1" }
```

`role` 필드 추가. 프론트엔드 메뉴 제어에 사용.

### 패키지 구조

```
com.agentsupport.user/
  entity/User.java
  repository/UserRepository.java
  service/UserService.java
  dto/RegisterRequest.java
  dto/UserSummaryDto.java
  dto/ApproveRequest.java
com.agentsupport.admin/
  AdminUserController.java
```

---

## 프론트엔드

### 신규 페이지

| 경로 | 설명 | 접근 |
| --- | --- | --- |
| `/register` | 가입 신청 폼 | 비로그인 |
| `/pending` | 승인 대기 안내 | 로그인(PENDING) |
| `/admin/users` | 가입 신청 관리 | ADMIN |

### 가입 폼 필드

아이디 / 비밀번호 / 이름 / 연락처 / 소속 GA / 이메일

### AuthGuard 변경

현재 로그인 여부만 확인 → role + status도 함께 확인:

1. 미로그인 → `/login`
2. 로그인 + PENDING → `/pending`
3. 로그인 + 권한 없는 페이지 → `/dashboard`
4. 로그인 + 권한 있음 → 정상 렌더링

### 사이드바 메뉴 필터링

`AppShell`에서 role에 따라 메뉴 항목 조건부 렌더링.

### useMe 훅 변경

`MeResponse`에 `role` 추가됨에 따라 타입 업데이트.

---

## 처리하지 않는 것 (MVP 범위 외)

- 이메일 인증 (가입 시 이메일 발송 없음)
- 관리자에게 가입 알림 (이메일/슬랙 등)
- 비밀번호 재설정
- 계정 수정/탈퇴
