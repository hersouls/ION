package app.roadmemory.car.debug

import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Proxy

/**
 * 디버그 전용: Vehicle SDK 밖에서 APK가 닿을 수 있는 데이터를 한 번에 재요(DataProbe의 짝).
 *   adb shell am broadcast -a app.roadmemory.car.DEBUG_PROBE -p app.roadmemory.car --es which api
 *   adb logcat -d -s RoadMemory.Probe      # NAVI · AAOS · ANDROID 로 시작하는 줄
 * NAVI = NaviHelper가 보내는 이벤트와 그 안의 값 · AAOS = 안드로이드 오토모티브 표준 차량 속성(android.car) · ANDROID = 위치 · 센서 · 네트워크.
 * 제출용 APK(release)에는 들어가지 않아요.
 */
object CarApiProbe {
    private const val TAG = "RoadMemory.Probe"

    private fun show(v: Any?): String = when (v) {
        null -> "null"
        is Collection<*> -> "[${v.size}개] " + v.take(3).joinToString(" ; ") { show(it) }
        is IntArray -> v.contentToString()
        is Array<*> -> v.contentToString()
        else -> v.toString()
    }.replace('\n', ' ').replace('\t', ' ').take(330)

    /** 메인 스레드(방송 수신기)에서 불러요. NaviHelper는 메인에서 만들고, 나머지는 백그라운드에서 재요. */
    fun run(context: Context, done: () -> Unit) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())

        // ── NAVI: 모든 이벤트를 로그로 받고, 되묻는 요청(get*)을 전부 불러요 ──
        val helper = NaviHelper(app)
        val proxy = Proxy.newProxyInstance(NaviHelperEventListener::class.java.classLoader, arrayOf(NaviHelperEventListener::class.java)) { p, m, args ->
            when (m.name) {
                "hashCode" -> System.identityHashCode(p)
                "equals" -> p === args?.firstOrNull()
                "toString" -> "navi-probe"
                else -> { Log.d(TAG, "NAVI\t${m.name}\t${args?.joinToString(" ; ") { show(it) } ?: ""}"); null }
            }
        } as NaviHelperEventListener
        helper.initialize({
            helper.addListener(proxy)
            Log.d(TAG, "NAVI\tinitialized\t")
            for (call in listOf<() -> Unit>(
                helper::getRouteStateInfo, helper::getCurrentLocationInfo, helper::getDestinationInfo, helper::getWaypointInfo,
                helper::getTBTInfo, helper::getChargerOperatorInfo, helper::getBookmarkInfo, helper::getRecentDestinationInfo,
            )) runCatching { call() }.onFailure { Log.d(TAG, "NAVI\tcall-failed\t${it.javaClass.simpleName}: ${it.message}") }
        }, { t -> Log.d(TAG, "NAVI\tinit-failed\t${t.javaClass.simpleName}: ${t.message}") })

        Thread {
            try {
                Thread.sleep(7_000)   // 이벤트가 올 시간
                main.post { runCatching { helper.removeListener(proxy); helper.release() } }
                probeAaos(app)
                probeAndroid(app)
                Log.d(TAG, "DONE\tapi")
            } catch (e: Throwable) {
                Log.d(TAG, "ERROR\t${e.javaClass.simpleName}: ${e.message}")
            } finally { done() }
        }.start()
    }

    // ── AAOS: android.car의 차량 속성 목록과 읽어지는 값 ──
    private fun probeAaos(context: Context) {
        try {
            val carCls = Class.forName("android.car.Car")
            val car = carCls.getMethod("createCar", Context::class.java).invoke(null, context)
            if (car == null) { Log.d(TAG, "AAOS\tcreateCar\tnull"); return }
            val pm = carCls.getMethod("getCarManager", String::class.java).invoke(car, "property")
            if (pm == null) { Log.d(TAG, "AAOS\tproperty-manager\tnull"); return }
            val ids = Class.forName("android.car.VehiclePropertyIds")
            val nameOf = ids.getMethod("toString", Int::class.javaPrimitiveType)
            val list = pm.javaClass.getMethod("getPropertyList").invoke(pm) as List<*>
            Log.d(TAG, "AAOS\tpropertyList\t${list.size}개")
            val getProp = pm.javaClass.getMethod("getProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            for (cfg in list) {
                val c = cfg!!
                val id = c.javaClass.getMethod("getPropertyId").invoke(c) as Int
                val areas = c.javaClass.getMethod("getAreaIds").invoke(c) as IntArray
                val name = runCatching { nameOf.invoke(null, id) as String }.getOrDefault("?")
                val first = areas.firstOrNull() ?: 0
                val result = try {
                    val v = getProp.invoke(pm, id, first)
                    "OK\t" + show(v?.javaClass?.getMethod("getValue")?.invoke(v))
                } catch (e: Throwable) {
                    val t = e.cause ?: e
                    "FAIL\t${t.javaClass.simpleName}: ${t.message}".take(180)
                }
                Log.d(TAG, "AAOS\t$name\t0x${Integer.toHexString(id)} 칸 ${areas.size}\t$result")
            }
        } catch (e: Throwable) {
            val t = e.cause ?: e
            Log.d(TAG, "AAOS\terror\t${t.javaClass.simpleName}: ${t.message}")
        }
    }

    // ── ANDROID: 위치 · 센서 · 네트워크 ──
    @SuppressLint("MissingPermission")
    private fun probeAndroid(context: Context) {
        runCatching {
            val lm = context.getSystemService(LocationManager::class.java)
            Log.d(TAG, "ANDROID\tlocation.providers\t${lm.allProviders}")
            for (p in lm.allProviders) {
                val loc = runCatching { lm.getLastKnownLocation(p) }.getOrElse { "FAIL ${it.javaClass.simpleName}: ${it.message}" }
                Log.d(TAG, "ANDROID\tlocation.last.$p\t$loc")
            }
        }.onFailure { Log.d(TAG, "ANDROID\tlocation\terror ${it.javaClass.simpleName}: ${it.message}") }
        runCatching {
            val sm = context.getSystemService(SensorManager::class.java)
            val sensors = sm.getSensorList(Sensor.TYPE_ALL)
            Log.d(TAG, "ANDROID\tsensors\t${sensors.size}개: " + sensors.joinToString(", ") { it.name }.take(280))
        }.onFailure { Log.d(TAG, "ANDROID\tsensors\terror ${it.javaClass.simpleName}" ) }
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val net = cm.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            Log.d(TAG, "ANDROID\tnetwork\tactive=${net != null} internet=${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)} validated=${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)} wifi=${caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)} cell=${caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
        }.onFailure { Log.d(TAG, "ANDROID\tnetwork\terror ${it.javaClass.simpleName}: ${it.message}") }
    }
}
