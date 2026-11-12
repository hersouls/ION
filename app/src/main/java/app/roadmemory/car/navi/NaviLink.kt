package app.roadmemory.car.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/** 내비에 보낼 곳 하나. 이름과 좌표만 있어요(마이 트립의 정류장, 캘린더의 약속 장소). */
data class Place(val name: String, val lat: Double, val lng: Double)

enum class GuideFail(val label: String) {
    NO_PERMISSION("이 빌드는 내비에 길을 보낼 권한이 없어요"),
    NO_NAVI("차의 내비와 이어지지 않았어요"),
    TOO_FAR("너무 멀어서 내비가 길을 찾지 못했어요"),
    TOO_CLOSE("너무 가까워서 내비가 길을 찾지 못했어요"),
    LOW_BATTERY("배터리가 모자라서 내비가 길을 찾지 못했어요"),
    OTHER("내비가 길을 찾지 못했어요"),
}

sealed interface GuideResult {
    /** 내비가 길을 받아 경로 후보를 열었어요. 운전자가 고르지 않으면 몇 초 뒤 저절로 안내를 시작해요(에뮬레이터 내비에서 확인). */
    data object Sent : GuideResult
    data class Failed(val why: GuideFail) : GuideResult
}

/**
 * 차의 내비 앱에 길을 보내요(NaviHelper.requestRoute · requestRouteWithWaypoints, 권한 NAVI_ROUTE_SEARCH).
 * 운전자가 "안내" 단추를 누른 뒤에만 불러요. ION이 먼저 경로를 바꾸지 않아요. 주행 중에는 화면이 열리지 않으니 서 있을 때만 눌러요.
 * 내비는 길을 받으면 경로 후보와 "안내 시작"(몇 초 카운트다운)을 열어요. 목적지 이벤트는 요청 뒤 11초쯤에야 와서, 3초 안에 오류가 없으면 "보냈어요"를 먼저 말해요.
 * 경유지는 SDK가 둘까지만 받아요. 정류장이 더 많은 일정은 구간마다 다음 정류장을 이어 보내요(마이 트립 화면이 그 차례를 기억해요).
 */
object NaviLink {
    private const val TAG = "RoadMemory"
    private val main = Handler(Looper.getMainLooper())

    fun guide(ctx: Context, to: Place, via: List<Place> = emptyList(), done: (GuideResult) -> Unit) {
        main.post {
            var failed = false
            var sent = false
            fun finish(r: GuideResult) {
                if (failed) return
                when (r) {
                    is GuideResult.Failed -> { failed = true; done(r) }
                    GuideResult.Sent -> if (!sent) { sent = true; done(r) }
                }
            }
            val helper = runCatching { NaviHelper(ctx.applicationContext) }.getOrElse {
                Log.w(TAG, "NaviHelper create failed: ${it.javaClass.simpleName}"); finish(GuideResult.Failed(GuideFail.NO_NAVI)); return@post
            }
            val listener = object : NaviHelperEventListener {
                override fun onRouteStarted(info: RouteStartInfo) { main.post { finish(GuideResult.Sent) } }
                override fun onDestinationInfo(info: DestinationInfo) { main.post { finish(GuideResult.Sent) } }
                override fun onRoutingCompleted() { main.post { finish(GuideResult.Sent) } }
                override fun onError(code: NaviErrorCode) { Log.d(TAG, "route error $code"); main.post { finish(GuideResult.Failed(reason(code))) } }
            }
            fun release() { runCatching { helper.removeListener(listener); helper.release() } }
            helper.initialize(
                {
                    helper.addListener(listener)
                    try {
                        val dest = RouteInfo(latitude = to.lat, longitude = to.lng, poiName = to.name, poiId = "ion", poiSubId = "0", address = to.name)
                        val stops = via.take(2)
                        if (stops.isEmpty()) {
                            helper.requestRoute(dest)
                        } else {
                            val idx = listOf(WaypointIndex.FIRST, WaypointIndex.SECOND)
                            helper.requestRouteWithWaypoints(RouteWithWaypointsRequest(dest, stops.mapIndexed { i, w ->
                                RequestWaypointInfo(latitude = w.lat, longitude = w.lng, poiName = w.name, waypointIndex = idx[i], poiId = "ion-w$i", address = w.name, poiSubId = "0")
                            }))
                        }
                        Log.d(TAG, "requestRoute sent: ${to.name} (+${stops.size} 경유)")   // 좌표는 로그에 남기지 않아요
                    } catch (e: SecurityException) {
                        finish(GuideResult.Failed(GuideFail.NO_PERMISSION)); release(); return@initialize
                    } catch (e: Exception) {
                        Log.w(TAG, "requestRoute failed: ${e.javaClass.simpleName}")
                        finish(GuideResult.Failed(GuideFail.OTHER)); release(); return@initialize
                    }
                    main.postDelayed({ finish(GuideResult.Sent) }, 3_000)
                    main.postDelayed({ release() }, 20_000)
                },
                { e -> Log.d(TAG, "navi init failed: $e"); main.post { finish(GuideResult.Failed(GuideFail.NO_NAVI)) } },
            )
        }
    }

    private fun reason(code: NaviErrorCode): GuideFail {
        val n = code.name
        return when {
            n.contains("LOW_BATTERY") -> GuideFail.LOW_BATTERY
            n.contains("OVER_DISTANCE") -> GuideFail.TOO_FAR
            n.contains("TOO_SHORT") -> GuideFail.TOO_CLOSE
            else -> GuideFail.OTHER
        }
    }
}
