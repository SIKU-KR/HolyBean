#!/usr/bin/env bash
#
# 로컬 Firestore 에뮬레이터에 메뉴 데이터를 시드한다.
# seed/menu-current.json 은 운영 menu/current 문서의 스냅샷(REST typed-value 형식)이다.
#
# 사전 조건: 다른 터미널에서 에뮬레이터가 떠 있어야 한다.
#   PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH" firebase emulators:start
#
# 사용:  ./seed-emulator.sh
set -euo pipefail

PROJECT="holybean-e4201"
HOST="${FIRESTORE_EMULATOR_HOST:-localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEED_FILE="$SCRIPT_DIR/seed/menu-current.json"

# 에뮬레이터가 응답하는지 확인
if ! curl -sf "http://$HOST/" >/dev/null 2>&1; then
  echo "에뮬레이터($HOST)에 연결할 수 없습니다. 먼저 'firebase emulators:start'를 실행하세요." >&2
  exit 1
fi

echo "menu/current 시드 중 -> $HOST"
curl -s -X PATCH \
  -H "Authorization: Bearer owner" \
  -H "Content-Type: application/json" \
  "http://$HOST/v1/projects/$PROJECT/databases/(default)/documents/menu/current" \
  -d @"$SEED_FILE" >/dev/null

count="$(grep -o '"name"' "$SEED_FILE" | wc -l | tr -d ' ')"
echo "완료: 메뉴 ${count}개 항목 시드됨"
