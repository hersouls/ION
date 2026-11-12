package app.roadmemory.car.brain

import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.TripBook
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * 여행 기록 세부 화면의 글(하이엔드의 규칙 버전, 오너 2026-11-11 "여행 기록 눌렀을때 세부적인 내용을 볼 수 있게 하이엔드급으로").
 * 모든 문장은 이 기기에 저장된 값(도착 · 출발 시각, 목적지, 거리, 사람, 곡, 충전)과 저장된 다른 여행에서 센 값으로만 만들어요.
 * 저장하지 않은 값(중간 경유지 · 충전한 곳 · 속도)은 말하지 않아요.
 */
object TripInsight {
    data class Detail(
        val title: String,
        /** "2026.11.02(월) 오후 5:40 도착" */
        val whenLine: String,
        /** "2주 전" · "1년 전" */
        val agoLine: String,
        /** 출발 시각을 알 때만: "오후 3:30" */
        val startTime: String?,
        val arriveTime: String,
        /** 출발부터 도착까지(멈춘 시간 포함): "2시간 10분" */
        val duration: String?,
        val rankLine: String,
        val avgLine: String?,
        /** 같은 곳으로 간 다른 기록(최근 것이 앞). */
        val sameWay: List<SavedTrip>,
        val sameWayLine: String?,
        /** "1년 전 오늘 즈음이에요" (다른 해의 오늘 즈음일 때만) */
        val thatDayLine: String?,
    )

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd(E)", Locale.KOREAN)
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

    /** 여정 선의 거리 눈금(km): 보기 좋은 간격(5 · 10 · 20 · 25 · 50 · 100 · 200 · 500)으로 눈금이 다섯 개 안쪽이 되게. 거리가 짧아 눈금이 없으면 빈 목록. */
    fun scaleTicks(km: Int): List<Int> {
        for (step in listOf(5, 10, 20, 25, 50, 100, 200, 500)) {
            val ticks = (step until km step step).toList()        // 끝(도착)과 겹치는 눈금은 빼요
            if (ticks.size <= 5) return ticks
        }
        return emptyList()
    }

    fun clock(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).format(timeFmt)

    /** 분 → "3시간 5분" · "45분" · "2시간". 0 이하면 null. */
    fun durationText(minutes: Long): String? = when {
        minutes <= 0 -> null
        minutes < 60 -> "${minutes}분"
        minutes % 60 == 0L -> "${minutes / 60}시간"
        else -> "${minutes / 60}시간 ${minutes % 60}분"
    }

    fun detail(
        t: SavedTrip, all: List<SavedTrip>, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault(),
    ): Detail {
        val arrived = Instant.ofEpochMilli(t.at).atZone(zone)
        val minutes = t.startedAt?.let { (t.at - it) / 60_000L }?.takeIf { it in 1..(24 * 60) }

        // 거리 순위와 평균: 저장된 여행끼리의 비교(세 건 미만이면 평균은 말하지 않아요)
        val byKm = all.sortedWith(compareByDescending<SavedTrip> { it.km }.thenBy { it.id })
        val rank = byKm.indexOfFirst { it.id == t.id }.let { if (it < 0) null else it + 1 }
        val rankLine = when {
            all.size <= 1 || rank == null -> "저장된 여행이 이 한 건뿐이에요"
            rank == 1 -> "가장 멀리 다녀온 여행이에요 · 전체 ${all.size}건"
            rank == all.size -> "가장 가까이 다녀온 여행이에요 · 전체 ${all.size}건"
            else -> "먼 순서로 ${rank}번째예요 · 전체 ${all.size}건"
        }
        val avg = if (all.size >= 3) Math.round(all.sumOf { it.km }.toDouble() / all.size).toInt() else null
        val avgLine = avg?.let { a ->
            val diff = t.km - a
            if (abs(diff) < 5) "평균 ${a}km와 비슷해요" else "평균 ${a}km보다 ${abs(diff)}km ${if (diff > 0) "길어요" else "짧아요"}"
        }

        // 같은 곳: 이름이 같거나 좌표가 가까운 다른 기록(목적지를 모르면 말하지 않아요)
        val knownPlace = !t.destName.isNullOrBlank() || (t.lat != null && t.lon != null)
        val same = if (knownPlace) TripBook.tripsTo(all, t.destName, t.lat, t.lon).filter { it.id != t.id } else emptyList()
        val sameLine = when {
            !knownPlace -> null
            same.isEmpty() -> "이곳은 처음 다녀온 길이에요"
            else -> {
                val order = (same + t).sortedBy { it.at }
                val nth = order.indexOfFirst { it.id == t.id } + 1
                val earlier = same.firstOrNull { it.at < t.at }          // 최근 것이 앞이라 첫 번째가 바로 지난번
                val tail = earlier?.let { " · 지난번은 ${it.month} ${it.km}km" } ?: same.lastOrNull { it.at > t.at }?.let { " · 다음에 ${it.month}에 또 갔어요" } ?: ""
                "이곳에 ${order.size}번 다녀왔어요 · 이번이 ${nth}번째예요$tail"
            }
        }

        val thatDay = Memories.thatDay(listOf(t), today, zone = zone).firstOrNull()?.let { "${it.yearsAgo}년 전 오늘 즈음이에요" }

        return Detail(
            title = t.destName?.takeIf { it.isNotBlank() } ?: "${t.month} 여행",
            whenLine = "${arrived.format(dateFmt)} ${arrived.format(timeFmt)} 도착",
            agoLine = TripBook.agoLabel(t.month, java.time.YearMonth.from(today)),
            startTime = t.startedAt?.let { clock(it, zone) },
            arriveTime = clock(t.at, zone),
            duration = minutes?.let { durationText(it) },
            rankLine = rankLine, avgLine = avgLine,
            sameWay = same, sameWayLine = sameLine, thatDayLine = thatDay,
        )
    }
}
