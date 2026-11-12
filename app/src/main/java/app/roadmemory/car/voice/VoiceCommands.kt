package app.roadmemory.car.voice

import app.roadmemory.car.data.TripBook
import app.roadmemory.car.gleo.GleoBridge

/** 들은 말이 이 앱에서 하는 일. */
sealed interface Command {
    /** Gleo가 부르는 함수와 같은 것(GleoBridge). 같은 규칙 · 같은 응답으로 이어져요. */
    data class Gleo(val action: String, val place: String? = null) : Command

    /** 오늘 화면(지금 모드의 화면)으로 돌아가요. */
    data object Today : Command

    /** ION 창을 닫아요. */
    data object Close : Command
}

/**
 * 글 → 일. 시연용 규칙이에요(말의 뜻을 이해하는 모델이 아니라 낱말을 봐요).
 * 실제 차에서 말을 알아듣고 함수를 고르는 일은 Gleo가 해요. 이 규칙은 앱 안의 마이크 버튼이 같은 함수에 닿게 하는 다리예요.
 */
object VoiceCommands {
    /** 말하기 예시(화면의 칩). 하나씩 눌러도 같은 길로 가요. */
    val EXAMPLES = listOf("이 길의 기억 틀어 줘", "지난 여행 보여 줘", "도착하면 배터리 얼마야?", "충전 화면 열어 줘")

    private val closeWords = listOf("닫아", "그만", "취소", "됐어")
    private val todayWords = listOf("오늘", "홈", "처음화면", "홈화면", "돌아가")
    private val loungeWords = listOf("쉼터", "라운지", "충전화면", "충전하는동안", "충전중에")
    private val stopWords = listOf("도착", "경유", "넣어", "넣을", "들러", "들를", "들릴", "가야", "해야", "모자", "부족", "충분", "갈수있", "닿")
    private val memoryWords = listOf("기억", "추억", "지난여행", "여행", "기록", "노래", "음악", "곡")

    /** [places]는 다녀온 곳 이름들(기본은 이 기기에 저장된 여행). 저장된 여행이 없으면 장소를 알아듣지 않아요. */
    fun route(heard: String, places: List<String> = TripBook.placeNames()): Command? {
        val t = normalize(heard)
        if (t.isEmpty()) return null
        return when {
            has(t, closeWords) -> Command.Close
            has(t, todayWords) && !has(t, memoryWords) -> Command.Today
            has(t, loungeWords) -> Command.Gleo(GleoBridge.OPEN_CHARGING_LOUNGE)
            t.contains("충전소") || t.contains("휴게소") || ((t.contains("충전") || t.contains("배터리")) && has(t, stopWords)) -> Command.Gleo(GleoBridge.SUGGEST_CHARGING_STOP)
            t.contains("충전") -> Command.Gleo(GleoBridge.OPEN_CHARGING_LOUNGE)
            has(t, memoryWords) || placeIn(t, places) != null -> Command.Gleo(GleoBridge.SHOW_ROAD_MEMORY, placeIn(t, places))
            else -> null
        }
    }

    /** 공백 · 문장 부호를 없애고 소문자로. 받아 적은 글은 띄어쓰기가 제각각이에요. */
    internal fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    private fun has(t: String, words: List<String>) = words.any { t.contains(it) }

    /** 다녀온 곳(저장된 여행의 목적지) 이름이 들어 있으면 그 이름. */
    private fun placeIn(t: String, places: List<String>): String? = places.firstOrNull { name -> normalize(name).let { it.isNotEmpty() && t.contains(it) } }
}
