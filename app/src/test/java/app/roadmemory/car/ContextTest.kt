package app.roadmemory.car

import app.roadmemory.car.brain.Brevity
import app.roadmemory.car.brain.ChargeHistory
import app.roadmemory.car.brain.ChatCommand
import app.roadmemory.car.brain.ChatContext
import app.roadmemory.car.brain.ContextNames
import app.roadmemory.car.brain.ContextResolver
import app.roadmemory.car.brain.Conversation
import app.roadmemory.car.brain.StudioAnswers
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.ChargeStop
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.Weather
import app.roadmemory.car.studio.CatalogParser
import app.roadmemory.car.studio.LibraryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** 하이엔드 고도화 1 → 2 → 3(2026-11-11): 한 턴 기억 · 묻는 꼴에 맞춘 답 모양 · 주행 중 한 토막. */
class ContextTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val today = LocalDate.of(2026, 11, 11)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val trips = listOf(
        SavedTrip(1, day(2024, 11, 10), "2024.11", "경포대", 37.79, 128.89, 120, 3, listOf("Solcho Sea Loop"), 1),
        SavedTrip(2, day(2026, 10, 3), "2026.10", "제주 서귀포", null, null, 200, 3, emptyList(), 2),
        SavedTrip(3, day(2026, 11, 2), "2026.11", "강릉", null, null, 150, 4, emptyList(), 0),
    )
    private val names = ContextNames(dests = trips.mapNotNull { it.destName }, tracks = listOf("Solcho Sea Loop", "October Maple Loop"), themes = cat.themes.map { it.title }, stops = cat.trips.flatMap { it.stops }.map { it.name }, places = cat.calendar.map { it.place })
    private fun a(lib: LibraryState = LibraryState(), nav: NavState? = null, weather: Weather? = null, charging: Boolean = false) =
        StudioAnswers(cat, lib, trips, "Solcho Sea Loop", ChargeHistory(), today, nav, weather, if (charging) 25 else null, { LocalTime.of(13, 5) }, CarSnapshot(soc = 80, rangeKm = 300f), charging)

    // ── 1. 한 턴 기억
    @Test fun pronounsResolveFromThePreviousTurn() {
        var ctx = ChatContext()
        val q1 = ContextResolver.rewrite("제주 몇 번 갔어?", ctx)
        val r1 = a().answer(q1)!!
        ctx = ContextResolver.update(ctx, q1, r1, names)
        assertEquals("제주", ctx.place)
        // 거기 → 제주
        assertEquals("제주 언제 갔어?", ContextResolver.rewrite("거기 언제 갔어?", ctx))
        assertEquals("2026.10에 갔어요 · 제주 서귀포 200km", a().answer(ContextResolver.rewrite("거기 언제 갔어?", ctx)))
        // 그럼 강릉은? → 같은 질문을 장소만 바꿔서
        assertEquals("강릉 몇 번 갔어?", ContextResolver.rewrite("그럼 강릉은?", ctx))
        // 때만 바꿔 다시: "지난달 충전 몇 번 했어?" → "그럼 올해는?"
        val q2 = "지난달 충전 몇 번 했어?"
        ctx = ContextResolver.update(ctx, q2, a().answer(q2)!!, names)
        assertEquals("올해 충전 몇 번 했어?", ContextResolver.rewrite("그럼 올해는?", ctx))
        assertEquals("올해 여행 2건에서 충전 2번이에요", a().answer(ContextResolver.rewrite("그럼 올해는?", ctx)))
        // 그때 → 지난달
        assertEquals("지난달 여행 몇 번 갔어?", ContextResolver.rewrite("그때 여행 몇 번 갔어?", ctx))
    }

    @Test fun thatOneMeansTheTrackJustMentioned() {
        var ctx = ChatContext()
        val q = "지금 무슨 노래야"
        ctx = ContextResolver.update(ctx, q, a().answer(q)!!, names)
        assertEquals("Solcho Sea Loop", ctx.track)
        val heard = ContextResolver.rewrite("그거 다시 틀어 줘", ctx)
        assertEquals(ChatCommand.PlayTrack("Solcho Sea Loop"), Conversation.command(heard))
    }

    @Test fun thereMeansTheStopJustMentioned() {
        val t = cat.trips.first()
        val lib = LibraryState().download(t.id).apply(Pane.SVC_TRIP, app.roadmemory.car.ui.TripRun.encode(app.roadmemory.car.ui.TripProgress(t.id, 0)))
        var ctx = ChatContext()
        val q = "다음 정류장 어디야"
        val r = a(lib).answer(q)!!
        ctx = ContextResolver.update(ctx, q, r, names)
        assertTrue(ctx.stop != null)
        val cmd = Conversation.command(ContextResolver.rewrite("거기로 안내해", ctx))
        assertTrue(cmd is ChatCommand.GuidePlace)
    }

    @Test fun nothingToResolveLeavesTheSentenceAlone() {
        assertEquals("거기 언제 갔어?", ContextResolver.rewrite("거기 언제 갔어?", ChatContext()))
        assertEquals("그럼 올해는?", ContextResolver.rewrite("그럼 올해는?", ChatContext()))
    }

    // ── 2. 묻는 꼴에 맞춘 답
    @Test fun countQuestionsGetTheCountFirst() {
        assertEquals("제주에 1번 갔어요 · 2026.10 제주 서귀포 200km", a().answer("제주 몇 번 갔어?"))
    }

    @Test fun whenQuestionsGetTheTimeFirst() {
        assertEquals("2026.10에 갔어요 · 제주 서귀포 200km", a().answer("제주 언제 갔어?"))
        assertEquals("부산에 간 기록이 없어서 언제인지 말할 수 없어요", a().answer("부산 언제 갔어?"))
    }

    @Test fun yesNoQuestionsStartWithYesOrNo() {
        assertEquals("네, 강릉에 1번 갔어요 · 마지막은 2026.11", a().answer("강릉 갔었어?"))
        assertEquals("아니요, 부산에 간 기록은 없어요", a().answer("부산 갔었나"))
        assertTrue(a(charging = true).answer("충전 중이야?")!!.startsWith("네, 충전 중이에요"))
        assertTrue(a().answer("충전 중이야?")!!.startsWith("아니요, 지금은 충전하고 있지 않아요"))
        assertTrue(a(charging = true).answer("충전 다 됐어?")!!.startsWith("아니요, 아직 충전 중이에요"))
        val nav = NavState(routeActive = true, destinationName = "경포대", estimatedSocAtDest = 61)
        assertTrue(a(nav = nav).answer("도착하면 충전 필요해?")!!.startsWith("아니요, "))
        assertTrue(a(nav = nav).answer("충전 없이 갈 수 있어?")!!.startsWith("네, "))
        val low = NavState(routeActive = true, destinationName = "경포대", estimatedSocAtDest = 12, chargeStops = listOf(ChargeStop("A휴게소", 20, 80)))
        assertTrue(a(nav = low).answer("도착하면 충전 필요해?")!!.startsWith("네, "))
        assertTrue(a(weather = Weather.RAIN).answer("비 와?")!!.startsWith("네, "))
        assertEquals("아니요, 아직 가져온 사진이 없어요. 디지털 앨범에서 동기화해요", a().answer("사진 있어?"))
    }

    @Test fun lastYearSaysNoRecordBeforeOfferingAnotherYear() {
        assertEquals("작년 이맘때 기록은 없어요 · 2년 전 오늘 즈음 경포대에 3명이 함께 있었어요.", a().answer("작년 이맘때"))
        assertEquals("2년 전 오늘 즈음 경포대에 3명이 함께 있었어요.", a().answer("2년 전 오늘"))
    }

    @Test fun todayScheduleOnlyCountsTodaysEntries() {
        val lib = LibraryState().download("sync_svc_calendar")
        assertTrue(a(lib).answer("오늘 일정 뭐야")!!.startsWith("오늘로 적힌 약속은 없어요 · 가까운 약속은"))
        assertTrue(a(lib).answer("약속 몇 개")!!.startsWith("가져온 약속은 ${cat.calendar.size}개예요"))
    }

    @Test fun chargeHistoryIsSummarisedNotJustOpened() {
        assertEquals(null, Conversation.command("충전 기록"))
        assertEquals("저장한 여행 3건에서 충전 3번", a().answer("충전 기록"))
    }

    // ── 3. 주행 중 한 토막
    @Test fun drivingKeepsOnlyTheFirstPiece() {
        assertEquals("여행 3건 · 합쳐서 470km예요", Brevity.forDriving("여행 3건 · 합쳐서 470km예요", false))
        assertEquals("여행 3건 · 서면 더 말할게요", Brevity.forDriving("여행 3건 · 합쳐서 470km예요 · 가장 길었던 건 제주 200km", true))
        assertEquals("누가 탔는지는 기록하지 않아요 · 서면 더 말할게요", Brevity.forDriving("누가 탔는지는 기록하지 않아요. 여행 3건에 평균 3.3명이 탔어요", true))
        assertEquals("서 있을 때 열 수 있어요", Brevity.forDriving("서 있을 때 열 수 있어요", true))
        val ui = UiState(sourceKind = app.roadmemory.car.data.SourceKind.PLEOS, mode = app.roadmemory.car.engine.Mode.DRIVING, trips = trips)
        assertTrue(Conversation.followUps("충전 몇 번 했어?", "저장한 여행 3건에서 충전 3번이에요", ui, LibraryState()).none { it.startsWith(Conversation.LINK) })
    }
}

/** 하이엔드 제안 4 · 5(2026-11-11): 들은 말 한 줄의 모양 · 비교 문장. */
class CompareAndHeardTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val today = LocalDate.of(2026, 11, 11)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val trips = listOf(
        SavedTrip(1, day(2025, 11, 10), "2025.11", "경포대", null, null, 120, 3, emptyList(), 1),
        SavedTrip(2, day(2026, 10, 3), "2026.10", "제주 서귀포", null, null, 200, 3, emptyList(), 2),
        SavedTrip(3, day(2026, 10, 20), "2026.10", "제주 애월", null, null, 80, 2, emptyList(), 1),
        SavedTrip(4, day(2026, 11, 2), "2026.11", "강릉", null, null, 150, 4, emptyList(), 0),
    )
    private val charges = ChargeHistory(listOf(app.roadmemory.car.brain.ChargeSession(1, "A", 20, 60, 40), app.roadmemory.car.brain.ChargeSession(2, "B", 30, 70, 40), app.roadmemory.car.brain.ChargeSession(3, "C", 20, 60, 40)))
    private fun a(charging: Boolean = false, pace: Double? = null) =
        StudioAnswers(cat, LibraryState(), trips, null, charges, today, null, null, null, { LocalTime.of(13, 5) }, CarSnapshot(soc = 50), charging, pace)

    @Test fun thisMonthAgainstLastMonth() {
        assertEquals("이번 달 여행 1건 · 지난달 여행 2건 · 1건 적어요", a().answer("지난달보다 많이 갔어?"))
        assertEquals("이번 달 충전 0번 · 지난달 충전 3번 · 3번 적어요", a().answer("이번 달 충전 지난달보다 많아?"))
        assertEquals("이번 달 150km · 지난달 280km · 130km 적어요", a().answer("지난달보다 멀리 갔어?"))
    }

    @Test fun thisYearAgainstLastYear() {
        assertEquals("올해 430km · 작년 120km · 310km 많아요", a().answer("작년보다 멀리 갔어?"))
        assertEquals("올해 여행 3건 · 작년 여행 1건 · 2건 많아요", a().answer("작년에 비해 여행 어때"))
    }

    @Test fun thisMonthAgainstTheUsualMonth() {
        // 다른 달: 2025.11(1건) · 2026.10(2건) → 평소 1.5건, 이번 달 1건 → 평소보다 적어요(0.8배 아래)
        assertEquals("이번 달 여행 1건 · 달마다 평소 1.5건(2달) · 평소보다 적어요", a().answer("평소보다 자주 가?"))
    }

    @Test fun thisChargeAgainstTheUsualPace() {
        assertEquals("지금은 충전하고 있지 않아요 · 평소에는 분당 1.0%씩 올랐어요", a().answer("여기 충전 평소보다 느려?"))
        assertTrue(a(charging = true).answer("여기 충전 평소보다 느려?")!!.startsWith("아직 속도를 재는 중이에요"))
        assertEquals("지금 분당 0.5% · 평소 1.0% · 평소보다 느려요", a(charging = true, pace = 0.5).answer("이 충전소 빨라?"))
        assertEquals("지금 분당 1.1% · 평소 1.0% · 평소만큼이에요", a(charging = true, pace = 1.1).answer("지금 충전 속도 평소랑 비교해줘"))
    }

    @Test fun heardLineRoundTrips() {
        val l = app.roadmemory.car.brain.HeardLine(5L, "거기 언제 갔어?", "제주 언제 갔어?", true, "2026.10에 갔어요 · 제주 서귀포 200km")
        assertEquals(l, app.roadmemory.car.brain.HeardLine.decode(l.encode()))
        val tabbed = app.roadmemory.car.brain.HeardLine(6L, "탭\t줄\n바꿈", "x", false, "y")
        assertEquals("탭 줄 바꿈", app.roadmemory.car.brain.HeardLine.decode(tabbed.encode())!!.heard)
    }
}
