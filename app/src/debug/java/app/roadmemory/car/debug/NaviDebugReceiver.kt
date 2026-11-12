package app.roadmemory.car.debug

import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 디버그 전용: 에뮬레이터 내비 앱에 경로를 요청해서 NaviHelper 이벤트가 앱까지 오는지 확인해요.
 *   adb shell am broadcast -a app.roadmemory.car.DEBUG_NAVI -p app.roadmemory.car --es name 푸른\ 바닷가 --ed lat 37.3595 --ed lon 127.1052
 *   adb shell am broadcast -a app.roadmemory.car.DEBUG_NAVI -p app.roadmemory.car --ez cancel true
 * 응답 · 이벤트는 `adb logcat -s RoadMemory.Debug`로 봐요.
 */
class NaviDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val helper = NaviHelper(app)
        val listener = object : NaviHelperEventListener {
            override fun onRouteStarted(info: ai.pleos.playground.navi.data.RouteStartInfo) { Log.d(TAG, "event onRouteStarted $info") }
            override fun onRoutingCompleted() { Log.d(TAG, "event onRoutingCompleted") }
            override fun onRouteCancelled() { Log.d(TAG, "event onRouteCancelled") }
            override fun onRouteEnded() { Log.d(TAG, "event onRouteEnded") }
            override fun onError(code: ai.pleos.playground.navi.constants.NaviErrorCode) { Log.d(TAG, "event onError $code") }
            override fun onDestinationInfo(info: ai.pleos.playground.navi.data.DestinationInfo) { Log.d(TAG, "event onDestinationInfo ${info.name} remain=${info.remainDistance}m/${info.remainTime}s") }
            override fun onTripInfo(info: ai.pleos.playground.navi.data.TripInfo) { Log.d(TAG, "event onTripInfo $info") }
        }
        val pending = goAsync()
        helper.initialize(
            {
                helper.addListener(listener)
                if (intent.getBooleanExtra("cancel", false)) {
                    helper.cancelRoute(); Log.d(TAG, "requestCancel")
                } else {
                    val name = intent.getStringExtra("name") ?: "푸른 바닷가"
                    val lat = intent.getDoubleExtra("lat", 37.3595)
                    val lon = intent.getDoubleExtra("lon", 127.1052)
                    helper.requestRoute(RouteInfo(latitude = lat, longitude = lon, poiName = name, poiId = "debug-1", poiSubId = "0", address = name))
                    Log.d(TAG, "requestRoute $name ($lat,$lon)")
                }
                // 이벤트를 받을 시간을 주고 정리해요
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    runCatching { helper.removeListener(listener); helper.release() }
                    pending.finish()
                }, 8_000)
            },
            { e -> Log.d(TAG, "init failed: $e"); pending.finish() },
        )
    }

    private companion object { const val TAG = "RoadMemory.Debug" }
}
