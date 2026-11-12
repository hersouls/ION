package app.roadmemory.car

import app.roadmemory.car.brain.ChargeHistory
import app.roadmemory.car.brain.ChatCommand
import app.roadmemory.car.brain.Conversation
import app.roadmemory.car.brain.Memories
import app.roadmemory.car.brain.StudioAnswers
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.ChargeStop
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.data.Weather
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.ontology.Ontology
import app.roadmemory.car.ontology.OntologyAnswers
import app.roadmemory.car.studio.CatalogParser
import app.roadmemory.car.studio.LibraryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/**
 * 사용자 관점 점검(2026-11-11, QnaReportTest의 표를 읽고 고친 것)을 기대로 남겨요. 원칙: 묻는 말에 답해요 · 모르면 모른다고 해요 · 조사가 맞아요 · 답에서 바로 화면으로 가요.
 */
class QnaExpectationsTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val onto = Ontology.parse(File("src/main/assets/ontology.json").readText(Charsets.UTF_8))
    private val today = LocalDate.of(2026, 11, 11)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val trips = listOf(
        SavedTrip(1, day(2024, 11, 10), "2024.11", "경포대", null, null, 120, 3, listOf("Solcho Sea Loop"), 1),
        SavedTrip(2, day(2026, 10, 3), "2026.10", "제주 서귀포", null, null, 200, 3, emptyList(), 2),
        SavedTrip(3, day(2026, 11, 2), "2026.11", "강릉", null, null, 150, 4, emptyList(), 0),
    )
    private fun a(nav: NavState? = null, weather: Weather? = null, lib: LibraryState = LibraryState(), t: List<SavedTrip> = trips) =
        StudioAnswers(cat, lib, t, null, ChargeHistory(), today, nav, weather, null, { LocalTime.of(13, 5) })

    @Test fun answersTheQuestionThatWasAsked() {
        // "도착하면 배터리 얼마야?"는 질문이지 충전소를 넣으라는 말이 아니에요
        assertEquals("지금 안내 중인 길이 없어요. 내비에서 목적지를 정하면 도착 예상 배터리를 말해요", a().answer("도착하면 배터리 얼마야?"))
        val nav = NavState(routeActive = true, destinationName = "경포대", estimatedSocAtDest = 42, chargeStops = listOf(ChargeStop("A휴게소", 20, 80)))
        assertEquals("경포대에 도착하면 배터리가 42%쯤이에요 · 내비가 A휴게소를 경로에 넣었어요", a(nav).answer("도착하면 배터리 얼마야?"))
        // "누구랑"은 모르는 것: 모른다고 먼저 말해요
        assertTrue(a().answer("누구랑 제일 많이 갔어?")!!.startsWith("누가 탔는지는 기록하지 않아요"))
        // 모두 한 번씩이면 "가장 자주"라고 하지 않아요
        assertTrue(a().answer("제일 많이 간 곳은?")!!.startsWith("두 번 이상 간 곳은 아직 없어요"))
    }

    /** 은행 2(120개)에서 고친 것: 장소 이름만 있는 물음 · 가족 · 차의 값 · 충전 중 여부 · 영문 제목 · 짧은 말. */
    @Test fun secondBankFixesHold() {
        assertEquals("2026.10에 갔어요 · 제주 서귀포 200km", a().answer("제주 언제 갔어?"))
        assertTrue(a().answer("가족이랑 갔어?")!!.startsWith("누가 탔는지는 기록하지 않아요"))
        assertEquals("아니요, 부산에 간 기록은 없어요", a().answer("부산 갔었나"))
        assertEquals("첫 여행은 2024.11 경포대 120km예요", a().answer("첫 여행"))
        assertEquals("11월 다녀온 곳은 강릉 · 경포대예요 · 여행 2건", a().answer("11월에 어디 갔어"))
        val car = CarSnapshot(soc = 42, rangeKm = 160f, outsideTempC = 12f)
        val c = StudioAnswers(cat, LibraryState(), trips, "Solcho Sea Loop", ChargeHistory(), today, null, null, null, { LocalTime.of(13, 5) }, car, false)
        assertEquals("배터리 42% · 160km 갈 수 있어요", c.answer("배터리 얼마야"))
        assertEquals("지금 160km 갈 수 있어요 · 배터리 42%", c.answer("주행 가능 거리"))
        assertEquals("외기온은 12도예요", c.answer("외기온 몇 도야"))
        assertEquals("아니요, 지금은 충전하고 있지 않아요 · 배터리 42%", c.answer("충전 중이야?"))
        assertEquals("지금 트는 곡은 Solcho Sea Loop예요", c.answer("지금 무슨 노래야"))
        assertEquals("안녕하세요! 가는 길에 필요한 것만 말할게요", c.answer("ION"))
        assertTrue(c.answer("도와줘")!!.contains("다음 정류장 안내"))
        assertEquals("지금 안내 중인 길이 없어요", c.answer("목적지"))
        // 명령 동의어
        assertEquals(ChatCommand.NextTrack, Conversation.command("이 노래 말고"))
        assertEquals(ChatCommand.StopMusic, Conversation.command("조용히 해"))
        assertEquals(ChatCommand.NextStop, Conversation.command("안내 시작"))
        assertEquals(ChatCommand.GuideAppointment, Conversation.command("약속 장소로 가자"))
        assertEquals(ChatCommand.ApplyRecommendedTheme, Conversation.command("적용"))
        assertEquals(Pane.SVC_ALBUM, (Conversation.command("앨범") as ChatCommand.Open).pane)
    }

    @Test fun smallTalkIsAnswered() {
        assertEquals("안녕하세요! 가는 길에 필요한 것만 말할게요", a().answer("안녕"))
        assertEquals("별말씀을요. 필요할 때 불러 주세요", a().answer("고마워"))
        assertEquals("지금 13시 5분이에요", a().answer("몇 시야?"))
        assertTrue(a(weather = Weather.RAIN).answer("날씨 어때?")!!.startsWith("밖은 비인 것 같아요"))
        assertTrue(a().answer("날씨 어때?")!!.contains("아직 모르겠어요"))
    }

    @Test fun particlesAndSentencesReadNaturally() {
        assertEquals("2년 전 오늘 즈음 경포대에 3명이 함께 있었어요.", Memories.sentence(Memories.thatDay(trips, today).first()))
        val lib = LibraryState().download(cat.themes[0].id).apply(Pane.SVC_THEME, cat.themes[0].id)
        assertEquals("지금 테마는 가람 물총새 테마예요 · 다이아 웍스", a(lib = lib).answer("지금 무슨 테마야?"))
        assertFalse(OntologyAnswers(onto).answer("충전 경유 제안에는 뭐가 필요해?")!!.contains("이(가)"))
        assertFalse(OntologyAnswers(onto).answer("경고등 해설은 왜 못 해?")!!.contains("은(는)"))
    }

    @Test fun syncedThingsCountAsHaving() {
        val lib = LibraryState().download("sync_svc_album").download("sync_svc_music")
        assertEquals("내려받은 상품은 없어요 · 마이현대에서 가져온 것은 사진 100장 · 곡 22곡", a(lib = lib).answer("내려받은 거 뭐 있어?"))
    }

    @Test fun answersLinkToTheScreen() {
        val ui = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED, car = CarSnapshot(soc = 80), trips = trips)
        val lib = LibraryState().download("sync_svc_music")
        val f1 = Conversation.followUps("충전 몇 번 했어?", "저장한 여행 3건에서 충전 3번이에요", ui, lib)
        assertTrue(f1.contains("→ 충전 화면 열어 줘")); assertTrue(f1.size <= 3)
        assertTrue(Conversation.followUps("지금 무슨 테마야?", "적용한 테마가 없어요", ui, lib).contains("→ 테마 보여 줘"))
        assertTrue(Conversation.followUps("외기온을 쓰는 기능은?", "외기온을 쓰는 기능은 2개예요", ui, lib).contains("→ 닿는 데이터 열어 줘"))
        // 링크 칩을 누르면 그 화면을 여는 명령이에요
        assertEquals(app.roadmemory.car.brain.ChatCommand.Open(Pane.CHARGE, "충전"), Conversation.command("→ 충전 화면 열어 줘"))
        assertEquals(app.roadmemory.car.brain.ChatCommand.Open(Pane.DATA, "ION이 닿는 데이터"), Conversation.command("→ 닿는 데이터 열어 줘"))
        assertEquals(app.roadmemory.car.brain.ChatCommand.Open(Pane.TRIPS, "여행 기록"), Conversation.command("→ 여행 기록 열어 줘"))
        // 충전 중이라고 아무 답에나 "곡 틀어 줘"를 붙이지 않아요
        val charging = ui.copy(mode = Mode.CHARGING)
        assertFalse(Conversation.followUps("외기온을 쓰는 기능은?", "…", charging, lib).contains("곡 틀어 줘"))
        assertTrue(Conversation.followUps("충전 몇 번 했어?", "…", charging, lib).contains("곡 틀어 줘"))
    }
}
