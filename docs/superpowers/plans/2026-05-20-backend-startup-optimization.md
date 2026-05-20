# Spring Boot Startup Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Spring Boot 백엔드의 Render Free 콜드 부팅 시간을 ~70초에서 ~20–25초로 단축한다.

**Architecture:** 두 가지 기법을 결합한다.
1. **Lazy Initialization** (prod profile 한정) — 빈 생성을 첫 요청 시점으로 지연
2. **CDS (Class Data Sharing)** — Docker build 단계에서 training run 으로 `app.jsa` 생성, 운영 부팅 시 클래스 메타데이터 prelink

**Tech Stack:** Spring Boot 3.5.0, Java 21 (eclipse-temurin), Gradle Kotlin DSL, PostgreSQL (운영) / H2 (training run), Docker (multi-stage), Render Free.

**Spec:** [docs/superpowers/specs/2026-05-20-spring-boot-startup-optimization-design.md](../specs/2026-05-20-spring-boot-startup-optimization-design.md)

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `backend/build.gradle.kts` | Modify | H2 dependency scope: `testRuntimeOnly` → `runtimeOnly` |
| `backend/src/main/resources/application-prod.yml` | Modify | `spring.main.lazy-initialization: true` 추가 |
| `backend/src/main/resources/application-training.yml` | Create | CDS training run 전용 profile (H2 + Flyway off + session disabled) |
| `backend/Dockerfile` | Modify | build stage 끝에 training run 추가, final stage 에 `app.jsa` 복사, ENTRYPOINT 에 `-XX:SharedArchiveFile` 추가 |

Java 소스 코드는 변경하지 않는다. 모든 변경은 설정 + 빌드 파이프라인 차원.

---

## Task 1: H2 의존성 스코프 변경

**Files:**
- Modify: `backend/build.gradle.kts:22-40`

H2 를 production runtime classpath 에도 포함시켜야 training run 에서 사용할 수 있다.

- [ ] **Step 1: build.gradle.kts 의 의존성 블록 수정**

`testRuntimeOnly("com.h2database:h2")` 라인을 찾아 `runtimeOnly` 로 변경:

```kotlin
dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springframework.session:spring-session-jdbc")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("com.h2database:h2")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}
```

(`testRuntimeOnly("com.h2database:h2")` 라인을 삭제하고 `runtimeOnly("com.h2database:h2")` 를 추가했음을 확인)

- [ ] **Step 2: Gradle 빌드 통과 확인**

```bash
cd backend
./gradlew build -x test -x spotlessCheck --no-daemon
```

Expected: `BUILD SUCCESSFUL` 메시지. `build/libs/` 에 fat JAR 생성됨.

- [ ] **Step 3: H2 가 런타임 클래스패스에 포함됐는지 검증**

```bash
cd backend
./gradlew dependencies --configuration runtimeClasspath --no-daemon | grep -i h2
```

Expected: 출력에 `com.h2database:h2:...` 라인 포함.

- [ ] **Step 4: Commit**

```bash
git add backend/build.gradle.kts
git commit -m "build: promote h2 to runtimeOnly for CDS training run"
```

---

## Task 2: prod profile 에 lazy initialization 추가

**Files:**
- Modify: `backend/src/main/resources/application-prod.yml`

prod 운영 환경에서만 lazy initialization 을 활성화한다. local 개발 환경(`application-local.yml`)은 변경하지 않는다.

- [ ] **Step 1: application-prod.yml 전체 내용을 다음으로 교체**

```yaml
server:
  servlet:
    session:
      cookie:
        secure: true
        same-site: none

spring:
  main:
    lazy-initialization: true
  session:
    jdbc:
      initialize-schema: never

logging:
  level:
    root: WARN
    com.agentsupport: INFO
```

(`spring.main.lazy-initialization: true` 라인이 추가된 것 외엔 기존과 동일)

- [ ] **Step 2: yml 파싱 검증 — 빌드로 확인**

```bash
cd backend
./gradlew build -x test -x spotlessCheck --no-daemon
```

Expected: `BUILD SUCCESSFUL`. (yml 파싱 오류 시 빌드는 통과하지만 실행 시 실패하므로 다음 Task 의 training run 에서 최종 검증)

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/application-prod.yml
git commit -m "perf: enable lazy initialization in prod profile"
```

---

## Task 3: CDS training profile 생성

**Files:**
- Create: `backend/src/main/resources/application-training.yml`

CDS training run 전용 profile. H2 in-memory DB 사용, Flyway 비활성, Spring Session 비활성. training run 은 "Spring context 가 한 번 정상 refresh 되는 것"만이 목적이며 실제 데이터 I/O 는 발생하지 않는다.

- [ ] **Step 1: application-training.yml 신규 생성**

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:training;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    username: sa
    password: ""
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: false
  session:
    store-type: none

pii:
  encryption-key: dHJhaW5pbmdfa2V5X2R1bW15X3ZhbHVlX2Zvcl9DRFNfb25seQ==
```

- [ ] **Step 2: training profile 로 컨텍스트가 정상 refresh 되는지 로컬에서 검증**

먼저 fat JAR 빌드:

```bash
cd backend
./gradlew build -x test -x spotlessCheck --no-daemon
```

빌드된 JAR 위치 확인 (`build/libs/*.jar`). 그 다음 training profile 로 한 번 실행해 정상 종료되는지 확인:

```bash
cd backend
java -Dspring.profiles.active=training \
     -Dspring.context.exit=onRefresh \
     -jar build/libs/$(ls build/libs/ | grep -v plain | head -1)
```

Expected:
- 로그에 `Started BackendApplication in X seconds` 표시
- 그 직후 `Application is exiting...` 또는 정상 종료 메시지
- 종료 코드 0 (echo $? 으로 확인 가능)

만약 실패 시 stack trace 를 보고 `application-training.yml` 의 설정을 보강한다 (예: 특정 빈이 추가 환경변수를 요구하는 경우).

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/application-training.yml
git commit -m "build: add training profile for CDS archive generation"
```

---

## Task 4: Dockerfile 에 CDS training run + shared archive 적용

**Files:**
- Modify: `backend/Dockerfile`

Docker build 의 마지막 단계에서 training run 으로 `app.jsa` 를 생성하고, final 이미지에 복사한 뒤 ENTRYPOINT 에서 그 archive 를 사용하도록 변경한다.

- [ ] **Step 1: Dockerfile 전체를 다음으로 교체**

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon -q || true
COPY src ./src
RUN ./gradlew build -x test -x spotlessCheck --no-daemon

# CDS training run: Spring context 를 한 번 refresh 시켜 app.jsa 생성
RUN cp build/libs/*.jar app.jar \
 && java -Dspring.profiles.active=training \
         -Dspring.context.exit=onRefresh \
         -XX:ArchiveClassesAtExit=app.jsa \
         -jar app.jar \
 && ls -la app.jsa

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
COPY --from=build /app/app.jsa app.jsa
EXPOSE 8080
ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-jar", "app.jar"]
```

기존 Dockerfile 과의 차이:
- build stage 마지막에 training run 블록 추가 (`cp`, `java -XX:ArchiveClassesAtExit`, `ls -la app.jsa`)
- final stage 에서 build stage 의 `app.jar` 경로 변경 (`build/libs/*.jar` → `app.jar`)
- final stage 에 `app.jsa` 복사 추가
- ENTRYPOINT 에 `-XX:SharedArchiveFile=app.jsa` 추가

- [ ] **Step 2: Docker 이미지 로컬 빌드**

```bash
cd backend
docker build -t agentsupport-backend:cds-test .
```

Expected:
- 모든 RUN 단계 통과
- training run 단계 로그에 `Started BackendApplication in X seconds` 표시
- 마지막 `ls -la app.jsa` 출력에 파일 사이즈 (수십 MB 예상) 표시
- `Successfully built ...` 메시지

만약 training run 단계에서 실패 시:
- Task 3 의 Step 2 가 로컬에서 통과했는지 재확인
- Docker 빌드 환경의 메모리 부족 가능 → `docker build --memory=2g` 시도

- [ ] **Step 3: 이미지 사이즈와 app.jsa 포함 확인**

```bash
docker run --rm --entrypoint sh agentsupport-backend:cds-test -c "ls -la app.jsa app.jar"
```

Expected: `app.jar` 와 `app.jsa` 두 파일 모두 존재.

- [ ] **Step 4: 로컬에서 부팅 시간 측정 (training profile 사용 — 운영 DB 없이 측정 가능)**

운영 DB 없이 부팅 시간만 측정하기 위해 training profile 로 실행한다 (H2 in-memory + Flyway off). 이 측정은 운영 부팅과 약간 다르지만 (운영은 Flyway validate 가 추가됨) CDS 효과의 1차 근사로 충분하다. 정확한 운영 측정은 Task 5 에서 진행.

먼저 **CDS 적용 전** 베이스라인 측정을 위해 이전 커밋의 이미지로 한 번 측정해두면 비교에 유용 (선택):

```bash
docker run --rm \
  -e SPRING_PROFILES_ACTIVE=training \
  agentsupport-backend:cds-test 2>&1 | grep "Started BackendApplication"
```

Expected: 로그에 `Started BackendApplication in X seconds` 표시. X 값을 기록.

기준치:
- 로컬은 Render 보다 일반적으로 더 빠른 CPU (예상 X = 5–15초)
- 의미 있는 비교는 Render 환경에서 (Task 5)

- [ ] **Step 5: Commit**

```bash
git add backend/Dockerfile
git commit -m "perf: add CDS training run and shared archive to Docker image"
```

---

## Task 5: Render 배포 및 운영 부팅 시간 검증

**Files:** (코드 변경 없음 — 운영 환경 검증)

- [ ] **Step 1: 사용자에게 push 승인 요청**

이 시점까지의 모든 커밋을 `perf/backend-startup-optimization` 브랜치에서 origin 에 push 하려면 사용자 명시적 허가가 필요하다 (memory: `feedback_git_push_confirm`).

다음 메시지를 사용자에게 보낸다:

> "구현 커밋 완료. `perf/backend-startup-optimization` 브랜치를 origin 으로 push 하고 Render 자동 배포를 트리거할까요? push 후 다음 콜드 부팅 측정까지는 약 5분 + Render Free 의 다음 idle period 가 필요합니다."

사용자 승인 받기 전에는 push 하지 않는다.

- [ ] **Step 2: 사용자 승인 시 push**

```bash
git push -u origin perf/backend-startup-optimization
```

Expected: `Branch 'perf/backend-startup-optimization' set up to track 'origin/perf/backend-startup-optimization'`.

- [ ] **Step 3: Render 배포 로그 확인**

사용자에게 Render 대시보드에서 배포가 성공했는지 확인 요청. 빌드 로그에 다음 표시되는지 확인:
- training run 의 `Started BackendApplication in X seconds`
- `ls -la app.jsa` 출력 (파일 사이즈 있음)

- [ ] **Step 4: 콜드 부팅 시간 측정**

Render Free 의 spin-down 을 유도하려면:
1. 모든 클라이언트 요청을 멈추고 16분 이상 대기
2. 다음 요청 시 Render 로그에서 새 컨테이너 부팅 메시지 확인
3. `Started BackendApplication in X seconds` 의 X 값 기록

Expected: 기존 ~70초 → **~20–30초** 수준

- [ ] **Step 5: 기능 회귀 검증**

배포 후 다음 시나리오 수동 확인:
- 로그인 성공
- `/dashboard` 정상 표시
- `/customers` 목록 조회 / 등록 / 수정 정상
- `/claims` 목록 조회 / 등록 정상
- 각 메뉴 첫 진입 시 500/타임아웃 없음 (lazy-initialization 부작용 검증)

- [ ] **Step 6: 결과 보고**

사용자에게 다음 정보 보고:
- 부팅 시간 변경: before X 초 → after Y 초
- 기능 회귀 여부
- (선택) PR 생성 및 master merge 여부 의견 청취

---

## 검증 체크리스트 (전체)

- [ ] H2 가 runtime classpath 에 포함됨 (Task 1, Step 3)
- [ ] application-prod.yml 에 lazy-initialization 적용 (Task 2)
- [ ] application-training.yml 로 컨텍스트 refresh 정상 (Task 3, Step 2)
- [ ] Docker build 성공 + `app.jsa` 생성 (Task 4, Step 2–3)
- [ ] 로컬 실행 시 부팅 시간 단축 확인 (Task 4, Step 4)
- [ ] Render 배포 + 콜드 부팅 시간 ~20–30초 (Task 5)
- [ ] 기능 회귀 없음 (Task 5, Step 5)
