package app.roadmemory.car.debug

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.roadmemory.car.family.FamilyPlace
import app.roadmemory.car.family.RouteFail
import app.roadmemory.car.family.RouteLauncher
import app.roadmemory.car.family.RouteResult

/**
 * 디버그 전용: 차의 내비 앱에 길을 보내요(NaviHelper.requestRoute, 권한 NAVI_ROUTE_SEARCH는 디버그 매니페스트에만 있어요).
 * 운전자가 "경로 안내"를 누른 뒤에만 불러요. 내비는 길을 받으면 경로 후보와 "안내 시작" 버튼(몇 초 카운트다운)을 열고, 운전자가
 * 고르지 않으면 저절로 안내를 시작해요(에뮬레이터에서 확인). 목적지 이벤트(onRouteStarted)는 요청 뒤 11초쯤에야 와서,
 * 요청이 받아들여지고 3초 안에 오류(onError)가 없으면 "보냈어요"를 먼저 말해요. 그 뒤 20초 안에 오류가 오면 이유로 바꿔요.
 * 20초가 지나면 리스너를 떼고 SDK를 놓아요.
 */
class NaviRoute(private val ctx: Context) : RouteLauncher {
    private val main = Handler(Looper.getMainLooper())

    override fun guide(to: FamilyPlace, done: (RouteResult) -> Unit) {
        main.post {
            var failed = false   // 실패는 마지막 말이에요
            var sent = false     // "보냈어요"는 한 번만 말해요
            fun finish(r: RouteResult) {
                if (failed) return
                when (r) {
                    is RouteResult.Failed -> { failed = true; done(r) }
                    RouteResult.Sent -> if (!sent) { sent = true; done(r) }
                }
            }

            val helper = runCatching { NaviHelper(ctx.applicationContext) }.getOrElse {
                Log.w(TAG, "NaviHelper create failed: ${it.javaClass.simpleName}"); finish(RouteResult.Failed(RouteFail.NO_NAVI)); return@post
            }
            val listener = object : NaviHelperEventListener {
                override fun onRouteStarted(info: RouteStartInfo) { Log.d(TAG, "family route started"); main.post { finish(RouteResult.Sent) } }
                override fun onDestinationInfo(info: DestinationInfo) { Log.d(TAG, "family destination received"); main.post { finish(RouteResult.Sent) } }
                override fun onRoutingCompleted() { Log.d(TAG, "family routing completed"); main.post { finish(RouteResult.Sent) } }
                override fun onError(code: NaviErrorCode) { Log.d(TAG, "family route error $code"); main.post { finish(RouteResult.Failed(reason(code))) } }
            }
            fun release() { runCatching { helper.removeListener(listener); helper.release() } }

            helper.initialize(
                {
                    helper.addListener(listener)
                    try {
                        // 좌표는 로그에 남기지 않아요
                        helper.requestRoute(RouteInfo(latitude = to.lat, longitude = to.lng, poiName = to.alias, poiId = "family", poiSubId = "0", address = to.alias))
                        Log.d(TAG, "family requestRoute sent")
                    } catch (e: SecurityException) {
                        finish(RouteResult.Failed(RouteFail.NO_PERMISSION)); release(); return@initialize
                    } catch (e: Exception) {
                        Log.w(TAG, "family requestRoute failed: ${e.javaClass.simpleName}")
                        finish(RouteResult.Failed(RouteFail.OTHER)); release(); return@initialize
                    }
                    main.postDelayed({ finish(RouteResult.Sent) }, 3_000)
                    main.postDelayed({ release() }, 20_000)
                },
                { e -> Log.d(TAG, "family navi init failed: $e"); main.post { finish(RouteResult.Failed(RouteFail.NO_NAVI)) } },
            )
        }
    }

    private fun reason(code: NaviErrorCode): RouteFail {
        val n = code.name
        return when {
            n.contains("LOW_BATTERY") -> RouteFail.LOW_BATTERY
            n.contains("OVER_DISTANCE") -> RouteFail.TOO_FAR
            n.contains("TOO_SHORT") -> RouteFail.TOO_CLOSE
            else -> RouteFail.OTHER
        }
    }

    private companion object { const val TAG = "RoadMemory.Debug" }
}
