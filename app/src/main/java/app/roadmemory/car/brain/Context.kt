package app.roadmemory.car.brain

/**
 * 한 턴 기억: 직전 질문에서 들은 장소 · 때 · 곡 · 테마 · 정류장을 들고 있다가, 다음 말의 "거기 · 그때 · 그거 · 그럼 지난달은?"을 풀어요.
 * 기기 안의 값만 쓰고(저장하지 않아요), 한 턴 전만 기억해요. LLM이 아니라 글자 바꿔 쓰기예요: 푼 문장이 평소의 답변 순서(명령 → 기록 질문 → ION-Link → 음성 명령)로 들어가요.
 */
data class ChatContext(
    /** 직전에 푼 질문(대명사를 풀고 난 문장). */
    val heard: String = "",
    val place: String? = null,
    /** 이번 달 · 지난달 · 올해 · 작년 · N월 */
    val scope: String? = null,
    val track: String? = null,
    val theme: String? = null,
    val stop: String? = null,
)

/** 대명사를 풀 때 맞춰 볼 이름들(저장한 목적지 · 곡 · 테마 · 정류장 · 약속 장소). */
data class ContextNames(
    val dests: List<String> = emptyList(), val tracks: List<String> = emptyList(), val themes: List<String> = emptyList(),
    val stops: List<String> = emptyList(), val places: List<String> = emptyList(),
)

object ContextResolver {
    private val SCOPE = Regex("이번달|지난달|올해|작년|\\d{1,2}월")

    /** 들은 말의 대명사 · 생략을 직전 턴으로 채워요. 채울 게 없으면 그대로 돌려줘요. */
    fun rewrite(heard: String, ctx: ChatContext): String {
        var h = heard.trim()
        val t = h.replace(" ", "")
        if (t.isEmpty()) return heard
        // "그럼 지난달은?" · "올해는?" · "10월엔?" — 직전 질문을 때만 바꿔 다시 물어요
        Regex("^(그럼|그러면|그리고)?(이번달|지난달|올해|작년|\\d{1,2}월)(은|는|엔|에는|에선|에서는)?\\??$").find(t)?.let { m ->
            if (ctx.heard.isNotEmpty()) return (m.groupValues[2] + " " + stripScope(ctx.heard)).trim()
        }
        // "그럼 강릉은?" · "제주에서는?" — 직전 질문을 장소만 바꿔 다시 물어요
        Regex("^(그럼|그러면|그리고)?([가-힣A-Za-z]{2,})(은|는|엔|에는|에선|에서는)\\??$").find(t)?.let { m ->
            val p = m.groupValues[2]
            if (ctx.heard.isNotEmpty() && p !in setOf("이번달", "지난달", "올해", "작년") && ctx.place != null && ctx.place != p) {
                return ctx.heard.replace(ctx.place, p)
            }
        }
        // 거기 · 그곳 → 장소
        if (ctx.place != null && Regex("거기|그곳|그동네").containsMatchIn(t)) h = h.replace(Regex("\\s*(거기|그곳|그동네)"), " ${ctx.place}").trim()
        // 그때 → 때(직전 질문의 이번 달 · 지난달 · N월)
        if (ctx.scope != null && Regex("그때|그\\s?달|그해").containsMatchIn(t) && !t.contains("그때들은") && !t.contains("그때곡")) h = h.replace(Regex("\\s*(그때|그\\s?달|그해)"), " ${ctx.scope}").trim()
        // 그거 · 그 곡 · 그 노래 (틀어) → 곡 이름
        if (ctx.track != null && Regex("(그거|그곡|그노래|그음악|이거|아까그|아까꺼|아까거|방금그)").containsMatchIn(t) && Regex("틀|재생|들려").containsMatchIn(t)) {
            h = "${ctx.track} 틀어 줘"
        }
        // 그 정류장으로 안내 · 거기로 가자 → 정류장 이름
        if ((ctx.stop != null || ctx.place != null) && Regex("(그정류장|그곳|거기|그리|그쪽)(으로|로)?(안내|가자|가줘|가)").containsMatchIn(t)) {
            h = "${ctx.stop ?: ctx.place}으로 안내해 줘"
        }
        return h
    }

    /** 푼 질문과 답에서 다음 턴에 쓸 것을 적어 둬요. 새로 나온 것만 바꾸고, 없는 자리는 그대로 둬요. */
    fun update(ctx: ChatContext, heard: String, reply: String, names: ContextNames): ChatContext {
        val h = heard.replace(" ", ""); val r = reply.replace(" ", "")
        val place = nameIn(h, names.dests) ?: nameIn(r, names.dests) ?: nameIn(h, names.places) ?: nameIn(r, names.places)
        val scope = SCOPE.find(h)?.value?.replace("이번달", "이번 달")
        val track = nameIn(r, names.tracks) ?: nameIn(h, names.tracks)
        val theme = nameIn(r, names.themes)
        val stop = nameIn(r, names.stops) ?: nameIn(h, names.stops)
        return ChatContext(
            heard = heard,
            place = place ?: ctx.place,
            scope = scope ?: ctx.scope,
            track = track ?: ctx.track,
            theme = theme ?: ctx.theme,
            stop = stop ?: ctx.stop,
        )
    }

    private fun stripScope(q: String): String = q.replace(Regex("(그럼|그러면)?\\s*(이번 달|이번달|지난달|올해|작년|\\d{1,2}월)(에는|에|엔)?\\s*"), "").trim()

    /** 글에 들어 있는 이름(띄어쓰기 무시 · 영문은 대소문자 무시) 가운데 가장 긴 것. 목적지는 낱말("제주 서귀포"의 "제주")로도 맞춰요. */
    private fun nameIn(t: String, names: List<String>): String? {
        val tl = t.lowercase()
        val whole = names.filter { it.length >= 2 }.sortedByDescending { it.length }.firstOrNull { tl.contains(it.replace(" ", "").lowercase()) }
        if (whole != null) return whole
        return names.flatMap { it.split(" ") }.filter { it.length >= 2 }.sortedByDescending { it.length }.firstOrNull { tl.contains(it.lowercase()) }
    }
}

/** 주행 중 짧게: 첫 토막만 말하고 나머지는 서면 말해요. 거절 · 한 토막짜리 답은 그대로예요. */
object Brevity {
    private const val MORE = " · 서면 더 말할게요"

    fun forDriving(reply: String, driving: Boolean): String {
        if (!driving) return reply
        val first = reply.split(" · ").first().split(Regex("(?<=요)\\.\\s+|(?<=요)\\s+(?=[가-힣])")).first().trim()
        return if (first.length < reply.trim().length) first.trimEnd('.') + MORE else reply
    }

    /** 주행 중 한 토막으로 줄인 뒤, 주행 규제가 글 길이를 제한하면(LIMIT_STRING_LENGTH) 그 길이에서 한 번 더 끊어요. */
    fun forDriving(reply: String, driving: Boolean, ux: app.roadmemory.car.data.UxLimit): String =
        app.roadmemory.car.data.UxRules.clip(forDriving(reply, driving), ux)
}
