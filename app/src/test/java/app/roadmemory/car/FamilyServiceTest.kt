package app.roadmemory.car

import app.roadmemory.car.brain.FamilyInsight
import app.roadmemory.car.data.ChargeStop
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.family.DemoFamilySource
import app.roadmemory.car.family.FamilyPlace
import app.roadmemory.car.family.FamilyRead
import app.roadmemory.car.family.FamilyText
import app.roadmemory.car.studio.CalendarEvent
import app.roadmemory.car.studio.CatalogParser
import app.roadmemory.car.studio.LibraryState
import app.roadmemory.car.ui.Services
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 위치 동기화 서비스 화면의 규칙 버전(하이엔드): 시연 값은 위치를 보여 주는 가족만 읽고, ION의 한마디는 쓴 값에서만 나와요.
 * 이름 · 좌표는 모두 지어낸 시연 값이에요(장소만 지도의 실제 장소).
 */
class FamilyServiceTest {
    private val catalog = CatalogParser.parse(java.io.File("src/main/assets/studio/catalog.json").readText())
    private val ui = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED)
    private val noNav = NavState()
    private fun read() = runBlocking { DemoFamilySource(catalog).read() } as FamilyRead.Ready
    private fun who(alias: String) = read().people.first { it.alias == alias }

    @Test fun demoHoldsFourFamilyOneOfThemNotSharing() {
        assertEquals(listOf("엄마", "아빠", "형", "동생"), catalog.family.map { it.alias })
        assertEquals(3, catalog.family.count { it.sharing })
        assertTrue(catalog.family.filter { it.sharing }.all { it.lat in 33.0..39.0 && it.lng in 124.0..132.0 })
        // 별칭은 역할이고 실명이 아니에요
        assertTrue(catalog.family.all { it.alias.length <= 3 })
    }

    @Test fun demoSourceReadsOnlyThoseWhoShare() {
        val r = read()
        assertEquals(listOf("엄마", "아빠", "형"), r.people.map { it.alias })
        assertEquals(1, r.notSharing)
        assertTrue("꺼 둔 가족은 이름도 위치도 읽지 않아요", r.people.none { it.alias == "동생" })
    }

    @Test fun nobodySharingIsSaidAsNobody() {
        val off = catalog.copy(family = catalog.family.map { it.copy(sharing = false) })
        assertEquals(FamilyRead.Nobody, runBlocking { DemoFamilySource(off).read() })
    }

    @Test fun staleLocationIsSaidFirstAndFreshOnesAreNot() {
        val stale = FamilyInsight.lines(who("형"), noNav, null)
        assertTrue(stale.first().text.startsWith("오래된 위치예요"))
        assertTrue(stale.first().reason.contains("47분"))
        assertTrue(FamilyInsight.lines(who("엄마"), noNav, null).none { it.text.startsWith("오래된 위치") })
        // 화면에 보이는 위치 문장과 같은 기준(30분)이에요
        assertTrue(FamilyText.age(who("형").ageSec).contains("오래된 위치예요"))
    }

    @Test fun movingMeansTheRouteGoesToTheSpotItWasReceived() {
        val lines = FamilyInsight.lines(who("아빠"), noNav, null)
        assertTrue(lines.any { it.text.startsWith("이동 중이라 안내하는 곳은") })
        assertTrue(FamilyInsight.lines(who("엄마"), noNav, null).none { it.text.startsWith("이동 중") })
    }

    @Test fun familyEventPlacesAreJoinedOnlyWhenTheCalendarWasSynced() {
        // 캘린더를 가져오지 않았으면 일정을 쓰지 않아요
        assertTrue(FamilyInsight.lines(who("엄마"), noNav, null).none { it.text.contains("약속 장소") })
        val cal = catalog.calendar
        val mom = FamilyInsight.lines(who("엄마"), noNav, cal)
        assertTrue(mom.any { it.text.contains("부모님 댁 방문") && it.text.contains("약속 장소(강릉 경포대 근처)에 있어요") })
        assertTrue("같은 자리인데 0.0km라고 말하지 않아요", mom.none { it.reason.contains("0.0km") })
        assertTrue(mom.first { it.text.contains("부모님 댁 방문") }.reason.contains("300m 안"))
        val dad = FamilyInsight.lines(who("아빠"), noNav, cal)
        assertTrue(dad.any { it.text.contains("가족 저녁 식사") && it.text.contains("근처에 있어요") })
        assertTrue(dad.first { it.text.contains("가족 저녁 식사") }.reason.contains("km"))
        // 가족 일정이 아닌 약속(친구 모임) 장소에 있어도 이어 말하지 않아요
        assertTrue(FamilyInsight.lines(who("형"), noNav, cal).none { it.text.contains("약속 장소") })
    }

    @Test fun onlyFamilyEventsAreJoined() {
        val mom = who("엄마")
        fun ev(title: String, with: String) = CalendarEvent("x", title, "내일", "어딘가", with)
        assertTrue(FamilyInsight.isFamilyEvent(ev("가족 저녁 식사", ""), mom))
        assertTrue(FamilyInsight.isFamilyEvent(ev("학예회", "엄마 · 아빠"), mom))
        assertTrue(FamilyInsight.isFamilyEvent(ev("부모님 댁 방문", ""), mom))
        assertFalse(FamilyInsight.isFamilyEvent(ev("친구 모임", ""), mom))
        assertFalse(FamilyInsight.isFamilyEvent(ev("정기 점검 예약", ""), mom))
    }

    @Test fun whileGuidingOnlyTheNavisValuesAreUsed() {
        val dad = who("아빠")
        val nav = NavState(routeActive = true, destinationName = FamilyText.destName(dad), remainDistanceM = 12_300, remainTimeS = 1_480, estimatedSocAtDest = 61)
        assertTrue(FamilyInsight.guiding(dad, nav))
        val lines = FamilyInsight.live(dad, nav)
        assertEquals("가는 중 · 남은 12km · 약 25분", lines[0].text)
        assertEquals("도착하면 배터리 약 61%예요 · 충전 없이 가요", lines[1].text)
        assertTrue(lines.all { it.reason.contains("내비") })
        // 가는 중에는 "이동 중이라…" 말을 겹쳐 하지 않아요
        assertTrue(FamilyInsight.lines(dad, nav, null).none { it.text.startsWith("이동 중이라") })
        // 다른 목적지로 가는 중이면 이 사람 얘기가 아니에요
        assertFalse(FamilyInsight.guiding(dad, nav.copy(destinationName = "엄마 · 경포대")))
        assertTrue(FamilyInsight.live(dad, noNav).isEmpty())
    }

    @Test fun lowBatteryAtDestinationRecommendsChargingButNeverChangesTheRoute() {
        val mom = who("엄마")
        val nav = NavState(routeActive = true, destinationName = FamilyText.destName(mom), remainDistanceM = 80_000, remainTimeS = 4_000, estimatedSocAtDest = 9, chargeStops = listOf(ChargeStop("강릉휴게소", 9, 70)))
        val lines = FamilyInsight.live(mom, nav)
        assertTrue(lines.any { it.text == "도착하면 배터리 약 9%예요 · 가는 길에 충전을 권해요" })
        assertTrue(lines.any { it.text == "내비가 강릉휴게소에서 충전하도록 넣었어요" })
        assertTrue(lines.any { it.reason.contains("ION은 경로를 바꾸지 않아요") })
    }

    @Test fun unknownNavValuesAreNeverInvented() {
        val mom = who("엄마")
        val bare = NavState(routeActive = true, destinationName = mom.alias)
        assertEquals(listOf("내비가 길을 계산하는 중이에요"), FamilyInsight.live(mom, bare).map { it.text })
        // 거리만 알면 거리만, 도착 때 배터리를 모르면 배터리 말을 하지 않아요
        val onlyKm = FamilyInsight.live(mom, bare.copy(remainDistanceM = 4_000))
        assertEquals(listOf("가는 중 · 남은 4.0km"), onlyKm.map { it.text })
        // 가까운 일정도 없고 오래되지도 않고 이동 중도 아닌 사람에게는 보탤 말이 없어요
        val calm = FamilyPlace("누나", 37.5, 127.0, "집", false, 60)
        assertTrue(FamilyInsight.lines(calm, noNav, catalog.calendar).isEmpty())
    }

    @Test fun routeNameKeepsTheAliasButNotTheCoordinates() {
        assertEquals("엄마 · 경포대", FamilyText.destName(who("엄마")))
        assertEquals("아빠", FamilyText.destName(who("아빠")))
        assertTrue(!FamilyText.destName(who("엄마")).any { it.isDigit() })
    }

    @Test fun theServiceOpensByVoiceLikeTheOtherServices() {
        for (say in listOf("가족 위치 보여 줘", "위치 동기화 열어 줘", "엄마 어디야? 가족 어디 있어")) {
            assertEquals(say, app.roadmemory.car.brain.ChatCommand.Open(Pane.SVC_LOCATION, "위치 동기화"), app.roadmemory.car.brain.Conversation.command(say))
        }
        // 음성으로 열 뿐, 길 안내는 화면의 "경로 안내"를 눌러야 해요(말로 바로 안내하지 않아요)
        assertTrue(app.roadmemory.car.brain.Conversation.command("엄마한테 길 안내해 줘") !is app.roadmemory.car.brain.ChatCommand.NextStop)
    }

    @Test fun statusFollowsTheRoute() {
        val loc = Services.byPane(Pane.SVC_LOCATION)!!
        val going = ui.copy(nav = NavState(routeActive = true, destinationName = "엄마 · 경포대"))
        assertEquals("엄마에게 가는 중", Services.status(going, loc, catalog, LibraryState()).tile)
        // 길 안내가 없으면 위치를 말하지 않고 몇 명이 보여 주는지만 말해요
        assertEquals("가족 3명", Services.status(ui, loc, catalog, LibraryState()).tile)
        assertNull(app.roadmemory.car.family.FamilyLink.source)
    }
}
