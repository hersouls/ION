package app.roadmemory.car.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 디버그 전용: `adb shell am broadcast -a app.roadmemory.car.DEBUG_PROBE -p app.roadmemory.car` → [DataProbe]가 한 번 돌아요. */
class DataProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        // --es which api → Vehicle SDK 밖(내비 · AAOS 표준 · 위치 · 센서 · 네트워크), 아니면 Vehicle SDK 읽기 API 전체
        if (intent.getStringExtra("which") == "api") CarApiProbe.run(context) { pending.finish() }
        else DataProbe.run(context) { pending.finish() }
    }
}
