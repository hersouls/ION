package app.roadmemory.car.brain

import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.studio.CalendarEvent
import app.roadmemory.car.studio.Catalog
import app.roadmemory.car.studio.LibraryState
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 캘린더 동기화의 하이엔드(규칙 버전). 서버 · 모델 없이 이 기기에서, 가져온 일정과 차가 준 값으로만 말해요.
 *  · 때: 일정에 적힌 말("금요일 저녁 7시" · "다음 주 화요일 오후 2시")을 오늘 기준의 날짜 · 시각으로 풀어요([CalendarTime]). 못 풀면 모른다고 두고 맨 뒤에 놓아요.
 *  · 묶기: 오늘 · 내일 · 이번 주 · 다음 주 · 그 뒤로 묶고, 지나지 않은 가장 가까운 약속을 "다음 약속"으로 올려요. 남은 시간도 말해요.
 *  · 가는 중: 내비가 약속 장소로 안내하고 있으면 내비가 준 남은 시간으로 도착 시각을 약속 시각과 견줘요("약속보다 12분 일찍 도착해요"). 도착 때 배터리도 내비 값이에요.
 *  · 이어 보기: 같은 곳에 다녀온 저장 여행, 마이 트립의 정류장, 바로 앞 약속에서 여기까지의 거리(직선 × 1.3)와 지금 갈 수 있는 거리.
 * 앱은 차의 위치를 읽지 않아서 "지금 출발하세요"는 말하지 않아요(여기서 약속 장소까지의 거리를 몰라요). ION은 먼저 경로를 바꾸지 않아요.
 */
object CalendarTime {
    data class Resolved(val date: LocalDate, val time: LocalTime?)

    private val days = mapOf(
        '월' to DayOfWeek.MONDAY, '화' to DayOfWeek.TUESDAY, '수' to DayOfWeek.WEDNESDAY, '목' to DayOfWeek.THURSDAY,
        '금' to DayOfWeek.FRIDAY, '토' to DayOfWeek.SATURDAY, '일' to DayOfWeek.SUNDAY,
    )

    /** 한 주는 월요일에 시작해요. */
    fun monday(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 적힌 말에서 날짜(와 시각)를 풀어요. 날짜를 못 풀면 null이에요(지어내지 않아요). 시각이 없으면 날짜만이에요. */
    fun resolve(text: String, today: LocalDate): Resolved? {
        val t = text.replace(" ", "")
        val date: LocalDate = when {
            t.contains("오늘") -> today
            t.contains("내일") -> today.plusDays(1)
            t.contains("모레") -> today.plusDays(2)
            else -> Regex("(\\d{1,2})월(\\d{1,2})일").find(t)?.let { m ->
                runCatching { LocalDate.of(today.year, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
                    ?.let { if (it.isBefore(today)) it.plusYears(1) else it }
            } ?: Regex("([월화수목금토일])요일").find(t)?.let { m ->
                val dow = days.getValue(m.groupValues[1][0])
                if (t.contains("다음주")) monday(today).plusDays(7L + dow.value - 1)
                else if (t.contains("이번주")) monday(today).plusDays(dow.value - 1L)
                else today.with(TemporalAdjusters.nextOrSame(dow))
            } ?: return null
        }
        return Resolved(date, time(t))
    }

    private fun time(t: String): LocalTime? {
        if (t.contains("정오")) return LocalTime.NOON
        if (t.contains("자정")) return LocalTime.MIDNIGHT
        val m = Regex("(\\d{1,2})시(?:(\\d{1,2})분|(반))?").find(t) ?: return null
        var h = m.groupValues[1].toInt()
        val min = when { m.groupValues[3].isNotEmpty() -> 30; m.groupValues[2].isNotEmpty() -> m.groupValues[2].toInt(); else -> 0 }
        val pm = listOf("오후", "저녁", "밤").any { t.contains(it) } || (t.contains("낮") && h < 6)
        val am = listOf("오전", "아침", "새벽").any { t.contains(it) }
        if (pm && h < 12) h += 12
        if (am && h == 12) h = 0
        return if (h in 0..23 && min in 0..59) LocalTime.of(h, min) else null
    }
}

/** 일정 하나와 풀어 낸 때. [date]가 null이면 때를 못 푼 일정이에요. */
data class Appt(val event: CalendarEvent, val date: LocalDate?, val time: LocalTime?) {
    val at: LocalDateTime? get() = date?.atTime(time ?: LocalTime.MIDNIGHT)
    val located: Boolean get() = event.lat != null && event.lng != null
}

enum class AgendaBucket(val label: String) { TODAY("오늘"), TOMORROW("내일"), THIS_WEEK("이번 주"), NEXT_WEEK("다음 주"), LATER("그 뒤"), UNKNOWN("때를 알 수 없는 일정") }

/** 달력 띠의 하루. */
data class AgendaDay(val date: LocalDate, val count: Int, val today: Boolean, val past: Boolean)

/** 가는 중인 약속의 도착 예상. [late]는 약속 시각보다 늦게 닿는다는 뜻이에요. 모든 값은 내비가 준 값에서 나와요. */
data class ApptArrival(val headline: String, val detail: String, val late: Boolean)

object Agenda {
    /** 달력 띠에 보이는 날 수(이번 주 월요일부터 두 주). */
    const val STRIP_DAYS = 14
    /** 약속 장소와 내비 목적지가 같은 곳이라고 보는 거리(km, 설계 값). */
    const val SAME_PLACE_KM = 0.5
    /** 저장한 여행의 목적지가 같은 곳이라고 보는 거리(km). 여행 기록의 같은 곳 매칭(1500m)과 같아요. */
    const val VISITED_KM = 1.5
    /** 앞 약속과 이어서 보는 사이(시간, 설계 값): 이 안이면 "앞 약속에서 여기까지"를 말해요. */
    const val CHAIN_HOURS = 48L

    private val dowShort = listOf("월", "화", "수", "목", "금", "토", "일")
    fun dow(d: LocalDate): String = dowShort[d.dayOfWeek.value - 1]

    /** 때가 이른 순서(같으면 가져온 순서). 때를 못 푼 일정은 맨 뒤예요. */
    fun build(events: List<CalendarEvent>, today: LocalDate): List<Appt> =
        events.map { e -> CalendarTime.resolve(e.whenText, today).let { r -> Appt(e, r?.date, r?.time) } }
            .withIndex().sortedWith(compareBy<IndexedValue<Appt>>({ it.value.at == null }, { it.value.at }, { it.index })).map { it.value }

    /** 지났는지: 시각이 있으면 그 시각이 지났을 때, 날짜만 있으면 그날이 지났을 때. 때를 모르면 지났다고 하지 않아요. */
    fun passed(a: Appt, now: LocalDateTime): Boolean = when {
        a.date == null -> false
        a.time == null -> a.date.isBefore(now.toLocalDate())
        else -> a.at!!.isBefore(now)
    }

    fun bucket(a: Appt, today: LocalDate): AgendaBucket {
        val d = a.date ?: return AgendaBucket.UNKNOWN
        val mon = CalendarTime.monday(today)
        return when {
            d == today -> AgendaBucket.TODAY
            d == today.plusDays(1) -> AgendaBucket.TOMORROW
            d.isBefore(mon.plusDays(7)) -> AgendaBucket.THIS_WEEK
            d.isBefore(mon.plusDays(14)) -> AgendaBucket.NEXT_WEEK
            else -> AgendaBucket.LATER
        }
    }

    /** 묶음 순서대로(빈 묶음은 빼요). */
    fun grouped(list: List<Appt>, today: LocalDate): List<Pair<AgendaBucket, List<Appt>>> =
        AgendaBucket.entries.mapNotNull { b -> list.filter { bucket(it, today) == b }.takeIf { it.isNotEmpty() }?.let { b to it } }

    /** 지나지 않은 가장 가까운 약속. 때를 푼 것이 하나도 없으면 null이에요. */
    fun next(list: List<Appt>, now: LocalDateTime): Appt? = list.firstOrNull { it.date != null && !passed(it, now) }

    /** 내비로 안내할 수 있는(장소를 아는) 가장 가까운 약속. 때를 푼 것이 없으면 가져온 순서의 첫 약속이에요. */
    fun nextLocated(list: List<Appt>, now: LocalDateTime): Appt? =
        list.firstOrNull { it.located && it.date != null && !passed(it, now) } ?: list.firstOrNull { it.located && it.date == null }

    fun timeLabel(t: LocalTime): String {
        val h12 = when { t.hour == 0 -> 12; t.hour > 12 -> t.hour - 12; else -> t.hour }
        val part = when { t.hour == 0 -> "밤"; t.hour < 12 -> "오전"; t.hour == 12 -> "낮"; else -> "오후" }
        return "$part $h12:${"%02d".format(t.minute)}"
    }

    /** "11.13(금) 오후 7:00". 때를 못 풀었으면 일정에 적힌 말 그대로예요. */
    fun dateLabel(a: Appt): String {
        val d = a.date ?: return a.event.whenText
        return "${d.monthValue}.${d.dayOfMonth}(${dow(d)})" + (a.time?.let { " " + timeLabel(it) } ?: "")
    }

    private fun span(min: Long): String = when {
        min < 60 -> "${min}분"
        min % 60 == 0L -> "${min / 60}시간"
        else -> "${min / 60}시간 ${min % 60}분"
    }

    /** 남은 시간 한마디: "곧이에요" · "40분 뒤" · "2시간 40분 뒤" · "내일" · "3일 뒤" · "지났어요". 때를 모르면 null. */
    fun countdown(a: Appt, now: LocalDateTime): String? {
        val d = a.date ?: return null
        if (passed(a, now)) return "지났어요"
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), d)
        if (days == 0L) {
            if (a.time == null) return "오늘"
            val min = Duration.between(now, a.at).toMinutes()
            return if (min < 1) "곧이에요" else "${span(min)} 뒤"
        }
        return if (days == 1L) "내일" else "${days}일 뒤"
    }

    /** 이번 주 월요일부터 두 주. 날마다 약속이 몇 개인지. */
    fun strip(list: List<Appt>, today: LocalDate): List<AgendaDay> {
        val mon = CalendarTime.monday(today)
        return (0 until STRIP_DAYS).map { i ->
            val d = mon.plusDays(i.toLong())
            AgendaDay(d, list.count { it.date == d }, d == today, d.isBefore(today))
        }
    }

    private fun norm(s: String) = s.replace(" ", "")

    /** 내비가 지금 이 약속 장소로 안내하고 있는지: 목적지 좌표가 가깝거나 이름이 같아요. */
    fun guiding(a: Appt, nav: NavState): Boolean {
        if (!nav.routeActive) return false
        val e = a.event
        if (e.lat != null && e.lng != null && nav.destLat != null && nav.destLon != null && FamilyInsight.km(e.lat, e.lng, nav.destLat, nav.destLon) <= SAME_PLACE_KM) return true
        val dest = nav.destinationName?.let(::norm)?.takeIf { it.isNotEmpty() } ?: return false
        val place = norm(e.place).takeIf { it.isNotEmpty() } ?: return false
        return dest == place || dest.contains(place)
    }

    /**
     * 가는 중인 약속의 도착 예상. 내비가 남은 시간을 주지 않았으면 null이에요(어림하지 않아요).
     * 약속이 오늘이고 시각이 있으면 약속 시각과 견주고, 아니면 도착 예상 시각만 말해요.
     */
    fun arrival(a: Appt, nav: NavState, now: LocalDateTime): ApptArrival? {
        if (!guiding(a, nav)) return null
        val sec = nav.remainTimeS?.takeIf { it >= 0 } ?: return null
        val eta = now.plusSeconds(sec.toLong())
        val detail = listOfNotNull(
            "도착 예상 ${timeLabel(eta.toLocalTime())}",
            nav.remainDistanceM?.let { "남은 ${"%.1f".format(it / 1000.0)}km" },
            nav.estimatedSocAtDest?.let { "도착하면 배터리 약 ${it}%" },
        ).joinToString(" · ")
        val at = a.at
        if (at == null || a.time == null || a.date != eta.toLocalDate() || passed(a, now)) return ApptArrival("약속 장소로 가는 중이에요", detail, false)
        val early = Duration.between(eta, at).toMinutes()
        return when {
            early >= 1 -> ApptArrival("약속보다 ${span(early)} 일찍 도착해요", detail, false)
            early <= -1 -> ApptArrival("약속보다 ${span(-early)} 늦게 도착해요", detail, true)
            else -> ApptArrival("약속 시각에 맞춰 도착해요", detail, false)
        }
    }

    /**
     * 바로 앞 약속에서 이 약속까지: 둘 다 장소를 알고 [CHAIN_HOURS] 안에 이어지면 거리(직선 × 1.3)와 지금 갈 수 있는 거리로 본 충전 여부를 말해요.
     * 갈 수 있는 거리는 차가 준 값이고 그 80%만 믿어요([TripPlan.SAFE_RANGE]). 모르면 거리만 말해요.
     */
    fun leg(list: List<Appt>, a: Appt, rangeKm: Float?): String? {
        if (!a.located || a.at == null) return null
        val prev = list.takeWhile { it !== a }.lastOrNull { it.located && it.at != null } ?: return null
        if (Duration.between(prev.at, a.at).toHours() !in 0..CHAIN_HOURS) return null
        val km = FamilyInsight.km(prev.event.lat!!, prev.event.lng!!, a.event.lat!!, a.event.lng!!) * TripPlan.ROAD_FACTOR
        if (km < 1.0) return "앞 약속(${prev.event.place})과 같은 곳이에요"
        val head = "앞 약속(${prev.event.place})에서 약 ${Math.round(km)}km"
        val r = rangeKm?.takeIf { it > 0f } ?: return head
        return head + if (km <= r * TripPlan.SAFE_RANGE) " · 지금 갈 수 있는 거리(${Math.round(r)}km)로 충전 없이 가요" else " · 지금 갈 수 있는 거리(${Math.round(r)}km)로는 가는 길에 충전이 필요해요"
    }

    /** 이 기기에 있는 다른 것과 이어 보기: 같은 곳에 다녀온 저장 여행, 마이 트립의 정류장. 이어지는 것이 없으면 비어 있어요. */
    fun links(a: Appt, cat: Catalog, lib: LibraryState, trips: List<SavedTrip>): List<String> {
        val e = a.event
        val place = norm(e.place)
        val out = mutableListOf<String>()
        val been = trips.filter { t ->
            val name = t.destName?.let(::norm).orEmpty()
            (name.isNotEmpty() && place.isNotEmpty() && (name == place || name.contains(place) || place.contains(name))) ||
                (e.lat != null && e.lng != null && t.lat != null && t.lon != null && FamilyInsight.km(e.lat, e.lng, t.lat, t.lon) <= VISITED_KM)
        }
        if (been.isNotEmpty()) out += "여기 ${been.size}번 다녀왔어요 · 마지막은 ${been.maxByOrNull { it.at }!!.month}"
        cat.trips.firstOrNull { t ->
            t.stops.any { s -> norm(s.name) == place || (e.lat != null && e.lng != null && FamilyInsight.km(e.lat, e.lng, s.lat, s.lng) <= FamilyInsight.AT_KM) }
        }?.let { t -> out += "마이 트립 ‘${t.title}’의 정류장이에요" + if (lib.has(t.id)) " · 담아 두었어요" else "" }
        return out
    }

    /** 약속 한 줄: "가족 저녁 식사 · 11.13(금) 오후 7:00 · 속초관광수산시장". */
    fun line(a: Appt): String = listOf(a.event.title, dateLabel(a), a.event.place).filter { it.isNotBlank() }.joinToString(" · ")
}
