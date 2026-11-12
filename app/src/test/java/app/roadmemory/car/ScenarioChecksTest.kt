package app.roadmemory.car

import app.roadmemory.car.brain.Advisor
import app.roadmemory.car.brain.QuietState
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.DemoTrips
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.studio.CatalogParser
import app.roadmemory.car.studio.LibraryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** 기획서 시나리오(#scenarios)의 한 줄이 앱에서 정말 그렇게 되는지 지키는 시험(2026-11-12 시나리오 점검에서 찾은 것). */
class ScenarioChecksTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val synced = LibraryState().download("sync_${Pane.SVC_ALBUM}").download("sync_${Pane.SVC_MUSIC}")

    // "오늘 즈음"의 기억(시나리오 I)이 끼지 않게 7월 1일로 두고, 충전 중 첫 카드만 봐요
    @Test fun scenarioC_theFirstCardWhileChargingIsThePhotoOffer_notTheNextTripDraft() {
        val ui = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.CHARGING, car = CarSnapshot(soc = 30, rangeKm = 310f), trips = DemoTrips.all)
        val first = Advisor.advise(ui, cat, synced, QuietState(), 22, today = LocalDate.of(2026, 7, 1)).first()
        assertEquals("충전하는 동안 사진을 넘겨 볼까요?", first.title)
    }

    @Test fun scenarioD_aLocatedAppointmentComesBeforeTheNextTripDraft() {
        val lib = LibraryState().download("sync_${Pane.SVC_CALENDAR}")
        val ui = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED, car = CarSnapshot(soc = 72, rangeKm = 310f))
        val first = Advisor.advise(ui, cat, lib, QuietState(), 18, today = LocalDate.of(2026, 11, 12)).first()
        assertEquals("calendar_go", first.kind)
        assertTrue(first.title, first.title.contains("안내할까요"))
    }

    @Test fun aRouteWithoutADestinationNameNeverMakesABlankHomeRow() {
        val ui = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED, nav = NavState(routeActive = true, destinationName = "", remainTimeS = 60))
        val row = app.roadmemory.car.ui.IonFeedItems.build(ui).first { it.id == "route" }
        assertEquals("목적지 · 1분", row.title)
    }

    @Test fun scenarioI_theReasonWritesTheTripDateTheAppWay() {
        val trips = DemoTrips.all
        val spot = trips.first { it.destName == "속초 관광수산시장" }
        val day = app.roadmemory.car.brain.Memories.date(spot)
        val ui = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED, car = CarSnapshot(soc = 80, rangeKm = 400f), trips = trips)
        val first = Advisor.advise(ui, cat, LibraryState(), QuietState(), 14, today = day.plusYears(1).plusDays(3)).first { it.kind == "that_day" }
        assertTrue(first.reasons.toString(), first.reasons.any { it.contains(day.toString().replace('-', '.')) && !it.contains(day.toString()) })
    }

    @Test fun theNextTripDraftStillComesWhenNotCharging() {
        val ui = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED, car = CarSnapshot(soc = 80, rangeKm = 400f), trips = DemoTrips.all)
        assertTrue(Advisor.advise(ui, cat, LibraryState(), QuietState(), 14, today = LocalDate.of(2026, 7, 1)).any { it.kind == "next_trip" })
    }
}
