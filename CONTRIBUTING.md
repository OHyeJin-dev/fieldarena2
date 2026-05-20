# Contributing

신규 개발자 onboarding과 일상 작업 흐름을 담은 문서. 프로젝트 셋업은 [README.md](./README.md), 아키텍처는 [ARCHITECTURE.md](./ARCHITECTURE.md), LLM 작업 컨벤션은 [AGENTS.md](./AGENTS.md)를 먼저 읽어둘 것.

---

## 1. 최초 클론 후

```powershell
git clone https://github.com/OHyeJin-dev/fieldarena2.git
cd fieldarena2
cd frontend; pnpm install
```

`pnpm install`은 `prepare` 스크립트로 **Husky pre-commit hook을 자동 등록**한다. 이후 모든 `git commit`은 FSD 룰 검사를 거친다 (위반 시 commit 차단).

수동 확인:
```powershell
git config core.hooksPath
# → frontend/.husky/_  가 떠야 정상
```

---

## 2. 프론트엔드 구조 (FSD)

`frontend/src`는 **Feature-Sliced Design (Practical variant)** 5 레이어 구조다.

```
frontend/src/
├── app/         # Next.js App Router. 라우트 + 페이지 조립.
├── widgets/     # 페이지를 구성하는 큰 UI 블록 (sidebar, top-bar, app-shell)
├── features/    # 사용자 액션 단위 (도메인 그룹으로 묶음)
├── entities/    # 도메인 모델 (DTO + 조회 훅)
└── shared/      # 공통 (api 클라이언트, UI 프리미티브, 타입)
```

### 핵심 규칙

1. **의존성 단방향**: 위에서 아래로만 import. `entities → features` 금지.
2. **같은 레이어 슬라이스끼리 import 금지**: `entities/proposal`이 `entities/contract`를 직접 import할 수 없음. 공통 타입은 `shared/`로.
3. **Public API 강제**: 슬라이스 루트(`@/entities/proposal`)로만 import. 내부 파일(`@/entities/proposal/api/index`) 직접 import 금지.
4. **features는 2단계 그룹**: `features/<domain>/<use-case>/`. 단일 슬라이스도 그룹 안에 둠.

### 슬라이스 내부

각 슬라이스는 segment 폴더로 분리:

```
features/<group>/<slice>/
├── api/index.ts    # fetch 함수 + 요청/응답 타입
├── model/index.ts  # React Query 훅, zod 스키마
├── ui/index.tsx    # React 컴포넌트 (있는 경우)
├── lib/            # 슬라이스 내부 유틸 (있는 경우)
└── index.ts        # Public API
```

해당 segment가 없으면 폴더 생략 가능.

자세한 설계 근거: [`docs/superpowers/specs/2026-05-19-frontend-fsd-migration-design.md`](./docs/superpowers/specs/2026-05-19-frontend-fsd-migration-design.md)

---

## 3. 새 슬라이스 만들기

**예시: 설계 취소 기능 (`features/proposal/cancel`)**

```powershell
cd frontend
New-Item -ItemType Directory -Path src/features/proposal/cancel/api -Force
New-Item -ItemType Directory -Path src/features/proposal/cancel/model -Force
```

**`src/features/proposal/cancel/api/index.ts`**:
```ts
import { apiFetch } from "@/shared/api";

export function cancelProposal(id: string): Promise<void> {
  return apiFetch<void>(`/api/proposals/${id}/cancel`, { method: "POST" });
}
```

**`src/features/proposal/cancel/model/index.ts`**:
```ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { cancelProposal } from "../api";

export function useCancelProposal() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => cancelProposal(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["proposals"] }),
  });
}
```

**`src/features/proposal/cancel/index.ts`** (Public API):
```ts
export { useCancelProposal } from "./model";
```

**소비처에서 사용**:
```ts
import { useCancelProposal } from "@/features/proposal/cancel";
```

---

## 4. 명령어

`cd frontend` 후:

```powershell
pnpm typecheck           # TypeScript 검사
pnpm lint                # ESLint
pnpm lint:fsd            # FSD 구조 검사 (Steiger)
pnpm build               # 프로덕션 빌드
pnpm dev                 # 개발 서버 (localhost:3000)
```

CI가 위 4개를 모두 돌리므로, push 전에 로컬에서 같은 명령으로 확인하면 PR 사이클을 줄일 수 있다.

---

## 5. Commit & PR 흐름

### 5.1 Commit

```powershell
git add <files>
git commit -m "feat: ..."
```

Pre-commit hook이 자동 실행 →
- ✓ Steiger 통과: commit 진행
- ✗ FSD 위반: 위반 내용 출력 + commit 차단. 코드 수정 후 다시 commit.

**hook을 건너뛰고 싶다면** (예: WIP 임시 commit):
```powershell
git commit --no-verify -m "wip: ..."
```
단, push 전에 룰을 맞춰야 CI가 통과한다.

### 5.2 PR

1. **브랜치 생성**: `git checkout -b feat/<topic>` (master에서 직접 작업 금지 — branch protection으로 차단됨)
2. **commit + push**: pre-commit hook이 로컬 검증, GitHub Actions가 PR 검증
3. **CI 통과 확인**: `Frontend CI` 체크가 ✓ 가 되어야 머지 가능
4. **PR description**: 변경 요약, 테스트 시나리오, 참고 링크 포함
5. **머지 방식**: `Merge commit` (기본). 다른 방식은 케이스별로 합의

### 5.3 긴급 핫픽스로 FSD 검사 우회

프로덕션 장애 대응 등으로 FSD 룰 위반을 일시 허용해야 한다면:

1. PR에 `hotfix-bypass-fsd` **라벨**을 추가
2. CI의 FSD lint 단계가 skip됨 (다른 단계는 그대로 통과해야 함)
3. **머지 후 follow-up 이슈를 반드시 생성**해서 위반을 정리

라벨은 감사 흔적이 되므로, 어떤 PR이 룰을 우회했는지 추적 가능하다.

---

## 6. CI 워크플로

`.github/workflows/frontend-ci.yml`이 다음 상황에 자동 실행:
- PR open/update (대상 브랜치 무관)
- master에 push

검사 단계 (순차):
1. **Typecheck** — `tsc --noEmit`
2. **Lint** — ESLint
3. **FSD lint** — Steiger (위반 시 PR에 코멘트 자동 작성)
4. **Build** — `next build`

트리거 경로: `frontend/**`, `.github/workflows/frontend-ci.yml`만 변경 시 실행. 백엔드/문서만 바뀌면 skip.

---

## 7. Branch Protection

master에는 다음 보호 규칙이 걸려 있다:

- 직접 push 차단 (PR만 허용)
- `Frontend CI` 체크 통과 필수
- 머지 전 브랜치가 master 최신 상태여야 함 (out-of-date 차단)
- 머지 시 conversation 모두 resolved여야 함

위반 시 GitHub UI에서 머지 버튼이 비활성화된다.

---

## 8. 문제 해결

| 증상 | 해결 |
|---|---|
| commit 시 `husky - pre-commit: not found` | `cd frontend; pnpm install` 다시 실행 |
| `pnpm lint:fsd`가 위반을 잡지만 의도적이라고 판단 | `steiger.config.ts`에서 해당 룰 `warn` 강등 또는 슬라이스 예외 설정. PR로 팀 리뷰 |
| CI에서 `packages field missing or empty` | pnpm 버전이 9 이하. CI는 v11 사용 — 로컬도 v10+ 권장 |
| 새 슬라이스가 `insignificant-slice` 룰에 걸림 | 소비처가 1개뿐이면 해당 소비처에 inline하라는 신호. 또는 추후 확장 계획이면 룰 예외 |

---

## 9. 참고 문서

- [FSD 공식 가이드](https://feature-sliced.design/docs)
- [Steiger 룰 목록](https://github.com/feature-sliced/steiger)
- 팀 FSD spec: [`docs/superpowers/specs/2026-05-19-frontend-fsd-migration-design.md`](./docs/superpowers/specs/2026-05-19-frontend-fsd-migration-design.md)
- 팀 FSD plan: [`docs/superpowers/plans/2026-05-19-frontend-fsd-migration.md`](./docs/superpowers/plans/2026-05-19-frontend-fsd-migration.md)
- LLM 작업 진입점: [`AGENTS.md`](./AGENTS.md)
