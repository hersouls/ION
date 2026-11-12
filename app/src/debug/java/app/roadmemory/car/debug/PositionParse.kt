package app.roadmemory.car.debug

import app.roadmemory.car.family.FamilyPlace
import app.roadmemory.car.family.FamilyRead
import org.json.JSONObject

/**
 * 위치 서비스의 "지금 상태" 답에서 허용 목록(alias)에 있는 사람만 골라요. 순수 함수라서 단위 테스트로 재요.
 *  · 기기마다 person(누구의 폰인지)과 fix(위도 · 경도 · 받은 시각), now(머무는 중 · 이동 중, 머무는 장소)가 와요.
 *  · 이 앱을 쓰는 사람(selfAlias)의 기기는 목록에 넣지 않아요(자기 자신에게 길 안내를 할 수는 없어요).
 *  · 숨긴 기기, 위치 받기를 끈(paused) 기기, 위치가 아직 없는 기기는 건너뛰어요. 한 사람의 기기가 여럿이면 가장 최근 위치를 써요.
 *  · 받은 지 얼마인지는 서버가 준 now(밀리초)와 fix.t로 재요(기기 시계가 틀려도 맞아요).
 *  · 장소 이름은 서버가 이미 아는 이름 붙은 장소(집 · 회사 …)만 써요. 좌표를 밖에 보내 이름을 알아내지 않아요.
 */
internal object PositionParse {
    fun people(body: String, alias: Map<String, String>, selfAlias: String? = null): FamilyRead {
        val root = runCatching { JSONObject(body) }.getOrElse { return FamilyRead.Failed("답을 읽지 못했어요") }
        val devices = root.optJSONArray("devices") ?: return FamilyRead.Failed("기기 목록이 없어요")
        val serverNow = root.optLong("now", 0L)

        val best = HashMap<String, FamilyPlace>()
        for (i in 0 until devices.length()) {
            val d = devices.optJSONObject(i) ?: continue
            val person = d.optString("person", "")
            val name = alias[person] ?: continue
            if (name == selfAlias) continue
            if (d.optBoolean("hidden", false) || d.optBoolean("paused", false)) continue
            val fix = d.optJSONObject("fix") ?: continue
            if (!fix.has("lat") || !fix.has("lng")) continue
            val lat = fix.optDouble("lat", Double.NaN)
            val lng = fix.optDouble("lng", Double.NaN)
            if (lat.isNaN() || lng.isNaN()) continue
            val t = fix.optLong("t", 0L)
            val now = d.optJSONObject("now")
            val moving = now?.optString("state") == "move"
            val place = if (moving) null else now?.optJSONObject("place")?.optString("name")?.takeIf { it.isNotBlank() }
            val age = if (serverNow > 0 && t > 0) ((serverNow - t) / 1000).coerceAtLeast(0) else null
            val candidate = FamilyPlace(name, lat, lng, place, moving, age)
            val old = best[name]
            if (old == null || (candidate.ageSec ?: Long.MAX_VALUE) < (old.ageSec ?: Long.MAX_VALUE)) best[name] = candidate
        }
        // 화면 순서는 별칭 표의 순서예요
        val ordered = alias.values.distinct().filter { it != selfAlias }.mapNotNull { best[it] }
        return if (ordered.isEmpty()) FamilyRead.Nobody else FamilyRead.Ready(ordered)
    }
}
