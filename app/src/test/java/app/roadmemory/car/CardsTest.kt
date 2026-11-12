package app.roadmemory.car

import app.roadmemory.car.data.CarPart
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.ChargeStop
import app.roadmemory.car.data.GearPos
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.SeatPos
import app.roadmemory.car.data.SeatState
import app.roadmemory.car.data.TempUnit
import app.roadmemory.car.engine.CardContext
import app.roadmemory.car.engine.CardEngine
import app.roadmemory.car.engine.CardKind
import app.roadmemory.car.engine.ChargePace
import app.roadmemory.car.engine.Mode
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 카드 규칙: 모든 말은 차가 실제로 준 값에서만 나오고, 값을 모르면(null) 말하지 않아요. */
class CardsTest {
    private fun car(
        occupied: Set<SeatPos> = emptySet(), belted: Map<SeatPos, Boolean?> = emptyMap(), open: Set<CarPart> = emptySet(),
        outside: Float? = null, cabin: Float? = null, unit: TempUnit = TempUnit.CELSIUS,
    ) = CarSnapshot(
        gear = GearPos.DRIVE,
        seats = SeatPos.entries.associateWith { SeatState(occupied = it in occupied, belted = belted[it]) },
        open = CarPart.entries.associateWith { it in open },
        outsideTempC = outside, cabinTempC = cabin, tempUnit = unit,
    )
    private val route = NavState(routeActive = true, destinationName = "푸른 바닷가", destLat = 37.0, destLon = 127.0, estimatedSocAtDest = 40)
    private fun pick(mode: Mode, car: CarSnapshot, nav: NavState = NavState(), handled: Set<String> = emptySet(), ctx: CardContext = CardContext()) = CardEngine.pick(mode, car, nav, handled, ctx)

    private fun trip(at: Long, month: String, name: String?, tracks: List<String> = emptyList(), people: Int = 3) =
        SavedTrip(1, at, month, name, 37.0, 127.0, 12, people, tracks, 0)

    // ── 서 있을 때 ──
    @Test fun rearAlertNeedsAnOpenDriverDoorAndSomeoneInTheBack() {
        val c = pick(Mode.PARKED, car(occupied = setOf(SeatPos.REAR_LEFT), open = setOf(CarPart.DRIVER_DOOR), cabin = 31f))!!
        assertEquals(CardKind.REAR_ALERT, c.kind)
        assertTrue(c.sub!!.contains("실내 온도 31도") && c.sub!!.contains("운전석 문"))
        assertNull(pick(Mode.PARKED, car(occupied = setOf(SeatPos.REAR_LEFT))))
        assertNull(pick(Mode.PARKED, car(open = setOf(CarPart.DRIVER_DOOR))))
    }

    @Test fun postDriveCheckListsOpenPartsButNotTheDriverDoor() {
        val c = pick(Mode.PARKED, car(open = setOf(CarPart.TRUNK, CarPart.HOOD, CarPart.DRIVER_DOOR)))!!
        assertEquals(CardKind.VEHICLE_CHECK, c.kind)
        assertEquals("2가지를 살펴봐요", c.title)
        assertTrue(c.sub!!.contains("트렁크") && c.sub!!.contains("후드") && !c.sub!!.contains("운전석 문"))
        assertNull(pick(Mode.PARKED, car()))
    }

    // ── 달리는 중 ──
    @Test fun seatbeltCardOnlyWhenSomeoneSitsWithoutABeltAndNeverGuessesUnknown() {
        val c = pick(Mode.DRIVING, car(occupied = setOf(SeatPos.PASSENGER), belted = mapOf(SeatPos.PASSENGER to false)))!!
        assertEquals(CardKind.SEATBELT_CHECK, c.kind)
        assertTrue(c.title.startsWith("조수석"))
        assertNull(pick(Mode.DRIVING, car(occupied = setOf(SeatPos.PASSENGER), belted = mapOf(SeatPos.PASSENGER to true))))
        assertNull("벨트 값을 모르면 말하지 않아요", pick(Mode.DRIVING, car(occupied = setOf(SeatPos.DRIVER), belted = mapOf(SeatPos.DRIVER to null))))
    }

    @Test fun chargeCardTellsWhatTheNavigationSaidNotWhatItInvented() {
        val low = route.copy(estimatedSocAtDest = 8)
        val plain = pick(Mode.DRIVING, car(), low)!!
        assertEquals(CardKind.CHARGE_VIA, plain.kind)
        assertEquals("도착하면 배터리가 8%예요", plain.title)
        assertTrue(plain.sub!!.contains("내비에서 충전소를 확인"))
        val withStop = pick(Mode.DRIVING, car(), low.copy(chargeStops = listOf(ChargeStop("푸른 충전소", 20, 80))))!!
        assertTrue(withStop.sub!!.contains("푸른 충전소"))
        assertNull(pick(Mode.DRIVING, car(), route.copy(estimatedSocAtDest = 40)))
        assertNull(pick(Mode.DRIVING, car(), low.copy(routeActive = false)))
    }

    @Test fun roadMemoryComesOnlyFromSavedTripsToTheSamePlace() {
        val now = YearMonth.of(2026, 11)
        val ctx = CardContext(trips = listOf(trip(2, "2024.10", "푸른 바닷가", listOf("Solcho Sea Loop"), 3)), month = now)
        val c = pick(Mode.DRIVING, car(), route, ctx = ctx)!!
        assertEquals(CardKind.ROAD_MEMORY, c.kind)
        assertEquals("이 길, 2년 전에 셋이 달렸어요", c.title)
        assertEquals("틀기", c.primary)
        val noTracks = pick(Mode.DRIVING, car(), route, ctx = ctx.copy(trips = listOf(trip(2, "2024.10", "푸른 바닷가"))))!!
        assertEquals("알았어요", noTracks.primary)
        assertTrue(noTracks.sub!!.contains("1건"))
        assertNull("다녀온 적 없는 곳", pick(Mode.DRIVING, car(), route.copy(destinationName = "다른 곳", destLat = 10.0, destLon = 10.0), ctx = ctx))
        assertNull("저장된 여행이 없으면 가상 기억을 만들지 않아요", pick(Mode.DRIVING, car(), route))
    }

    @Test fun restSuggestionAfterTwoHoursOnly() {
        assertNull(pick(Mode.DRIVING, car(), ctx = CardContext(driveMinutes = 119)))
        val c = pick(Mode.DRIVING, car(), ctx = CardContext(driveMinutes = 120))!!
        assertEquals(CardKind.REST_SUGGEST, c.kind)
        assertEquals("2시간째 달렸어요", c.title)
        assertEquals("2시간 30분째 달렸어요", pick(Mode.DRIVING, car(), ctx = CardContext(driveMinutes = 150))!!.title)
        assertNull(pick(Mode.DRIVING, car(), ctx = CardContext(driveMinutes = null)))
    }

    @Test fun coldNoticeIsQualitativeAndNeedsTheRealOutsideTemperature() {
        val c = pick(Mode.DRIVING, car(outside = -3f))!!
        assertEquals(CardKind.COLD_NOTICE, c.kind)
        assertEquals("바깥이 영하 3도예요", c.title)
        assertTrue("퍼센트 같은 지어낸 숫자는 없어요", !c.title.contains("%") && !c.sub!!.contains("%"))
        assertNull(pick(Mode.DRIVING, car(outside = 5f)))
        assertNull(pick(Mode.DRIVING, car(outside = null)))
    }

    @Test fun rearSeatCardOffersMusicWithoutAnyInventedName() {
        val c = pick(Mode.DRIVING, car(occupied = setOf(SeatPos.REAR_RIGHT)))!!
        assertEquals(CardKind.REAR_SEAT, c.kind)
        assertEquals("뒷좌석에 탔어요", c.title)
    }

    // ── 고르는 순서 · 한 번만 · 충전 중 ──
    @Test fun oneCardAtATimeAndAnsweredCardsStaySilent() {
        val busy = car(occupied = setOf(SeatPos.DRIVER, SeatPos.REAR_LEFT), belted = mapOf(SeatPos.DRIVER to false), outside = -5f)
        assertEquals(CardKind.SEATBELT_CHECK, pick(Mode.DRIVING, busy)!!.kind)
        assertEquals(CardKind.COLD_NOTICE, pick(Mode.DRIVING, busy, handled = setOf("SEATBELT_CHECK"))!!.kind)
        assertEquals(CardKind.REAR_SEAT, pick(Mode.DRIVING, busy, handled = setOf("SEATBELT_CHECK", "COLD_NOTICE"))!!.kind)
        assertNull(pick(Mode.DRIVING, busy, handled = setOf("SEATBELT_CHECK", "COLD_NOTICE", "REAR_SEAT")))
    }

    @Test fun chargingNeverShowsACard() = assertNull(pick(Mode.CHARGING, car(occupied = setOf(SeatPos.DRIVER), belted = mapOf(SeatPos.DRIVER to false))))

    @Test fun temperatureWordsFollowTheCarsUnit() {
        assertEquals("31도", CardEngine.tempText(31f, TempUnit.CELSIUS))
        assertEquals("영하 3도", CardEngine.tempText(-3f, TempUnit.CELSIUS))
        assertEquals("화씨 88도", CardEngine.tempText(31f, TempUnit.FAHRENHEIT))
    }

    // ── 충전 속도: 실제로 잰 값만 ──
    @Test fun chargePaceIsUnknownUntilItHasMeasuredAndNeverUsesAnAssumedSpeed() {
        val pace = ChargePace()
        assertNull(pace.minutesLeft(40))
        pace.onSoc(0L, 40)
        assertNull("한 번만 알면 못 재요", pace.minutesLeft(40))
        pace.onSoc(30_000L, 41)
        assertNull("1분이 안 지났어요", pace.minutesLeft(41))
        pace.onSoc(120_000L, 44)   // 2분에 4% = 분당 2%
        assertEquals(2.0, pace.percentPerMinute()!!, 0.001)
        assertEquals(18, pace.minutesLeft(44))   // 80%까지 36% ÷ 2
        pace.reset()
        assertNull(pace.minutesLeft(44))
    }
}
