package app.roadmemory.car.brain

import app.roadmemory.car.data.SavedTrip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 그날의 기억(하이엔드의 규칙 버전): 저장한 여행 가운데 "다른 해의 오늘 즈음"을 찾아요. 날짜와 장소는 저장한 값 그대로예요. */
object Memories {
    data class ThatDay(val trip: SavedTrip, val yearsAgo: Int, val daysOff: Long)

    fun date(t: SavedTrip, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(t.at).atZone(zone).toLocalDate()

    /** [today]와 월 · 일이 ±[window]일 안이고 해가 다른 여행(가까운 날짜 · 최근 해 먼저). */
    fun thatDay(trips: List<SavedTrip>, today: LocalDate, window: Int = 3, zone: ZoneId = ZoneId.systemDefault()): List<ThatDay> =
        trips.mapNotNull { t ->
            val d = date(t, zone)
            if (d.year == today.year) return@mapNotNull null
            val same = d.withYear(today.year)
            val off = ChronoUnit.DAYS.between(same, today)
            if (Math.abs(off) <= window) ThatDay(t, today.year - d.year, off) else null
        }.sortedWith(compareBy({ Math.abs(it.daysOff) }, { it.yearsAgo }))

    fun sentence(m: ThatDay): String {
        val when_ = when (m.daysOff) { 0L -> "오늘"; else -> "오늘 즈음" }
        val where = m.trip.destName?.let { "${it}에" } ?: "${m.trip.km}km를 달리는 길에"
        val who = when (m.trip.people) { 0, 1 -> ""; else -> " ${m.trip.people}명이 함께" }
        return "${m.yearsAgo}년 전 $when_ $where$who 있었어요."
    }
}
