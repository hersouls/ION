package app.roadmemory.car

import app.roadmemory.car.brain.Memories
import app.roadmemory.car.data.DemoTrips
import app.roadmemory.car.data.TripBook
import app.roadmemory.car.data.Tracks
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시연용 가상 여행(DemoTrips): 앱이 읽는 모양에 맞고, ION의 기억 기능이 이 기록으로 돌아가는지. */
class DemoTripsTest {
    private val all = DemoTrips.all

    @Test fun thereAreSeveralAndAllMarkedAsMadeUp() {
        assertTrue(all.size >= 10)
        assertTrue(all.all { DemoTrips.isDemo(it) })
        assertEquals("번호가 겹치면 안 돼요", all.size, all.map { it.id }.toSet().size)
    }

    @Test fun newestFirstLikeTheStore() {
        assertEquals(all.sortedByDescending { it.at }, all)
    }

    @Test fun monthMatchesTheDateAndFieldsAreSane() {
        all.forEach { t ->
            val d = Memories.date(t, ZoneId.of("Asia/Seoul"))
            assertEquals(t.month, "%04d.%02d".format(d.year, d.monthValue))
            assertTrue("km ${t.km}", t.km in 1..600)
            assertTrue("people ${t.people}", t.people in 1..5)
            assertTrue("charges ${t.charges}", t.charges in 0..3)
            // 한 번에 가는 길의 충전은 거리에 맞아야 해요: 150km 안쪽은 충전 없이도 가요
            if (t.km < 100) assertEquals("${t.destName} ${t.km}km", 0, t.charges)
        }
    }

    @Test fun placesHaveCoordinatesInKoreaOrNoNameAtAll() {
        all.forEach { t ->
            if (t.destName == null) { assertEquals(null, t.lat); assertEquals(null, t.lon) }
            else { assertTrue("${t.destName} lat", t.lat!! in 33.0..38.7); assertTrue("${t.destName} lon", t.lon!! in 124.5..130.0) }
        }
        assertTrue("이름 없는 여행도 하나 있어요(내비를 안 쓴 경우)", all.any { it.destName == null })
    }

    @Test fun tracksAreSongsTheAppReallyHas() {
        val titles = Tracks.all.map { it.title }.toSet()
        all.flatMap { it.tracks }.forEach { assertTrue("$it 곡이 앱에 없어요", it in titles) }
    }

    @Test fun thatDayFindsLastYearsSokchoTrip() {
        val m = Memories.thatDay(all, LocalDate.of(2026, 11, 11), zone = ZoneId.of("Asia/Seoul")).firstOrNull()
        assertEquals("속초 관광수산시장", m?.trip?.destName)
        assertEquals(1, m?.yearsAgo)
    }

    @Test fun sameDestinationIsFoundByNameAndByCoordinates() {
        assertEquals(2, TripBook.tripsTo(all, "남이섬", null, null).size)
        assertEquals(2, TripBook.tripsTo(all, null, 37.7915, 127.5255).size)
    }
}
