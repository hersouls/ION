package app.roadmemory.car.debug

import android.content.Context
import org.json.JSONArray

/**
 * 디버그 전용: 가족 위치를 읽어 올 곳과, 화면에 보일 별칭 표. 소스에는 주소도 이름도 없고, 실행 중에 [FamilyDebugReceiver]가
 * 이 기기(에뮬레이터)의 앱 저장소에만 넣어요. alias 표의 앞은 위치를 주는 쪽의 이름, 뒤가 화면에 보이는 별칭 · 역할이에요.
 * 표에 없는 사람의 위치는 읽지 않아요(허용 목록). selfAlias는 이 앱을 쓰는 사람(운전자)의 별칭이고, 그 사람의 위치는 목록에 넣지 않아요.
 */
data class FamilyConfig(val baseUrl: String, val alias: LinkedHashMap<String, String>, val selfAlias: String? = null) {
    fun save(ctx: Context) {
        prefs(ctx).edit()
            .putString(KEY_URL, baseUrl)
            .putString(KEY_SELF, selfAlias)
            .putString(KEY_ALIAS, JSONArray(alias.map { (k, v) -> JSONArray(listOf(k, v)) }).toString())
            .apply()
    }

    companion object {
        private const val PREFS = "family_link"
        private const val KEY_URL = "base_url"
        private const val KEY_ALIAS = "alias"
        private const val KEY_SELF = "self"

        private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        fun load(ctx: Context): FamilyConfig? {
            val sp = prefs(ctx)
            val url = sp.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: return null
            val raw = sp.getString(KEY_ALIAS, null) ?: return null
            return runCatching { FamilyConfig(url, parseAlias(raw), sp.getString(KEY_SELF, null)?.takeIf { it.isNotBlank() }) }.getOrNull()?.takeIf { it.alias.isNotEmpty() }
        }

        fun clear(ctx: Context) { prefs(ctx).edit().clear().apply() }

        /** [["실명","별칭"], …] → 같은 순서의 표(화면 순서가 돼요). 비어 있는 칸은 버려요. */
        fun parseAlias(json: String): LinkedHashMap<String, String> {
            val a = JSONArray(json)
            val out = LinkedHashMap<String, String>()
            for (i in 0 until a.length()) {
                val pair = a.optJSONArray(i) ?: continue
                val k = pair.optString(0).trim()
                val v = pair.optString(1).trim()
                if (k.isNotEmpty() && v.isNotEmpty()) out[k] = v
            }
            return out
        }
    }
}
