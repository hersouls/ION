package app.roadmemory.car

import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.engine.CardKind
import app.roadmemory.car.engine.GlanceCard
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.ui.usesWorkspace
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 서 있을 때만 둘로 나뉜 작업 화면을 쓰고, 달리는 중에는 서비스 화면이 절대 열리지 않는지(주행 규제). */
class WorkspaceTest {
    private val connected = UiState(sourceKind = SourceKind.PLEOS)
    private fun card(kind: CardKind) = GlanceCard(kind, "t", null, "a", "b", "h")

    @Test fun notConnectedIsNeverTheWorkspace() = assertFalse(usesWorkspace(UiState(sourceKind = null, mode = Mode.PARKED)))

    @Test fun parkedAndChargingAreTheWorkspace() {
        assertTrue(usesWorkspace(connected.copy(mode = Mode.PARKED)))
        assertTrue(usesWorkspace(connected.copy(mode = Mode.CHARGING)))
    }

    @Test fun aCarlessDeviceStillGetsTheWorkspaceSoItCanSayItIsNotConnected() =
        assertTrue(usesWorkspace(UiState(sourceKind = SourceKind.NONE, mode = Mode.PARKED)))

    @Test fun drivingNeverOpensAnyPaneEvenIfOneIsSet() {
        val driving = connected.copy(mode = Mode.DRIVING)
        assertFalse(usesWorkspace(driving))
        for (pane in Pane.all) assertFalse(pane, usesWorkspace(driving.copy(pane = pane)))
    }

    @Test fun parkedSafetyCardsAndTheArrivalSummaryUseTheWholeScreen() {
        val parked = connected.copy(mode = Mode.PARKED)
        assertFalse(usesWorkspace(parked.copy(card = card(CardKind.REAR_ALERT))))
        assertFalse(usesWorkspace(parked.copy(card = card(CardKind.VEHICLE_CHECK))))
        assertFalse(usesWorkspace(parked.copy(trip = TripSummary("11.07", 12, 3, 2, 0))))
    }

    @Test fun aShortMoveUnderOneKmLeavesNoSummarySoTheWorkspaceComesBack() {
        // 1km가 안 되는 주행(주차 정리)은 도착 요약을 띄우지 않으니, 서 있는 동안 "주행 중" 화면에 멈추지 않아요
        val parked = connected.copy(mode = Mode.PARKED)
        assertTrue(usesWorkspace(parked.copy(trip = TripSummary("11.12", 0, 1, 0, 0))))
        assertFalse(usesWorkspace(parked.copy(trip = TripSummary("11.12", 1, 1, 0, 0))))
    }

    @Test fun anOpenPaneKeepsTheWorkspaceEvenWithACard() {
        val parked = connected.copy(mode = Mode.PARKED, card = card(CardKind.REAR_ALERT))
        assertTrue(usesWorkspace(parked.copy(pane = Pane.TRIPS)))
    }
}
