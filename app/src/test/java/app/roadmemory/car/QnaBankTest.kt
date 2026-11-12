package app.roadmemory.car

import app.roadmemory.car.brain.ChargeHistory
import app.roadmemory.car.brain.ChargeSession
import app.roadmemory.car.brain.Conversation
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
import app.roadmemory.car.ui.TripProgress
import app.roadmemory.car.ui.TripRun
import app.roadmemory.car.voice.Command
import app.roadmemory.car.voice.VoiceCommands
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** 질문 은행(사람 말투 100개 남짓)을 돌려 어디로 가는지 세요. "모름"과 엉뚱한 길을 build/qna-bank.md 에 적어요. */
class QnaBankTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val onto = Ontology.parse(File("src/main/assets/ontology.json").readText(Charsets.UTF_8))
    private val today = LocalDate.of(2026, 11, 11)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val trips = listOf(
        SavedTrip(1, day(2024, 11, 10), "2024.11", "경포대", 37.79, 128.89, 120, 3, listOf("Solcho Sea Loop"), 1),
        SavedTrip(2, day(2026, 10, 3), "2026.10", "제주 서귀포", null, null, 200, 3, emptyList(), 2),
        SavedTrip(3, day(2026, 11, 2), "2026.11", "강릉", null, null, 150, 4, listOf("October Maple Loop"), 0),
        SavedTrip(4, day(2026, 9, 14), "2026.09", "강릉", null, null, 140, 2, emptyList(), 1),
    )
    private val charges = ChargeHistory(listOf(ChargeSession(1, "A휴게소", 20, 60, 30), ChargeSession(2, "B휴게소", 30, 70, 30), ChargeSession(3, "느린충전소", 20, 30, 40)))
    private val t = cat.trips.first()
    private val lib = LibraryState().download(t.id).apply(Pane.SVC_TRIP, TripRun.encode(TripProgress(t.id, 0))).download(cat.themes[0].id).apply(Pane.SVC_THEME, cat.themes[0].id).download("sync_svc_album").download("sync_svc_music").download("sync_svc_calendar")
    private val nav = NavState(routeActive = true, destinationName = "강릉 커피거리", estimatedSocAtDest = 61, remainTimeS = 1800, chargeStops = listOf(ChargeStop("느린충전소", 20, 80)))
    private val ui = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.CHARGING, car = CarSnapshot(soc = 80, rangeKm = 300f), trips = trips, nav = nav, weather = Weather.RAIN, chargeMinLeft = 25, nowPlaying = "Solcho Sea Loop")

    val bank = listOf(
        // 기록 · 충전
        "충전 몇번했어", "충전은 몇 번 했지?", "지금까지 충전 횟수", "이번달 충전 몇번", "올해 충전 몇 번 했어?", "제주에서 충전 몇 번", "작년에 충전 몇번",
        "충전 속도 어때", "충전 빠른 편이야?", "어디가 제일 느렸어?", "평소 충전 속도", "충전 얼마나 걸려?", "충전 언제 끝나?", "충전 몇 분 남았어", "80퍼까지 얼마나 남았어",
        // 여행
        "여행 몇 번 갔어", "올해 여행 몇번", "지난달 여행", "몇 키로 달렸어", "총 몇 km 달렸어?", "제일 멀리 간 데가 어디야", "가장 길었던 여행", "많이 간 곳", "자주 가는 곳 어디야",
        "강릉 갔었어?", "경포대 간 적 있어", "제주 언제 갔어?", "부산 갔었나", "마지막 여행 언제야", "최근 여행", "몇 명이 탔어", "누구랑 갔어", "가족이랑 갔어?",
        "작년 오늘 뭐 했어", "1년 전 오늘", "그날 어디 있었어", "오늘 같은 날 여행", "지난 여행 보여줘", "여행 기록 열어",
        // 일정 · 내비
        "어디까지 왔어", "다음 정류장 어디야", "다음은 어디", "내 일정 진행 상황", "일정 뭐 남았어", "다음 정류장으로 가자", "다음 정류장 안내", "다음으로 안내해", "정류장 안내 시작",
        "도착하면 배터리 몇 퍼야", "도착 배터리", "도착하면 충전 필요해?", "얼마나 남았어", "언제 도착해", "목적지 어디야", "지금 어디로 가고 있어", "충전소 들러야 해?", "충전소 넣어줘", "다음 휴게소 들러",
        "내 일정 열어줘", "마이 트립 보여줘", "일정 보여줘", "여행 일정",
        // 테마 · 아트 · 앨범 · 뮤직
        "지금 테마 뭐야", "무슨 테마 쓰고 있어", "테마 바꿔줘", "테마 추천", "어울리는 테마 골라줘", "그걸로 적용해", "적용", "테마 열어", "테마 보여줘",
        "곡 틀어", "노래 틀어줘", "음악 틀어", "분위기 맞는 곡", "곡 바꿔", "다음 곡", "멈춰", "음악 꺼", "조용히 해", "지금 무슨 노래야", "뭐 틀고 있어", "이 곡 뭐야",
        "앨범 열어", "사진 보여줘", "사진 몇 장 있어", "내 사진", "아트 보여줘", "작품 열어", "걸어둔 작품 뭐야", "아트 뭐 걸었어",
        "내려받은 거", "뭐 받았어", "다운받은 거 뭐야", "내 콘텐츠",
        // 캘린더
        "약속 뭐 있어", "다음 약속", "오늘 일정 뭐야", "일정 알려줘", "약속 장소로 안내", "약속 장소 가자", "캘린더 열어",
        // 차 · 기능 · 데이터
        "배터리 얼마야", "지금 배터리", "몇 키로 갈 수 있어", "주행 가능 거리", "외기온 몇 도야", "실내 온도", "날씨 어때", "비 와?", "몇 시야", "지금 시간",
        "외기온 쓰는 기능", "충전 경유 제안 뭐 필요해", "경고등 왜 못 봐", "못 읽는 데이터", "만들 수 있는 기능", "시그니처 송 언제 나와", "시그니처 송 바꿔", "목소리 바꿔줘", "데이터 화면 열어", "기능 목록",
        // 인사 · 잡담
        "안녕", "안녕 이온", "하이", "고마워", "땡큐", "수고", "잘자", "너 누구야", "넌 뭐 할 수 있어", "도와줘", "뭐 해줄 수 있어", "이온아", "ION",
        "충전 화면 열어", "충전하는 동안 뭐 할까", "쉼터 열어", "홈으로", "닫아", "그만",
    )

    /** 두 번째 100개: 다른 말투 · 줄임말 · 오타 비슷한 말 · 섞인 질문. */
    val bank2 = listOf(
        "충전 몇 번이나 했어", "충전 기록", "충전 이력 보여줘", "10월에 충전 몇 번", "강릉에서 충전했어?", "충전 제일 오래 걸린 데", "충전 평균", "분당 몇 퍼 올라", "지금 충전 속도", "충전 끝나려면",
        "완충까지", "충전 다 됐어?", "충전 중이야?", "배터리 몇 프로", "배터리 남은 거", "잔량", "몇 퍼센트야", "주행거리", "얼마나 더 갈 수 있어", "항속거리",
        "여행 기록", "여행 통계", "올해 몇 km", "9월 여행", "11월에 어디 갔어", "제주 몇 번 갔어", "강릉 마지막으로 간 게 언제야", "서귀포 갔었지", "가장 최근 여행지", "첫 여행",
        "몇 명 탔었어", "평균 몇 명", "제일 많이 탄 날", "작년 이맘때", "2년 전 오늘", "오늘 날짜에 뭐 했어", "그때 들은 곡", "그날 노래 틀어줘", "그날 음악", "추억 틀어줘",
        "내 일정", "진행 중인 일정", "몇 번째 정류장이야", "남은 정류장", "다음 정류장 이름", "다음 정류장 가자", "안내 시작", "다음 데로 가", "경로 안내", "정류장 건너뛰어",
        "도착 예상", "도착할 때 배터리", "충전 없이 갈 수 있어?", "도착하면 몇 퍼", "몇 분 남았어", "도착 시간", "남은 거리", "어디로 가는 중이야", "목적지", "지금 경로",
        "테마 뭐 쓰고 있어", "현재 테마", "테마 바꿔", "다른 테마 추천", "오늘 어울리는 테마", "그 테마 적용", "적용해줘", "테마 목록", "디스플레이 테마 열어", "밤 테마",
        "노래 틀어", "음악 켜", "곡 추천", "노래 추천해줘", "다른 노래", "이 노래 말고", "노래 꺼", "음악 멈춰", "지금 곡", "무슨 음악이야",
        "앨범", "사진 열어", "사진 몇 장", "내 사진 보여줘", "아트", "작품 보여줘", "걸어둔 아트", "지금 작품 뭐야", "받은 거 뭐 있어", "내가 받은 테마",
        "약속", "약속 몇 개", "다음 일정 뭐야", "일정 몇 개 있어", "약속 장소 안내해줘", "약속 장소로 가자", "캘린더", "외기온", "실내 온도 몇 도", "밖에 몇 도야",
        "날씨", "지금 몇 시", "시간", "이온", "ion 안녕", "고맙다", "굿", "잘했어", "넌 누구니", "뭐 할 줄 알아",
        "도움말", "뭐 물어볼 수 있어", "시그니처 송", "목소리", "닿는 데이터", "기능 몇 개", "안 되는 기능", "경고등", "충전소 찾아줘", "휴게소 가자",
        // 비교(제안 5)
        "지난달보다 많이 갔어?", "이번 달 충전 지난달보다 많아?", "작년보다 멀리 갔어?", "작년에 비해 여행 어때", "평소보다 자주 가?", "여기 충전 평소보다 느려?", "이 충전소 빨라?", "지금 충전 속도 평소랑 비교해줘",
    )

    /** 빈 상황: 서 있고 · 내비 없음 · 받은 것 없음 · 기록 없음 · 충전 잰 적 없음. 모르면 "없어요"라고 해야 해요. */
    private val emptyLib = LibraryState()
    private val emptyUi = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED, car = CarSnapshot(soc = 42, rangeKm = 160f), trips = emptyList(), nav = NavState(), weather = null, chargeMinLeft = null, nowPlaying = null)

    private fun answer(q: String, empty: Boolean = false): Pair<String, String> {
        Conversation.command(q)?.let { return "명령" to it.toString() }
        val sa = if (empty) StudioAnswers(cat, emptyLib, emptyList(), null, ChargeHistory(), today, emptyUi.nav, null, null, { LocalTime.of(13, 5) }, emptyUi.car, false)
        else StudioAnswers(cat, lib, trips, ui.nowPlaying, charges, today, ui.nav, ui.weather, ui.chargeMinLeft, { LocalTime.of(13, 5) }, ui.car.copy(outsideTempC = 12f, cabinTempC = 21f), true)
        sa.answer(q)?.let { return "기록" to it }
        OntologyAnswers(onto).answer(q)?.let { return "ION-Link" to it }
        return when (val c = VoiceCommands.route(q, (if (empty) emptyList() else trips).mapNotNull { it.destName })) {
            null -> "모름" to ""
            Command.Close -> "음성" to "[닫기]"
            Command.Today -> "음성" to "[홈]"
            is Command.Gleo -> "음성" to "[${c.action}${c.place?.let { " · $it" } ?: ""}]"
        }
    }

    @Test fun run() {
        val sb = StringBuilder("# 질문 은행 ${bank.size}개 (상황: 충전 중 · 일정 진행 · 내비 안내 중 · 기록 4건)\n\n| 질문 | 길 | 답 |\n|---|---|---|\n")
        var unknown = 0
        bank.forEach { q -> val (via, a) = answer(q); if (via == "모름") unknown++; sb.append("| $q | $via | ${a.replace("|", "｜")} |\n") }
        File("build/qna-bank.md").writeText(sb.toString())
        println("QNA_BANK ${bank.size} questions · 모름 $unknown")
    }

    @Test fun run2() {
        val sb = StringBuilder("# 질문 은행 2 · ${bank2.size}개 (왼쪽: 가득한 상황 · 오른쪽: 빈 상황)\n\n| 질문 | 길 | 답 | 빈 상황 길 | 빈 상황 답 |\n|---|---|---|---|---|\n")
        var unknown = 0
        bank2.forEach { q ->
            val (via, a) = answer(q); val (via2, a2) = answer(q, empty = true)
            if (via == "모름") unknown++
            sb.append("| $q | $via | ${a.replace("|", "｜")} | $via2 | ${a2.replace("|", "｜")} |\n")
        }
        File("build/qna-bank2.md").writeText(sb.toString())
        println("QNA_BANK2 ${bank2.size} questions · 모름 $unknown")
    }

    /** 은행 3: 차에서 실제로 들은 말(`tools/pull_heard.sh`가 `src/test/resources/heard-bank.txt`로 가져온 것). 파일이 없으면 건너뛰어요. */
    @Test fun run3() {
        val f = File("src/test/resources/heard-bank.txt")
        if (!f.exists()) { println("QNA_BANK3 none (tools/pull_heard.sh 로 가져와요)"); return }
        val heard = f.readLines().mapNotNull { app.roadmemory.car.brain.HeardLine.decode(it) }
        val sb = StringBuilder("# 질문 은행 3 · 차에서 들은 말 ${heard.size}개\n\n| 들은 말 | 푼 문장 | 그때 | 지금 길 | 지금 답 |\n|---|---|---|---|---|\n")
        var unknown = 0
        heard.forEach { h ->
            val (via, a) = answer(h.resolved)
            if (via == "모름") unknown++
            sb.append("| ${h.heard} | ${h.resolved} | ${if (h.understood) "답함" else "모름"} | $via | ${a.replace("|", "｜")} |\n")
        }
        File("build/qna-bank3.md").writeText(sb.toString())
        println("QNA_BANK3 ${heard.size} heard · 지금도 모름 $unknown")
    }
}
