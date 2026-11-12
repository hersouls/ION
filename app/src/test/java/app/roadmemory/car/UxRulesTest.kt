package app.roadmemory.car

import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.GearPos
import app.roadmemory.car.data.UxLimit
import app.roadmemory.car.data.UxRules
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.engine.ModeEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 주행 규제(CarUxRestrictions)는 플랫폼이 내려 주는 값을 그대로 따라요: 규제가 켜지면 기어와 상관없이 주행 화면, 글과 목록은 플래그가 있을 때만 줄여요. */
class UxRulesTest {
    private val none = UxLimit()
    private val parkedKnown = UxRules.of(requiresDo = false, flags = UxLimit.BASELINE, maxItems = 0, maxStringLength = 0, maxDepth = 0)
    private val drivingNa = UxRules.of(
        requiresDo = true,
        flags = UxLimit.NO_KEYBOARD or UxLimit.NO_SETUP or UxLimit.NO_VIDEO or UxLimit.LIMIT_CONTENT or UxLimit.LIMIT_STRING_LENGTH or UxLimit.NO_DIALPAD or UxLimit.NO_FILTERING,
        maxItems = 21, maxStringLength = 120, maxDepth = 3,
    )
    private val drivingEu = UxRules.of(requiresDo = true, flags = UxLimit.NO_VIDEO or UxLimit.LIMIT_STRING_LENGTH, maxItems = 0, maxStringLength = 60, maxDepth = 0)

    @Test fun flagsAreReadBitByBit() {
        assertTrue(drivingNa.known); assertTrue(drivingNa.requiresDo)
        assertTrue(drivingNa.noKeyboard); assertTrue(drivingNa.noSetup); assertTrue(drivingNa.noVideo)
        assertTrue(drivingNa.limitContent); assertTrue(drivingNa.limitStringLength); assertTrue(drivingNa.noDialpad); assertTrue(drivingNa.noFiltering)
        assertEquals(21, drivingNa.maxItems); assertEquals(120, drivingNa.maxStringLength); assertEquals(3, drivingNa.maxDepth)
        assertFalse("유럽식: 키보드는 허용", drivingEu.noKeyboard)
        assertEquals("0은 모르는 값이라 null", null, drivingEu.maxItems)
        assertFalse(parkedKnown.requiresDo); assertEquals(0, parkedKnown.flags)
    }

    @Test fun regulationDecidesDrivingModeBeforeGear() {
        assertEquals("규제가 켜지면 기어가 P여도 주행", Mode.DRIVING, ModeEngine.modeOf(CarSnapshot(gear = GearPos.PARK), drivingNa))
        assertEquals("규제가 켜지면 기어를 몰라도 주행", Mode.DRIVING, ModeEngine.modeOf(CarSnapshot(), drivingEu))
        assertEquals("규제가 꺼져 있어도 기어 D면 주행(안전한 쪽)", Mode.DRIVING, ModeEngine.modeOf(CarSnapshot(gear = GearPos.DRIVE), parkedKnown))
        assertEquals("규제가 꺼지고 P면 정차", Mode.PARKED, ModeEngine.modeOf(CarSnapshot(gear = GearPos.PARK), parkedKnown))
        assertEquals("규제를 모르면 기어로만", Mode.PARKED, ModeEngine.modeOf(CarSnapshot(gear = GearPos.PARK), none))
        assertEquals("규제가 꺼지고 충전구가 열리면 충전", Mode.CHARGING, ModeEngine.modeOf(CarSnapshot(gear = GearPos.PARK, chargePortOpen = true), parkedKnown))
    }

    @Test fun stringsAreClippedOnlyUnderTheFlag() {
        val long = "가".repeat(200)
        assertEquals("플래그가 없으면 그대로", long, UxRules.clip(long, parkedKnown))
        assertEquals("모르면 그대로", long, UxRules.clip(long, none))
        val cut = UxRules.clip(long, drivingNa)
        assertEquals(120, cut.length); assertTrue(cut.endsWith("…"))
        assertEquals("짧은 글은 그대로", "안녕하세요", UxRules.clip("안녕하세요", drivingNa))
        assertEquals(60, UxRules.clip(long, drivingEu).length)
    }

    @Test fun listsAreCappedOnlyUnderTheFlag() {
        val items = (1..50).toList()
        assertEquals(50, UxRules.cap(items, parkedKnown).size)
        assertEquals(21, UxRules.cap(items, drivingNa).size)
        assertEquals("LIMIT_CONTENT가 없으면 최대 수가 있어도 그대로", 50, UxRules.cap(items, drivingEu).size)
    }

    @Test fun describeIsOneReadableLine() {
        assertEquals("모름", UxRules.describe(none))
        assertEquals("제한 없음", UxRules.describe(parkedKnown))
        assertEquals("제한 필요 · 영상 없음 · 글 60자", UxRules.describe(drivingEu))
        assertTrue(UxRules.describe(drivingNa).startsWith("제한 필요 · 키보드 없음"))
    }
}
