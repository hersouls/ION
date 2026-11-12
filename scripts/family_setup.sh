#!/bin/zsh
# 가족 위치(시험용 debug 빌드) 연결 설정을 에뮬레이터의 앱에 넣어요. 제출용(release) 빌드에는 이 기능이 없어요.
#   app/scripts/family_setup.sh          설정 파일을 읽어 앱에 넣어요
#   app/scripts/family_setup.sh clear    앱에서 지워요(가족 위치 칸이 사라져요)
#
# 설정 파일은 저장소 밖에 둬요(이름 · 주소가 저장소에 들어가지 않게): ~/Library/Application Support/ION-demo/family.json
#   {"base_url": "https://…", "self": "<이 앱을 쓰는 사람의 이름>", "alias": [["위치를 주는 쪽의 이름", "화면에 보일 별칭 · 역할"], …]}
# alias에 없는 사람의 위치는 읽지 않아요(허용 목록). self의 위치는 목록에 넣지 않아요.
# 한글이 명령줄에서 깨지지 않게 설정 파일을 base64로 보내요.
set -euo pipefail

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
CFG="${FAMILY_CONFIG:-$HOME/Library/Application Support/ION-demo/family.json}"
PKG="app.roadmemory.car"
ACTION="app.roadmemory.car.DEBUG_FAMILY"

if [[ "${1:-}" == "clear" ]]; then
  "$ADB" shell am broadcast -a "$ACTION" -p "$PKG" --user current --receiver-include-background --ez clear true >/dev/null
  echo "지웠어요"
  exit 0
fi

[[ -f "$CFG" ]] || { echo "설정 파일이 없어요: $CFG" >&2; exit 1; }
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); assert d["base_url"].startswith("https://") and d["alias"]' "$CFG" \
  || { echo "설정 파일 모양이 맞지 않아요(base_url은 https, alias는 비어 있지 않아야 해요)" >&2; exit 1; }
B64="$(base64 < "$CFG" | tr -d '\n')"
"$ADB" shell am broadcast -a "$ACTION" -p "$PKG" --user current --receiver-include-background --es b64 "$B64" >/dev/null
echo "넣었어요. 확인: adb logcat -d -s RoadMemory.Debug | grep 'family link'"
