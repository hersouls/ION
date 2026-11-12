package app.roadmemory.car.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Proxy

/**
 * 주행 규제(AAOS `CarUxRestrictions`). Pleos 가이드(driving-restriction-policy · development-guide)는 앱이
 * `CarUxRestrictionsManager.registerListener()`로 받은 값(`isRequiresDistractionOptimization` · `getActiveRestrictions`)을 기준으로
 * 주행 중 기능을 줄이라고 해요. 플래그는 지역마다 달라서(북미는 키보드 · 설정 · 영상을 막고, 유럽은 키보드를 허용하는 식) 앱이 정하지 않고
 * 플랫폼이 내려 주는 값을 그대로 따라요. 못 읽으면(차가 아니거나 서비스가 없으면) [known]이 false이고 기어로만 모드를 나눠요.
 */
data class UxLimit(
    /** true = 차에서 값을 받았어요. false = 모르는 상태(기어로만 판단). */
    val known: Boolean = false,
    /** 주행 중 제한이 필요한 상태(달리는 중). 기어와 상관없이 주행 화면으로 가요. */
    val requiresDo: Boolean = false,
    /** 켜진 제한 플래그의 합(`getActiveRestrictions`). */
    val flags: Int = 0,
    /** LIMIT_CONTENT일 때 목록에 보여 줄 수 있는 항목 수. */
    val maxItems: Int? = null,
    /** LIMIT_STRING_LENGTH일 때 글 한 줄의 최대 길이. */
    val maxStringLength: Int? = null,
    /** LIMIT_CONTENT일 때 들어갈 수 있는 화면 깊이. */
    val maxDepth: Int? = null,
) {
    fun has(flag: Int) = flags and flag != 0
    val noDialpad: Boolean get() = has(NO_DIALPAD)
    val noFiltering: Boolean get() = has(NO_FILTERING)
    val limitStringLength: Boolean get() = has(LIMIT_STRING_LENGTH)
    val noKeyboard: Boolean get() = has(NO_KEYBOARD)
    val noVideo: Boolean get() = has(NO_VIDEO)
    val limitContent: Boolean get() = has(LIMIT_CONTENT)
    val noSetup: Boolean get() = has(NO_SETUP)

    companion object {
        // android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_*
        const val BASELINE = 0
        const val NO_DIALPAD = 1
        const val NO_FILTERING = 2
        const val LIMIT_STRING_LENGTH = 4
        const val NO_KEYBOARD = 8
        const val NO_VIDEO = 16
        const val LIMIT_CONTENT = 32
        const val NO_SETUP = 64
        const val NO_TEXT_MESSAGE = 128
        const val NO_VOICE_TRANSCRIPTION = 256
        const val FULLY_RESTRICTED = 511
    }
}

/** 규제 값으로 글 · 목록을 줄이는 순수 규칙(시험할 수 있게 안드로이드와 떼어 두었어요). */
object UxRules {
    private const val ELLIPSIS = "…"

    fun of(requiresDo: Boolean, flags: Int, maxItems: Int, maxStringLength: Int, maxDepth: Int): UxLimit = UxLimit(
        known = true, requiresDo = requiresDo, flags = flags,
        maxItems = maxItems.takeIf { it > 0 }, maxStringLength = maxStringLength.takeIf { it > 0 }, maxDepth = maxDepth.takeIf { it > 0 },
    )

    /** LIMIT_STRING_LENGTH가 켜져 있으면 최대 길이에서 끊고 "…"를 붙여요. 아니면 그대로예요. */
    fun clip(text: String, limit: UxLimit): String {
        val max = limit.maxStringLength ?: return text
        if (!limit.limitStringLength || text.length <= max) return text
        return text.take((max - 1).coerceAtLeast(1)) + ELLIPSIS
    }

    /** LIMIT_CONTENT가 켜져 있으면 목록을 최대 항목 수에서 잘라요. */
    fun <T> cap(items: List<T>, limit: UxLimit): List<T> {
        val max = limit.maxItems ?: return items
        return if (limit.limitContent && items.size > max) items.take(max) else items
    }

    /** 로그 · 설명용 한 줄: "제한 필요 · 키보드 없음 · 영상 없음 · 설정 없음 · 글 120자 · 목록 21개". 제한이 없으면 "제한 없음". */
    fun describe(limit: UxLimit): String {
        if (!limit.known) return "모름"
        val parts = buildList {
            add(if (limit.requiresDo) "제한 필요" else "제한 없음")
            if (limit.noKeyboard) add("키보드 없음")
            if (limit.noDialpad) add("다이얼 없음")
            if (limit.noFiltering) add("거르기 없음")
            if (limit.noVideo) add("영상 없음")
            if (limit.noSetup) add("설정 없음")
            if (limit.has(UxLimit.NO_TEXT_MESSAGE)) add("문자 없음")
            if (limit.has(UxLimit.NO_VOICE_TRANSCRIPTION)) add("음성 받아쓰기 없음")
            if (limit.limitStringLength) limit.maxStringLength?.let { add("글 ${it}자") }
            if (limit.limitContent) { limit.maxItems?.let { add("목록 ${it}개") }; limit.maxDepth?.let { add("깊이 $it") } }
        }
        return parts.joinToString(" · ")
    }
}

/**
 * 차의 `CarUxRestrictionsManager`를 구독해요. android.car 라이브러리는 차에만 있어서 [AaosReader]처럼 리플렉션으로 열고,
 * 없거나 실패하면 한 번만 알리고 조용히 멈춰요(앱의 나머지는 그대로예요). 콜백은 메인 스레드로 와요.
 */
class UxRestrictionsReader(private val context: Context, private val onChange: (UxLimit) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var manager: Any? = null
    private var car: Any? = null

    fun start() {
        main.post {
            try {
                val carCls = Class.forName("android.car.Car")
                val c = carCls.getMethod("createCar", Context::class.java).invoke(null, context.applicationContext)
                if (c == null) { Log.w(TAG, "주행 규제: 차 서비스가 없어요"); return@post }
                car = c
                val m = carCls.getMethod("getCarManager", String::class.java).invoke(c, SERVICE)
                if (m == null) { Log.w(TAG, "주행 규제: 관리자를 못 열었어요"); return@post }
                manager = m
                (m.javaClass.getMethod("getCurrentCarUxRestrictions").invoke(m))?.let { deliver(it) }
                val listenerCls = Class.forName("android.car.drivingstate.CarUxRestrictionsManager\$OnUxRestrictionsChangedListener")
                val listener = Proxy.newProxyInstance(listenerCls.classLoader, arrayOf(listenerCls)) { proxy, method, args ->
                    when (method.name) {
                        "onUxRestrictionsChanged" -> { args?.firstOrNull()?.let { deliver(it) }; null }
                        "hashCode" -> System.identityHashCode(proxy)
                        "equals" -> proxy === args?.firstOrNull()
                        "toString" -> "UxRestrictionsListener"
                        else -> null
                    }
                }
                m.javaClass.getMethod("registerListener", listenerCls).invoke(m, listener)
            } catch (t: Throwable) {
                Log.w(TAG, "주행 규제를 못 읽었어요(이 기기에는 없을 수 있어요): ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    fun stop() {
        main.post {
            runCatching { manager?.let { it.javaClass.getMethod("unregisterListener").invoke(it) } }
            runCatching { car?.let { it.javaClass.getMethod("disconnect").invoke(it) } }
            manager = null; car = null
        }
    }

    /** `CarUxRestrictions` 객체 → [UxLimit]. 메서드가 없는 플랫폼이면 있는 값만 읽어요. */
    private fun deliver(r: Any) {
        fun int(name: String): Int = runCatching { (r.javaClass.getMethod(name).invoke(r) as? Number)?.toInt() }.getOrNull() ?: 0
        fun bool(name: String): Boolean = runCatching { r.javaClass.getMethod(name).invoke(r) as? Boolean }.getOrNull() ?: false
        val limit = UxRules.of(
            requiresDo = bool("isRequiresDistractionOptimization"), flags = int("getActiveRestrictions"),
            maxItems = int("getMaxCumulativeContentItems"), maxStringLength = int("getMaxRestrictedStringLength"), maxDepth = int("getMaxContentDepth"),
        )
        Log.i(TAG, "주행 규제: ${UxRules.describe(limit)} (flags=${limit.flags})")
        onChange(limit)
    }

    private companion object {
        const val TAG = "RoadMemory"
        /** android.car.Car.CAR_UX_RESTRICTION_SERVICE */
        const val SERVICE = "uxrestriction"
    }
}
