package app.roadmemory.car

import app.roadmemory.car.ion.IonState
import app.roadmemory.car.ui.DisplayInfo
import app.roadmemory.car.ui.IonMarks
import app.roadmemory.car.ui.IonOrb
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ION 구의 도트: 이온 점 하나에서 번지는 물결로 전체가 드러났다가 일부가 배경에 녹아요(경계선 없이 연속적으로). */
class IonOrbTest {
    private val mark = IonMarks.all.getValue("idle")
    private val rows = mark.rows.size
    private val cols = mark.columns
    private val ox = mark.ionColumn.toFloat()
    private val oy = mark.ionRow.toFloat()
    private val cells = mark.rows.flatMapIndexed { r, row -> row.mapIndexedNotNull { c, ch -> if (ch != '0') Triple(c, r, ch - '0') else null } }
    private fun e(lv: Int, c: Int, r: Int, t: Float, st: IonState = IonState.IDLE, v: Float = 0f) = IonOrb.dotIntensity(lv, c, r, cols, rows, t, st, v, ox, oy)
    private fun alpha(lv: Int, c: Int, r: Int, t: Float) = IonOrb.dotAlpha(e(lv, c, r, t))
    private val times = (0..900).map { it * 0.05f }.filter { IonOrb.startupProgress(it) < 0f }   // 45초(시동 장면 제외): 숨 한 번(약 18초)과 물결 여러 번

    @Test fun intensityStaysInRangeForEveryDotStateAndTime() {
        for ((c, r, lv) in cells) for (st in IonState.entries) for (t in listOf(0f, 1.2f, 2.5f, IonOrb.STILL_T, 31f, 48.5f)) for (v in listOf(0f, 1f)) assertTrue(e(lv, c, r, t, st, v) in 0f..1f)
    }

    @Test fun neverAHardEdge_intensityChangesGraduallyFromOneMomentToTheNext() {
        // 켜짐/꺼짐으로 끊기지 않아요: 0.05초 사이의 변화가 작아요(물결 앞머리가 지나가도 부드럽게).
        for ((c, r, lv) in cells.filterIndexed { i, _ -> i % 7 == 0 }) for (i in 1 until times.size) {
            assertTrue("($c,$r) t=${times[i]}", abs(e(lv, c, r, times[i]) - e(lv, c, r, times[i - 1])) < 0.25f)
        }
    }

    @Test fun theCoreNeverVanishes() {
        for ((c, r, lv) in cells.filter { it.third == 5 }) for (t in times) assertTrue("($c,$r) t=$t", e(lv, c, r, t) > 0.3f)
    }

    @Test fun paleDotsMeltIntoTheBackgroundWhenTheWaveIsAway() {
        val pale = cells.filter { it.third == 1 }
        assertTrue(pale.isNotEmpty())
        for ((c, r, lv) in pale) assertTrue("고요한 장면에서 옅은 도트는 보이지 않아요", alpha(lv, c, r, IonOrb.STILL_T) <= 0.03f)
    }

    @Test fun theWaveFromTheIonDotBringsOutTheWholeOrb() {
        // 물결 앞머리가 지나는 동안에는 옅은 도트도 진해져서 구 전체가 드러나요.
        val pale = cells.filter { it.third <= 2 }
        for ((c, r, lv) in pale) assertTrue("($c,$r)", times.any { alpha(lv, c, r, it) > 0.5f })
    }

    @Test fun partsOfTheOrbDisappearLikeAWaveAndComeBack() {
        val body = cells.filter { it.third >= 3 }
        val spread = times.filter { t ->
            val a = body.map { (c, r, lv) -> alpha(lv, c, r, t) }
            a.min() < 0.1f && a.max() > 0.5f
        }
        assertTrue("일부는 사라지고 일부는 보이는 순간이 있어요(${spread.size}/${times.size})", spread.size > 20)
        val whole = times.filter { t -> body.all { (c, r, lv) -> alpha(lv, c, r, t) > 0.3f } }
        assertTrue("전체가 보이는 순간도 있어요(${whole.size}/${times.size})", whole.size > 20)
    }

    @Test fun theFrontTravelsOutwardFromTheIonDot() {
        val t1 = 1.0f; val t2 = 3.0f
        assertTrue(IonOrb.front(t2, IonState.IDLE) > IonOrb.front(t1, IonState.IDLE))
        assertTrue(IonOrb.ring(IonOrb.front(t1, IonState.IDLE), t1, IonState.IDLE, 0f) > 0.99f)
        assertTrue("앞머리에서 먼 도트에는 물결이 닿지 않아요", IonOrb.ring(IonOrb.front(t1, IonState.IDLE) + 12f, t1, IonState.IDLE, 0f) < 0.01f)
        assertTrue("듣는 중에는 더 자주 퍼져요", IonOrb.front(0.8f, IonState.LISTENING) > IonOrb.front(0.8f, IonState.IDLE))
    }

    @Test fun movementIsTinyAndPushesAwayFromTheIonDot() {
        for ((c, r, lv) in cells) for (t in times.filterIndexed { i, _ -> i % 9 == 0 }) {
            assertTrue(abs(IonOrb.dotDx(lv, c, r, t, IonState.IDLE, 0f, ox, oy)) < 1f)
            assertTrue(abs(IonOrb.dotDy(lv, c, r, t, IonState.IDLE, 0f, ox, oy)) < 1f)
        }
        // 앞머리가 지나는 순간 이온 점에서 먼 쪽(왼쪽 아래)의 도트는 더 먼 쪽으로 밀려요.
        val far = cells.maxBy { (c, r, _) -> hypot(c - ox, r - oy) }
        val d = hypot(far.first - ox, far.second - oy)
        val t = (0..4500).map { it * 0.01f }.first { abs(IonOrb.front(it, IonState.IDLE) - d) < 0.2f }
        val dx = IonOrb.dotDx(far.third, far.first, far.second, t, IonState.IDLE, 0f, ox, oy)
        val dy = IonOrb.dotDy(far.third, far.first, far.second, t, IonState.IDLE, 0f, ox, oy)
        assertTrue("바깥쪽으로 밀려요", dx * (far.first - ox) + dy * (far.second - oy) > 0f)
    }

    @Test fun listeningWaveGrowsWithTheVoice() {
        fun total(v: Float) = cells.sumOf { (c, r, lv) -> times.take(60).sumOf { t -> abs(IonOrb.dotDx(lv, c, r, t, IonState.LISTENING, v, ox, oy)).toDouble() } }
        assertTrue(total(1f) > total(0f) * 1.2)
        fun bright(v: Float) = cells.sumOf { (c, r, lv) -> times.take(60).sumOf { t -> e(lv, c, r, t, IonState.LISTENING, v).toDouble() } }
        assertTrue("목소리가 크면 물결이 더 진해요", bright(1f) > bright(0f))
    }

    @Test fun thinkingSwirlsTheBrightnessAroundTheOrb() {
        val a = cells.map { (c, r, lv) -> e(lv, c, r, 0.5f, IonState.THINKING) }
        val b = cells.map { (c, r, lv) -> e(lv, c, r, 1.0f, IonState.THINKING) }
        assertTrue(a != b)
    }

    @Test fun dotAlphaIsContinuousAndMeltsPaleDots() {
        assertEquals(0f, IonOrb.dotAlpha(0f), 0f)
        assertEquals(1f, IonOrb.dotAlpha(1f), 0f)
        assertTrue("가장 옅은 도트(0.16)는 거의 보이지 않아요", IonOrb.dotAlpha(IonMarks.LEVELS[1]) < 0.05f)
        var prev = 0f
        for (i in 0..100) { val a = IonOrb.dotAlpha(i / 100f); assertTrue(a >= prev); assertTrue(a - prev < 0.03f); prev = a }
    }

    // ── 시동이 켜지는 느낌(가끔) ──
    private val ion = IonState.IDLE
    private fun startupAt(sec: Float) = sec * IonOrb.STARTUP_LEN
    private fun aStartup(lv: Int, c: Int, r: Int, s: Float) = IonOrb.dotAlpha(e(lv, c, r, startupAt(s)))

    @Test fun startupComesOnlyOccasionallyAndAlsoRightAtLaunch() {
        assertEquals(0f, IonOrb.startupProgress(0f), 0f)                       // 앱을 켠 순간
        assertEquals(0.5f, IonOrb.startupProgress(IonOrb.STARTUP_LEN / 2), 1e-4f)
        assertTrue(IonOrb.startupProgress(10f) < 0f)
        assertTrue(IonOrb.startupProgress(IonOrb.STARTUP_PERIOD + 0.5f) in 0f..1f)   // 한 번 더
        val active = (0..4800).count { IonOrb.startupProgress(it * 0.05f) >= 0f }
        assertTrue("240초 가운데 시동 장면은 약 6% 이하", active < 4800 * 0.07)
        assertTrue(IonOrb.startupProgress(IonOrb.STILL_T) < 0f)                // 멈춘 장면에는 시동이 없어요
    }

    @Test fun startupBeginsDarkThenIgnitesFromTheIonDotOutward() {
        val body = cells.filter { it.third >= 3 }
        for ((c, r, lv) in body) assertTrue("처음에는 모두 꺼져 있어요", aStartup(lv, c, r, 0.02f) <= 0.03f)
        // 앞머리가 중간쯤 왔을 때: 이온 점에 가까운 도트는 켜지고 먼 도트는 아직 꺼져 있어요
        val sorted = body.sortedBy { (c, r, _) -> hypot(c - ox, r - oy) }
        val near = sorted.first(); val far = sorted.last()
        val dNear = hypot(near.first - ox, near.second - oy)
        val dFar = hypot(far.first - ox, far.second - oy)
        val mid = 0.40f
        assertTrue("가까운 도트는 이미 켜졌어요", IonOrb.ignition(dNear, mid) > 0.9f)
        assertTrue("먼 도트는 아직 꺼져 있어요", IonOrb.ignition(dFar, mid) < 0.05f)
        assertTrue(aStartup(far.third, far.first, far.second, mid) <= 0.03f)
        // 앞머리는 시간이 갈수록 바깥으로 가요: 같은 도트의 점화 정도가 늘어나요
        assertTrue(IonOrb.ignition(dFar, 0.30f) <= IonOrb.ignition(dFar, 0.50f) && IonOrb.ignition(dFar, 0.50f) <= IonOrb.ignition(dFar, 0.62f))
        assertEquals(1f, IonOrb.ignition(dFar, 0.62f), 0.01f)
    }

    @Test fun theRevPulseBrieflyLightsTheWholeOrbThenSettlesBack() {
        val pulseS = 0.81f                                                      // 맥박의 가운데(0.62 + 0.19)
        for ((c, r, lv) in cells) assertTrue("($c,$r) 맥박에서는 옅은 도트까지 켜져요", aStartup(lv, c, r, pulseS) > 0.5f)
        val pale = cells.filter { it.third == 1 }
        for ((c, r, lv) in pale) assertTrue("시동이 끝나면 옅은 도트는 다시 배경에 녹아요", alpha(lv, c, r, 6.0f) <= 0.5f && alpha(lv, c, r, IonOrb.STILL_T) <= 0.03f)
        assertEquals(0f, IonOrb.rev(0.5f), 0f); assertEquals(1f, IonOrb.rev(0.81f), 0.01f); assertEquals(0f, IonOrb.rev(1f), 1e-3f)
    }

    @Test fun startupIsSmoothAndTheShakeIsTiny() {
        val dt = 1f / 30f
        var maxStep = 0f
        for ((c, r, lv) in cells.filterIndexed { i, _ -> i % 5 == 0 }) {
            var prev = e(lv, c, r, 0f)
            var t = dt
            while (t < IonOrb.STARTUP_LEN) { val cur = e(lv, c, r, t); maxStep = maxOf(maxStep, abs(cur - prev)); prev = cur; t += dt }
        }
        assertTrue("시동 중에도 한 프레임 사이의 변화가 작아요(경계선 없음): $maxStep", maxStep < 0.3f)
        for ((c, r, lv) in cells) { var t = 0f; while (t < IonOrb.STARTUP_LEN) { assertTrue(abs(IonOrb.dotDx(lv, c, r, t, ion, 0f, ox, oy)) < 1f); t += 0.07f } }
    }

    @Test fun startupDoesNotInterruptListeningOrThinking() {
        for (st in listOf(IonState.LISTENING, IonState.THINKING)) {
            assertTrue(!IonOrb.startupApplies(st))
            val core = cells.first { it.third == 5 }
            assertTrue("$st 에서는 켜지는 중에도 구가 꺼지지 않아요", e(core.third, core.first, core.second, 0.1f, st, 1f) > 0.05f)
        }
        assertTrue(IonOrb.startupApplies(IonState.IDLE) && IonOrb.startupApplies(IonState.DONE))
    }

    @Test fun hashIsStableAndInRange() {
        for (c in 0 until 300) for (k in 1..16) {
            val v = IonOrb.hash(c, k)
            assertTrue(v >= 0f && v < 1f)
            assertEquals(v, IonOrb.hash(c, k), 0f)
        }
    }

    @Test fun displayInfoOnlyCarriesWhatTheCarGave() {
        val info = DisplayInfo("배터리", "48%", 0.48f)
        assertEquals("배터리", info.label); assertEquals("48%", info.value); assertEquals(0.48f, info.fraction!!, 0f)
        assertNull(DisplayInfo("외기온", "25℃").fraction)
    }
}
