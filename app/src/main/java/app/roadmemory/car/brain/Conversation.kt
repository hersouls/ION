package app.roadmemory.car.brain

import app.roadmemory.car.data.DemoUser
import app.roadmemory.car.Pane
import app.roadmemory.car.UiState
import app.roadmemory.car.data.Weather
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.studio.Catalog
import app.roadmemory.car.studio.LibraryState
import app.roadmemory.car.ui.TripRun
import java.time.LocalDate

/** ION 칸의 말로 시키는 일(명령 규칙보다 먼저 봐요). */
sealed interface ChatCommand {
    data object NextStop : ChatCommand
    data object PlayMusic : ChatCommand
    data object StopMusic : ChatCommand
    data object ThatDayMusic : ChatCommand
    data object RecommendTheme : ChatCommand
    data object ApplyRecommendedTheme : ChatCommand
    /** 지금 곡 말고 다른 곡. */
    data object NextTrack : ChatCommand
    /** 좌표 있는 첫 약속 장소로 내비 안내. */
    data object GuideAppointment : ChatCommand
    /** 이름을 말한 곡 틀기("Solcho Sea Loop 틀어 줘" · "그거 틀어 줘"를 푼 것). */
    data class PlayTrack(val title: String) : ChatCommand
    /** 이름을 말한 곳으로 내비 안내(정류장 · 약속 장소 · 저장한 목적지). 좌표를 모르면 컨트롤러가 그렇다고 말해요. */
    data class GuidePlace(val name: String) : ChatCommand
    /** 여행 기록하기: 시작 · 이어서 · 끝내기. */
    data object StartRecording : ChatCommand
    data object ResumeRecording : ChatCommand
    data object EndRecording : ChatCommand
    data class Open(val pane: String, val label: String) : ChatCommand
    /** 앨범 슬라이드쇼 시작(start = true) 또는 멈춤(false). 서 있을 때만 틀어요. */
    data class Slideshow(val start: Boolean) : ChatCommand
}

/**
 * ION 대화 화면의 규칙(하이엔드의 규칙 버전): 상황에 맞는 첫마디 · 지금 할 수 있는 말(칩) · 말로 시키는 일 · 답 뒤에 이어지는 일.
 * 모든 문장은 차가 준 값과 이 기기의 기록으로만 채워요. LLM 없이요.
 */
object Conversation {
    /** 첫마디 두 줄: 인사 + 지금 상황. 값이 없는 것은 말하지 않아요. */
    fun greeting(ui: UiState, hour: Int, cat: Catalog?, lib: LibraryState, today: LocalDate = LocalDate.now()): List<String> {
        val hello = when {
            hour in 5..10 -> "${DemoUser.CALL}, 좋은 아침이에요!"
            hour in 11..16 -> "${DemoUser.CALL}, 안녕하세요!"
            hour in 17..21 -> "${DemoUser.CALL}, 좋은 저녁이에요!"
            else -> "늦은 시간이네요, ${DemoUser.CALL}. 안녕하세요!"
        }
        val now = buildList {
            ui.car.soc?.let { soc -> add("배터리 $soc%" + (ui.car.rangeKm?.takeIf { it > 0f }?.let { " · ${Math.round(it)}km 갈 수 있어요" } ?: "")) }
            when (ui.mode) {
                Mode.CHARGING -> add(ui.chargeMinLeft?.let { "충전 중, 80%까지 약 ${it}분이에요" } ?: "충전 중이에요")
                Mode.DRIVING -> add("달리는 중이라 꼭 필요한 것만 말할게요")
                Mode.PARKED -> Unit
            }
            ui.weather?.let { if (it == Weather.RAIN || it == Weather.SNOW || it == Weather.FOG) add("밖은 ${it.label}이에요. 천천히 가요") }
            val run = TripRun.current(lib); val trip = cat?.trips?.firstOrNull { it.id == run?.tripId }
            if (run != null && trip != null) trip.stops.getOrNull(run.stopIndex + 1)?.let { add("가던 일정의 다음 정류장은 ${Korean.ieyo(it.name)}") }
            else if (ui.nav.routeActive && !ui.nav.destinationName.isNullOrBlank()) add("${Korean.euro(ui.nav.destinationName!!)} 가는 길이에요" + (ui.nav.estimatedSocAtDest?.let { " · 도착하면 배터리 $it%" } ?: ""))
            Memories.thatDay(ui.trips, today).firstOrNull()?.let { add(Memories.sentence(it)) }
        }
        val line2 = if (now.isEmpty()) "ION이에요. 가는 길에 필요한 것만 알려 드릴게요." else "ION이에요. " + now.take(2).joinToString(". ") { it.trimEnd('.') } + "."
        return listOf(hello, line2)
    }

    /** 지금 할 수 있는 말 가운데 앞의 서너 개(상황에 맞는 것이 먼저). 화면은 [rotate]로 이 풀에서 돌려 보여 줘요. */
    fun chips(ui: UiState, cat: Catalog?, lib: LibraryState): List<String> = chipPool(ui, cat, lib).take(4)

    /** 풀에서 [n]개를 골라요. [tick]이 바뀌면 다른 묶음이에요(같은 묶음이 연달아 나오지 않게 해요). 풀의 첫 칩(가장 급한 말)은 늘 들어가요. */
    fun rotate(pool: List<String>, tick: Int, n: Int): List<String> {
        if (pool.size <= n) return pool
        val head = pool.first()
        val rest = pool.drop(1)
        val rnd = java.util.Random(tick.toLong() * 7919L + 17L)
        val picked = rest.shuffled(rnd).take(n - 1)
        return listOf(head) + picked
    }

    /** 지금 할 수 있는 말의 풀. 상황에 맞는 것이 앞에, 언제나 물을 수 있는 것이 뒤에 와요. */
    fun chipPool(ui: UiState, cat: Catalog?, lib: LibraryState): List<String> = buildList {
        val run = TripRun.current(lib)
        if (run != null) { add("다음 정류장 안내해 줘"); add("내 일정 어디까지 왔어?") }
        if (ui.mode == Mode.CHARGING) {
            if (lib.has("sync_${Pane.SVC_MUSIC}")) add(if (ui.nowPlaying == null) "곡 틀어 줘" else "곡 멈춰 줘")
            if (lib.has("sync_${Pane.SVC_ALBUM}")) add("앨범 보여 줘")
            add("충전하는 동안 뭐 할까")
        }
        if (ui.nav.routeActive) add("도착하면 배터리 얼마야?")
        if (lib.appliedId(Pane.SVC_THEME) == null && cat != null && cat.themes.isNotEmpty()) add("테마 추천해 줘")
        if (ui.trips.isNotEmpty()) { add("충전 몇 번 했어?"); add("가장 멀리 간 곳은?") }
        // 언제나 물을 수 있는 말
        addAll(listOf("이 길의 기억 틀어 줘", "지난 여행 보여 줘", "외기온을 쓰는 기능은?", "충전 경유 제안에는 뭐가 필요해?", "지금 무슨 테마야?", "내려받은 거 뭐 있어?", "충전 화면 열어 줘", "만들 수 있는 기능은?"))
        if (ui.trips.isNotEmpty()) addAll(listOf("누구랑 제일 많이 갔어?", "작년 오늘은?", "제일 많이 간 곳은?"))
        if (cat != null && cat.themes.isNotEmpty()) add("테마 보여 줘")
        if (lib.has("sync_${Pane.SVC_ALBUM}")) add("앨범 보여 줘")
    }.distinct()

    /** 말에서 시킬 일을 찾아요. 없으면 null(그다음 질문 · 명령 규칙이 봐요). */
    fun command(text: String): ChatCommand? {
        val t = text.replace(" ", "").removePrefix(LINK)
        fun has(vararg k: String) = k.any { t.contains(it) }
        return when {
            // "슬라이드쇼 멈춰"가 곡 멈춤(멈춰)으로 새지 않게 맨 앞에서 먼저 봐요
            has("슬라이드쇼", "슬라이드", "사진돌려", "앨범돌려", "사진넘겨줘", "앨범넘겨") -> ChatCommand.Slideshow(start = !has("멈", "꺼", "그만", "중지", "끝", "종료"))
            (has("다음정류장") && has("안내", "가자", "가줘", "출발", "으로가", "로가")) || has("다음으로안내", "정류장안내시작", "다음정류장출발", "다음데로", "다음으로가") || t in setOf("안내시작", "경로안내", "경로안내시작") -> ChatCommand.NextStop
            has("약속장소") && has("안내", "가자", "가줘") -> ChatCommand.GuideAppointment
            has("기록끝", "기록그만", "기록멈", "기록종료", "기록마쳐", "기록마무리", "기록저장") -> ChatCommand.EndRecording
            has("이어서기록", "기록이어") -> ChatCommand.ResumeRecording
            has("여행기록해", "여행기록시작", "기록시작", "기록해줘", "기록해봐", "이번여행기록", "기록하기") -> ChatCommand.StartRecording
            trackIn(t)?.takeIf { has("틀", "재생", "들려") } != null -> ChatCommand.PlayTrack(trackIn(t)!!)
            has("다음곡", "곡바꿔", "노래바꿔", "다른곡", "다른노래", "노래말고", "곡말고", "다음노래") -> ChatCommand.NextTrack
            has("그때곡", "그때들은", "그날곡", "그날노래", "그날음악", "그날들은", "추억틀", "그때노래", "그때음악") -> ChatCommand.ThatDayMusic
            has("곡멈", "음악멈", "노래멈", "그만틀", "멈춰", "음악꺼", "노래꺼", "곡꺼", "조용히") -> ChatCommand.StopMusic
            has("곡틀", "음악틀", "노래틀", "분위기맞는곡", "분위기에맞는곡", "곡추천", "노래추천", "음악켜", "노래켜", "음악재생", "곡재생") -> ChatCommand.PlayMusic
            has("그테마적용", "테마적용", "적용해줘", "그걸로적용", "그걸로해", "적용해") || t == "적용" -> ChatCommand.ApplyRecommendedTheme
            has("테마추천", "테마골라", "어울리는테마", "테마바꿔", "테마바꾸", "다른테마") -> ChatCommand.RecommendTheme
            has("앨범보여", "사진보여", "앨범열", "사진열", "앨범켜") || t in setOf("앨범", "디지털앨범", "사진") -> ChatCommand.Open(Pane.SVC_ALBUM, "디지털 앨범")
            has("내일정보여", "마이트립열", "일정열어", "마이트립보여", "마이트립", "여행일정보여", "담은일정") || (has("일정보여") && !has("오늘일정")) -> ChatCommand.Open(Pane.SVC_TRIP, "마이 트립")
            has("테마열어", "테마보여", "테마목록", "밤테마", "야간테마", "테마고르") || t in setOf("테마", "디스플레이테마") -> ChatCommand.Open(Pane.SVC_THEME, "디스플레이 테마")
            (has("충전화면", "충전기록", "충전이력") && has("열", "보여")) || t == "충전화면" -> ChatCommand.Open(Pane.CHARGE, "충전")
            has("여행기록열", "여행기록보여", "지난여행열") -> ChatCommand.Open(Pane.TRIPS, "여행 기록")
            has("시그니처송열", "시그니처송보여", "목소리묶음", "시그니처송바꿔", "목소리바꿔", "시그니처송골라") || t in setOf("시그니처송", "목소리", "목소리설정") -> ChatCommand.Open(Pane.PACK, "시그니처 송")
            has("닿는데이터", "데이터화면", "기능목록") -> ChatCommand.Open(Pane.DATA, "ION이 닿는 데이터")
            has("캘린더열", "일정목록열", "약속보여", "캘린더보여") || t in setOf("캘린더", "캘린더동기화") -> ChatCommand.Open(Pane.SVC_CALENDAR, "캘린더 동기화")
            has("뮤직열", "내곡보여", "곡목록") || t in setOf("뮤직", "디지털뮤직", "음악") -> ChatCommand.Open(Pane.SVC_MUSIC, "디지털 뮤직")
            has("아트열", "아트보여", "작품보여", "작품열") || t in setOf("아트", "디지털아트", "작품") -> ChatCommand.Open(Pane.SVC_ART, "디지털 아트")
            has("위치동기화열", "위치동기화보여", "가족위치열", "가족위치보여", "가족어디") -> ChatCommand.Open(Pane.SVC_LOCATION, "위치 동기화")
            else -> Regex("^([가-힣A-Za-z0-9]{2,})(으로|로)(안내해줘|안내해|안내|가자|가줘)$").find(t)?.groupValues?.get(1)
                ?.takeIf { it !in setOf("다음", "집", "거기", "그곳", "약속장소") && !it.endsWith("정류장") }?.let { ChatCommand.GuidePlace(it) }
        }
    }

    /** 말 안에 든 내 곡 이름(띄어쓰기 · 대소문자 무시). */
    private fun trackIn(t: String): String? {
        val tl = t.lowercase()
        return app.roadmemory.car.data.Tracks.all.map { it.title }.sortedByDescending { it.length }.firstOrNull { tl.contains(it.replace(" ", "").lowercase()) }
    }

    /** 링크 칩의 앞머리. 이 칩은 누르면 그 화면으로 바로 가요(명령 "…열어 줘"와 같은 길). */
    const val LINK = "→"

    /** 답에서 바로 갈 수 있는 화면(링크 칩). 질문 · 답의 주제로 골라요. */
    fun links(heard: String, reply: String, lib: LibraryState): List<String> {
        val h = heard.replace(" ", "") + reply.replace(" ", "")
        return buildList {
            if (h.contains("정류장") || h.contains("일정")) add("$LINK 마이 트립 열어 줘")
            if (h.contains("충전")) add("$LINK 충전 화면 열어 줘")
            if (h.contains("테마")) add("$LINK 테마 보여 줘")
            if (h.contains("시그니처송") || h.contains("목소리")) add("$LINK 시그니처 송 열어 줘")
            if (h.contains("여행") && !h.contains("일정")) add("$LINK 여행 기록 열어 줘")
            if (h.contains("기능") || h.contains("데이터") || h.contains("권한")) add("$LINK 닿는 데이터 열어 줘")
            if (h.contains("사진") || h.contains("앨범")) add("$LINK 앨범 보여 줘")
            if (h.contains("곡") && lib.has("sync_${Pane.SVC_MUSIC}")) add("$LINK 뮤직 열어 줘")
            if (h.contains("아트") || h.contains("작품")) add("$LINK 아트 보여 줘")
            if (h.contains("약속")) add("$LINK 캘린더 열어 줘")
        }.distinct().filterNot { reply.contains("화면을 열었어요") && command(it) == command(heard) }.take(2)
    }

    /** 답 뒤에 이어 붙는 칩: 이어서 물을 말 한두 개 + 바로 갈 화면 링크. */
    fun followUps(heard: String, reply: String, ui: UiState, lib: LibraryState, driving: Boolean = ui.mode == Mode.DRIVING): List<String> =
        (suggestions(heard, reply, ui, lib) + (if (driving) emptyList() else links(heard, reply, lib))).distinct().take(3)

    private fun suggestions(heard: String, reply: String, ui: UiState, lib: LibraryState): List<String> {
        val h = heard.replace(" ", ""); val r = reply
        return buildList {
            if (h.contains("어디까지") && r.contains("다음은")) add("다음 정류장 안내해 줘")
            if (h.contains("테마추천") && !r.contains("없어")) add("그 테마 적용해 줘")
            if (r.contains("그때 들은 곡") || (h.contains("작년오늘") && r.contains("년 전"))) add("그때 곡 틀어 줘")
            if (h.contains("충전몇번") && ui.trips.isNotEmpty()) add("가장 멀리 간 곳은?")
            if (h.contains("가장멀리") && ui.trips.isNotEmpty()) add("누구랑 제일 많이 갔어?")
            if (ui.mode == Mode.CHARGING && ui.nowPlaying == null && lib.has("sync_${Pane.SVC_MUSIC}") && h.contains("충전") && !h.contains("곡")) add("곡 틀어 줘")
        }.distinct().take(2)
    }
}
