#!/usr/bin/env bash
#
# 로컬 개발용 Firestore 에뮬레이터를 띄우고, 준비되면 메뉴를 자동 시드한 뒤
# 포그라운드로 유지한다. Ctrl+C로 종료하면 에뮬레이터도 함께 정리된다.
#
# 사용:  ./dev-emulator.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST="${FIRESTORE_EMULATOR_HOST:-localhost:8080}"

# Firestore 에뮬레이터는 Java 21 이상이 필요하다.
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"

cd "$SCRIPT_DIR"

# 에뮬레이터를 백그라운드로 기동
firebase emulators:start &
EMU_PID=$!

cleanup() { kill "$EMU_PID" 2>/dev/null || true; }
trap cleanup INT TERM EXIT

# 준비될 때까지 대기 (중간에 죽으면 중단)
echo "에뮬레이터 기동 대기 중..."
until curl -sf "http://$HOST/" >/dev/null 2>&1; do
  kill -0 "$EMU_PID" 2>/dev/null || { echo "에뮬레이터 기동 실패" >&2; exit 1; }
  sleep 0.5
done

# 메뉴 시드
"$SCRIPT_DIR/seed-emulator.sh"

echo "준비 완료. 종료하려면 Ctrl+C 를 누르세요. (UI: http://localhost:4000)"
wait "$EMU_PID"
