# AgentSupport (fieldarena2)

> 한국 GA(독립대리점) 보험설계사를 위한 통합 업무 지원 플랫폼.
> 단일 레포 + `/frontend` (Next.js) + `/backend` (Spring Boot).
>
> **배포**: Vercel(FE) + Render(BE) + Supabase(Postgres, 세션 포함)
> **로컬 dev도 클라우드 DB를 사용**해서 Docker 불요. 별도 Redis 없이 Spring Session JDBC로 Postgres에 세션 저장.

자세한 규칙·아키텍처·작업 흐름:
- **신규 개발자 onboarding / FSD 가이드**: [`CONTRIBUTING.md`](./CONTRIBUTING.md)
- **LLM 작업 진입점**: [`AGENTS.md`](./AGENTS.md)
- **시스템 구조**: [`ARCHITECTURE.md`](./ARCHITECTURE.md)
- **디자인 토큰**: [`docs/DESIGN.md`](./docs/DESIGN.md)
- **현재 진행 중 작업**: [`docs/exec-plans/active/`](./docs/exec-plans/active/)

---

## 1. 사전 준비 (Windows)

| 도구 | 권장 버전 | 설치 |
|---|---|---|
| Node.js | 20 LTS 이상 | `winget install OpenJS.NodeJS.LTS` |
| pnpm | 9.x 이상 | `corepack enable; corepack prepare pnpm@latest --activate` |
| Java JDK | 21 LTS (Temurin) | `winget install EclipseAdoptium.Temurin.21.JDK` |
| Git | 최신 | `winget install Git.Git` |
| VS Code | 최신 | `winget install Microsoft.VisualStudioCode` |

확인:
```powershell
node -v; pnpm -v; java -version; git --version
```

---

## 2. 클라우드 서비스 가입

이번 셋업은 로컬 Docker 대신 무료 클라우드 서비스를 쓴다. 외부 서비스는 **Supabase 하나만** 가입하면 된다 (세션도 Postgres에 저장하므로 Redis 불요).

### 2.1 Supabase (PostgreSQL)
1. https://supabase.com/ 에서 GitHub로 회원가입 (1분).
2. **New Project** → 이름 `agentsupport-dev` → 리전 `Northeast Asia (Seoul)` → DB 비밀번호 생성·저장.
3. 프로젝트 생성 완료까지 약 2분 대기.
4. **Project Settings → Database → Connection string**에서 다음을 메모:
   - Host: `db.xxxxxxxxxxxxx.supabase.co`
   - Port: `5432` (Direct connection — pooler 6543 아님)
   - Database: `postgres`
   - User: `postgres`
   - Password: (생성 시 설정한 비밀번호)

> 무료 티어: 500MB DB, 7일 비활성 시 일시정지(재활성 1클릭). 보험 mock 데이터 + 세션 저장 충분.

### 2.2 (나중에) Vercel + Render
배포 단계에서 가입. MVP 작업 중에는 불요.

---

## 3. VS Code로 열기

1. VS Code 실행 → **File → Open Folder** → `D:\fieldarena2`.
2. 오른쪽 아래 **"권장 확장 설치"** 알림이 뜨면 **Install All**.
   - 또는 `Ctrl+Shift+P` → "Extensions: Show Recommended Extensions".

---

## 4. 부트스트랩

VS Code 통합 터미널(`` Ctrl+` ``)에서:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\setup.ps1
```

스크립트가 수행하는 것:
1. 사전 도구 체크 (node, pnpm, java, git)
2. `frontend/`에 Next.js 앱 생성 + 필요 라이브러리 설치
3. `backend/`에 Spring Boot 앱 생성 (Java 21, Gradle, 필요 의존성 포함)
4. `.env.example` → `.env` 복사

---

## 5. `.env` 채우기

부트스트랩이 만든 `.env` 파일을 열어서 2장에서 메모한 값으로 채운다:

```
DB_HOST=db.xxxxxxxxxxxxx.supabase.co
DB_PORT=5432
DB_NAME=postgres
DB_USERNAME=postgres
DB_PASSWORD=<Supabase에서 설정한 비밀번호>
```

> `.env`는 절대 git에 커밋하지 마. `.gitignore`에 이미 박혀 있음.

---

## 6. 첫 실행

두 개의 터미널(VS Code에서 분할: `Ctrl+Shift+5`):

```powershell
# 터미널 1: Backend
cd backend
.\gradlew.bat bootRun
# → http://localhost:8080/actuator/health 응답 OK
```

```powershell
# 터미널 2: Frontend
cd frontend
pnpm dev
# → http://localhost:3000
```

---

## 7. 다음 단계: 첫 작업

부트스트랩이 끝났다면 진짜 첫 작업은 [`docs/exec-plans/active/01-login-mvp.md`](./docs/exec-plans/active/01-login-mvp.md)에 정의돼 있다.

LLM 에이전트(Claude Code 등)에 다음과 같이 요청:

> "`docs/exec-plans/active/01-login-mvp.md`를 따라 작업해줘.
> `AGENTS.md` 4장의 컨텍스트 로드 순서를 지키고, plan에 [결정 필요]로 표시된 항목은 임의 결정 말고 나에게 물어봐."

---

## 8. 주요 명령어

루트:
```powershell
.\setup.ps1                 # 부트스트랩 (재실행 시 기존 디렉토리 skip)
```

Frontend (`cd frontend`):
```powershell
pnpm dev                    # 개발 서버 (localhost:3000)
pnpm build                  # 프로덕션 빌드
pnpm typecheck              # 타입 체크
pnpm lint                   # 린트
pnpm test                   # 단위 테스트
```

Backend (`cd backend`):
```powershell
.\gradlew.bat bootRun       # 개발 서버 (localhost:8080)
.\gradlew.bat build         # 빌드 + 테스트
.\gradlew.bat test          # 테스트만
```

자세한 명령은 [`AGENTS.md`](./AGENTS.md#5-명령어) 5장 참조.

---

## 9. 문제 해결

| 증상 | 원인/해결 |
|---|---|
| `pnpm: 명령을 찾을 수 없음` | `corepack enable` 실행 또는 PowerShell **새 창** |
| `java: 명령을 찾을 수 없음` | JAVA_HOME 환경변수 + PATH 설정 후 PowerShell **새 창** |
| Spring Boot `Connection refused` (DB) | `.env` 값 확인. Supabase 프로젝트 일시정지 상태면 대시보드에서 활성화 |
| 한글 깨짐 (PowerShell 출력) | PowerShell 5.1 한계. `winget install Microsoft.PowerShell` 후 `pwsh` 사용 |
| Vercel 배포 시 cookie 안 보냄 | cross-origin 이슈. `ARCHITECTURE.md` 4·6장 + tech-debt-tracker 참고 |

---

## 10. 라이선스

TBD
