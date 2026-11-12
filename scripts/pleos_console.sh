#!/bin/bash
# Pleos 에뮬레이터의 Virtual Console을 터미널에서 다뤄요.
#   ./scripts/pleos_console.sh start          # adb forward + 브라우저 하트비트(켜 두어야 콘솔이 DDS ONLINE이 돼요)
#   ./scripts/pleos_console.sh put "gear?gear=Drive"
#   ./scripts/pleos_console.sh stop
# 공식 문서에는 하트비트 얘기가 없어요: 콘솔 페이지가 1초마다 POST /browser/session을 보내고, 그동안만 에뮬레이션이 켜져요.
ADB=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
# 에뮬레이터가 여러 대면 Pleos 기기(AVD 이름이 Pleos로 시작)를 골라요. ANDROID_SERIAL을 직접 주면 그걸 써요.
if [ -z "$ANDROID_SERIAL" ]; then
  for s in $("$ADB" devices | awk 'NR>1 && $2=="device"{print $1}'); do
    case "$("$ADB" -s "$s" shell getprop ro.boot.qemu.avd_name 2>/dev/null | tr -d '\r')" in Pleos*) export ANDROID_SERIAL="$s"; break;; esac
  done
fi
VC=http://127.0.0.1:8088
case "$1" in
  start)
    "$ADB" forward tcp:8088 tcp:8088 >/dev/null
    pkill -f pleos_heartbeat 2>/dev/null
    ( exec -a pleos_heartbeat bash -c 'while true; do curl -s -m 3 -X POST "http://127.0.0.1:8088/browser/session?client=cli" >/dev/null 2>&1; sleep 1; done' ) >/dev/null 2>&1 &
    for i in 1 2 3 4 5 6; do sleep 1; curl -s -m 3 $VC/vehicle/state | grep -q '"emulator_active": *true' && { echo "console online"; exit 0; }; done
    echo "console not online yet"; exit 1 ;;
  put)  curl -s -m 8 -o /dev/null -w "PUT $2 -> %{http_code}\n" -X PUT "$VC/vehicle/$2" ;;
  stop) pkill -f pleos_heartbeat; echo stopped ;;
  *) echo "usage: $0 start|put <path?query>|stop"; exit 2 ;;
esac
