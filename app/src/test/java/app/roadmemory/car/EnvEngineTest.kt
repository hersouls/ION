package app.roadmemory.car

import app.roadmemory.car.data.EnvSignals
import app.roadmemory.car.data.Weather
import app.roadmemory.car.data.WiperLevel
import app.roadmemory.car.engine.EnvEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvEngineTest {
    private val s = 1_000L

    /** 신호를 시간순으로 먹이고 마지막 추정을 돌려줘요. */
    private fun run(vararg steps: Pair<Long, EnvSignals>): app.roadmemory.car.data.EnvState {
        var st = EnvEngine.State()
        var last = app.roadmemory.car.data.EnvState()
        for ((t, sig) in steps) { val (n, e) = EnvEngine.step(st, sig, t); st = n; last = e }
        return last
    }

    // 자동(AUTO) 모드에서 비가 오면 쓸 때마다 ON · OFF 알림이 와요(자동의 대기는 비가 아니라서 지속 규칙에 걸리지 않아요)
    private val on = EnvSignals(wiperOn = true, wiperLevel = WiperLevel.AUTO)
    private val off = EnvSignals(wiperOn = false, wiperLevel = WiperLevel.AUTO)

    @Test fun noSignalsMeansUnknown() {
        assertNull(run(0L to EnvSignals(), 10 * s to EnvSignals()).weather)
    }

    @Test fun oneSweepIsNotRain() {
        assertNull(run(0L to off, 5 * s to on, 6 * s to off, 30 * s to off).weather)
    }

    @Test fun mistAndSwitchedOffSweepsDoNotCount() {
        val mist = EnvSignals(wiperOn = true, wiperLevel = WiperLevel.MIST)
        val mistOff = EnvSignals(wiperOn = false, wiperLevel = WiperLevel.MIST)
        assertNull(run(0L to mist, s to mistOff, 2 * s to mist, 3 * s to mistOff, 4 * s to mist).weather)
    }

    @Test fun threeSweepsInAMinuteIsRain() {
        val e = run(0L to on, s to off, 5 * s to on, 6 * s to off, 10 * s to on)
        assertEquals(Weather.RAIN, e.weather)
        assertTrue(e.reasons.isNotEmpty())
    }

    @Test fun reasonTextIsHonestAboutWhyRainIsStillHeld() {
        val sweeps = arrayOf(0L to on, s to off, 5 * s to on, 6 * s to off, 10 * s to on, 11 * s to off)
        assertEquals(listOf("와이퍼가 3번 쓸었어요"), run(*sweeps.take(5).toTypedArray()).reasons)
        assertEquals(listOf("조금 전까지 와이퍼가 움직였어요"), run(*sweeps, 100 * s to off).reasons)
        val low = EnvSignals(wiperLevel = WiperLevel.CONTINUOUS)
        assertEquals(listOf("와이퍼가 계속 켜져 있어요"), run(0L to low, 25 * s to low).reasons)
    }

    @Test fun sweepsSpreadOverMoreThanAMinuteAreNotRain() {
        assertNull(run(0L to on, s to off, 70 * s to on, 71 * s to off, 140 * s to on).weather)
    }

    @Test fun headlightsOrDefrostLowerTheBarToTwoSweeps() {
        val lit = on.copy(lowBeam = true)
        val litOff = off.copy(lowBeam = true)
        assertEquals(Weather.RAIN, run(0L to lit, s to litOff, 5 * s to lit).weather)
        assertNull(run(0L to on, s to off, 5 * s to on).weather)
    }

    @Test fun rainHoldsForThreeMinutesAfterTheLastSweepThenClears() {
        val sweeps = arrayOf(0L to on, s to off, 5 * s to on, 6 * s to off, 10 * s to on, 11 * s to off)
        assertEquals(Weather.RAIN, run(*sweeps, 150 * s to off).weather)
        assertNull(run(*sweeps, 200 * s to off).weather)
    }

    @Test fun fogLampsNeedTwentySeconds() {
        val fog = EnvSignals(frontFog = true)
        assertNull(run(0L to fog, 10 * s to fog).weather)
        assertEquals(Weather.FOG, run(0L to fog, 25 * s to fog).weather)
        assertNull(run(0L to fog, 25 * s to EnvSignals(frontFog = false)).weather)
        assertEquals(Weather.FOG, run(0L to EnvSignals(rearFog = true), 21 * s to EnvSignals(rearFog = true)).weather)
    }

    @Test fun aManualWiperLeftOnIsRainAfterTwentySeconds() {
        val low = EnvSignals(wiperOn = false, wiperLevel = WiperLevel.CONTINUOUS)   // 알림이 안 와도(스위치만 바뀌어도) 스위치를 읽어요
        assertNull(run(0L to low, 10 * s to low).weather)
        assertEquals(Weather.RAIN, run(0L to low, 25 * s to low).weather)
        assertEquals(Weather.RAIN, run(0L to EnvSignals(wiperLevel = WiperLevel.INTERMITTENT), 21 * s to EnvSignals(wiperLevel = WiperLevel.INTERMITTENT)).weather)
    }

    @Test fun aSteadyOnStateIsRainAfterTwentySecondsButOffAndMistAreNot() {
        val steady = EnvSignals(wiperOn = true, wiperLevel = WiperLevel.AUTO)
        assertEquals(Weather.RAIN, run(0L to steady, 22 * s to steady).weather)
        val offSwitch = EnvSignals(wiperOn = true, wiperLevel = WiperLevel.OFF)   // 스위치가 꺼진 움직임(점검 등)
        assertNull(run(0L to offSwitch, 30 * s to offSwitch).weather)
        val mist = EnvSignals(wiperOn = true, wiperLevel = WiperLevel.MIST)
        assertNull(run(0L to mist, 30 * s to mist).weather)
        assertNull(run(0L to EnvSignals(wiperOn = false, wiperLevel = WiperLevel.AUTO), 60 * s to EnvSignals(wiperOn = false, wiperLevel = WiperLevel.AUTO)).weather)
    }

    @Test fun manualWiperSwitchedOffBeforeTwentySecondsIsNotRain() {
        val low = EnvSignals(wiperLevel = WiperLevel.CONTINUOUS)
        assertNull(run(0L to low, 10 * s to low, 12 * s to EnvSignals(wiperLevel = WiperLevel.OFF), 40 * s to EnvSignals(wiperLevel = WiperLevel.OFF)).weather)
    }

    @Test fun rainWinsOverFog() {
        val fog = EnvSignals(frontFog = true, wiperOn = true, wiperLevel = WiperLevel.AUTO)
        val fogOff = fog.copy(wiperOn = false)
        assertEquals(Weather.RAIN, run(0L to fog, s to fogOff, 5 * s to fog, 6 * s to fogOff, 10 * s to fog, 30 * s to fogOff).weather)
    }
}
