package app.roadmemory.car.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.roadmemory.car.ion.IonState
import app.roadmemory.car.ion.IonUi
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

/** 디스플레이가 글자로 보여 줄 한 값. 차가 준 값만 담아요(예: 배터리 48%). [fraction]은 0~1이고, 있으면 가는 막대로도 보여요. */
data class DisplayInfo(val label: String, val value: String, val fraction: Float? = null)

/**
 * 오르브의 도트가 살아 움직이는 방식을 정하는 순수 계산(그리는 일과 나눠서 JVM 단위 테스트로 확인해요).
 * 구의 오른쪽 위 가장자리의 한 자리(떨어져 나온 점은 그리지 않아요, 물결이 시작되는 자리로만 써요)에서 물결이 번져 나가요. 물결의 앞머리가 지나는 도트는 가장 진해져서 구 전체가 드러나고,
 * 지나가고 나면 다시 옅어져서 배경에 녹아요. 거기에 천천히 숨 쉬는 물결이 겹쳐서 일부가 물결 모양으로 사라졌다 나타나요.
 * 켜짐/꺼짐을 끊지 않고 모두 연속적인 농도로만 바뀌어서 배경과 경계선이 생기지 않아요.
 */
object IonOrb {
    private const val TAU = 6.2831855f

    /** 움직임을 끈 기기 · 주행 중에 멈춰 있는 장면의 시각: 물결이 지나간 뒤 숨이 가장 깊은 때라 구가 고요히 보여요. */
    const val STILL_T = 13.46f

    /** 물결의 앞머리가 시작 자리에서 끝까지 가는 거리(칸). */
    const val REACH = 34f

    /** 시동이 켜지는 느낌을 주는 간격(초)과 길이(초). 앱을 켠 순간에도 한 번 나와요. */
    const val STARTUP_PERIOD = 48f
    const val STARTUP_LEN = 2.8f

    /** 시동 장면의 진행(0~1). 지금이 시동 장면이 아니면 -1. */
    fun startupProgress(t: Float): Float {
        val m = t % STARTUP_PERIOD
        return if (m < STARTUP_LEN) m / STARTUP_LEN else -1f
    }

    private fun smooth(x: Float): Float { val v = x.coerceIn(0f, 1f); return v * v * (3f - 2f * v) }

    /** 시동의 마지막 "부릉" 맥박(0~1~0). 진행 [s]의 뒤쪽 38%에서 한 번 솟았다 가라앉아요. */
    fun rev(s: Float): Float {
        if (s < 0.62f) return 0f
        val x = sin(Math.PI.toFloat() * ((s - 0.62f) / 0.38f).coerceIn(0f, 1f))
        return x * x
    }

    /** 시동의 점화 앞머리: 시작 자리에서 [d]칸 떨어진 도트가 켜진 정도(0~1). 처음에는 모두 꺼지고 앞머리가 지나가면서 차례로 켜져요(경계 없이 이어져요). */
    fun ignition(d: Float, s: Float): Float {
        val front = ((s - 0.10f) / 0.52f).coerceIn(0f, 1f) * 32f
        return smooth((front - d) / 5f)
    }

    /** 시동 장면이 이 상태에서도 나오는지: 듣는 중 · 생각 중에는 입력 되먹임이 먼저라 나오지 않아요. */
    fun startupApplies(state: IonState): Boolean = state != IonState.LISTENING && state != IonState.THINKING

    /** [k]번째 고유한 박자(0 이상 1 미만). */
    fun phase(c: Int, r: Int, k: Int): Float = hash(c * 37 + r, k)

    /** 가장자리일수록(옅을수록) 큰 값(0~0.84). 떠다니는 폭을 정해요. */
    fun looseness(level: Int): Float = 1f - IonMarks.LEVELS[level.coerceIn(0, 5)]

    /** 시작 자리에서 퍼지는 물결의 앞머리 거리(칸). 듣는 중에는 더 자주 퍼져요. */
    fun front(t: Float, state: IonState): Float {
        val period = if (state == IonState.LISTENING) 1.6f else 4.5f
        return (t % period) / period * REACH
    }

    /** 시작 자리에서 [d]칸 떨어진 도트에 닿은 물결의 세기(0~1). 듣는 중에는 목소리가 클수록 세요. */
    fun ring(d: Float, t: Float, state: IonState, voice: Float): Float {
        val x = (d - front(t, state)) / 3.2f
        val amp = if (state == IonState.LISTENING) 0.5f + 0.5f * voice.coerceIn(0f, 1f) else 1f
        return exp(-x * x) * amp
    }

    /**
     * 도트 하나의 농도(0~1). 도트 자체의 농도([IonMarks.LEVELS])에 물결의 앞머리가 더해지고(옅은 도트도 앞머리가 지나는 동안은 진해져요),
     * 숨 쉬는 물결이 곱해져요(전체가 보임 ↔ 일부가 물결 모양으로 사라짐). 생각 중에는 밝기가 구를 따라 돌아요.
     * ([ox], [oy]는 물결이 시작되는 칸 자리.)
     */
    fun dotIntensity(level: Int, c: Int, r: Int, cols: Int, rows: Int, t: Float, state: IonState, voice: Float, ox: Float, oy: Float): Float {
        val b = IonMarks.LEVELS[level.coerceIn(0, 5)]
        val d = hypot(c - ox, r - oy)
        val ring = ring(d, t, state, voice)
        val swell = 0.5f + 0.5f * sin(d * 0.55f - t * 1.3f)
        val breath = 0.5f + 0.5f * sin(t * 0.35f)
        val mod = 1f - breath * 0.65f * (1f - swell)
        val twinkle = 0.94f + 0.06f * sin(t * (0.8f + 1.2f * phase(c, r, 2)) + phase(c, r, 1) * TAU)
        var e = (b + (1f - b) * ring) * mod * twinkle
        val s = if (startupApplies(state)) startupProgress(t) else -1f
        if (s >= 0f) {
            // 시동: 모두 꺼졌다가 시작 자리에서 차례로 켜지고, 마지막 맥박에서 옅은 도트까지 잠깐 진해졌다가 평소로 돌아와요
            val on = ignition(d, s)
            e = e * on + (1f - e * on) * rev(s) * 0.85f * on
        }
        if (state == IonState.THINKING) {
            val vx = c - (cols - 1) / 2f
            val vy = r - (rows - 1) / 2f
            e *= 0.35f + 0.9f * (0.5f + 0.5f * sin(atan2(vy, vx) * 2f - t * 4f))
        }
        return e.coerceIn(0f, 1f)
    }

    /**
     * 그릴 때의 진하기(0~1). 옅은 도트는 배경과 같아져 보이지 않고(흰색 도트 = 배경), 어두운 도트부터 보여요.
     * 문턱(0.14)을 빼고 다시 펴서 이어지는 곡선이라 켜짐/꺼짐의 경계선이 없어요.
     */
    fun dotAlpha(intensity: Float): Float = ((intensity - 0.14f) / 0.86f).coerceIn(0f, 1f)

    /** 도트 하나의 가로 이동(칸 단위). 아주 살짝 떠다니고, 물결의 앞머리가 지나면 시작 자리에서 바깥쪽으로 조금 밀려요. */
    fun dotDx(level: Int, c: Int, r: Int, t: Float, state: IonState, voice: Float, ox: Float, oy: Float): Float =
        drift(level, c, r, t, 0.9f, 4) + shake(c, r, t, state, 7) + push(c, r, t, state, voice, ox, oy) * ((c - ox) / dist(c, r, ox, oy))

    /** 도트 하나의 세로 이동(칸 단위). */
    fun dotDy(level: Int, c: Int, r: Int, t: Float, state: IonState, voice: Float, ox: Float, oy: Float): Float =
        drift(level, c, r, t, 0.7f, 5) + shake(c, r, t, state, 8) + push(c, r, t, state, voice, ox, oy) * ((r - oy) / dist(c, r, ox, oy))

    private fun dist(c: Int, r: Int, ox: Float, oy: Float): Float = hypot(c - ox, r - oy).coerceAtLeast(0.001f)

    private fun drift(level: Int, c: Int, r: Int, t: Float, speed: Float, k: Int): Float =
        (0.03f + 0.10f * looseness(level)) * sin(t * speed + phase(c, r, k) * TAU)

    /** 시동의 "부릉" 맥박 동안 도트가 아주 작게 떨려요(칸 단위, 최대 0.12). */
    private fun shake(c: Int, r: Int, t: Float, state: IonState, k: Int): Float {
        val s = if (startupApplies(state)) startupProgress(t) else -1f
        return if (s < 0f) 0f else 0.12f * rev(s) * sin(t * 47f + phase(c, r, k) * TAU)
    }

    /** 물결의 앞머리가 지나는 동안 바깥으로 미는 양(칸). 목소리가 클수록 커요. */
    private fun push(c: Int, r: Int, t: Float, state: IonState, voice: Float, ox: Float, oy: Float): Float =
        ring(dist(c, r, ox, oy), t, state, voice) * (if (state == IonState.LISTENING) 0.3f + 0.5f * voice.coerceIn(0f, 1f) else 0.4f)

    /** 같은 (c, k)에는 늘 같은 값(0 이상 1 미만). 박자 · 파편의 자리 같은 것을 정해요. */
    fun hash(c: Int, k: Int): Float {
        var x = c * 374761393 + k * 668265263
        x = (x xor (x ushr 13)) * 1274126177
        x = x xor (x ushr 16)
        return (x and 0xFFFF) / 65536f
    }
}

/** 인트로 막이 아직 화면을 덮고 있는지. 덮고 있는 동안은 디스플레이의 시계가 0에서 기다렸다가, 막이 닫히면 시동 장면부터 눈에 보이게 시작해요. */
internal val LocalIntroOpen = compositionLocalOf { false }

/** 기기에서 애니메이션을 끈 경우(접근성): 움직임은 없애고 결과만 보여 줘요. */
/**
 * ION 크기의 황금비 사다리(dp): 이웃끼리 1 : 1.618이에요(피보나치 34 · 55 · 89 · 144 · 233 · 377).
 * 오너 2026-11-11 "다빈치 황금비율 수치를 반영해서 ION 크기를 조정해줘". 구 · 그 둘레의 여백 · 곁의 글 칸이 모두 이 사다리의 이웃 칸이에요:
 * 디스플레이 카드 높이 H → 구 지름 H/φ, 위아래 · 좌우 여백 (H − H/φ)/2, 글 칸 너비 구 × φ.
 */
object IonSize {
    const val PHI = 1.618f
    /** 말하기 창의 작은 ION */
    val S = 55.dp
    /** 낮은 창의 디스플레이 구 · 아래 막대의 ION · 작은 막대의 ION */
    val M = 89.dp
    /** 넉넉한 창의 디스플레이 카드 높이 · 옆 막대의 ION */
    val L = 144.dp
    /** 옆 막대의 너비 */
    val XL = 233.dp
    /** 인트로의 ION */
    val INTRO = 377.dp
}

@Composable
internal fun rememberMotionOff(): Boolean {
    val context = LocalContext.current
    return remember { runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false) }
}

/**
 * ION 구: 둥근 17x17 격자에 작은 사각 빛을 다섯 단계로 켠 웹 기획서의 마크에서 떨어져 나온 한 점만 뺀 구예요(뒤의 빛 · 그림자도 없어요).
 * 캐릭터나 얼굴은 없어요. 듣는 중에는 파문이 번지고 목소리 크기에 따라 빛이 환해져요. 생각 중에는 도트 밝기가 물결쳐요.
 * [animated]면 숨 쉬듯 조용히 움직이고 작은 파편이 떠올라요(2026-11-11부터 주행 중의 막대 아바타도 움직여요 — 오너 요청). 듣는 중 · 생각 중의 움직임은
 * 입력의 되먹임이라 항상 보여 줘요. 기기에서 애니메이션을 끈 경우에는 모두 멈춰요. [revealOnShow]면 가운데부터 바깥으로 켜져요.
 *
 * [scene]이 구가 아니면 같은 도트로 지금 상황에 맞는 그림을 그려요(주행 = 굴러가는 타이어, 충전 = 배터리 …, [IonScenes]).
 * 장면이 바뀔 때는 앞 장면의 도트가 옅어지고 새 장면의 도트가 짙어져서 경계 없이 이어져요. 듣는 중 · 생각 중에는 늘 구예요.
 */
@Composable
internal fun IonOrb(ion: IonUi, modifier: Modifier, animated: Boolean, revealOnShow: Boolean = false, scene: IonScene = IonScene()) {
    val still = rememberMotionOff()
    val listening = ion.state == IonState.LISTENING
    val thinking = ion.state == IonState.THINKING
    val target = if (listening || thinking) IonScene() else scene

    // 장면 바꿈: 앞 장면([fromKind])이 옅어지고 새 장면([toKind])이 짙어져요(움직임을 끈 기기에서는 바로 바뀌어요)
    var fromKind by remember { mutableStateOf(target.kind) }
    var toKind by remember { mutableStateOf(target.kind) }
    val mix = remember { Animatable(1f) }
    LaunchedEffect(target.kind) {
        if (target.kind != toKind) {
            fromKind = toKind
            toKind = target.kind
            if (still) mix.snapTo(1f) else { mix.snapTo(0f); mix.animateTo(1f, tween(IviMotion.LONG2)) }
        }
    }

    val orbMoves = !still && (animated || listening || thinking)
    val running = orbMoves || (!still && (fromKind.alwaysMoves || toKind.alwaysMoves))

    // 시계: 30fps로만 갱신해서 다시 그리는 비용을 줄여요.
    var clock by remember { mutableFloatStateOf(0f) }
    val introOpen = LocalIntroOpen.current
    LaunchedEffect(running, introOpen) {
        if (running && !introOpen) {
            clock = 0f
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                if (now - last >= 33_000_000L) { clock += (now - last) / 1e9f; last = now }
            }
        }
    }
    val reveal = remember { Animatable(if (still || !revealOnShow) 1f else 0f) }
    LaunchedEffect(Unit) { if (!still && revealOnShow) reveal.animateTo(1f, tween(IviMotion.LONG2)) }

    val p = LocalPalette.current
    val ink = LocalLookColors.current?.let { androidx.compose.ui.graphics.Color(it.orb.toInt()) } ?: p.textPrimary
    val mark = IonMarks.all.getValue("idle")
    Canvas(modifier) {
        val shown = reveal.value
        val level = ion.level.coerceIn(0f, 1f)
        val toW = if (fromKind == toKind) 1f else mix.value
        // 구는 서 있을 때만 움직이고, 장면은 주행 중에도 그 장면의 움직임이 있어요
        val orbT = if (orbMoves) clock else IonOrb.STILL_T
        fun sceneT(kind: SceneKind) = if (!still && (animated || kind.alwaysMoves)) clock else IonScenes.pose(kind)

        // 가운데부터 바깥으로 켜지고(원이 커지는 만큼만 그려요)
        val clip = Path().apply { addOval(Rect(Offset(size.width / 2f, size.height / 2f), hypot(size.width, size.height) / 2f * shown)) }
        clipPath(clip) {
            if (fromKind != toKind) drawLayer(fromKind, 1f - toW, target, ion, mark, orbT, sceneT(fromKind), level, ink)
            drawLayer(toKind, toW, target, ion, mark, orbT, sceneT(toKind), level, ink)
        }

        val pitch = minOf(size.width / mark.columns, size.height / mark.rows.size)
        drawFragments(pitch, orbT, orbMoves, shown, ink)
    }
}

/** 한 장면(구 또는 도트 그림)을 [w](0~1)만큼의 짙기로 그려요. */
private fun DrawScope.drawLayer(kind: SceneKind, w: Float, scene: IonScene, ion: IonUi, mark: IonMarks.Mark, orbT: Float, sceneT: Float, level: Float, ink: Color) {
    if (w <= 0.01f) return
    if (kind == SceneKind.ORB) {
        val pitch = minOf(size.width / mark.columns, size.height / mark.rows.size)
        val left = (size.width - pitch * mark.columns) / 2f
        val top = (size.height - pitch * mark.rows.size) / 2f
        val side = pitch * IonMarks.CELL_SIZE / IonMarks.CELL_PITCH
        val inset = (pitch - side) / 2f
        val radius = CornerRadius(side * 0.26f)
        val rows = mark.rows.size
        val cols = mark.columns
        val ox = mark.ionColumn.toFloat()
        val oy = mark.ionRow.toFloat()
        // 도트마다 자기 박자로 반짝이고 떠다녀요(꺼진 도트는 그리지 않아요)
        mark.rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                val lv = ch - '0'
                if (lv > 0) {
                    val e = IonOrb.dotIntensity(lv, c, r, cols, rows, orbT, ion.state, level, ox, oy)
                    val a = IonOrb.dotAlpha(e) * w
                    if (a > 0.03f) {
                        val dx = IonOrb.dotDx(lv, c, r, orbT, ion.state, level, ox, oy) * pitch
                        val dy = IonOrb.dotDy(lv, c, r, orbT, ion.state, level, ox, oy) * pitch
                        val sz = side * (0.82f + 0.18f * e)
                        val o = (side - sz) / 2f
                        drawRoundRect(ink.copy(alpha = a), Offset(left + c * pitch + inset + dx + o, top + r * pitch + inset + dy + o), Size(sz, sz), radius)
                    }
                }
            }
        }
    } else {
        val n = IonScenes.N
        val pitch = minOf(size.width, size.height) / n
        val left = (size.width - pitch * n) / 2f
        val top = (size.height - pitch * n) / 2f
        val side = pitch * IonMarks.CELL_SIZE / IonMarks.CELL_PITCH
        val inset = (pitch - side) / 2f
        val radius = CornerRadius(side * 0.26f)
        val sc = scene.copy(kind = kind)
        for (r in 0 until n) for (c in 0 until n) {
            val e = IonScenes.density(sc, c, r, sceneT)
            val a = IonOrb.dotAlpha(e) * w
            if (a > 0.03f) {
                val sz = side * (0.82f + 0.18f * e)
                val o = (side - sz) / 2f
                drawRoundRect(ink.copy(alpha = a), Offset(left + c * pitch + inset + o, top + r * pitch + inset + o), Size(sz, sz), radius)
            }
        }
    }
}

/** 떨어져 나온 작은 파편: 구 둘레에서 천천히 떠오르며 반짝여요(멈춘 때는 그 자리에서 조용히 켜져 있어요). */
private fun DrawScope.drawFragments(pitch: Float, t: Float, moving: Boolean, shown: Float, ink: Color) {
    if (shown < 1f) return
    for (i in 0 until 7) {
        val bx = (0.5f + (IonOrb.hash(i, 11) - 0.5f) * 1.5f) * size.width
        val by = IonOrb.hash(i, 12)
        val speed = 0.012f + 0.02f * IonOrb.hash(i, 13)
        val rise = if (moving) (by + t * speed) % 1f else by
        val y = size.height * (1.0f - rise * 1.1f)
        val s = pitch * (0.35f + 0.35f * IonOrb.hash(i, 14))
        val twinkle = if (moving) 0.30f + 0.70f * abs(sin(t * 1.3f + i * 2.1f)) else 0.55f
        drawRoundRect(ink.copy(alpha = 0.45f * twinkle), Offset(bx, y), Size(s, s), CornerRadius(s * 0.14f))
    }
}

/**
 * ION 디스플레이. 테두리 없이 도트 뒤는 그냥 흰색(앱의 바탕색)이고(그라데이션 · 빛 · 그림자도 없어요), 값이 있으면(차가 준 것만) 오른쪽에 라벨 · 값 · 가는 막대가 놓여요.
 * 왼쪽 ION 칸 안에서만 쓰고(오른쪽 내용 칸은 건드리지 않아요), 작은 정사각형으로 쓰면 구만 가운데에 놓여요.
 */
@Composable
internal fun IonDisplay(
    ion: IonUi,
    info: DisplayInfo?,
    modifier: Modifier,
    animated: Boolean,
    revealOnShow: Boolean = false,
    description: String = "ION · ${ion.state.label}",
    scene: IonScene = IonScene(),
) {
    val p = LocalPalette.current
    val showInfo = info != null && ion.state != IonState.LISTENING && ion.state != IonState.THINKING
    // 값이 보일 때는 구가 왼쪽, 듣는 중 · 생각 중에는 구가 가운데로 옮겨 와요(움직임을 끈 기기에서는 바로 바뀌어요).
    val still = rememberMotionOff()
    val bias by animateFloatAsState(if (showInfo) -1f else 0f, if (still) snap() else tween(IviMotion.LONG2), label = "orb")
    val textAlpha by animateFloatAsState(if (showInfo) 1f else 0f, if (still) snap() else tween(IviMotion.LONG2), label = "text")
    BoxWithConstraints(
        modifier.background(p.backgroundPrimary)
            .semantics { contentDescription = description + (info?.let { " · ${it.label} ${it.value}" } ?: "") },
    ) {
        // 황금비: 카드 높이 H 안에서 구 지름은 H/φ, 남는 자리를 위아래 · 좌우 여백으로 반씩 나눠요(IonSize)
        val orb: Dp = maxHeight / IonSize.PHI
        val pad: Dp = (maxHeight - orb) / 2
        Box(Modifier.fillMaxSize()) {
            IonOrb(ion, Modifier.align(BiasAlignment(bias, 0f)).padding(horizontal = pad).size(orb), animated, revealOnShow, scene)
            if (info != null && textAlpha > 0.01f) {
                Column(
                    Modifier.align(Alignment.CenterStart).padding(start = pad + orb + pad).widthIn(max = orb * IonSize.PHI).alpha(textAlpha),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(info.label, color = p.textTertiary, style = Ivi.labelMedium)
                    Text(info.value, color = p.textPrimary, style = Ivi.titleMedium)
                    info.fraction?.let { f ->
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.width(orb).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.surfaceHigh)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0f, 1f)).background(p.textPrimary))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 왼쪽 ION 칸의 맨 위에 그 칸의 가로 폭만큼 펼쳐지는 ION 디스플레이 카드.
 * 그림은 늘 움직이는 ION 구예요. 상황별 도트 장면(충전 = 배터리 …, [IonScenes])은 오너가 2026-11-11에 뺐어요("ION 아이콘이 없어지고 배터리 아이콘이 나타남"):
 * 상황은 곁의 글자(충전 중 62%)가 말하고, ION은 모양을 바꾸지 않아요. 장면 코드는 남아 있지만 여기서는 쓰지 않아요.
 */
@Composable
internal fun IonDisplayBand(chat: IonChat, height: Dp, modifier: Modifier = Modifier) {
    IonDisplay(chat.ion, chat.info, modifier.fillMaxWidth().height(height), animated = true, revealOnShow = true, scene = IonScene())
}
