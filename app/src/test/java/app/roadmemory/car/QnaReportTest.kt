package app.roadmemory.car

import app.roadmemory.car.brain.ChargeHistory
import app.roadmemory.car.brain.ChargeSession
import app.roadmemory.car.brain.Conversation
import app.roadmemory.car.brain.StudioAnswers
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.ontology.Ontology
import app.roadmemory.car.ontology.OntologyAnswers
import app.roadmemory.car.studio.CatalogParser
import app.roadmemory.car.studio.LibraryState
import app.roadmemory.car.ui.TripProgress
import app.roadmemory.car.ui.TripRun
import app.roadmemory.car.voice.Command
import app.roadmemory.car.voice.VoiceCommands
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * 사용자 관점 점검: 앱이 내놓는 모든 말(돌아가는 칩 · 이어지는 말 · 예시)을 네 가지 상황에서 실제 답변 순서(명령 → 기록 질문 → ION-Link 질문 → 음성 명령)로 돌려
 * build/qna-report.md 에 질문 → 답 표를 써요. 사람이 읽고 어색한 답을 고친 뒤, 고친 기대는 QnaExpectationsTest 에 남겨요.
 */
class QnaReportTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val onto = Ontology.parse(File("src/main/assets/ontology.json").readText(Charsets.UTF_8))
    private val today = LocalDate.of(2026, 11, 11)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val trips = listOf(
        SavedTrip(1, day(2024, 11, 10), "2024.11", "경포대", 37.79, 128.89, 120, 3, listOf("Solcho Sea Loop"), 1),
        SavedTrip(2, day(2026, 10, 3), "2026.10", "제주 서귀포", null, null, 200, 3, emptyList(), 2),
        SavedTrip(3, day(2026, 11, 2), "2026.11", "강릉", null, null, 150, 4, listOf("October Maple Loop"), 0),
    )
    private val charges = ChargeHistory(listOf(ChargeSession(1, "A휴게소", 20, 60, 30), ChargeSession(2, "B휴게소", 30, 70, 30), ChargeSession(3, "느린충전소", 20, 30, 40)))

    data class Situation(val name: String, val ui: UiState, val lib: LibraryState)

    private fun situations(): List<Situation> {
        val base = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED, car = CarSnapshot(soc = 80, rangeKm = 300f))
        val t = cat.trips.first()
        return listOf(
            Situation("① 처음 켠 차(기록 · 데이터 없음)", base, LibraryState()),
            Situation("② 여행 기록 셋 · 충전 기록 셋", base.copy(trips = trips), LibraryState()),
            Situation("③ 마이 트립 진행 중(1/5) · 테마 적용", base.copy(trips = trips), LibraryState().download(t.id).apply(Pane.SVC_TRIP, TripRun.encode(TripProgress(t.id, 0))).download(cat.themes[0].id).apply(Pane.SVC_THEME, cat.themes[0].id)),
            Situation("④ 충전 중 · 앨범 · 뮤직 · 캘린더 동기화", base.copy(mode = Mode.CHARGING, chargeMinLeft = 25, trips = trips), LibraryState().download("sync_svc_album").download("sync_svc_music").download("sync_svc_calendar")),
        )
    }

    private fun simulateCommand(c: app.roadmemory.car.brain.ChatCommand, s: Situation): String = when (c) {
        app.roadmemory.car.brain.ChatCommand.NextStop -> "[명령] 다음 정류장으로 내비 안내(가는 일정이 없으면 '가는 중인 일정이 없어요…')"
        app.roadmemory.car.brain.ChatCommand.PlayMusic -> "[명령] 분위기에 맞는 곡 틀기(곡을 안 가져왔으면 '마이현대에서 곡을 아직 가져오지 않았어요…')"
        app.roadmemory.car.brain.ChatCommand.StopMusic -> "[명령] 곡을 멈췄어요"
        app.roadmemory.car.brain.ChatCommand.NextTrack -> "[명령] 다른 곡 틀기(곡을 안 가져왔으면 안내)"
        is app.roadmemory.car.brain.ChatCommand.PlayTrack -> "[명령] ${c.title} 틀기"
        is app.roadmemory.car.brain.ChatCommand.GuidePlace -> "[명령] ${c.name}으로 내비 안내(위치를 모르면 안내)"
        app.roadmemory.car.brain.ChatCommand.GuideAppointment -> "[명령] 좌표 있는 첫 약속 장소로 내비 안내(일정을 안 가져왔으면 안내)"
        app.roadmemory.car.brain.ChatCommand.ThatDayMusic -> "[명령] 그날 들은 곡 틀기(없으면 '그때 들은 곡 기록이 없어요')"
        app.roadmemory.car.brain.ChatCommand.RecommendTheme -> "[명령] 테마 하나 권하기(근거 포함)"
        app.roadmemory.car.brain.ChatCommand.ApplyRecommendedTheme -> "[명령] 권한 테마 내려받아 적용(먼저 추천을 안 받았으면 안내)"
        app.roadmemory.car.brain.ChatCommand.StartRecording -> "[명령] 여행 기록하기 시작(내비 목적지가 있으면 그곳으로, 없으면 처음 도착할 때 끝나요)"
        app.roadmemory.car.brain.ChatCommand.ResumeRecording -> "[명령] 이어서 기록(시동을 껐다 켠 뒤 묻는 중일 때만, 아니면 '이어서 할 기록이 없어요')"
        app.roadmemory.car.brain.ChatCommand.EndRecording -> "[명령] 기록 끝내기(1km도 안 달렸으면 남기지 않아요, 기록하는 여행이 없으면 안내)"
        is app.roadmemory.car.brain.ChatCommand.Open -> "[명령] ${c.label} 화면 열기"
        is app.roadmemory.car.brain.ChatCommand.Slideshow -> if (c.start) "[명령] 앨범 슬라이드쇼 시작(앨범을 안 가져왔으면 안내, 달리는 중에는 안 틀어요)" else "[명령] 슬라이드쇼 멈춤"
    }

    private fun answer(q: String, s: Situation): Pair<String, String> {
        Conversation.command(q)?.let { return "명령" to simulateCommand(it, s) }
        StudioAnswers(cat, s.lib, s.ui.trips, s.ui.nowPlaying, charges.takeIf { s.ui.trips.isNotEmpty() } ?: ChargeHistory(), today).answer(q)?.let { return "기록" to it }
        OntologyAnswers(onto).answer(q)?.let { return "ION-Link" to it }
        return when (val c = VoiceCommands.route(q, s.ui.trips.mapNotNull { it.destName })) {
            null -> "모름" to "‘$q’는 아직 알아듣지 못해요. 아래 말 중에서 골라 볼까요?"
            Command.Close -> "음성" to "[닫기]"
            Command.Today -> "음성" to "오늘 화면으로 돌아왔어요"
            is Command.Gleo -> "음성" to "[Gleo 함수 ${c.action}${c.place?.let { " · $it" } ?: ""}]"
        }
    }

    @Test fun writeReport() {
        val out = StringBuilder("# ION 칸 질문 → 답 점검 (${today})\n\n")
        val seen = linkedSetOf<String>()
        for (s in situations()) {
            out.append("## ${s.name}\n\n")
            out.append("첫마디: ${Conversation.greeting(s.ui, 14, cat, s.lib, today).joinToString(" / ")}\n\n")
            val pool = Conversation.chipPool(s.ui, cat, s.lib)
            val extra = listOf("가장 멀리 간 곳은?", "누구랑 제일 많이 갔어?", "작년 오늘은?", "10월 제주에서 충전 몇 번 했어?", "지난달 여행 몇 번 갔어?", "강릉 간 적 있어?", "부산 간 적 있어?", "충전 속도 빨라?", "그 테마 적용해 줘", "그때 곡 틀어 줘", "곡 멈춰 줘", "내 일정 보여 줘", "테마 보여 줘", "도착하면 배터리 얼마야?", "경고등 해설은 왜 못 해?", "시그니처 송은 언제 나와?", "안녕", "고마워", "몇 시야?", "날씨 어때?")
            out.append("| 질문 | 길 | 답 | 이어지는 말 |\n|---|---|---|---|\n")
            for (q in (pool + extra).distinct()) {
                val (via, a) = answer(q, s)
                val fu = if (via == "기록" || via == "ION-Link") Conversation.followUps(q, a, s.ui, s.lib).joinToString(" · ") else ""
                out.append("| $q | $via | ${a.replace("|", "｜").replace("\n", " ")} | $fu |\n")
                seen += q
            }
            out.append("\n")
        }
        File("build/qna-report.md").writeText(out.toString())
        println("QNA_REPORT ${seen.size} questions → build/qna-report.md")
    }
}
