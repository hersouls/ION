package app.roadmemory.car.ui

import app.roadmemory.car.Pane
import app.roadmemory.car.studio.Look
import app.roadmemory.car.studio.Theme

/**
 * 디스플레이 테마의 카드 세트: ION 홈의 카드 열네 장(서비스 여덟 · 화면 여섯)마다 테마의 그림 한 장이 있어요.
 * 실제 블루링크 스토어의 테마는 메뉴 카드 · 홈 카드 · 프로필 · 클러스터가 한 세계관의 그림으로 바뀌어요. ION App은 시스템 영역(차의 메뉴 · 계기판)을
 * 그릴 수 없어서, 앱 창 안의 카드를 같은 방식으로 입혀요. 키는 화면 이름([Pane])이고 서비스 칸은 서비스의 화면 이름이에요.
 */
internal object ThemeCards {
    /** 화면 칸(서비스가 아닌 것). 이름은 ION 홈의 칸 이름과 같아요. */
    val screens: List<Pair<String, String>> = listOf(
        Pane.CHARGE to "충전",
        Pane.FAMILY to "가족 · 자리",
        Pane.CHECK to "점검",
        Pane.TRIPS to "여행 기록",
        Pane.DATA to "ION이 닿는 데이터",
        Pane.SETTINGS to "설정",
    )

    /** 세트가 갖춰야 하는 카드 전부: 서비스 여덟 다음에 화면 여섯, ION 홈의 차례 그대로. */
    val all: List<Pair<String, String>> get() = Services.all.map { it.pane to it.title } + screens

    /** 세트가 있는 테마인지(카드 열네 장이 다 있어야 세트로 말해요). */
    fun isSet(look: Look?): Boolean = look != null && all.all { (key, _) -> look.cards[key] != null }

    /** 세트에 빠진 카드의 화면 이름(테스트와 점검용). */
    fun missing(look: Look?): List<String> = all.map { it.first }.filter { look?.cards?.get(it) == null }
}

/**
 * 디스플레이 테마의 갈래(스토어 목록을 찾아보기 쉽게). 테마의 태그로 가려요: 야구 · 축구 · 아이돌 · 영화 · 드라마 · 예능.
 * 새 테마가 어느 갈래에도 맞지 않으면 "전체"에서만 보여요(ThemeSetTest가 지금 테마가 모두 갈래를 가졌는지 지켜요).
 */
internal enum class ThemeKind(val label: String, private val tags: Set<String>) {
    BASEBALL("야구", setOf("프로야구")),
    FOOTBALL("축구 · 풋살", setOf("축구", "풋살", "여자축구")),
    IDOL("아이돌 · 가수", setOf("아이돌", "가수", "힙합", "연습실")),
    SCREEN("영화 · 드라마 · 예능", setOf("영화", "드라마", "예능", "앨범"));

    companion object {
        fun of(t: Theme): ThemeKind? = entries.firstOrNull { k -> t.tags.any { it in k.tags } }
    }
}
