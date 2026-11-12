package app.roadmemory.car.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.roadmemory.car.gleo.GleoBridge

/**
 * 디버그 전용: 시그니처 송 팩을 화면 없이 적용하거나 꺼요.
 *   adb shell am broadcast -a app.roadmemory.car.DEBUG_PACK -p app.roadmemory.car --user 10 --receiver-include-background --es id w_demo22   # 적용
 *   adb shell am broadcast -a app.roadmemory.car.DEBUG_PACK -p app.roadmemory.car --user 10 --receiver-include-background --es id off        # 끔
 *   ... --es fire start        # 순간 하나를 지금 값으로 바로 내요(콘솔로 바꾸기 어려운 순간 시험)
 * 결과는 `adb logcat -s RoadMemory`에 나와요. 앱이 켜져 있어야 해요(컨트롤러가 있을 때만 받아요).
 */
class PackDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = GleoBridge.controller ?: run { Log.w(TAG, "앱이 켜져 있지 않아요"); return }
        intent.getStringExtra("id")?.let { c.applyPack(if (it == "off") null else it) }
        intent.getStringExtra("fire")?.let { c.debugFire(it) }
    }

    private companion object { const val TAG = "RoadMemory.Debug" }
}
