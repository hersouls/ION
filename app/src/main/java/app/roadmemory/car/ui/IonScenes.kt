package app.roadmemory.car.ui

import androidx.compose.runtime.compositionLocalOf
import app.roadmemory.car.Pane
import app.roadmemory.car.engine.CardKind
import app.roadmemory.car.engine.Mode
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** ION 디스플레이가 지금 상황에 맞춰 그리는 장면. ORB = 평소의 구, 나머지는 같은 도트로 그린 그림이에요. [alwaysMoves] = 서 있지 않아도(주행 중에도) 움직이는 장면. */
enum class SceneKind(val alwaysMoves: Boolean) {
    ORB(false),
    /** 주행: 굴러가는 타이어와 뒤로 흘러가는 길 */
    TIRE(true),
    /** 충전: 배터리가 채워져요(충전 중이면 앞으로 전기가 흘러요) */
    BATTERY(false),
    /** 가족 · 뒷좌석: 위에서 본 차와 사람이 앉은 자리 */
    SEATS(false),
    /** 점검: 옆에서 본 차를 훑는 빛 */
    CAR(false),
    /** 여행 기록: 굽이진 길과 지나온 곳 */
    ROAD(false),
}

/**
 * 한 장면이 그릴 것. [soc]와 [seats]는 차가 준 값이에요(모르면 null / 빈 자리로 그려요, 지어내지 않아요).
 * [seats]는 운전석 · 조수석 · 뒷좌석 왼쪽 · 뒷좌석 오른쪽 차례로 사람이 앉아 있는지예요.
 */
data class IonScene(
    val kind: SceneKind = SceneKind.ORB,
    val soc: Int? = null,
    val charging: Boolean = false,
    val seats: List<Boolean> = List(4) { false },
) {
    companion object {
        /**
         * 지금 상황에 맞는 장면. 듣는 중 · 생각 중에는 어디서나 구가 먼저라서 이 함수 밖(IonOrb)에서 구로 바꿔요.
         * 달리는 중 = 타이어(안전벨트 · 뒷좌석 카드가 뜨면 자리), 서 있을 때는 열린 화면(충전 · 가족 · 점검 · 여행 기록)을 따라가요.
         */
        fun pick(connected: Boolean, mode: Mode, pane: String?, card: CardKind?, soc: Int?, seats: List<Boolean>, arrived: Boolean): IonScene {
            fun s(kind: SceneKind, charging: Boolean = false) = IonScene(kind, soc, charging, seats)
            if (!connected) return IonScene()
            if (mode == Mode.DRIVING) return if (card == CardKind.SEATBELT_CHECK || card == CardKind.REAR_SEAT) s(SceneKind.SEATS) else s(SceneKind.TIRE)
            return when (pane) {
                Pane.CHARGE -> s(SceneKind.BATTERY, mode == Mode.CHARGING)
                Pane.FAMILY -> s(SceneKind.SEATS)
                Pane.CHECK -> s(SceneKind.CAR)
                Pane.TRIPS -> s(SceneKind.ROAD)
                else -> when {
                    mode == Mode.CHARGING -> s(SceneKind.BATTERY, true)
                    card == CardKind.REAR_ALERT -> s(SceneKind.SEATS)
                    card == CardKind.VEHICLE_CHECK -> s(SceneKind.CAR)
                    arrived -> s(SceneKind.ROAD)
                    else -> IonScene()
                }
            }
        }
    }
}

/** 화면 전체가 같은 장면을 쓰도록 위에서 내려줘요(IonShell 안의 디스플레이가 읽어요). */
internal val LocalIonScene = compositionLocalOf { IonScene() }

/**
 * 장면의 도트 농도를 정하는 순수 계산(JVM 단위 테스트로 확인해요). 21x21 격자를 가로 · 세로 -1~1 좌표로 보고,
 * 도형을 거리 함수로 그려서 도트 농도(0~1)를 돌려줘요. 가장자리가 부드럽게 이어져서 돌아가고 흐르는 동안에도 경계가 깜박이지 않아요.
 * 구와 같이 옅은 도트는 배경에 녹아요([IonOrb.dotAlpha]).
 */
object IonScenes {
    const val N = 21

    /** 움직임을 끈 기기 · 움직이지 않는 때의 멈춘 장면 시각. */
    const val POSE = 0.9f

    /** [kind]를 멈춰 둘 때 가장 잘 읽히는 시각(길은 앞머리 점이 절반쯤, 점검은 빛이 차 한가운데). */
    fun pose(kind: SceneKind): Float = when (kind) {
        SceneKind.ROAD -> 4.2f
        SceneKind.CAR -> 1.85f
        else -> POSE
    }

    /** 바퀴가 도는 빠르기(라디안/초). 길이 흘러가는 빠르기는 이 값에 바퀴 반지름을 곱한 만큼이라 미끄러지지 않고 굴러가는 걸로 보여요. */
    const val ROLL = 2.2f

    private const val TAU = 6.2831855f
    private const val PI_F = 3.1415927f
    private const val TIRE_CY = -0.06f
    private const val TIRE_R = 0.84f
    private const val GROUND_Y = 0.90f

    /** [i]번째 칸 가운데의 좌표(-1~1). */
    fun coord(i: Int): Float = (i - (N - 1) / 2) / ((N - 1) / 2f)

    /** [c]열 [r]줄 도트의 농도(0~1). 구([SceneKind.ORB])는 [IonOrb]가 그려서 여기서는 0이에요. */
    fun density(scene: IonScene, c: Int, r: Int, t: Float): Float {
        val x = coord(c)
        val y = coord(r)
        val v = when (scene.kind) {
            SceneKind.ORB -> 0f
            SceneKind.TIRE -> tire(x, y, t)
            SceneKind.BATTERY -> battery(x, y, t, scene)
            SceneKind.SEATS -> seats(x, y, t, scene.seats)
            SceneKind.CAR -> car(x, y, t)
            SceneKind.ROAD -> road(x, y, t)
        }
        // 도트마다 아주 작게 숨 쉬어요(±4%)
        return (v * (0.96f + 0.04f * sin(t * 0.9f + IonOrb.phase(c, r, 3) * TAU))).coerceIn(0f, 1f)
    }

    // ───────── 도형 도구 ─────────

    private fun smoothstep(a: Float, b: Float, x: Float): Float {
        val v = ((x - a) / (b - a)).coerceIn(0f, 1f)
        return v * v * (3f - 2f * v)
    }

    private fun fract(x: Float): Float = x - floor(x)

    /** 선(중심에서 [d]만큼 떨어진 곳)의 농도: 가운데 [half]까지는 1, 그 밖으로 0.1 안에 0으로 이어져요. */
    private fun stroke(d: Float, half: Float = 0.055f, soft: Float = 0.05f): Float = ((half + soft - abs(d)) / (2f * soft)).coerceIn(0f, 1f)

    /** 부호 있는 거리([d] < 0이 안쪽)로 채운 도형의 농도. */
    private fun fill(d: Float): Float = ((0.05f - d) / 0.10f).coerceIn(0f, 1f)

    private fun sdCircle(x: Float, y: Float, cx: Float, cy: Float, rad: Float): Float = hypot(x - cx, y - cy) - rad

    private fun sdBox(x: Float, y: Float, cx: Float, cy: Float, hw: Float, hh: Float, rad: Float): Float {
        val qx = abs(x - cx) - (hw - rad)
        val qy = abs(y - cy) - (hh - rad)
        return hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - rad
    }

    // ───────── 주행: 굴러가는 타이어 ─────────

    private fun tire(x: Float, y: Float, t: Float): Float {
        val dy = y - TIRE_CY
        val rho = hypot(x, dy)
        val th = atan2(dy, x)
        val rot = ROLL * t
        // 트레드: 블록이 돌아가요
        val band = smoothstep(0.60f, 0.68f, rho) * (1f - smoothstep(0.82f, 0.90f, rho))
        val block = smoothstep(0.10f, 0.90f, 0.5f + 0.5f * cos(12f * (th - rot)))
        val tread = band * (0.40f + 0.60f * block)
        // 옆면은 옅게, 림은 가는 고리, 바퀴살 다섯 개가 허브에서 뻗어요
        val side = smoothstep(0.44f, 0.50f, rho) * (1f - smoothstep(0.60f, 0.66f, rho)) * 0.26f
        val rim = stroke(rho - 0.46f, 0.05f) * 0.85f
        var spokes = 0f
        for (k in 0 until 5) {
            val ang = th - rot - k * TAU / 5f
            val a = ang - TAU * floor((ang + PI_F) / TAU)
            if (cos(a) > 0f) {
                val perp = rho * sin(a)
                val reach = smoothstep(0.08f, 0.16f, rho) * (1f - smoothstep(0.42f, 0.48f, rho))
                spokes = max(spokes, stroke(perp, 0.045f, 0.07f) * reach)   // 돌아가는 가는 선이라 가장자리를 넓게 풀어서 프레임 사이에 깜박이지 않게 해요
            }
        }
        val hub = fill(rho - 0.11f)
        // 길: 바퀴가 구르는 만큼 왼쪽으로 흘러가는 점선(양 끝은 흰색에 녹아요)
        val u = x + ROLL * TIRE_R * t
        val dash = smoothstep(0.15f, 0.85f, 0.5f + 0.5f * cos(TAU * u / 0.9f))
        val ground = stroke(y - GROUND_Y, 0.05f) * dash * 0.55f * (1f - smoothstep(0.70f, 1.0f, abs(x)))
        return maxOf(tread, side, rim, spokes, hub, ground)
    }

    // ───────── 충전: 배터리 ─────────

    private const val BAT_X0 = -0.65f
    private const val BAT_W = 1.24f

    private fun battery(x: Float, y: Float, t: Float, scene: IonScene): Float {
        val f = (scene.soc ?: 0).coerceIn(0, 100) / 100f
        val body = stroke(sdBox(x, y, -0.06f, 0f, 0.74f, 0.44f, 0.12f)) * 0.9f
        val nub = fill(sdBox(x, y, 0.76f, 0f, 0.08f, 0.17f, 0.04f)) * 0.9f
        val inner = fill(sdBox(x, y, -0.03f, 0f, 0.62f, 0.30f, 0.08f))
        val fillEnd = BAT_X0 + BAT_W * f
        val cov = (1f - smoothstep(fillEnd - 0.05f, fillEnd + 0.05f, x)) * smoothstep(0f, 0.02f, f)
        var level = 0.62f
        if (scene.charging) {
            // 충전 중: 채워진 곳을 따라 왼쪽에서 오른쪽으로 전기가 흐르고, 채워지는 끝이 숨 쉬어요
            val s = fract(t / 2.4f)
            val pp = BAT_X0 + (fillEnd - BAT_X0) * s
            val dp = (x - pp) / 0.15f
            level += 0.35f * (1f - 0.5f * s) * exp(-dp * dp)
            val df = (x - fillEnd) / 0.10f
            level += 0.30f * (0.5f + 0.5f * sin(t * 4.2f)) * exp(-df * df)
        } else {
            level += 0.04f * sin(t * 0.8f)
        }
        return maxOf(body, nub, inner * cov * level)
    }

    // ───────── 가족 · 뒷좌석: 위에서 본 차 ─────────

    private val SEAT_X = floatArrayOf(-0.17f, 0.17f, -0.17f, 0.17f)
    private val SEAT_Y = floatArrayOf(-0.22f, -0.22f, 0.30f, 0.30f)

    private fun seats(x: Float, y: Float, t: Float, occupied: List<Boolean>): Float {
        val shell = stroke(sdBox(x, y, 0f, 0f, 0.42f, 0.90f, 0.30f)) * 0.66f
        val front = fill(sdBox(x, y, 0f, -0.55f, 0.26f, 0.03f, 0.02f)) * 0.5f
        val rear = fill(sdBox(x, y, 0f, 0.64f, 0.26f, 0.03f, 0.02f)) * 0.5f
        var v = maxOf(shell, front, rear)
        for (i in 0 until 4) {
            val d = sdBox(x, y, SEAT_X[i], SEAT_Y[i], 0.10f, 0.12f, 0.05f)
            val on = occupied.getOrElse(i) { false }
            v = max(v, if (on) fill(d) * (0.95f + 0.05f * sin(t * 1.4f + i)) else stroke(d, 0.04f) * 0.55f)
        }
        return v
    }

    // ───────── 점검: 옆에서 본 차를 훑는 빛 ─────────

    private val WHEEL_X = floatArrayOf(-0.52f, 0.52f)

    private fun car(x: Float, y: Float, t: Float): Float {
        val lower = sdBox(x, y, 0f, 0.22f, 0.88f, 0.20f, 0.14f)
        val cabin = sdBox(x, y, -0.08f, -0.10f, 0.50f, 0.24f, 0.22f)
        // 바퀴가 들어가는 자리에서는 차체 선을 비워요
        val arch = min(smoothstep(0.24f, 0.30f, hypot(x + 0.52f, y - 0.42f)), smoothstep(0.24f, 0.30f, hypot(x - 0.52f, y - 0.42f)))
        val shell = stroke(min(lower, cabin)) * arch
        var wheels = 0f
        for (wx in WHEEL_X) {
            wheels = maxOf(wheels, stroke(sdCircle(x, y, wx, 0.42f, 0.22f)), fill(sdCircle(x, y, wx, 0.42f, 0.06f)))
        }
        // 가로로 훑는 빛: 지나가는 곳의 선이 진해지고 차 안쪽이 옅게 비쳐요
        val xs = -1.1f + 2.2f * fract(t / 3.4f)
        val dx = (x - xs) / 0.22f
        val glow = exp(-dx * dx)
        val body = fill(min(lower, cabin)) * arch
        return max(max(shell, wheels) * (0.70f + 0.30f * glow), body * glow * 0.22f)
    }

    // ───────── 여행 기록: 굽이진 길 ─────────

    private fun roadY(x: Float): Float = -0.5f * x + 0.26f * sin(4.4f * x + 0.3f)

    private fun roadSlope(x: Float): Float = -0.5f + 0.26f * 4.4f * cos(4.4f * x + 0.3f)

    private fun road(x: Float, y: Float, t: Float): Float {
        val d = (y - roadY(x)) / hypot(1f, roadSlope(x))
        val edges = 1f - smoothstep(0.80f, 0.92f, abs(x))
        val line = stroke(d, 0.045f) * edges
        // 지나온 길은 진하고 앞으로 갈 길은 옅은 점선이에요. 앞머리 점이 7초에 한 번 길을 따라가요(끝에서는 사라졌다가 처음부터).
        val ph = fract(t / 7f)
        val life = smoothstep(0f, 0.06f, ph) * (1f - smoothstep(0.92f, 1f, ph))
        val xm = -0.9f + 1.8f * ph
        val traveled = 1f - smoothstep(xm - 0.02f, xm + 0.03f, x)
        val dash = smoothstep(0.35f, 0.65f, 0.5f + 0.5f * cos(TAU * x / 0.34f))
        val path = line * (0.60f * dash * (1f - traveled * life) + 0.95f * traveled * life)
        val head = fill(sdCircle(x, y, xm, roadY(xm), 0.09f)) * life
        val start = stroke(sdCircle(x, y, -0.85f, roadY(-0.85f), 0.10f), 0.04f) * 0.7f
        val end = fill(sdCircle(x, y, 0.85f, roadY(0.85f), 0.08f)) * 0.7f
        return maxOf(path, head, start, end)
    }
}
