package app.roadmemory.car

import app.roadmemory.car.brain.Advisor
import app.roadmemory.car.brain.QuietState
import app.roadmemory.car.data.ArrivalKind
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.NaviArrival
import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.navi.Place
import app.roadmemory.car.studio.CatalogParser
import app.roadmemory.car.studio.LibraryState
import app.roadmemory.car.ui.Services
import app.roadmemory.car.ui.TripGuide
import app.roadmemory.car.ui.TripProgress
import app.roadmemory.car.ui.TripRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 마이 트립의 도착 자동 감지: 내비가 알려 주는 경유지 · 목적지 도착(onWayPointArrived · onDestinationArrived)으로 진행을 옮겨요.
 * 위치는 읽지 않아요: 내비가 준 이름 · 좌표를 이 일정의 정류장과 견줘서, 이 일정의 다음 정류장이 아니면 건드리지 않아요.
 */
class TripArrivalTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val trip = cat.trips.first { it.stops.size >= 5 }
    private fun arrive(kind: ArrivalKind, name: String?) = NaviArrival(kind, name, if (kind == ArrivalKind.WAYPOINT) 0 else null, 0L)

    @Test fun progressKeepsTheOldShapeAndTheNewOne() {
        assertEquals("t#2", TripRun.encode(TripProgress("t", 2)))
        assertEquals(TripProgress("t", 2), TripRun.parse("t#2"))
        val sent = TripProgress("t", 0, end = 2)
        assertEquals("t#0#2#0", TripRun.encode(sent))
        assertEquals(sent, TripRun.parse("t#0#2#0"))
        val reached = TripProgress("t", 1, end = 2, arrived = true)
        assertEquals(reached, TripRun.parse(TripRun.encode(reached)))
        assertNull(TripRun.parse("t#x")); assertNull(TripRun.parse("t#0#2#9")); assertNull(TripRun.parse("t#0#2")); assertNull(TripRun.parse(null))
    }

    @Test fun guideSendsThreeStopsAtOnceAndRemembersTheLast() {
        val p = TripGuide.plan(trip, 0)!!
        assertEquals(trip.stops[2].name, p.dest.name)
        assertEquals(listOf(trip.stops[0].name, trip.stops[1].name), p.through.map { it.name })
        assertEquals(2, p.end)
        val near = TripGuide.plan(trip, trip.stops.size - 2)!!
        assertEquals(trip.stops.last().name, near.dest.name); assertEquals(listOf(trip.stops[trip.stops.size - 2].name), near.through.map { it.name }); assertEquals(trip.stops.size - 1, near.end)
        val last = TripGuide.plan(trip, trip.stops.size - 1)!!
        assertEquals(emptyList<Place>(), last.through); assertEquals(trip.stops.size - 1, last.end)
        assertNull(TripGuide.plan(trip, trip.stops.size))
    }

    @Test fun waypointsMoveTheProgressOneStopAtATimeAndTheDestinationEndsTheLeg() {
        var run = TripProgress(trip.id, 0, end = 2)
        run = TripRun.onArrival(run, trip, arrive(ArrivalKind.WAYPOINT, trip.stops[0].name))!!
        assertEquals(TripProgress(trip.id, 0, end = 2, arrived = true), run)
        run = TripRun.onArrival(run, trip, arrive(ArrivalKind.WAYPOINT, trip.stops[1].name))!!
        assertEquals(TripProgress(trip.id, 1, end = 2, arrived = true), run)
        run = TripRun.onArrival(run, trip, arrive(ArrivalKind.DESTINATION, trip.stops[2].name))!!
        assertEquals(TripProgress(trip.id, 2, end = 2, arrived = true), run)
        assertNull("끝난 구간의 도착이 또 와도 움직이지 않아요", TripRun.onArrival(run, trip, arrive(ArrivalKind.DESTINATION, trip.stops[2].name)))
    }

    @Test fun aWaypointWithAnUnknownNameStillTakesTheNextStopInOrder() {
        val run = TripProgress(trip.id, 0, end = 2)
        assertEquals(0, TripRun.onArrival(run, trip, arrive(ArrivalKind.WAYPOINT, "내비가 부른 다른 이름"))!!.stopIndex)
        assertEquals(1, TripRun.onArrival(run.copy(stopIndex = 0, arrived = true), trip, arrive(ArrivalKind.WAYPOINT, null))!!.stopIndex)
    }

    @Test fun theDestinationIsMatchedByCoordinatesBeforeTheName() {
        val run = TripProgress(trip.id, 0, end = 2)
        val s = trip.stops[2]
        val got = TripRun.onArrival(run, trip, arrive(ArrivalKind.DESTINATION, "전혀 다른 이름"), Place("x", s.lat + 0.001, s.lng))   // 약 110m
        assertEquals(2, got!!.stopIndex)
        val far = TripRun.onArrival(run, trip, arrive(ArrivalKind.DESTINATION, "전혀 다른 이름"), Place("x", s.lat + 0.05, s.lng))    // 약 5.5km
        assertNull("좌표도 이름도 이 일정의 정류장이 아니면 다른 길의 도착이에요", far)
    }

    @Test fun anotherRoutesArrivalLeavesTheTripAlone() {
        assertNull(TripRun.onArrival(TripProgress(trip.id, 0, end = 2), trip, arrive(ArrivalKind.DESTINATION, "우리 집")))
        assertNull("경유지가 없는 구간에서 온 경유지 도착은 이 일정의 것이 아니에요", TripRun.onArrival(TripProgress(trip.id, 3), trip, arrive(ArrivalKind.WAYPOINT, trip.stops[3].name)))
    }

    @Test fun arrivingAtTheLastStopFinishesTheTrip() {
        val last = trip.stops.size - 1
        val run = TripProgress(trip.id, last - 1, end = last)
        val done = TripRun.onArrival(run, trip, arrive(ArrivalKind.DESTINATION, trip.stops[last].name))!!
        assertEquals(TripProgress(trip.id, last, end = last, arrived = true), done)
        val lib = LibraryState().download(trip.id).apply(Pane.SVC_TRIP, TripRun.encode(done))
        val ui = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED)
        assertTrue(Services.status(ui, Services.byPane(Pane.SVC_TRIP)!!, cat, lib).tile.startsWith("여행을 마쳤어요 ${last + 1}/${trip.stops.size}"))
        assertTrue("마지막에 닿았으면 더 권할 정류장이 없어요", Advisor.advise(ui, cat, lib, QuietState(), 10).none { it.kind == "next_stop" })
    }

    @Test fun theChatAnswersWhereTheTripIsAfterArrival() {
        val a = app.roadmemory.car.brain.StudioAnswers(cat, LibraryState().download(trip.id).apply(Pane.SVC_TRIP, TripRun.encode(TripProgress(trip.id, 1, end = 2, arrived = true))), emptyList(), null)
        val got = a.answer("내 일정 어디까지 왔어?")
        assertTrue(got, got != null && got.contains("${trip.stops[1].name}에 도착했어요") && got.contains(trip.stops[2].name))
        val going = app.roadmemory.car.brain.StudioAnswers(cat, LibraryState().download(trip.id).apply(Pane.SVC_TRIP, TripRun.encode(TripProgress(trip.id, 1))), emptyList(), null).answer("내 일정 어디까지 왔어?")
        assertTrue(going, going != null && going.contains("가는 중이에요") && !going.contains("도착했어요"))
    }

    @Test fun arrivalChangesWhatTheStatusAndTheAdviceSay() {
        val ui = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED, car = CarSnapshot(soc = 80, rangeKm = 300f))
        val going = LibraryState().download(trip.id).apply(Pane.SVC_TRIP, TripRun.encode(TripProgress(trip.id, 0)))
        assertTrue(Services.status(ui, Services.byPane(Pane.SVC_TRIP)!!, cat, going).tile.startsWith("가는 중 1/"))
        val here = LibraryState().download(trip.id).apply(Pane.SVC_TRIP, TripRun.encode(TripProgress(trip.id, 0, end = 0, arrived = true)))
        assertTrue(Services.status(ui, Services.byPane(Pane.SVC_TRIP)!!, cat, here).tile.startsWith("도착 1/"))
        val advice = Advisor.advise(ui, cat, here, QuietState(), 10).first { it.kind == "next_stop" }
        assertTrue("도착했다는 말이 먼저 나와요", advice.title.startsWith("${trip.stops[0].name}에 도착했어요"))
        assertTrue(advice.title.contains(trip.stops[1].name))
        val notArrived = Advisor.advise(ui, cat, going, QuietState(), 10).first { it.kind == "next_stop" }
        assertFalse(notArrived.title.contains("도착했어요"))
        assertNotNull(advice.action)
    }
}
