package app.roadmemory.car.data

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log

/**
 * 차량 표준(AAOS `android.car`)의 속성 두 가지를 읽어요: 외기온(ENV_OUTSIDE_TEMPERATURE, CAR_EXTERIOR_ENVIRONMENT 권한)과
 * 충전 플러그 연결(EV_CHARGE_PORT_CONNECTED, CAR_ENERGY_PORTS 권한). Pleos SDK에는 없는 값이에요(docs/DATA.md).
 * android.car 라이브러리는 차에만 있어서 리플렉션으로 열고, 없거나 권한이 없으면 한 번만 알리고 조용히 멈춰요(앱의 나머지는 그대로예요).
 */
class AaosReader(private val context: Context, private val onValues: (outsideC: Float?, plugConnected: Boolean?) -> Unit) {
    private var thread: HandlerThread? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        val t = HandlerThread("aaos-reader").also { it.start() }
        thread = t
        val h = Handler(t.looper)
        h.post {
            val pm = openPropertyManager()
            if (pm == null) { Log.w(TAG, "차량 표준 속성을 못 열었어요(이 기기에는 없을 수 있어요)"); return@post }
            val get = pm.javaClass.getMethod("getProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            fun read(id: Int): Any? = try {
                val v = get.invoke(pm, id, 0)
                v?.javaClass?.getMethod("getValue")?.invoke(v)
            } catch (e: Throwable) { null }
            val tick = object : Runnable {
                override fun run() {
                    if (!running) return
                    onValues((read(ENV_OUTSIDE_TEMPERATURE) as? Number)?.toFloat(), read(EV_CHARGE_PORT_CONNECTED) as? Boolean)
                    h.postDelayed(this, POLL_MS)
                }
            }
            tick.run()
        }
    }

    fun stop() {
        running = false
        thread?.quitSafely(); thread = null
    }

    private fun openPropertyManager(): Any? = try {
        val carCls = Class.forName("android.car.Car")
        val car = carCls.getMethod("createCar", Context::class.java).invoke(null, context)
        if (car == null) null else carCls.getMethod("getCarManager", String::class.java).invoke(car, "property")
    } catch (e: Throwable) { null }

    private companion object {
        const val TAG = "RoadMemory"
        const val POLL_MS = 30_000L
        const val ENV_OUTSIDE_TEMPERATURE = 0x11600703
        const val EV_CHARGE_PORT_CONNECTED = 0x1120030b
    }
}
