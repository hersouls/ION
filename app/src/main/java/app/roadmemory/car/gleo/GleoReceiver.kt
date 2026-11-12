package app.roadmemory.car.gleo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** component = "service"인 Gleo 함수는 sendBroadcast로 와요(UI 없이 처리). */
class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GleoBridge.handle(context, intent)
    }
}
