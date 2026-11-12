package app.roadmemory.car

import app.roadmemory.car.data.CarPart
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.SeatPos
import app.roadmemory.car.data.SeatState
import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.engine.CardEngine
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.ui.IonFeedItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "ION이 챙기고 있어요" 목록: 차가 준 값에서만 나오고, 값이 없으면 한 줄도 없어요. */
class IonFeedTest {
    private val base = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED, car = CarSnapshot())
    private fun ids(ui: UiState) = IonFeedItems.build(ui).map { it.id }

    @Test fun nothingKnownMeansNothingSaid() = assertTrue(IonFeedItems.build(base).isEmpty())

    @Test fun unbeltedSeatIsNamedWithoutGuessing() {
        val seated = base.car.copy(seats = SeatPos.entries.associateWith { SeatState(occupied = it == SeatPos.PASSENGER, belted = if (it == SeatPos.PASSENGER) false else null) })
        val item = IonFeedItems.build(base.copy(car = seated)).single()
        assertEquals("조수석 안전벨트를 확인해 주세요", item.title)
        val unknown = base.car.copy(seats = SeatPos.entries.associateWith { SeatState(occupied = it == SeatPos.PASSENGER, belted = null) })
        assertTrue("벨트 값을 모르면 말하지 않아요", IonFeedItems.build(base.copy(car = unknown)).isEmpty())
    }

    @Test fun routeShowsDestinationAndTheCarsOwnArrivalBattery() {
        val nav = NavState(routeActive = true, destinationName = "푸른 바닷가", remainTimeS = 1800, estimatedSocAtDest = CardEngine.LOW_SOC_AT_DEST)
        val items = IonFeedItems.build(base.copy(mode = Mode.DRIVING, nav = nav))
        assertEquals(listOf("route", "charge"), items.map { it.id })
        assertEquals("푸른 바닷가 · 30분", items[0].title)
        assertEquals("도착하면 배터리가 ${CardEngine.LOW_SOC_AT_DEST}%예요", items[0].detail)
        assertEquals(listOf("route"), ids(base.copy(mode = Mode.DRIVING, nav = nav.copy(estimatedSocAtDest = CardEngine.LOW_SOC_AT_DEST + 1))))
        assertEquals("도착 시각을 몰라요", IonFeedItems.build(base.copy(nav = nav.copy(remainTimeS = null, estimatedSocAtDest = null))).single().title.substringAfter("· "))
    }

    @Test fun chargingSaysItIsMeasuringUntilItHasMeasured() {
        assertEquals("충전 속도를 재는 중이에요", IonFeedItems.build(base.copy(mode = Mode.CHARGING)).single().detail)
        assertEquals("80%까지 약 18분 남았어요", IonFeedItems.build(base.copy(mode = Mode.CHARGING, chargeMinLeft = 18)).single().detail)
    }

    @Test fun restAndColdAndOpenParts() {
        assertEquals(listOf("rest"), ids(base.copy(mode = Mode.DRIVING, driveMinutes = 130)))
        assertTrue(ids(base.copy(mode = Mode.DRIVING, driveMinutes = 90)).isEmpty())
        assertEquals(listOf("cold"), ids(base.copy(car = base.car.copy(outsideTempC = -2f))))
        assertTrue(ids(base.copy(car = base.car.copy(outsideTempC = 12f))).isEmpty())
        val open = base.car.copy(open = CarPart.entries.associateWith { it == CarPart.TRUNK })
        assertEquals(listOf("check"), ids(base.copy(car = open)))
    }
}
