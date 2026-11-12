package app.roadmemory.car

import app.roadmemory.car.brain.Advisor
import app.roadmemory.car.brain.Agenda
import app.roadmemory.car.brain.AgendaBucket
import app.roadmemory.car.brain.CalendarTime
import app.roadmemory.car.brain.ChargeHistory
import app.roadmemory.car.brain.QuietState
import app.roadmemory.car.brain.StudioAnswers
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.studio.CalendarEvent
import app.roadmemory.car.studio.CatalogParser
import app.roadmemory.car.studio.LibraryState
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 캘린더 동기화의 규칙(2026-11-11 "캘린더 기능 하이엔드급으로"): 적힌 말을 날짜로 풀기 · 묶기 · 남은 시간 · 가는 중 도착 견주기 · 이어 보기.
 * 오늘은 2026.11.11(수)로 고정해요. 모르는 값으로는 말하지 않아요.
 */
class AgendaTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val today = LocalDate.of(2026, 11, 11)   // 수요일
    private val now = today.atTime(13, 5)
    private val agenda = Agenda.build(cat.calendar, today)
    private fun ev(id: String, w: String, place: String = "어딘가", lat: Double? = null, lng: Double? = null) = CalendarEvent(id, "일 $id", w, place, "", lat, lng)
    private fun r(w: String) = CalendarTime.resolve(w, today)

    @Test fun wordsBecomeDatesAndTimes() {
        assertEquals(CalendarTime.Resolved(LocalDate.of(2026, 11, 13), LocalTime.of(19, 0)), r("금요일 저녁 7시"))
        assertEquals(CalendarTime.Resolved(LocalDate.of(2026, 11, 14), LocalTime.of(10, 0)), r("토요일 오전 10시"))
        assertEquals(CalendarTime.Resolved(LocalDate.of(2026, 11, 17), LocalTime.of(14, 0)), r("다음 주 화요일 오후 2시"))
        assertEquals(CalendarTime.Resolved(LocalDate.of(2026, 11, 22), LocalTime.NOON), r("다음 주 일요일 정오"))
        assertEquals("오늘 요일이면 오늘이에요", LocalDate.of(2026, 11, 11), r("수요일 오후 3시 반")!!.date)
        assertEquals(LocalTime.of(15, 30), r("수요일 오후 3시 반")!!.time)
        assertEquals(LocalDate.of(2026, 11, 12), r("내일 아침 8시 20분")!!.date)
        assertEquals(LocalTime.of(8, 20), r("내일 아침 8시 20분")!!.time)
        assertEquals(LocalDate.of(2026, 12, 3), r("12월 3일")!!.date)
        assertNull("시각이 없으면 날짜만이에요", r("12월 3일")!!.time)
        assertEquals("지난 날짜는 내년이에요", LocalDate.of(2027, 3, 1), r("3월 1일 오전 9시")!!.date)
        assertNull("날짜를 못 풀면 지어내지 않아요", r("조만간"))
        assertNull(r("오후 2시"))
    }

    @Test fun agendaIsSortedAndBucketed() {
        assertEquals(listOf("ev1", "ev2", "ev3", "ev4", "ev5"), agenda.map { it.event.id })
        assertEquals(listOf(AgendaBucket.THIS_WEEK to 2, AgendaBucket.NEXT_WEEK to 3), Agenda.grouped(agenda, today).map { it.first to it.second.size })
        val mixed = Agenda.build(listOf(ev("x", "조만간"), ev("b", "내일 오전 9시"), ev("a", "오늘 오후 6시"), ev("c", "12월 25일")), today)
        assertEquals("때가 이른 순서 · 모르는 것은 맨 뒤", listOf("a", "b", "c", "x"), mixed.map { it.event.id })
        assertEquals(listOf(AgendaBucket.TODAY, AgendaBucket.TOMORROW, AgendaBucket.LATER, AgendaBucket.UNKNOWN), mixed.map { Agenda.bucket(it, today) })
    }

    @Test fun nextAndCountdown() {
        assertEquals("ev1", Agenda.next(agenda, now)!!.event.id)
        assertEquals("2일 뒤", Agenda.countdown(agenda[0], now))
        assertEquals("11.13(금) 오후 7:00", Agenda.dateLabel(agenda[0]))
        assertEquals("11.22(일) 낮 12:00", Agenda.dateLabel(agenda[4]))
        val list = Agenda.build(listOf(ev("a", "오늘 오전 9시"), ev("b", "오늘 오후 3시 45분"), ev("c", "내일 오전 9시"), ev("d", "오늘"), ev("x", "조만간")), today)
        fun cd(id: String) = Agenda.countdown(list.first { it.event.id == id }, now)
        assertEquals("지났어요", cd("a")); assertEquals("2시간 40분 뒤", cd("b")); assertEquals("내일", cd("c")); assertEquals("오늘", cd("d")); assertNull(cd("x"))
        assertEquals("지난 약속은 건너뛰어요", "d", Agenda.next(list, now)!!.event.id)
        assertEquals("40분 뒤", Agenda.countdown(list.first { it.event.id == "b" }, today.atTime(15, 5)))
        assertEquals("곧이에요", Agenda.countdown(list.first { it.event.id == "b" }, today.atTime(15, 44, 30)))
        assertEquals("2시간 뒤", Agenda.countdown(list.first { it.event.id == "b" }, today.atTime(13, 45)))
        assertNull("때를 푼 것이 없으면 다음 약속이 없어요", Agenda.next(Agenda.build(listOf(ev("x", "조만간")), today), now))
    }

    @Test fun stripIsTwoWeeksFromMonday() {
        val s = Agenda.strip(agenda, today)
        assertEquals(14, s.size)
        assertEquals(LocalDate.of(2026, 11, 9), s.first().date)
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0, 1), s.map { it.count })
        assertTrue(s[2].today); assertTrue(s[0].past); assertFalse(s[2].past)
    }

    @Test fun whileGuidingArrivalIsComparedWithTheAppointment() {
        val fri = LocalDate.of(2026, 11, 13)
        val a = Agenda.build(cat.calendar, fri)[0]   // 금요일 저녁 7시 · 속초관광수산시장
        val nav = NavState(routeActive = true, destinationName = "어떤 주차장", destLat = 38.2049, destLon = 128.5902, remainDistanceM = 31200, remainTimeS = 35 * 60, estimatedSocAtDest = 54)
        val early = Agenda.arrival(a, nav, fri.atTime(18, 0))!!
        assertEquals("약속보다 25분 일찍 도착해요", early.headline); assertFalse(early.late)
        assertEquals("도착 예상 오후 6:35 · 남은 31.2km · 도착하면 배터리 약 54%", early.detail)
        val late = Agenda.arrival(a, nav, fri.atTime(18, 40))!!
        assertEquals("약속보다 15분 늦게 도착해요", late.headline); assertTrue(late.late)
        assertEquals("약속 시각에 맞춰 도착해요", Agenda.arrival(a, nav, fri.atTime(18, 25))!!.headline)
        assertEquals("약속보다 1시간 25분 일찍 도착해요", Agenda.arrival(a, nav, fri.atTime(17, 0))!!.headline)
        // 이름만 같아도 같은 곳이에요
        assertTrue(Agenda.guiding(a, NavState(routeActive = true, destinationName = "속초관광수산시장")))
        // 다른 곳으로 가는 중이거나 안내가 없으면 말하지 않아요
        assertNull(Agenda.arrival(a, nav.copy(destLat = 37.5, destLon = 127.0), fri.atTime(18, 0)))
        assertNull(Agenda.arrival(a, nav.copy(routeActive = false), fri.atTime(18, 0)))
        // 내비가 남은 시간을 주지 않았으면 어림하지 않아요
        assertNull(Agenda.arrival(a, nav.copy(remainTimeS = null), fri.atTime(18, 0)))
        // 약속이 다른 날이면 견주지 않고 도착 예상만 말해요
        assertEquals("약속 장소로 가는 중이에요", Agenda.arrival(agenda[0], nav, now)!!.headline)
    }

    @Test fun legFromThePreviousAppointmentUsesTheRangeTheCarGave() {
        val sat = agenda[1]   // 토요일 오전 10시 · 강릉 (앞 약속: 금요일 저녁 속초)
        val far = Agenda.leg(agenda, sat, 300f)!!
        assertTrue(far, far.startsWith("앞 약속(속초관광수산시장)에서 약 ") && far.endsWith("지금 갈 수 있는 거리(300km)로 충전 없이 가요"))
        assertTrue(Agenda.leg(agenda, sat, 40f)!!.endsWith("지금 갈 수 있는 거리(40km)로는 가는 길에 충전이 필요해요"))
        assertFalse("갈 수 있는 거리를 모르면 거리만 말해요", Agenda.leg(agenda, sat, null)!!.contains("충전"))
        assertNull("첫 약속에는 앞 약속이 없어요", Agenda.leg(agenda, agenda[0], 300f))
        assertNull("여드레 뒤 약속은 이어 보지 않아요", Agenda.leg(agenda, agenda[4], 300f))
        assertNull("장소를 모르는 약속", Agenda.leg(agenda, agenda[2], 300f))
    }

    @Test fun linksToSavedTripsAndMyTrip() {
        fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val trips = listOf(
            SavedTrip(1, day(2024, 11, 10), "2024.11", "낙산사", null, null, 120, 3, emptyList(), 1),
            SavedTrip(2, day(2026, 10, 3), "2026.10", "양양 어딘가", 38.1246, 128.6290, 200, 3, emptyList(), 2),
            SavedTrip(3, day(2026, 11, 2), "2026.11", "제주", null, null, 150, 4, emptyList(), 0),
        )
        val friends = agenda[4]   // 낙산사
        val l = Agenda.links(friends, cat, LibraryState(), trips)
        assertEquals("여기 2번 다녀왔어요 · 마지막은 2026.10", l.first())
        val trip = cat.trips.first { t -> t.stops.any { it.name == "낙산사" } }
        assertTrue(l.any { it == "마이 트립 ‘${trip.title}’의 정류장이에요" })
        assertTrue(Agenda.links(friends, cat, LibraryState().download(trip.id), trips).any { it.endsWith("담아 두었어요") })
        assertTrue("이어지는 것이 없으면 비어 있어요", Agenda.links(agenda[2], cat, LibraryState(), trips).isEmpty())
    }

    @Test fun advisorOffersTheNearestAppointmentAndComparesArrivalWhileGuiding() {
        val lib = LibraryState().download("sync_svc_calendar")
        val parked = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED)
        val go = Advisor.advise(parked, cat, lib, QuietState(), 13, today = today).first { it.kind == "calendar_go" }
        assertEquals("가족 저녁 식사 · 속초관광수산시장으로 안내할까요?", go.title)
        assertEquals("11.13(금) 오후 7:00 · 2일 뒤 · 가족 셋", go.sub)
        // 토요일 아침에는 금요일 약속이 지났으니 토요일 약속을 권해요
        val sat = LocalDate.of(2026, 11, 14)
        assertTrue(Advisor.advise(parked, cat, lib, QuietState(), 8, today = sat).first { it.kind == "calendar_go" }.title.startsWith("부모님 댁 방문"))
        val fri = LocalDate.of(2026, 11, 13)
        val guiding = parked.copy(nav = NavState(routeActive = true, destinationName = "속초관광수산시장", remainTimeS = 20 * 60))
        val eta = Advisor.advise(guiding, cat, lib, QuietState(), 18, today = fri, clock = { LocalTime.of(18, 10) }).first { it.kind == "calendar_eta" }
        assertEquals("가족 저녁 식사 · 약속보다 30분 일찍 도착해요", eta.title)
        assertTrue("가져오지 않았으면 권하지 않아요", Advisor.advise(guiding, cat, LibraryState(), QuietState(), 18, today = fri).none { it.kind.startsWith("calendar") })
    }

    @Test fun answersUseRealDates() {
        val lib = LibraryState().download("sync_svc_calendar")
        fun a(day: LocalDate = today, time: LocalTime = LocalTime.of(13, 5), nav: NavState? = null) =
            StudioAnswers(cat, lib, emptyList(), null, ChargeHistory(), day, nav, null, null, { time }, CarSnapshot(soc = 80, rangeKm = 300f), false)
        assertEquals("오늘로 적힌 약속은 없어요 · 가까운 약속은 가족 저녁 식사 · 11.13(금) 오후 7:00 · 속초관광수산시장 · 2일 뒤 · 안내할 수 있어요", a().answer("오늘 일정 뭐야"))
        assertEquals("이번 주 약속은 2개예요 · 가족 저녁 식사 · 11.13(금) 오후 7:00 · 속초관광수산시장 / 부모님 댁 방문 · 11.14(토) 오전 10:00 · 강릉 경포대 근처", a().answer("이번 주 일정 알려 줘"))
        assertTrue(a().answer("다음 주 약속 뭐 있어?")!!.startsWith("다음 주 약속은 3개예요"))
        assertTrue(a().answer("내일 일정 있어?")!!.startsWith("내일 약속은 없어요 · 가까운 약속은"))
        val fri = LocalDate.of(2026, 11, 13)
        assertTrue(a(fri).answer("오늘 약속 뭐야")!!.startsWith("오늘 약속은 1개예요 · 가족 저녁 식사"))
        assertEquals("가족 저녁 식사 · 11.13(금) 오후 7:00 · 속초관광수산시장 · 5시간 55분 뒤", a(fri).answer("다음 약속 언제야?"))
        assertTrue(a(fri).answer("약속에 늦을까?")!!.startsWith("약속 장소로 가는 길이 내비에 없어서 도착 시각을 아직 몰라요"))
        val nav = NavState(routeActive = true, destinationName = "속초관광수산시장", remainTimeS = 50 * 60)
        assertTrue(a(fri, LocalTime.of(18, 30), nav).answer("약속에 늦을까?")!!.startsWith("가족 저녁 식사 · 약속보다 20분 늦게 도착해요"))
    }
}
