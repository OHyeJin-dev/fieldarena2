# Spring Boot 부팅 시간 단축 설계

## 배경

Render Free 플랜에 배포된 백엔드는 15분 무요청 시 컨테이너가 spin-down 된다. 이후 첫 요청 시 컨테이너가 콜드 부팅되면서 사용자 로그인 처리에 1~2분이 걸리는 현상이 확인됐다 (2026-05-20 로그 기준 ~70초).

본 작업은 **유료화 없이** 콜드 부팅 시간을 줄여 사용자 체감 지연을 완화하는 것을 목표로 한다.

## 목표

- 운영 환경에서 Spring Boot 부팅 시간을 현재 ~70초에서 **~20–25초**로 단축
- 외부 keep-alive 핑이나 유료 플랜 전환 없이 달성
- 기존 기능(인증, JPA, Flyway, springdoc, Spring Session) 회귀 없음

## 비목표

- GraalVM Native Image 변환 (springdoc-openapi, Flyway 등 호환성 이슈로 별도 작업이 필요)
- Spring AOT (JVM 모드) 적용 — CDS 대비 추가 이득이 크지 않음
- Render Free 의 spin-down 자체를 막는 것은 본 작업 범위가 아님 (필요 시 별도 keep-alive 또는 유료화로 해결)
- 테스트 자동화 추가

## 적용 기법

1. **Lazy Initialization** (prod profile 한정)
   - `spring.main.lazy-initialization: true`
   - 부팅 시점에 모든 빈을 만들지 않고 첫 요청 시 만든다 → 부팅 ~30% 단축
   - prod 에만 적용. local 개발 환경은 기존처럼 eager 로 두어 설정 오류를 부팅 시점에 즉시 발견

2. **CDS (Class Data Sharing)** — Spring Boot 3.3+ 방식
   - 빌드 시 training run 으로 클래스 메타데이터 아카이브(`app.jsa`) 생성
   - 운영 부팅 시 `-XX:SharedArchiveFile=app.jsa` 로 prelink → 부팅 추가 단축

## 변경 파일

| 파일 | 변경 | 비고 |
|---|---|---|
| `backend/build.gradle.kts` | 수정 | `com.h2database:h2` 를 `testRuntimeOnly` → `runtimeOnly` 로 격상 (training run 용) |
| `backend/src/main/resources/application-prod.yml` | 수정 | `spring.main.lazy-initialization: true` 추가 |
| `backend/src/main/resources/application-training.yml` | 신규 | CDS training run 전용 profile |
| `backend/Dockerfile` | 수정 | build stage 끝에 training run 추가, final stage 에 `app.jsa` 복사, ENTRYPOINT 변경 |

기타 Java 코드는 변경하지 않는다.

## `application-training.yml` 상세

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

설계 의도:
- training 의 목적은 Spring context 를 한 번 refresh 시켜 모든 클래스를 JVM 에 로드시키는 것뿐. 실제 데이터 I/O 는 일어나지 않는다.
- H2 in-memory + Flyway 비활성 + `ddl-auto=create-drop` 으로 가장 가벼운 컨텍스트를 구성한다.
- 운영 yml 의 `spring.session.jdbc.initialize-schema: never` 가 training 에서 문제를 일으키지 않도록 `store-type: none` 으로 세션 자체를 비활성화한다.
- `pii.encryption-key` 는 더미 base64 값 (training run 에서 어떤 PII 도 처리하지 않음).

## `application-prod.yml` 변경

기존 `spring:` 블록에 `main.lazy-initialization` 키를 추가한다. 변경 후 전체:

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

## Dockerfile 변경

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon -q || true
COPY src ./src
RUN ./gradlew build -x test -x spotlessCheck --no-daemon

# CDS training run
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

동작 흐름:
1. Gradle 로 fat JAR 빌드
2. training profile 로 한 번 실행 → Spring context refresh 직후 `spring.context.exit=onRefresh` 가 정상 종료시킴 → JVM 이 `app.jsa` 에 클래스 메타데이터 덤프
3. final stage 에 `app.jar` + `app.jsa` 복사
4. 운영 부팅 시 `-XX:SharedArchiveFile=app.jsa` 로 클래스 메타데이터 prelink

## 예상 효과

| 단계 | Render Free 콜드 부팅 시간 (예상) |
|---|---|
| 현재 | ~70초 |
| Lazy init 적용 | ~45초 |
| + CDS 적용 | **~20–25초** |

## 리스크와 대응

| 리스크 | 대응 |
|---|---|
| training run 실패로 Docker build 깨짐 | training profile 설정을 가볍게 유지. 첫 PR 빌드 로그로 검증 |
| `app.jsa` 가 JDK 마이너 버전 변경 시 무효화 | 베이스 이미지 태그 `21-jdk` / `21-jre` 가 같은 메이저 버전이면 OK. 재빌드 시 매번 새로 만들어지므로 큰 위험 아님 |
| Lazy init 으로 첫 요청 시 추가 지연 (수백 ms ~ 1초) | 콜드 부팅이 더 큰 문제였으므로 트레이드오프 수용 |
| Lazy init 으로 설정 오류가 부팅이 아닌 첫 요청 시 발현 | prod 에만 적용. local 개발 환경은 eager 유지로 개발 단계에서 발견 가능 |
| h2 가 운영 이미지에 포함되어 약간의 용량 증가 (~2 MB) | 무시할 수준 |

## 검증

| 항목 | 방법 |
|---|---|
| training run 성공 | Docker build 로그에서 `Started BackendApplication` + 정상 종료 메시지 확인 |
| `app.jsa` 생성 | build stage 의 `ls -la app.jsa` 출력 확인 |
| 운영 부팅 단축 | Render 배포 후 로그의 `Started BackendApplication in X seconds` 측정 |
| 기능 회귀 | 로그인 후 대시보드 / customers / claims 정상 동작 확인 |
| Lazy init 부작용 | 각 메뉴 첫 진입 시 500/타임아웃 없음 확인 |

자동화 테스트는 추가하지 않는다. 부팅 시간은 build/deploy 로그로 측정한다.

## 실측 결과 (2026-05-20)

| 단계 | Render Free 콜드 부팅 시간 |
|---|---|
| 적용 전 | 수분 이상 (사용자 체감 매우 길었음, 정확치 미측정) |
| 적용 후 | **~2분 (~120초)** |

### 회고

- 체감 개선은 확보. 목표 ~20–25초에는 미달.
- 추정 원인: Render Free 의 매우 낮은 CPU 자원으로 클래스 로딩 외 영역(Hibernate 메타데이터 초기화, JPA 엔티티 스캐닝, 보안 필터 체인 구성)이 부팅 시간을 지배. CDS + Lazy init 의 효과가 상대적으로 작음.
- 추가 단축 후보: GraalVM Native Image (~1초 부팅 가능). 단 springdoc-openapi, Flyway, Spring Security 의 reflection metadata 작업이 큰 편이라 ROI 낮음. 현 시점에서는 보류.

### 부수 사항

- 빌드 실패 1건 발생 (PR #5 1차 배포): `cp build/libs/*.jar app.jar` 가 fat JAR + plain JAR 둘 다 매치해 실패. PR #9 에서 `tasks.named<Jar>("jar") { enabled = false }` 로 plain JAR 비활성화하여 해결.
- 코드 리뷰에서 발견된 잠재 버그 1건: `PiiEncryptor.INSTANCE` 가 lazy-init 환경에서 `AdminInitializer` 실행 시점에 null 일 수 있어 admin 사용자 PII 가 평문 저장되는 문제. `@Lazy(false)` 추가로 사전 차단.