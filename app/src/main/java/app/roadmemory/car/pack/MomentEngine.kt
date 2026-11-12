package app.roadmemory.car.pack

import app.roadmemory.car.data.CarPart
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.DriverCabin
import app.roadmemory.car.data.EnvSignals
import app.roadmemory.car.data.GearPos
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.SeatPos
import app.roadmemory.car.data.WiperLevel
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.engine.ModeEngine

/** 한 번에 보는 차의 값. 시계는 now(밀리초)로 받아서 JVM에서 시험해요. */
data class MomentInput(
    val car: CarSnapshot = CarSnapshot(),
    val nav: NavState = NavState(),
    val env: EnvSignals = EnvSignals(),
    val cabin: DriverCabin = DriverCabin(),
    /** 이번 주행을 시작하고 지난 분(모르면 null). */
    val driveMinutes: Int? = null,
    val now: Long = 0L,
)

/** reaction = 사람이 한 동작 뒤에 값이 바뀌면(작동시) · push = ION이 값을 지켜보다가 때가 되면 먼저(푸시형). ION-Link의 `mode`와 같아요. */
enum class MomentMode { REACTION, PUSH }

/** 알아차린 순간 하나. part는 열린 곳, destination은 도착한 곳(문장을 채울 때 써요). */
data class Fired(val moment: String, val mode: MomentMode, val part: CarPart? = null, val destination: String? = null)

/**
 * 차의 값이 바뀌는 것을 보고 "시그니처 송이 나올 순간"을 골라요(순수 함수라 MomentEngineTest가 확인해요). 어떤 순간이 있는지는 ION-Link(`mo.*`)가 정하고,
 * 여기는 그 순간의 `signal` 문장 그대로를 값의 변화로 옮겨요:
 *  - 작동시(reaction): 값이 **바뀐 순간**에만 나와요. 바뀌기 전 값을 알고 있어야 해요(앱을 켜자마자 처음 들어온 값은 변화가 아니에요).
 *  - 푸시형(push): 조건이 **처음 맞은 순간**에 한 번 나오고, 조건이 풀리기 전에는 다시 나오지 않아요(풀리면 다시 기다려요).
 *  - 같은 순간은 작동시 20초 · 푸시형 10분 안에 다시 나오지 않고, 어떤 순간이든 2.5초 안에 이어서 나오지 않아요. 켠 지 5초 동안은 조용히 값만 봐요.
 *  - 값을 모르면(null) 조건도 모르는 것이라 아무것도 하지 않아요.
 */
object MomentEngine {
    const val WARMUP_MS = 5_000L
    const val REACTION_GAP_MS = 20_000L
    const val PUSH_GAP_MS = 10 * 60_000L
    const val ANY_GAP_MS = 2_500L
    const val NEAR_SECONDS = 600
    const val SOC_LOW = 20
    const val DEST_SOC_LOW = 10
    const val CHARGE_DONE_SOC = 80
    const val REST_MINUTES = 120
    const val COLD_C = 0f
    const val TEMP_STEP = 1f

    /** 한꺼번에 여럿이 맞으면 앞에 있는 것이 먼저예요(도착이 주차보다, 배터리 부족이 나머지보다). */
    val PRIORITY = listOf("soc_low", "arrive", "near", "charge_done", "rest", "cold", "start", "board", "charge", "door", "park", "wiper", "climate")
    val MODES: Map<String, MomentMode> = mapOf(
        "board" to MomentMode.REACTION, "start" to MomentMode.REACTION, "door" to MomentMode.REACTION, "park" to MomentMode.REACTION,
        "wiper" to MomentMode.REACTION, "climate" to MomentMode.REACTION, "charge" to MomentMode.REACTION,
        "near" to MomentMode.PUSH, "arrive" to MomentMode.PUSH, "soc_low" to MomentMode.PUSH, "charge_done" to MomentMode.PUSH,
        "rest" to MomentMode.PUSH, "cold" to MomentMode.PUSH,
    )

    data class State(
        val prev: MomentInput? = null,
        val startedAt: Long? = null,
        val lastFired: Map<String, Long> = emptyMap(),
        val lastAny: Long? = null,
        /** 지금 조건이 맞아 있는 푸시형(이미 나왔거나 간격 때문에 건너뛴 것). 조건이 풀리면 빠져요. */
        val active: Set<String> = emptySet(),
        /** 이번 주행에서 내비 경로가 있었는지(도착을 알아차리는 데 써요). */
        val routeSeen: Boolean = false,
        val lastDestination: String? = null,
        /** 에어컨 · 히터 온도의 기준값. 이 값에서 1도 이상 벗어나면 "바꿨다"고 봐요. */
        val tempRef: Float? = null,
    )

    fun step(s: State, cur: MomentInput): Pair<State, List<Fired>> {
        val prev = s.prev
        val started = s.startedAt ?: cur.now
        val warm = cur.now - started < WARMUP_MS
        val mode = ModeEngine.modeOf(cur.car)
        val c = cur.car
        val candidates = LinkedHashMap<String, Fired>()
        fun add(id: String, part: CarPart? = null, destination: String? = null) { candidates[id] = Fired(id, MODES.getValue(id), part, destination) }

        var tempRef = s.tempRef
        cur.cabin.setTempC?.let { t ->
            if (tempRef == null) tempRef = t
            else if (Math.abs(t - tempRef!!) >= TEMP_STEP) { if (prev != null) add("climate"); tempRef = t }
        }
        var routeSeen = s.routeSeen
        var lastDest = s.lastDestination
        if (cur.nav.routeActive) { routeSeen = true; cur.nav.destinationName?.takeIf { it.isNotBlank() }?.let { lastDest = it } }

        if (prev != null) {
            val p = prev.car
            if (p.seats[SeatPos.DRIVER]?.occupied == false && c.seats[SeatPos.DRIVER]?.occupied == true) add("board")
            if (p.gear == GearPos.PARK && c.gear == GearPos.DRIVE) add("start")
            if (mode != Mode.DRIVING) CarPart.entries.firstOrNull { part -> p.open[part] == false && c.isOpen(part) }?.let { add("door", part = it) }
            val wasDriving = p.gear == GearPos.DRIVE || p.gear == GearPos.REVERSE
            if (wasDriving && c.gear == GearPos.PARK) {
                add("park")
                if (routeSeen) add("arrive", destination = lastDest)
            }
            if (wiperKnown(prev.env) && !wiperActive(prev.env) && wiperActive(cur.env)) add("wiper")
            if (mode != Mode.DRIVING && ((p.chargePortOpen == false && c.chargePortOpen == true) || (p.plugConnected == false && c.plugConnected == true))) add("charge")
            if (mode == Mode.CHARGING && p.soc != null && c.soc != null && p.soc < CHARGE_DONE_SOC && c.soc >= CHARGE_DONE_SOC) add("charge_done")
        }
        // 도착한 뒤에는 표지를 비워요(경로가 아직 남아 있으면 다음 정차에서 다시 도착으로 봐요)
        if (prev != null && (prev.car.gear == GearPos.DRIVE || prev.car.gear == GearPos.REVERSE) && c.gear == GearPos.PARK) {
            routeSeen = cur.nav.routeActive
            if (!cur.nav.routeActive) lastDest = null
        }

        // 푸시형: 조건이 처음 맞은 순간(null = 모르는 값, 아무것도 정하지 않아요)
        val conditions = mapOf(
            "near" to if (!cur.nav.routeActive) false else cur.nav.remainTimeS?.let { it in 0..NEAR_SECONDS },
            "soc_low" to socLow(cur, mode),
            "rest" to cur.driveMinutes.let { if (it == null) false else it >= REST_MINUTES },
            "cold" to c.outsideTempC?.let { it <= COLD_C },
        )
        var active = s.active
        for ((id, cond) in conditions) {
            when (cond) {
                true -> if (id !in active && !warm) { add(id); active = active + id }
                false -> active = active - id
                null -> Unit
            }
        }

        // 간격 · 풀어 두는 시간
        val fired = ArrayList<Fired>()
        var lastFired = s.lastFired
        var lastAny = s.lastAny
        val anyBlocked = s.lastAny?.let { cur.now - it < ANY_GAP_MS } == true   // 앞 걸음에서 막 나왔으면 이번 걸음은 쉬어요(같은 걸음에 함께 맞은 것은 모두 후보예요)
        if (!warm && !anyBlocked) for (id in PRIORITY) {
            val f = candidates[id] ?: continue
            val gap = if (f.mode == MomentMode.PUSH) PUSH_GAP_MS else REACTION_GAP_MS
            if (lastFired[id]?.let { cur.now - it < gap } == true) continue
            fired += f
            lastFired = lastFired + (id to cur.now)
            lastAny = cur.now
        }
        return State(cur, started, lastFired, lastAny, active, routeSeen, lastDest, tempRef) to fired
    }

    /** 배터리가 20% 이하이거나, 경로가 있고 도착 예상 배터리가 10% 이하. 충전 중이면 조건이 아니에요. 두 값을 다 모르면 null. */
    private fun socLow(cur: MomentInput, mode: Mode): Boolean? {
        if (mode == Mode.CHARGING) return false
        val soc = cur.car.soc
        val est = if (cur.nav.routeActive) cur.nav.estimatedSocAtDest else null
        if (soc == null && est == null) return null
        if ((soc != null && soc <= SOC_LOW) || (est != null && est <= DEST_SOC_LOW)) return true
        return if (soc != null) false else null
    }

    fun wiperKnown(e: EnvSignals) = e.wiperOn != null || e.wiperLevel != null

    /** 와이퍼가 켜져 있는지: 앞유리가 쓸고 있거나(워셔액 한 번 쓸기 MIST는 빼요) 스위치가 간헐 · 연속이에요. 자동(AUTO)의 대기는 켜진 것이 아니에요. */
    fun wiperActive(e: EnvSignals): Boolean {
        val lvl = e.wiperLevel
        val manual = lvl == WiperLevel.INTERMITTENT || lvl == WiperLevel.CONTINUOUS
        return manual || (e.wiperOn == true && lvl != WiperLevel.MIST && lvl != WiperLevel.OFF)
    }

    /** 한꺼번에 알아차린 것 중 이 팩이 소리를 가진 가장 앞선 순간(없으면 null). */
    fun choose(fired: List<Fired>, has: (String) -> Boolean): Fired? =
        PRIORITY.firstNotNullOfOrNull { id -> fired.firstOrNull { it.moment == id && has(id) } }
}
