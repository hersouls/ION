package app.roadmemory.car.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.roadmemory.car.data.ArrivalKind
import app.roadmemory.car.data.NaviArrival
import app.roadmemory.car.gleo.GleoBridge

/**
 * 디버그 전용: 내비의 도착 알림을 흉내 내요(에뮬레이터의 내비는 도착 이벤트를 보내지 않아서, 마이 트립의 자동 도착을 이걸로 시험해요).
 *   경유지:  adb shell am broadcast -a app.roadmemory.car.DEBUG_ARRIVE -p app.roadmemory.car --es kind waypoint --es name 정동진해변
 *   목적지:  adb shell am broadcast -a app.roadmemory.car.DEBUG_ARRIVE -p app.roadmemory.car --es kind destination --es name 초당할머니순두부
 * 결과는 `adb logcat -s RoadMemory`의 "마이 트립: … 도착" 줄이에요.
 */
class ArriveDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = if (intent.getStringExtra("kind") == "destination") ArrivalKind.DESTINATION else ArrivalKind.WAYPOINT
        val c = GleoBridge.controller
        Log.d("RoadMemory.Debug", "DEBUG_ARRIVE $kind ${intent.getStringExtra("name")} controller=${c != null}")
        c?.debugArrive(NaviArrival(kind, intent.getStringExtra("name"), if (kind == ArrivalKind.WAYPOINT) 0 else null, System.currentTimeMillis()))
    }
}
