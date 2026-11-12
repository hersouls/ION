package app.roadmemory.car

import app.roadmemory.car.brain.Advice
import app.roadmemory.car.brain.Advisor
import app.roadmemory.car.brain.ChatCommand
import app.roadmemory.car.brain.Conversation
import app.roadmemory.car.brain.QuietState
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.studio.CatalogParser
import app.roadmemory.car.studio.LibraryState
import java.io.File
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.Recording
import app.roadmemory.car.data.RecordingLogic
import app.roadmemory.car.data.SeatPos
import app.roadmemory.car.data.SeatState
import app.roadmemory.car.engine.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 기록하기: 내비 목적지로 시작 → 달리며 쌓기 → 나눠 달려도 합치기 → 시동 껐다 켜면 이어서 → 도착하면 한 건. */
class TripRecordingTest {
    private val route = NavState(routeActive = true, destinationName = "속초 관광수산시장", destLat = 38.2058, destLon = 128.5909)
    private fun car(km: Float? = null, people: Int = 0) = CarSnapshot(
        tripKm = km, seats = SeatPos.entries.take(people).associateWith { SeatState(occupied = true, belted = true) },
    )

    /** 컨트롤러가 하는 대로 상태를 이어서 돌려요. */
    private class Run(var rec: Recording, var seg: Boolean = false, var prev: Mode? = Mode.PARKED) {
        fun tick(mode: Mode, car: CarSnapshot, nav: NavState = NavState()): Recording {
            val s = RecordingLogic.tick(rec, prev, mode, car, nav, seg)
            rec = s.rec; seg = s.segActive; prev = mode
            return rec
        }
    }

    @Test fun beginsFromTheNavDestinationAndPeople() {
        val r = Recording.begin(1_000, route, people = 2)
        assertEquals("속초 관광수산시장", r.destName); assertEquals(38.2058, r.destLat!!, 0.0001)
        assertEquals(2, r.people); assertEquals(0, r.km); assertEquals(1, r.legs); assertFalse(r.paused)
    }

    @Test fun beginsWithoutARouteAndKeepsNoDestination() {
        val r = Recording.begin(1_000, NavState(), people = 1)
        assertNull(r.destName); assertNull(r.destLat)
    }

    @Test fun staleTripKmWhileParkedIsNotCounted() {
        // 정차 중에 시작하면 car.tripKm에는 지난 주행의 87km가 남아 있어요
        val run = Run(Recording.begin(0, route, 1))
        run.tick(Mode.PARKED, car(km = 87f, people = 1))
        assertEquals(0, run.rec.km)
        run.tick(Mode.DRIVING, car(km = 0f, people = 1))      // 출발: 새로 재기 시작
        run.tick(Mode.DRIVING, car(km = 12.4f, people = 1))
        assertEquals(12, run.rec.km)
    }

    @Test fun peopleIsTheMostThatSatAndTracksAndChargesStack() {
        val run = Run(Recording.begin(0, route, 1))
        run.tick(Mode.DRIVING, car(km = 0f, people = 2))
        run.tick(Mode.DRIVING, car(km = 30f, people = 4))
        run.tick(Mode.DRIVING, car(km = 31f, people = 3))
        assertEquals(4, run.rec.people)
        run.tick(Mode.CHARGING, car(km = 90f, people = 3))
        run.tick(Mode.CHARGING, car(km = 90f, people = 3))   // 충전 중 계속 읽혀도 한 번만 세요
        run.tick(Mode.PARKED, car(km = 90f, people = 3))
        run.tick(Mode.DRIVING, car(km = 0f, people = 3))
        run.tick(Mode.CHARGING, car(km = 40f, people = 3))
        assertEquals(2, run.rec.charges)
        assertEquals(listOf("A", "B"), run.rec.withTrack("A").withTrack("B").withTrack("A").tracks)
    }

    @Test fun legsAddUpAcrossStops() {
        val run = Run(Recording.begin(0, route, 2))
        run.tick(Mode.DRIVING, car(km = 0f, people = 2))
        run.tick(Mode.DRIVING, car(km = 95f, people = 2))
        run.tick(Mode.PARKED, car(km = 96f, people = 2), route)       // 휴게소: 안내는 이어지고
        assertEquals(96, run.rec.km)
        run.tick(Mode.DRIVING, car(km = 0f, people = 2), route)       // 다시 출발하면 tripKm이 0부터예요
        assertEquals("끝난 구간이 합쳐져야 해요", 96, run.rec.km)
        run.tick(Mode.DRIVING, car(km = 160f, people = 2), route)
        assertEquals(256, run.rec.km)
    }

    @Test fun navDestinationChangesAreFollowed() {
        val run = Run(Recording.begin(0, NavState(), 1))
        run.tick(Mode.DRIVING, car(km = 0f), route)
        assertEquals("속초 관광수산시장", run.rec.destName)
        run.tick(Mode.DRIVING, car(km = 5f), NavState(routeActive = true, destinationName = "낙산사", destLat = 38.1243, destLon = 128.6544))
        assertEquals("낙산사", run.rec.destName)
        run.tick(Mode.DRIVING, car(km = 6f), NavState())              // 안내가 끝나도 마지막 목적지를 지키지 않고 지우지도 않아요
        assertEquals("낙산사", run.rec.destName)
    }

    @Test fun restartAsksAndSumsTheLastLegThenContinuesWhenDriverDrives() {
        val before = Recording(startedAt = 1, destName = "속초 관광수산시장", kmDone = 96, kmSeg = 30, people = 3, tracks = listOf("A"), charges = 1)
        val stored = Recording.decode(before.encode())!!
        assertEquals(before, stored)
        val back = Recording.afterRestart(stored)
        assertTrue(back.paused); assertEquals(126, back.kmDone); assertEquals(0, back.kmSeg)

        val run = Run(back, prev = null)
        run.tick(Mode.PARKED, car(), route)                           // 묻는 동안은 아무것도 바뀌지 않아요
        assertTrue(run.rec.paused); assertEquals(126, run.rec.km); assertEquals(1, run.rec.legs)
        run.tick(Mode.DRIVING, car(km = 0f, people = 1), route)       // 답 없이 출발 → 이어서
        assertFalse(run.rec.paused); assertEquals(2, run.rec.legs)
        run.tick(Mode.DRIVING, car(km = 44f, people = 1), route)
        assertEquals(170, run.rec.km); assertEquals(3, run.rec.people)
    }

    @Test fun startedWhileDrivingCountsFromTheTripStart() {
        val r = Recording.begin(0, route, 2, segKm = 18)
        assertEquals(18, r.km)
        val run = Run(r, seg = true, prev = Mode.DRIVING)
        run.tick(Mode.DRIVING, car(km = 25f, people = 2))
        assertEquals(25, run.rec.km)
    }

    @Test fun becomesATripOnlyAfterOneKm() {
        assertNull(Recording.begin(0, route, 2).toTrip(5, "2026.11"))
        val t = Recording(startedAt = 0, destName = "속초 관광수산시장", destLat = 38.2, destLon = 128.5, kmDone = 100, kmSeg = 12, people = 3, tracks = listOf("A"), charges = 1).toTrip(77, "2026.11")
        assertNotNull(t)
        assertEquals(112, t!!.km); assertEquals(3, t.people); assertEquals(1, t.charges); assertEquals("속초 관광수산시장", t.destName); assertEquals(0L, t.id)
    }

    @Test fun brokenStoredTextIsIgnored() {
        assertNull(Recording.decode(null)); assertNull(Recording.decode("")); assertNull(Recording.decode("{\"x\":1}"))
    }

    // ── 말로 시키기 · ION이 권해요 ──

    @Test fun spokenCommandsStartResumeAndEnd() {
        assertEquals(ChatCommand.StartRecording, Conversation.command("여행 기록해 줘"))
        assertEquals(ChatCommand.StartRecording, Conversation.command("기록 시작"))
        assertEquals(ChatCommand.ResumeRecording, Conversation.command("이어서 기록해 줘"))
        assertEquals(ChatCommand.EndRecording, Conversation.command("기록 끝내 줘"))
        assertEquals("기록 멈춰는 곡 멈춤이 아니에요", ChatCommand.EndRecording, Conversation.command("기록 멈춰"))
        assertEquals(ChatCommand.StopMusic, Conversation.command("곡 멈춰"))
        assertEquals(ChatCommand.Open(app.roadmemory.car.Pane.TRIPS, "여행 기록"), Conversation.command("여행 기록 보여 줘"))
    }

    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val parked = app.roadmemory.car.UiState(mode = Mode.PARKED, car = CarSnapshot(soc = 80, rangeKm = 300f), nav = route)

    @Test fun ionOffersToRecordWhenTheNavHasADestinationAndNothingIsRecording() {
        val a = Advisor.advise(parked, cat, LibraryState(), QuietState(), 10).first { it.kind == "record_trip" }
        assertEquals(Advice.Action.Record, a.action)
        assertTrue(a.title.contains("속초 관광수산시장으로"))
        assertTrue("이유에 쓴 값이 나와요", a.reasons.any { it.contains("속초 관광수산시장") })
    }

    @Test fun noOfferWhileRecordingDrivingOrWithoutARoute() {
        val none = { ui: app.roadmemory.car.UiState -> Advisor.advise(ui, cat, LibraryState(), QuietState(), 10).none { it.kind == "record_trip" } }
        assertTrue(none(parked.copy(recording = Recording.begin(0, route, 1))))
        assertTrue(none(parked.copy(mode = Mode.DRIVING)))
        assertTrue(none(parked.copy(nav = NavState())))
    }
}
