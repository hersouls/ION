package app.roadmemory.car

import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.GearPos
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.TripBook
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.engine.ModeEngine
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 모드는 차 신호가 정하고(기어 · 충전구), "기억"은 이 기기에 저장된 진짜 여행에서만 나와요. */
class EngineTest {
    private fun car(gear: GearPos?, port: Boolean? = null) = CarSnapshot(gear = gear, chargePortOpen = port)

    @Test fun modeFollowsGearAndChargePort() {
        assertEquals(Mode.DRIVING, ModeEngine.modeOf(car(GearPos.DRIVE)))
        assertEquals(Mode.DRIVING, ModeEngine.modeOf(car(GearPos.REVERSE)))
        assertEquals(Mode.CHARGING, ModeEngine.modeOf(car(GearPos.PARK, port = true)))
        assertEquals(Mode.PARKED, ModeEngine.modeOf(car(GearPos.PARK, port = false)))
        assertEquals("값을 모르면 서 있는 걸로 봐요", Mode.PARKED, ModeEngine.modeOf(CarSnapshot()))
        assertEquals("달리는 중에는 충전구가 열려 있어도 달리는 중", Mode.DRIVING, ModeEngine.modeOf(car(GearPos.DRIVE, port = true)))
    }

    private fun t(id: Long, at: Long, name: String?, lat: Double?, lon: Double?) = SavedTrip(id, at, "2026.11", name, lat, lon, 10, 2, emptyList(), 0)

    @Test fun tripsToTheSamePlaceMatchByNameOrByDistanceNewestFirst() {
        val trips = listOf(
            t(1, 100, "푸른 바닷가", 37.0, 127.0), t(2, 300, "푸른 바닷가 주차장", 37.0, 127.0), t(3, 200, null, 37.001, 127.001), t(4, 50, "먼 곳", 35.0, 129.0),
        )
        assertEquals(listOf(2L, 3L, 1L), TripBook.tripsTo(trips, "푸른 바닷가", 37.0, 127.0).map { it.id })   // 이름이 겹치는 둘 + 가까운 한 곳
        assertEquals(listOf(4L), TripBook.tripsTo(trips, "먼 곳", null, null).map { it.id })
        assertTrue(TripBook.tripsTo(trips, "없는 곳", 10.0, 10.0).isEmpty())
        assertTrue("좌표도 이름도 모르면 같은 곳이라고 하지 않아요", TripBook.tripsTo(trips, null, null, null).isEmpty())
    }

    @Test fun wordsForTimeAndPeople() {
        val now = YearMonth.of(2026, 11)
        assertEquals("2년 전", TripBook.agoLabel("2024.10", now))
        assertEquals("5달 전", TripBook.agoLabel("2026.06", now))
        assertEquals("지난달", TripBook.agoLabel("2026.10", now))
        assertEquals("이번 달", TripBook.agoLabel("2026.11", now))
        assertEquals("예전", TripBook.agoLabel("모름", now))
        assertEquals("", TripBook.peopleWord(0)); assertEquals("혼자", TripBook.peopleWord(1)); assertEquals("셋이", TripBook.peopleWord(3)); assertEquals("7명이", TripBook.peopleWord(7))
        assertEquals(listOf("푸른 바닷가", "먼 곳"), TripBook.placeNames(listOf(t(1, 1, "푸른 바닷가", null, null), t(2, 2, "푸른 바닷가", null, null), t(3, 3, "먼 곳", null, null), t(4, 4, null, null, null))))
    }
}
