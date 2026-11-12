package app.roadmemory.car

import app.roadmemory.car.engine.CardKind
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.ui.IonOrb
import app.roadmemory.car.ui.IonScene
import app.roadmemory.car.ui.IonScenes
import app.roadmemory.car.ui.SceneKind
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ION 디스플레이의 장면: 상황에 맞는 그림이 골라지고, 도트로 그려진 그림이 읽히고, 움직임이 이어져요. */
class IonScenesTest {
    private val n = IonScenes.N
    private fun at(s: IonScene, c: Int, r: Int, t: Float) = IonScenes.density(s, c, r, t)
    private fun frame(s: IonScene, t: Float) = List(n * n) { at(s, it % n, it / n, t) }
    private fun lit(s: IonScene, t: Float, min: Float = 0.4f) = frame(s, t).count { it >= min }
    private fun tire() = IonScene(SceneKind.TIRE)

    // ───────── 상황 → 장면 ─────────

    private fun pick(mode: Mode = Mode.PARKED, pane: String? = null, card: CardKind? = null, connected: Boolean = true, arrived: Boolean = false) =
        IonScene.pick(connected, mode, pane, card, 48, listOf(true, false, false, false), arrived)

    @Test fun drivingShowsTheRollingTire() {
        assertEquals(SceneKind.TIRE, pick(Mode.DRIVING).kind)
        assertEquals(SceneKind.TIRE, pick(Mode.DRIVING, card = CardKind.REST_SUGGEST).kind)
        assertTrue(SceneKind.TIRE.alwaysMoves)
    }

    @Test fun drivingSeatCardsShowTheSeats() {
        assertEquals(SceneKind.SEATS, pick(Mode.DRIVING, card = CardKind.SEATBELT_CHECK).kind)
        assertEquals(SceneKind.SEATS, pick(Mode.DRIVING, card = CardKind.REAR_SEAT).kind)
    }

    @Test fun chargingShowsTheBatteryAndItFlowsOnlyWhileCharging() {
        assertEquals(SceneKind.BATTERY, pick(Mode.CHARGING).kind)
        assertTrue(pick(Mode.CHARGING).charging)
        val parkedOpen = pick(Mode.PARKED, pane = Pane.CHARGE)
        assertEquals(SceneKind.BATTERY, parkedOpen.kind)
        assertTrue(!parkedOpen.charging)
    }

    @Test fun openedScreensChooseTheirScene() {
        assertEquals(SceneKind.SEATS, pick(pane = Pane.FAMILY).kind)
        assertEquals(SceneKind.CAR, pick(pane = Pane.CHECK).kind)
        assertEquals(SceneKind.ROAD, pick(pane = Pane.TRIPS).kind)
        // 열린 화면이 충전 중보다 먼저예요
        assertEquals(SceneKind.SEATS, pick(Mode.CHARGING, pane = Pane.FAMILY).kind)
    }

    @Test fun parkedCardsAndArrival() {
        assertEquals(SceneKind.SEATS, pick(card = CardKind.REAR_ALERT).kind)
        assertEquals(SceneKind.CAR, pick(card = CardKind.VEHICLE_CHECK).kind)
        assertEquals(SceneKind.ROAD, pick(arrived = true).kind)
    }

    @Test fun otherwiseTheOrb() {
        assertEquals(SceneKind.ORB, pick().kind)
        assertEquals(SceneKind.ORB, pick(pane = Pane.SETTINGS).kind)
        assertEquals(SceneKind.ORB, pick(pane = Pane.DATA).kind)
        assertEquals(SceneKind.ORB, pick(Mode.DRIVING, connected = false).kind)
    }

    // ───────── 도트 ─────────

    @Test fun densityStaysInRangeForEverySceneAndTime() {
        for (k in SceneKind.entries) for (t in listOf(0f, 0.3f, IonScenes.POSE, 2.1f, 7.7f, 31f)) for (soc in listOf(null, 0, 48, 100)) {
            val s = IonScene(k, soc, charging = true, seats = listOf(true, true, true, true))
            for (v in frame(s, t)) assertTrue(v in 0f..1f)
        }
    }

    @Test fun everySceneIsReadableButNotFilled() {
        for (k in listOf(SceneKind.TIRE, SceneKind.BATTERY, SceneKind.SEATS, SceneKind.CAR, SceneKind.ROAD)) {
            val s = IonScene(k, 60, false, listOf(true, false, true, false))
            val dark = lit(s, IonScenes.pose(k))
            assertTrue("$k 은 보여야 해요($dark)", dark >= 12)
            assertTrue("$k 은 빽빽하지 않아야 해요($dark)", dark < n * n * 0.4)
        }
    }

    @Test fun theOrbIsDrawnByTheOrbNotTheScenes() {
        assertEquals(0f, frame(IonScene(), 3f).max(), 0f)
    }

    // ───────── 주행: 타이어 ─────────

    @Test fun theTireTurnsAndRepeats() {
        val a = frame(tire(), 1.0f)
        val b = frame(tire(), 1.1f)
        assertTrue("시간이 가면 바퀴가 돌아요", a.zip(b).any { (x, y) -> abs(x - y) > 0.25f })
        assertEquals("같은 순간은 같은 모습이에요", a, frame(tire(), 1.0f))
    }

    @Test fun theTireNeverJumpsFromOneFrameToTheNext() {
        // 30fps의 한 프레임 사이에 도트 하나가 크게 뒤집히면 깜박여 보여요
        var worst = 0f
        var t = 0f
        while (t < 6f) {
            val a = frame(tire(), t)
            val b = frame(tire(), t + 1f / 30f)
            worst = maxOf(worst, a.zip(b).maxOf { (x, y) -> abs(x - y) })
            t += 0.37f
        }
        assertTrue("한 프레임 최대 변화 $worst", worst < 0.45f)
    }

    @Test fun theRoadFlowsBackwardsAtTheSpeedTheWheelTurns() {
        // 길(맨 아래 줄)은 0.9칸 간격 점선이라, 바퀴가 구른 거리가 한 간격이 되는 시간마다 같은 모습이에요
        val period = 0.9f / (IonScenes.ROLL * 0.84f)
        val row = n - 2
        val a = List(n) { at(tire(), it, row, 1.0f) }
        val b = List(n) { at(tire(), it, row, 1.0f + period) }
        val c = List(n) { at(tire(), it, row, 1.0f + period / 2f) }
        a.zip(b).forEach { (x, y) -> assertEquals(x, y, 0.1f) }
        assertTrue("반 간격이면 다른 모습이에요", a.zip(c).any { (x, y) -> abs(x - y) > 0.2f })
        // 양 끝은 흰색에 녹아요
        assertTrue(a.first() < 0.05f && a.last() < 0.05f)
    }

    @Test fun theTireIsAlwaysThereRimHubAndTread() {
        val mid = n / 2
        for (t in listOf(0f, 0.5f, 1.7f, 4.2f)) {
            assertTrue("허브", at(tire(), mid, mid - 1, t) > 0.6f)
            val ring = (0 until n).count { c -> at(tire(), c, 2, t) > 0.3f }
            assertTrue("위쪽 트레드가 보여요", ring >= 3)
        }
    }

    // ───────── 충전: 배터리 ─────────

    private fun batteryFill(soc: Int?, charging: Boolean = false, t: Float = IonScenes.POSE): Int {
        val s = IonScene(SceneKind.BATTERY, soc, charging)
        val y = n / 2
        // 배터리 안쪽 가운데 줄(좌표 -0.6~0.55)에서 채워진 칸
        return (4..n - 5).count { c -> at(s, c, y, t) >= 0.4f }
    }

    @Test fun batteryFillFollowsTheChargeTheCarReports() {
        val none = batteryFill(null)
        val low = batteryFill(20)
        val half = batteryFill(50)
        val full = batteryFill(100)
        assertTrue("0% 근처는 비어요($none)", none <= 1)
        assertTrue("$low < $half", low < half)
        assertTrue("$half < $full", half < full)
        assertTrue("가득 차면 안쪽이 거의 다 켜져요($full)", full >= 10)
    }

    @Test fun chargingElectricityMovesAlongTheFill() {
        val s = IonScene(SceneKind.BATTERY, 70, charging = true)
        val a = frame(s, 0.5f)
        val b = frame(s, 1.4f)
        assertTrue(a.zip(b).any { (x, y) -> abs(x - y) > 0.15f })
        // 충전 중이 아니면 거의 가만히 있어요
        val q = IonScene(SceneKind.BATTERY, 70, charging = false)
        assertTrue(frame(q, 0.5f).zip(frame(q, 1.4f)).all { (x, y) -> abs(x - y) < 0.12f })
    }

    // ───────── 가족 · 뒷좌석 ─────────

    @Test fun occupiedSeatsAreLitAndEmptyOnesAreOnlyOutlined() {
        // 격자에서 운전석 · 조수석 · 뒷좌석 왼쪽 · 오른쪽 가운데 칸
        val cells = listOf(8 to 8, 12 to 8, 8 to 13, 12 to 13)
        for (i in 0 until 4) {
            val seats = List(4) { it == i }
            for ((j, cell) in cells.withIndex()) {
                val v = at(IonScene(SceneKind.SEATS, seats = seats), cell.first, cell.second, 1f)
                if (i == j) assertTrue("자리 $j 은 진해야 해요($v)", v > 0.8f) else assertTrue("자리 $j 은 비어 있어요($v)", v < 0.2f)
            }
        }
    }

    // ───────── 점검 · 여행 기록 ─────────

    @Test fun theScanLightMakesTheCarDarkerWhereItPasses() {
        val s = IonScene(SceneKind.CAR)
        // 같은 시각, 빛이 지나는 줄과 먼 줄의 합을 비교: t = 3.4 * (xs + 1.1) / 2.2
        val t = 3.4f * (0f + 1.1f) / 2.2f
        fun sum(from: Int, to: Int) = (from..to).sumOf { c -> (0 until n).sumOf { r -> at(s, c, r, t).toDouble() } }
        assertTrue("지나는 곳이 더 진해요", sum(9, 11) / 3 > sum(1, 3) / 3 * 1.15)
    }

    @Test fun roadTravelledPartIsDarkerThanTheWayAhead() {
        val s = IonScene(SceneKind.ROAD)
        val t = 7f * 0.62f   // 앞머리 점이 길의 60% 즈음
        val xm = -0.9f + 1.8f * 0.62f
        fun colOf(x: Float) = (x * 10f + 10f).toInt()
        fun best(c: Int) = (0 until n).maxOf { r -> at(s, c, r, t) }
        assertTrue("지나온 길 ${best(colOf(xm - 0.7f))} > 앞 ${best(colOf(xm + 0.45f))}", best(colOf(xm - 0.7f)) > best(colOf(xm + 0.45f)) * 1.3f)
    }

    @Test fun roadHeadFadesAtTheEndSoThereIsNoCut() {
        val s = IonScene(SceneKind.ROAD)
        val before = frame(s, 6.9f)
        val after = frame(s, 7.05f)
        assertTrue(before.zip(after).all { (x, y) -> abs(x - y) < 0.6f })
    }

    @Test fun stillPoseUsesTheSameFrameEveryTime() {
        for (k in listOf(SceneKind.TIRE, SceneKind.BATTERY, SceneKind.SEATS, SceneKind.CAR, SceneKind.ROAD)) {
            assertEquals(frame(IonScene(k, 50), IonScenes.pose(k)), frame(IonScene(k, 50), IonScenes.pose(k)))
        }
    }

    @Test fun paleSceneDotsMeltIntoTheBackground() {
        // 옅은 도트(농도 0.14 이하)는 그려지지 않아요: 구와 같은 규칙
        assertEquals(0f, IonOrb.dotAlpha(0.14f), 0f)
    }
}
