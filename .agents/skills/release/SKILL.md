---
name: release
description: Cut a new HolyBean Android release — bump the app version (MAJOR.MINOR), curate feature-focused Korean release notes from the commits since the last tag, commit, and create a GitHub Release that triggers the Firebase App Distribution workflow. Use when the user wants to release or deploy the app (e.g. "/release", "/release major", "배포해줘", "릴리즈 내자").
---

# HolyBean 릴리스 스킬

GitHub Release 발행 → `.github/workflows/distribute.yml` 가 자동으로 APK 빌드 후 Firebase App Distribution
에 업로드한다. 이 스킬은 그 Release 를 만드는 일까지(버전 범프 + 노트 큐레이션 + 커밋 + `gh release create`)를 담당한다.

## 버전 규칙 (MAJOR.MINOR — patch 없음)
- `versionName = MAJOR.MINOR`. 작은 변경도 전부 **minor** 로 흡수한다.
  - `major`: 호환 깨지는 변경 / 대규모 아키텍처 개편(예: v3 급). **사용자가 명시할 때만.**
  - `minor`: 그 외 모든 변경(기능 추가·버그 수정 포함). **기본값.**
- `versionCode` 는 `android/app/build.gradle.kts` 가 `appVersionName` 에서 자동 파생(`MAJOR*100+MINOR`)하므로
  직접 건드리지 않는다. 이 스킬은 `appVersionName` **한 줄만** 고친다.
- 태그/릴리스명은 `vMAJOR.MINOR` (예: `v3.1`, `v4.0`).

## 절차

1. **범프 타입 결정** — 인자로 `major` 가 들어온 경우에만 major, 그 외(인자 없음 포함)는 **무조건 minor**.
   커밋을 분석해 타입을 바꾸거나 사용자에게 되묻지 않는다.

2. **현재/다음 버전 계산** — `android/app/build.gradle.kts` 에서 `val appVersionName = "X.Y"` 를 읽는다.
   - minor → `X.(Y+1)`  ·  major → `(X+1).0`
   - 직전 태그: `git describe --tags --abbrev=0` (예: `v3.0.0`).

3. **버전 갱신** — `appVersionName` 라인의 값만 새 버전으로 Edit. (versionCode 는 gradle 이 파생)

4. **릴리스 노트 큐레이션 (직접 explore)** — 단순 `git log` 덤프 금지.
   - `git log <직전태그>..HEAD --oneline` 으로 대상 커밋을 모은다.
   - 변경 규모가 모호하면 `git show <sha>` / `git diff <직전태그>..HEAD --stat` 으로 **실제 변경을 살펴본다.**
   - 사용자(매장 운영자) 관점에서 **무엇이 좋아졌는가** 중심으로 한글 노트를 작성한다.
     기능 추가·개선·버그 수정을 묶고, 내부 리팩터링/빌드 잡일은 묶거나 생략한다.
   - 형식 예:
     ```
     ## v3.1
     - (기능) …
     - (개선) …
     - (수정) …
     ```

5. **커밋 & 푸시** — 버전 범프를 `chore(release): vX.Y` 로 커밋하고 `git push`.

6. **릴리스 생성** — `gh release create vX.Y --title "vX.Y" --notes "<큐레이션된 노트>"`.
   → `distribute.yml` (`on: release: published`) 가 트리거되어 빌드·업로드를 수행한다.

7. **안내** — `gh run watch` 또는 Actions 실행 URL 을 사용자에게 알려 배포 진행을 추적하게 한다.

## 주의
- 푸시/릴리스 생성은 되돌리기 어려운 outward-facing 작업이다. 버전과 노트를 사용자에게 한 번 보여주고 진행한다.
- `appVersionName` 라인 형식(`val appVersionName = "X.Y"`)이 바뀌면 3번 Edit 가 실패하니 형식을 유지할 것.
