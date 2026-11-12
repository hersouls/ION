package app.roadmemory.car

import app.roadmemory.car.brain.Advisor
import app.roadmemory.car.brain.QuietState
import app.roadmemory.car.brain.StudioAnswers
import app.roadmemory.car.brain.TripPlan
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.SeatPos
import app.roadmemory.car.data.SeatState
import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.data.Weather
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.studio.CatalogParser
import app.roadmemory.car.studio.LibraryState
import app.roadmemory.car.ui.TripProgress
import app.roadmemory.car.ui.TripRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** ION Brain의 규칙 버전: 센 값으로만 권하고 답해요. 모르는 값으로는 권하지 않고, 거절하면 조용해져요. */
class BrainTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val parked = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED, car = CarSnapshot(soc = 80, rangeKm = 300f))
    private val quiet = QuietState()

    @Test fun tripPlanCountsChargesFromRange() {
        val t = cat.trips.first { it.title.contains("속초") }
        val far = TripPlan.plan(t, 60f)
        assertTrue("60km로는 중간에 충전이 필요해요", (far.chargesNeeded ?: 0) >= 1)
        assertTrue(far.chargeBefore.isNotEmpty())
        assertEquals(0, TripPlan.plan(t, 900f).chargesNeeded)
        assertNull("갈 수 있는 거리를 모르면 판단하지 않아요", TripPlan.plan(t, null).chargesNeeded)
        assertTrue(TripPlan.plan(t, 900f).summary.contains("충전 없이"))
        assertTrue(TripPlan.km(t.stops[0], t.stops[0]) == 0.0)
    }

    @Test fun nothingWhileDriving() {
        val lib = LibraryState().download("sync_svc_calendar")
        assertTrue(Advisor.advise(parked.copy(mode = Mode.DRIVING), cat, lib, quiet, 10).isEmpty())
    }

    @Test fun nextStopComesFirstWhenATripIsRunning() {
        val t = cat.trips.first()
        val lib = LibraryState().download(t.id).apply(Pane.SVC_TRIP, TripRun.encode(TripProgress(t.id, 0)))
        val a = Advisor.advise(parked, cat, lib, quiet, 10)
        assertEquals("next_stop", a.first().kind)
        assertTrue(a.first().title.contains(t.stops[1].name))
        assertTrue("이유는 쓴 값에서 나와요", a.first().reasons.any { it.contains(t.title) })
    }

    @Test fun chargePlanAdviceNeedsARange() {
        val t = cat.trips.first { it.title.contains("속초") }
        val lib = LibraryState().download(t.id)
        assertTrue(Advisor.advise(parked.copy(car = CarSnapshot(soc = 20, rangeKm = 60f)), cat, lib, quiet, 10).any { it.kind == "trip_charge" })
        assertFalse("갈 수 있는 거리를 모르면 충전 계획을 권하지 않아요", Advisor.advise(parked.copy(car = CarSnapshot()), cat, lib, quiet, 10).any { it.kind == "trip_charge" })
    }

    @Test fun themePickFollowsWeatherAndPeople() {
        val rain = Advisor.advise(parked.copy(weather = Weather.RAIN), cat, LibraryState(), quiet, 14).first { it.kind == "theme_pick" }
        assertTrue(rain.reasons.any { it.contains("비") })
        val three = parked.copy(car = parked.car.copy(seats = SeatPos.entries.take(3).associateWith { SeatState(occupied = true, belted = true) }))
        val cheer = Advisor.advise(three, cat, LibraryState(), quiet, 14).first { it.kind == "theme_pick" }
        assertTrue(cheer.reasons.any { it.contains("3명") })
        val applied = LibraryState().download(cat.themes.first().id).apply(Pane.SVC_THEME, cat.themes.first().id)
        assertFalse("이미 적용했으면 테마를 권하지 않아요", Advisor.advise(parked, cat, applied, quiet, 14).any { it.kind == "theme_pick" })
    }

    @Test fun chargingSuggestsAlbumAndMusic() {
        val lib = LibraryState().download("sync_svc_album").download("sync_svc_music")
        val kinds = Advisor.advise(parked.copy(mode = Mode.CHARGING, chargeMinLeft = 25), cat, lib, quiet, 15).map { it.kind }
        assertTrue(kinds.contains("charge_album") && kinds.contains("charge_music"))
        assertFalse("곡이 이미 돌면 권하지 않아요", Advisor.advise(parked.copy(mode = Mode.CHARGING, nowPlaying = "x"), cat, lib, quiet, 15).any { it.kind == "charge_music" })
    }

    @Test fun quietLearnsFromTwoDismissals() {
        var q = QuietState()
        q = q.dismiss("theme_pick", 1000L)
        assertFalse(q.muted("theme_pick", 2000L))
        q = q.dismiss("theme_pick", 2000L)
        assertTrue(q.muted("theme_pick", 3000L))
        assertFalse(q.muted("theme_pick", 2000L + QuietState.MUTE_MS + 1))
        assertTrue(Advisor.advise(parked, cat, LibraryState(), q, 14, now = 3000L).none { it.kind == "theme_pick" })
        assertEquals(q, QuietState.decode(q.encode()))
        assertTrue(q.accept("x").summary.contains("수락 1"))
        assertEquals(QuietState(), QuietState.decode("깨진"))
    }

    @Test fun answersCountOnlyWhatIsStored() {
        val none = StudioAnswers(cat, LibraryState(), emptyList(), null)
        assertTrue(none.answer("충전 몇 번 했어?")!!.contains("없어"))
        assertNull("모르는 질문은 다음 답변자에게 넘겨요", none.answer("외기온을 쓰는 기능은?"))
        val trips = listOf(SavedTrip(1, 0, "2026.11", "경포대", null, null, 120, 3, emptyList(), 2), SavedTrip(2, 0, "2026.10", null, null, null, 30, 1, emptyList(), 0))
        val some = StudioAnswers(cat, LibraryState(), trips, "Solcho Sea Loop")
        assertEquals("저장한 여행 2건에서 충전 2번이에요", some.answer("충전 몇 번 했어?"))
        assertTrue(some.answer("몇 km 달렸어?")!!.contains("150km"))
        assertTrue(some.answer("지금 뭐 틀어?")!!.contains("Solcho"))
        val t = cat.trips.first()
        val run = LibraryState().download(t.id).apply(Pane.SVC_TRIP, TripRun.encode(TripProgress(t.id, 1)))
        assertTrue(StudioAnswers(cat, run, emptyList(), null).answer("내 일정 어디까지 왔어?")!!.contains("2/${t.stops.size}"))
    }
}

class BrainUpgradeTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val parked = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED, car = CarSnapshot(soc = 80, rangeKm = 300f))
    private fun day(y: Int, m: Int, d: Int) = java.time.LocalDate.of(y, m, d).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun chargeHistoryLearnsTheUsualPaceAndSlowStations() {
        var h = app.roadmemory.car.brain.ChargeHistory()
        assertNull(h.usualPace)
        h = h.add(app.roadmemory.car.brain.ChargeSession(1, "A휴게소", 20, 60, 30)).add(app.roadmemory.car.brain.ChargeSession(2, "B휴게소", 30, 70, 30))
        assertNull("세 번 미만이면 평소를 말하지 않아요", h.usualPace)
        h = h.add(app.roadmemory.car.brain.ChargeSession(3, "느린충전소", 20, 30, 40))
        assertTrue(h.usualPace!! > 0)
        assertEquals(true, h.slowerThanUsual("느린충전소"))
        assertEquals(false, h.slowerThanUsual("A휴게소"))
        assertNull(h.slowerThanUsual("모르는곳"))
        assertTrue(h.line("느린충전소")!!.contains("느렸어요"))
        assertEquals(h, app.roadmemory.car.brain.ChargeHistory.decode(h.encode()))
        assertNull("1분 미만 · 2% 미만은 속도로 치지 않아요", app.roadmemory.car.brain.ChargeSession(1, "x", 50, 51, 5).pctPerMin)
    }

    @Test fun chargerHistoryAdviceWhenNaviPicksASlowOne() {
        val h = app.roadmemory.car.brain.ChargeHistory(listOf(
            app.roadmemory.car.brain.ChargeSession(1, "A", 20, 60, 30), app.roadmemory.car.brain.ChargeSession(2, "B", 30, 70, 30), app.roadmemory.car.brain.ChargeSession(3, "느린충전소", 20, 30, 40)))
        val nav = app.roadmemory.car.data.NavState(routeActive = true, destinationName = "강릉", chargeStops = listOf(app.roadmemory.car.data.ChargeStop("느린충전소", 20, 80)))
        val a = Advisor.advise(parked.copy(nav = nav), cat, LibraryState(), QuietState(), 12, charges = h)
        assertEquals("charger_history", a.first().kind)
        assertTrue(a.first().reasons.any { it.contains("분당") })
        assertTrue(Advisor.advise(parked.copy(nav = nav), cat, LibraryState(), QuietState(), 12).none { it.kind == "charger_history" })
    }

    @Test fun thatDayFindsOtherYearsAroundToday() {
        val trips = listOf(SavedTrip(1, day(2024, 11, 10), "2024.11", "경포대", null, null, 120, 3, listOf("Solcho Sea Loop"), 1), SavedTrip(2, day(2026, 11, 11), "2026.11", "집", null, null, 5, 1, emptyList(), 0), SavedTrip(3, day(2025, 6, 1), "2025.06", "부산", null, null, 300, 2, emptyList(), 2))
        val m = app.roadmemory.car.brain.Memories.thatDay(trips, java.time.LocalDate.of(2026, 11, 11))
        assertEquals(1, m.size)
        assertEquals(2, m.first().yearsAgo)
        assertEquals("2년 전 오늘 즈음 경포대에 3명이 함께 있었어요.", app.roadmemory.car.brain.Memories.sentence(m.first()))
        val a = Advisor.advise(parked.copy(trips = trips), cat, LibraryState(), QuietState(), 12, today = java.time.LocalDate.of(2026, 11, 11))
        val td = a.first { it.kind == "that_day" }
        assertEquals(app.roadmemory.car.brain.Advice.Action.Play(listOf("Solcho Sea Loop")), td.action)
    }

    @Test fun nextTripDraftFitsTheRangeAndPrefersVisitedRegions() {
        val trips = listOf(SavedTrip(1, day(2025, 5, 5), "2025.05", "강릉 경포대", null, null, 120, 3, emptyList(), 1))
        val a = Advisor.advise(parked.copy(trips = trips, car = CarSnapshot(soc = 90, rangeKm = 400f)), cat, LibraryState(), QuietState(), 12).first { it.kind == "next_trip" }
        assertTrue(a.title.contains("강릉") || a.sub.contains("충전 없이"))
        assertTrue(a.reasons.any { it.contains("충전 없이") })
        assertTrue("담은 일정이 있으면 초안을 권하지 않아요", Advisor.advise(parked, cat, LibraryState().download(cat.trips.first().id), QuietState(), 12).none { it.kind == "next_trip" })
        assertTrue("갈 수 있는 거리를 모르면 권하지 않아요", Advisor.advise(parked.copy(car = CarSnapshot()), cat, LibraryState(), QuietState(), 12).none { it.kind == "next_trip" })
    }

    @Test fun musicPickFollowsMood() {
        val lib = LibraryState().download("sync_svc_music")
        val rainNight = Advisor.advise(parked.copy(mode = Mode.CHARGING, weather = Weather.RAIN), cat, lib, QuietState(), 22, today = java.time.LocalDate.of(2026, 3, 1)).first { it.kind == "charge_music" }
        assertTrue(rainNight.title.contains("Lullaby") || rainNight.title.contains("Pond"))
        val autumn = Advisor.advise(parked.copy(mode = Mode.CHARGING, weather = Weather.CLOUDY), cat, lib, QuietState(), 18, today = java.time.LocalDate.of(2026, 10, 1)).first { it.kind == "charge_music" }
        assertTrue(autumn.title.contains("Maple"))
    }

    @Test fun answersFilterByPlaceAndTime() {
        val trips = listOf(
            SavedTrip(1, day(2026, 10, 3), "2026.10", "제주 서귀포", null, null, 200, 3, emptyList(), 2),
            SavedTrip(2, day(2026, 10, 20), "2026.10", "제주 애월", null, null, 80, 2, emptyList(), 1),
            SavedTrip(3, day(2026, 11, 2), "2026.11", "강릉", null, null, 150, 4, emptyList(), 0),
        )
        val a = StudioAnswers(cat, LibraryState(), trips, null, today = java.time.LocalDate.of(2026, 11, 11))
        assertEquals("10월 제주에서 여행 2건에서 충전 3번이에요", a.answer("10월 제주에서 충전 몇 번 했어?"))
        assertEquals("지난달 여행 2건에서 충전 3번이에요", a.answer("지난달 충전 몇 번 했어?"))
        assertTrue(a.answer("이번 달 여행 몇 번 갔어?")!!.startsWith("이번 달 여행 1건"))
        assertTrue(a.answer("가장 멀리 간 곳은?")!!.contains("제주 서귀포 200km"))
        assertTrue(a.answer("강릉 간 적 있어?")!!.startsWith("네, 강릉에 1번"))
        assertTrue(a.answer("부산 간 적 있어?")!!.contains("없어요"))
        assertTrue(a.answer("누구랑 제일 많이 갔어?")!!.contains("4명"))
        assertTrue(a.answer("충전 속도 빨라?")!!.contains("세 번 이상"))
    }
}

class ConversationTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val parked = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED, car = CarSnapshot(soc = 80, rangeKm = 300f))

    @Test fun greetingUsesOnlyKnownValues() {
        val g = app.roadmemory.car.brain.Conversation.greeting(parked, 9, cat, LibraryState())
        assertEquals("문유님, 좋은 아침이에요!", g[0]); assertTrue(g[1].contains("배터리 80%") && g[1].contains("300km"))
        val none = app.roadmemory.car.brain.Conversation.greeting(parked.copy(car = CarSnapshot()), 23, cat, LibraryState())
        assertEquals("ION이에요. 가는 길에 필요한 것만 알려 드릴게요.", none[1])
        val charging = app.roadmemory.car.brain.Conversation.greeting(parked.copy(mode = Mode.CHARGING, chargeMinLeft = 20), 14, cat, LibraryState())
        assertTrue(charging[1].contains("약 20분"))
        val t = cat.trips.first(); val lib = LibraryState().download(t.id).apply(Pane.SVC_TRIP, TripRun.encode(TripProgress(t.id, 0)))
        assertTrue(app.roadmemory.car.brain.Conversation.greeting(parked, 14, cat, lib)[1].contains(t.stops[1].name))
    }

    @Test fun chipsFollowTheSituation() {
        val t = cat.trips.first(); val lib = LibraryState().download(t.id).apply(Pane.SVC_TRIP, TripRun.encode(TripProgress(t.id, 0)))
        assertEquals("다음 정류장 안내해 줘", app.roadmemory.car.brain.Conversation.chips(parked, cat, lib).first())
        val c = app.roadmemory.car.brain.Conversation.chips(parked.copy(mode = Mode.CHARGING), cat, LibraryState().download("sync_svc_music"))
        assertTrue(c.contains("곡 틀어 줘"))
        assertTrue(app.roadmemory.car.brain.Conversation.chips(parked, cat, LibraryState()).size in 3..4)
    }

    @Test fun commandsAndFollowUps() {
        val C = app.roadmemory.car.brain.Conversation
        assertEquals(app.roadmemory.car.brain.ChatCommand.NextStop, C.command("다음 정류장 안내해 줘"))
        assertEquals(app.roadmemory.car.brain.ChatCommand.PlayMusic, C.command("곡 틀어 줘"))
        assertEquals(app.roadmemory.car.brain.ChatCommand.StopMusic, C.command("곡 멈춰 줘"))
        assertEquals(app.roadmemory.car.brain.ChatCommand.RecommendTheme, C.command("테마 추천해 줘"))
        assertEquals(app.roadmemory.car.brain.ChatCommand.ApplyRecommendedTheme, C.command("그 테마 적용해 줘"))
        assertEquals(app.roadmemory.car.brain.ChatCommand.ThatDayMusic, C.command("그때 곡 틀어 줘"))
        assertNull(C.command("외기온을 쓰는 기능은?"))
        assertEquals("다음 정류장 안내해 줘", C.followUps("내 일정 어디까지 왔어?", "… 다음은 강릉 커피거리", parked, LibraryState()).first())
        assertEquals("그 테마 적용해 줘", C.followUps("테마 추천해 줘", "가람 물총새 테마 어때요? …", parked, LibraryState()).first())
        assertTrue(C.followUps("테마 추천해 줘", "지금 값으로는 고를 테마가 없어요", parked, LibraryState()).none { it == "그 테마 적용해 줘" })
    }

    @Test fun chipsRotateFromThePoolKeepingTheFirst() {
        val C = app.roadmemory.car.brain.Conversation
        val t = cat.trips.first(); val lib = LibraryState().download(t.id).apply(Pane.SVC_TRIP, TripRun.encode(TripProgress(t.id, 0)))
        val pool = C.chipPool(parked.copy(trips = listOf(SavedTrip(1, 0, "2026.11", "강릉", null, null, 10, 2, emptyList(), 0))), cat, lib)
        assertTrue("풀은 넉넉해야 돌릴 수 있어요", pool.size >= 8)
        assertEquals(pool.toSet().size, pool.size)
        val a = C.rotate(pool, 0, 3); val b = C.rotate(pool, 1, 3)
        assertEquals(3, a.size); assertEquals("다음 정류장 안내해 줘", a.first()); assertEquals(a.first(), b.first())
        assertTrue("묶음이 바뀌어야 해요", (1..6).any { C.rotate(pool, it, 3) != a })
        assertEquals(C.rotate(pool, 5, 3), C.rotate(pool, 5, 3))
        assertEquals(listOf("x", "y"), C.rotate(listOf("x", "y"), 3, 3))
    }

    @Test fun koreanParticles() {
        val K = app.roadmemory.car.brain.Korean
        assertEquals("강릉 커피거리예요", K.ieyo("강릉 커피거리")); assertEquals("정동진해변이에요", K.ieyo("정동진해변"))
        assertEquals("경포대로", K.euro("경포대")); assertEquals("낙산사로", K.euro("낙산사")); assertEquals("시장으로", K.euro("시장")); assertEquals("서울로", K.euro("서울"))
        assertEquals("E-pit(으)로", K.euro("E-pit"))
    }
}
