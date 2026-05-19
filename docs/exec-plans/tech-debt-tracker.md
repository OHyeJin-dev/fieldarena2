# Tech Debt Tracker

> 작업 중 발견된 기술 부채·결정 보류 항목 등록부.
> 각 exec-plan 회고에서 발견된 부채를 여기로 옮긴다.
> 해결한 항목은 삭제하지 말고 `[Resolved]`로 표시 + 해결 PR 링크 첨부.

---

## 형식

```text
### [상태] 제목
- **등록일**: YYYY-MM-DD
- **출처**: exec-plan 또는 이슈 번호
- **영향도**: High / Medium / Low
- **설명**: ...
- **제안 해결**: ...
- **해결 PR**: (resolved 시)
```

상태:

- `[Open]` — 미해결
- `[InProgress]` — 처리 중
- `[Resolved]` — 완료
- `[Wontfix]` — 의도적으로 처리 안 함

---

## 초기 등록 (AGENTS.md / DESIGN.md 작성 시 식별된 결정 보류)

### [Open] 다크 모드 정책 결정

- **등록일**: 2026-05-18
- **출처**: `docs/DESIGN.md`
- **영향도**: Medium
- **설명**: `tailwind.config.darkMode = "class"`가 켜져 있으나 dark 컬러 토큰이 없다. 어정쩡한 상태가 가장 위험.
- **제안 해결**: 안 하면 설정 제거 / 할 거면 모든 컬러 토큰의 dark pair 정의 PR.

### [Open] 디자인 토큰의 letter-spacing 음수값 한글 영향 검증

- **등록일**: 2026-05-18
- **출처**: `docs/DESIGN.md`
- **영향도**: Low
- **설명**: headline-lg/md의 letter-spacing 음수값이 한글에서 자간 축소 효과를 줄 수 있음.
- **제안 해결**: 한글 콘텐츠로 시각 테스트 후 0으로 변경할지 결정.

### [Open] CSP 정책 미정의

- **등록일**: 2026-05-18
- **출처**: `ARCHITECTURE.md` 6.3, `01-login-mvp.md` 5장
- **영향도**: High (운영 진입 전 필수)
- **설명**: Content Security Policy 미설정. Tailwind 호환되도록 설계 필요.
- **제안 해결**: `docs/SECURITY.md` 작성 시 정식 정책 수립.

### [Open] OpenJDK 벤더 미결정

- **등록일**: 2026-05-18
- **출처**: `AGENTS.md` 2장
- **영향도**: Low
- **설명**: Temurin / Corretto / Liberica 미결정. CI 일관성을 위해 결정 필요.
- **제안 해결**: 첫 CI 워크플로 작성 시점에 결정.

### [Open] 로그인 좌측 비주얼 이미지 라이선스

- **등록일**: 2026-05-18
- **출처**: `01-login-mvp.md` 5장
- **영향도**: Medium (출시 전 필수)
- **설명**: Stitch placeholder 이미지(lh3.googleusercontent...)는 라이선스 불명확.
- **제안 해결**: Unsplash, 자체 촬영, 또는 일러스트 외주 결정.

### [Resolved] ORM 선택 (JPA / MyBatis / jOOQ)

- **등록일**: 2026-05-18
- **출처**: `AGENTS.md` 2장, `ARCHITECTURE.md` ADR
- **영향도**: High (첫 도메인 모델 작성 전 결정)
- **설명**: 한국 금융권은 MyBatis 비중 높음. 컴플라이언스 친화. 다만 JPA가 개발 속도 우위.
- **해결**: Spring Data JPA 채택. 이미 `build.gradle.kts`에 포함된 의존성 그대로 사용. 상세 근거 `ARCHITECTURE.md` ADR-004. 결정일 2026-05-19.

---

## 2026-05-18 추가 (Vercel + Render + Supabase 결정 후)

### [Open] 커스텀 도메인 확보 및 cookie SameSite 정상화

- **등록일**: 2026-05-18
- **출처**: `ARCHITECTURE.md` 3.2, 6.2, ADR-005
- **영향도**: **High** (운영 진입 전 필수)
- **설명**: Vercel(`*.vercel.app`)과 Render(`*.onrender.com`)는 다른 eTLD+1 → 세션 쿠키에 `SameSite=None; Secure` 강제. Safari ITP 등 third-party cookie 차단 영향. CSRF 위험 증가.
- **제안 해결**: `agentsupport.kr` 등 root domain 확보 → `app.agentsupport.kr` (Vercel) + `api.agentsupport.kr` (Render) 분리 → `.agentsupport.kr` cookie domain → `SameSite=Lax` 복귀.

### [Open] Redis 도입 재평가 (Spring Session JDBC 한계 도달 시)

- **등록일**: 2026-05-18
- **출처**: `ARCHITECTURE.md` 8.5, ADR-003
- **영향도**: Low (현재) / Medium (트래픽 증가 시)
- **설명**: 현재 세션은 Postgres에 저장 (Spring Session JDBC). 매 요청마다 DB 쿼리 발생.
- **모니터링 트리거**:
  - 동시 접속자 1,000명 초과
  - 평균 응답 시간 > 100ms 중 세션 쿼리가 > 30%
  - 다중 BE 인스턴스 운영
- **제안 해결**: 위 임계점 도달 시 Redis 도입 검토 (Render Key Value, Upstash, NCP Redis 중 선택).

### [Open] Render 무료 티어 cold start

- **등록일**: 2026-05-18
- **출처**: `ARCHITECTURE.md` 8.1, ADR-005
- **영향도**: Low (dev) / Medium (사용자 demo 시)
- **설명**: Render Web Service 무료 티어는 15분 비활성 시 cold. Spring Boot 첫 요청 30~60s 지연.
- **제안 해결**: 외부 demo·운영 진입 시 Render Starter ($7/월) 전환.

### [Open] Supabase 7일 비활성 자동 일시정지

- **등록일**: 2026-05-18
- **출처**: `ARCHITECTURE.md` 8.1
- **영향도**: Low
- **설명**: Supabase 무료 티어 프로젝트는 7일간 요청 없으면 일시정지. 데이터는 유지되지만 첫 접속 시 재활성화 클릭 필요.
- **제안 해결**: dev가 일주일 이상 멈출 일 없으면 무시. 장기 미사용 시 GitHub Actions로 weekly ping 또는 유료 전환.

### [Resolved] ADR-005 배포 인프라 결정

- **결정일**: 2026-05-18
- **결정**: Vercel(FE) + Render(BE) + Supabase(Postgres, 세션 포함). Spring Session JDBC 사용으로 별도 Redis 미도입.
- **상세**: `ARCHITECTURE.md` ADR-005.

### [Open] 의존성 추가 시 npm registry / Maven Central 실제 확인 후 등록

- **등록일**: 2026-05-18
- **출처**: 부트스트랩 진행 중 잘못된 패키지명(`@fontsource-variable/pretendard`)으로 시간 낭비
- **영향도**: Low (프로세스)
- **설명**: 외부 패키지 의존성을 plan 또는 setup script에 추가할 때 npm/Maven Central에서 실제 존재 여부 + 최신 버전을 확인 후 등록.
- **제안 해결**: `docs/PLANS.md` 작성 시 체크리스트 항목에 추가.
