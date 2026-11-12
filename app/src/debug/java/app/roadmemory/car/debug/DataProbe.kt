package app.roadmemory.car.debug

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.MirrorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.TurnSignalArea
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.MirrorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WheelValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 디버그 전용: Vehicle SDK의 "지금 값 읽기" API(get* · is*)를 영역별로 전부 불러서 에뮬레이터가 실제로 무엇을 돌려주는지 적어요.
 *   adb shell am broadcast -a app.roadmemory.car.DEBUG_PROBE -p app.roadmemory.car
 *   adb logcat -d -s RoadMemory.Probe        # 한 줄 = OK|FAIL|TIMEOUT|THROWN <TAB> 영역.메서드 <TAB> 칸 <TAB> 값/이유
 * OK = 값이 왔어요 · FAIL = SDK가 실패 콜백을 줬어요(권한 · 미지원) · TIMEOUT = 1.5초 안에 아무 답이 없어요 · THROWN = 호출 자체가 예외.
 * 이 파일은 제출용 APK(release)에 들어가지 않아요.
 */
object DataProbe {
    private const val TAG = "RoadMemory.Probe"
    private const val WAIT_MS = 1_500L

    private fun areasFor(type: Class<*>): List<Pair<String, Any>> = when (type) {
        // 좌석 하나씩 + 묶음(공조는 "앞 두 자리" · "네 자리 전체"처럼 묶은 칸만 받는 항목이 있어요)
        SeatArea::class.java -> listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT).map { it.name to SeatArea(it) } +
            listOf("ROW_1_BOTH" to SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT),
                "ALL_4" to SeatArea(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT))
        DoorArea::class.java -> listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT, DoorValue.HOOD, DoorValue.REAR).map { it.name to DoorArea(it) }
        WindowArea::class.java -> listOf(WindowValue.FRONT_WINDSHIELD, WindowValue.REAR_WINDSHIELD, WindowValue.ROW_1_LEFT, WindowValue.ROW_1_RIGHT, WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT, WindowValue.ROOF_TOP_1).map { it.name to WindowArea(it) }
        WheelArea::class.java -> listOf(WheelValue.LEFT_FRONT, WheelValue.RIGHT_FRONT, WheelValue.LEFT_REAR, WheelValue.RIGHT_REAR).map { it.name to WheelArea(it) }
        MirrorArea::class.java -> listOf(MirrorValue.DRIVER_LEFT, MirrorValue.DRIVER_RIGHT).map { it.name to MirrorArea(it) }
        TurnSignalArea::class.java -> listOf(TurnSignalValue.LEFT, TurnSignalValue.RIGHT).map { it.name to TurnSignalArea(it) }
        else -> if (type.simpleName == "GlobalArea") {
            // GlobalArea는 SDK 안에서만 보여서(internal) 이름으로 찾아 INSTANCE를 꺼내 써요
            runCatching { listOf("GLOBAL" to type.getField("INSTANCE").get(null)!!) }.getOrDefault(emptyList())
        } else emptyList()
    }

    private fun show(v: Any?): String = when (v) {
        null -> "null"
        is Enum<*> -> v.name
        is Array<*> -> v.contentToString()
        is Map<*, *> -> "맵 ${v.size}개: " + v.keys.take(12).joinToString(",")
        else -> v.toString()
    }.replace('\n', ' ').replace('\t', ' ').take(200)

    /** 호출 하나: (결과 종류, 값/이유) */
    private fun call(target: Any, m: Method, area: Any?, extra: Any? = null): Pair<String, String> {
        val latch = CountDownLatch(1)
        var kind = "TIMEOUT"
        var text = "1.5초 안에 답이 없어요"
        val ok: (Any?) -> Unit = { v -> kind = "OK"; text = show(v); latch.countDown() }
        val fail: (Exception) -> Unit = { e -> kind = "FAIL"; text = "${e.javaClass.simpleName}: ${e.message}".take(200); latch.countDown() }
        return try {
            when {
                area != null && extra != null -> m.invoke(target, area, extra, ok, fail)
                area != null -> m.invoke(target, area, ok, fail)
                else -> m.invoke(target, ok, fail)
            }
            latch.await(WAIT_MS, TimeUnit.MILLISECONDS)
            kind to text
        } catch (e: Throwable) {
            val c = e.cause ?: e
            "THROWN" to "${c.javaClass.simpleName}: ${c.message}".take(200)
        }
    }

    /** 백그라운드 스레드에서 돌려요(메인 스레드를 막지 않아요). */
    fun run(context: Context, done: () -> Unit = {}) {
        // Vehicle은 메인 스레드에서 만들어야 차 서비스에 연결돼요(백그라운드에서 만들면 ERROR_SERVICE_CONNECTION_FAILURE)
        val main = Handler(Looper.getMainLooper())
        var created: Vehicle? = null
        val made = CountDownLatch(1)
        if (Looper.myLooper() == Looper.getMainLooper()) {
            created = Vehicle(context.applicationContext).also { it.initialize() }   // 방송 수신기는 메인 스레드에서 불려요
        } else {
            main.post { created = Vehicle(context.applicationContext).also { it.initialize() }; made.countDown() }
            made.await(3, TimeUnit.SECONDS)
        }
        val vehicle = created ?: run { Log.d(TAG, "ERROR\tVehicle을 만들지 못했어요"); done(); return }
        Thread {
            val counts = linkedMapOf("OK" to 0, "FAIL" to 0, "TIMEOUT" to 0, "THROWN" to 0)
            try {
                Thread.sleep(2_500)   // 차 서비스에 연결될 시간
                Log.d(TAG, "START\tVehicle SDK 읽기 API 전체 시험")
                val domains = Vehicle::class.java.methods
                    .filter { it.parameterCount == 0 && it.name.startsWith("get") && it.returnType.name.startsWith("ai.pleos.playground.vehicle.api.") }
                    .sortedBy { it.name }
                for (d in domains) {
                    val domain = d.name.removePrefix("get")
                    val target = d.invoke(vehicle) ?: continue
                    val methods = d.returnType.methods
                        .filter { (it.name.startsWith("get") || it.name.startsWith("is")) && it.parameterCount in 2..4 }
                        .filter { mm -> mm.parameterTypes.takeLast(2).all { it.name == "kotlin.jvm.functions.Function1" } }
                        .sortedBy { it.name }
                    for (m in methods) {
                        val areaType = if (m.parameterCount >= 3) m.parameterTypes[0] else null
                        // 설정 온도 · 현재 온도는 단위를 하나 더 받아요(섭씨로 읽어요)
                        val extra: Any? = if (m.parameterCount == 4 && m.parameterTypes[1] == TemperatureUnit::class.java) TemperatureUnit.CELSIUS else null
                        if (m.parameterCount == 4 && extra == null) { Log.d(TAG, "SKIP\t$domain.${m.name}\t-\t다른 인자 모양"); continue }
                        val areas = if (areaType == null) listOf<Pair<String, Any?>>("-" to null) else areasFor(areaType).ifEmpty { listOf("(지원 안 하는 칸 종류 ${areaType.simpleName})" to null) }
                        for ((label, area) in areas) {
                            if (areaType != null && area == null) { Log.d(TAG, "SKIP\t$domain.${m.name}\t$label\t"); continue }
                            val (kind, text) = call(target, m, area, extra)
                            counts[kind] = (counts[kind] ?: 0) + 1
                            Log.d(TAG, "$kind\t$domain.${m.name}${if (extra != null) "(°C)" else ""}\t$label\t$text")
                        }
                    }
                }
                Log.d(TAG, "DONE\t${counts.entries.joinToString(" ") { "${it.key}=${it.value}" }}")
            } catch (e: Throwable) {
                Log.d(TAG, "ERROR\t${e.javaClass.simpleName}: ${e.message}")
            } finally {
                main.post { runCatching { vehicle.release() } }
                done()
            }
        }.start()
    }
}
