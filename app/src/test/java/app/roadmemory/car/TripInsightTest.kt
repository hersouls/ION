package app.roadmemory.car

import app.roadmemory.car.brain.TripInsight
import app.roadmemory.car.data.DemoTrips
import app.roadmemory.car.data.SavedTrip
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 여행 기록 세부 화면의 글: 저장된 값과 저장된 다른 여행에서 센 값으로만 만들어져요. */
class TripInsightTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val today = LocalDate.of(2026, 11, 11)
    private val all = DemoTrips.all
    private fun d(id: Long) = TripInsight.detail(all.first { it.id == id }, all, today, seoul)

    @Test fun timesAndDurationComeFromStoredValues() {
        val x = d(-1)                                   // 남이섬 2026.11.02 17:40 도착, 130분
        assertEquals("가평 남이섬 선착장", x.title)
        assertEquals("2026.11.02(월) 오후 5:40 도착", x.whenLine)
        assertEquals("오후 3:30", x.startTime)
        assertEquals("오후 5:40", x.arriveTime)
        assertEquals("2시간 10분", x.duration)
        assertEquals("이번 달", x.agoLine); assertEquals("지난달", d(-2).agoLine); assertEquals("1년 전", d(-9).agoLine)
    }

    @Test fun durationText() {
        assertEquals("45분", TripInsight.durationText(45)); assertEquals("2시간", TripInsight.durationText(120))
        assertEquals("7시간", TripInsight.durationText(420)); assertEquals("3시간 5분", TripInsight.durationText(185))
        assertNull(TripInsight.durationText(0)); assertNull(TripInsight.durationText(-3))
    }

    @Test fun unknownStartMeansNoDurationAndNoStartTime() {
        val t = SavedTrip(1, 1_700_000_000_000, "2023.11", "어딘가", 37.0, 127.0, 50, 2, emptyList(), 0)
        val x = TripInsight.detail(t, listOf(t), today, seoul)
        assertNull(x.startTime); assertNull(x.duration)
        assertEquals("저장된 여행이 이 한 건뿐이에요", x.rankLine); assertNull(x.avgLine)
    }

    @Test fun rankAndAverageAreCountedFromSavedTrips() {
        assertEquals("가장 멀리 다녀온 여행이에요 · 전체 12건", d(-11).rankLine)        // 경주 366km
        assertEquals("가장 가까이 다녀온 여행이에요 · 전체 12건", d(-5).rankLine)         // 이름 없는 44km
        val avg = Math.round(all.sumOf { it.km }.toDouble() / all.size).toInt()
        assertTrue(d(-3).avgLine!!.startsWith("평균 ${avg}km보다 "))                  // 담양 298km는 평균(175)보다 길어요
        assertTrue(d(-3).avgLine!!.endsWith("길어요"))
        assertTrue(d(-2).avgLine!!.endsWith("짧아요"))
    }

    @Test fun sameWayCountsOtherTripsToTheSamePlace() {
        assertEquals("이곳에 2번 다녀왔어요 · 이번이 2번째예요 · 지난번은 2025.10 101km", d(-1).sameWayLine)
        assertEquals(1, d(-1).sameWay.size)
        assertEquals("이곳에 2번 다녀왔어요 · 이번이 1번째예요 · 다음에 2026.11에 또 갔어요", d(-10).sameWayLine)
        assertEquals("이곳은 처음 다녀온 길이에요", d(-4).sameWayLine)
        assertNull("목적지를 모르면 같은 곳 말은 없어요", d(-5).sameWayLine)
    }

    @Test fun thatDayOnlyAroundTodayInAnotherYear() {
        assertEquals("1년 전 오늘 즈음이에요", d(-9).thatDayLine)       // 2025.11.09
        assertNull(d(-1).thatDayLine)                                 // 올해
        assertNull(d(-4).thatDayLine)
    }

    @Test fun everyDemoTripHasAPlausibleDuration() {
        all.forEach { t ->
            val min = (t.at - t.startedAt!!) / 60_000
            assertTrue("${t.destName} ${min}분", min in 20..720)
            val overall = t.km / (min / 60.0)                           // 멈춘 시간 포함 평균
            assertTrue("${t.destName} ${overall}km/h", overall in 25.0..110.0)
        }
    }

    @Test fun scaleTicksUseNiceSteps() {
        assertEquals(listOf(20, 40, 60, 80), TripInsight.scaleTicks(98))
        assertEquals(listOf(10, 20, 30, 40), TripInsight.scaleTicks(44))
        assertEquals(listOf(100, 200, 300), TripInsight.scaleTicks(366))
        assertEquals(listOf(20, 40, 60), TripInsight.scaleTicks(61))
        assertEquals(listOf(5, 10), TripInsight.scaleTicks(12))
        assertEquals(emptyList<Int>(), TripInsight.scaleTicks(5))
        assertEquals("도착과 겹치는 눈금은 빼요", listOf(20, 40, 60, 80), TripInsight.scaleTicks(100))
        (1..800).forEach { km -> assertTrue("$km", TripInsight.scaleTicks(km).size <= 5 && TripInsight.scaleTicks(km).all { it in 1 until km }) }
    }
}
