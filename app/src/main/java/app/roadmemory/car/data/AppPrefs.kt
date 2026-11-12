package app.roadmemory.car.data

import android.content.Context

/**
 * 기본 구성 설정: 켜고 끄는 값 세 가지. 정차했을 때만 열리는 설정 화면에서 바꾸고, 이 기기(차 안 앱 저장소)에만 남아요.
 * 단위는 차의 표시 단위를 따라서 여기에 없어요.
 */
data class AppPrefs(
    /** 카드의 한 문장을 소리로도 읽어요(주행 중에도 한 문장만). */
    val voiceGuide: Boolean = true,
    /** 충전을 시작하면 ION의 충전 화면을 저절로 열어요. 끄면 서 있을 때의 ION 홈에서 직접 열어요. */
    val autoLounge: Boolean = true,
) {
    /** 이전 버전이 저장한 "음성|조용한 평일|쉼터" 모양을 그대로 지켜요(가운데 칸은 쓰지 않아요). */
    fun encode(): String = listOf(voiceGuide, true, autoLounge).joinToString("|") { if (it) "1" else "0" }

    companion object {
        /** 저장된 글에서 되살려요. 깨졌거나 없는 칸은 기본값(켜짐)이에요. */
        fun decode(raw: String?): AppPrefs {
            val p = raw?.split('|') ?: return AppPrefs()
            fun on(i: Int) = p.getOrNull(i) != "0"
            return AppPrefs(on(0), on(2))
        }
    }
}

class AppPrefsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    fun load(): AppPrefs = AppPrefs.decode(prefs.getString("basic", null))
    fun save(s: AppPrefs) { prefs.edit().putString("basic", s.encode()).apply() }

    /** 정차 중 오른쪽 칸에서 마지막으로 보던 화면: "menu"(초기 메뉴) · "memories" · "settings" · "tone". 앱을 다시 켜면 거기서부터 보여요. */
    fun lastPane(): String = prefs.getString("last_pane", null) ?: "menu"
    fun saveLastPane(pane: String) { prefs.edit().putString("last_pane", pane).apply() }

    /** 적용한 시그니처 송 팩의 id(없으면 null = 끔). 이 기기에만 남아요. */
    fun packId(): String? = prefs.getString("pack_id", null)?.takeIf { it.isNotBlank() }
    fun savePackId(id: String?) { prefs.edit().apply { if (id == null) remove("pack_id") else putString("pack_id", id) }.apply() }

    /** 이번 주(ISO 주: 월~일)에 달린 거리(km). 주가 바뀌면 0부터 다시 세요. 조용한 날 카드가 "이번 주 123km"로 말해요. */
    fun weekKm(): Int = if (prefs.getString("week_key", null) == weekKey()) prefs.getInt("week_km", 0) else 0

    /** 끝난 여행의 거리를 이번 주에 더하고, 이번 주 합계를 돌려줘요. */
    fun addTrip(km: Int): Int {
        val total = weekKm() + km.coerceAtLeast(0)
        prefs.edit().putString("week_key", weekKey()).putInt("week_km", total).apply()
        return total
    }

    private fun weekKey(): String {
        val d = java.time.LocalDate.now()
        return "${d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR)}-${d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
    }
}
