package app.roadmemory.car

import app.roadmemory.car.data.TripMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 이번 트립 거리는 오도미터 차이예요: 출발(P → D)에서 0km로 시작하고, 서 있는 동안은 그 값을 지켜요. */
class TripMeterTest {
    @Test fun aParkedCarHasNoTripYet() {
        val m = TripMeter()
        assertNull(m.onOdometer(1000f))
        assertNull(m.onGear(park = true))
    }

    @Test fun departingStartsAtZeroAndTheOdometerDifferenceFollows() {
        val m = TripMeter()
        m.onOdometer(1000f)
        assertEquals(0f, m.onGear(park = false)!!, 0f)
        assertEquals(12.5f, m.onOdometer(1012.5f)!!, 0.001f)
        assertEquals(142f, m.onOdometer(1142f)!!, 0.001f)
    }

    @Test fun shiftingBetweenDriveAndReverseDoesNotRestartTheTrip() {
        val m = TripMeter()
        m.onOdometer(50f); m.onGear(park = false); m.onOdometer(60f)
        assertNull(m.onGear(park = false))   // D → R: 출발이 아니에요
        assertEquals(15f, m.onOdometer(65f)!!, 0.001f)
    }

    @Test fun parkingKeepsTheLastTripUntilTheNextDeparture() {
        val m = TripMeter()
        m.onOdometer(0f); m.onGear(park = false); m.onOdometer(30f)
        assertNull(m.onGear(park = true))
        assertEquals(30f, m.onOdometer(30f)!!, 0.001f)
        assertEquals(0f, m.onGear(park = false)!!, 0f)   // 다음 출발에서 0km로
        assertEquals(5f, m.onOdometer(35f)!!, 0.001f)
    }

    @Test fun switchingTheAppOnWhileDrivingUsesTheFirstReadingAsTheStart() {
        val m = TripMeter()
        m.onGear(park = false)               // 오도미터를 읽기 전에 D
        assertEquals(0f, m.onOdometer(500f)!!, 0f)
        assertEquals(7f, m.onOdometer(507f)!!, 0.001f)
    }

    @Test fun anOdometerThatGoesBackwardsNeverGivesANegativeTrip() {
        val m = TripMeter()
        m.onOdometer(100f); m.onGear(park = false)
        assertEquals(0f, m.onOdometer(90f)!!, 0f)
    }
}
