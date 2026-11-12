package app.roadmemory.car.ontology

/**
 * ION-Link에 대한 질문에 답해요(ION 대화). 말뜻을 이해하는 모델이 아니라 이름과 낱말을 보는 규칙이에요.
 * 명령("충전 쉼터 열어 줘")을 가로채지 않도록, 개념 · 기능 이름이 있어도 **질문 표지**(필요한 데이터 · 만들 수 · 쓰는 기능 …)가 있을 때만 답해요.
 * 답할 게 없으면 null을 돌려줘서 원래의 명령 규칙이 이어받아요.
 */
class OntologyAnswers(private val o: Ontology) {

    fun answer(heard: String): String? {
        val t = norm(heard)
        if (t.isEmpty()) return null
        momentAnswer(t)?.let { return it }
        return when {
            hasAny(t, UNREACHABLE) -> unreachableSummary()
            hasAny(t, FEASIBLE_ALL) -> feasibleSummary()
            else -> byName(t)
        }
    }

    // ── 시그니처 송 ──

    /**
     * "시그니처 송은 언제 나와?" · "왜 문 잠글 때는 소리가 안 나?" 같은 물음. 소리 · 목소리 · 시그니처 · 팩 같은 낱말과 물음 표지가 **둘 다** 있을 때만 답해서
     * "소리 줄여 줘" 같은 명령은 가로채지 않아요. 순간은 ION-Link의 `mo.*` 노드이고, 적용한 팩이 있으면 그 팩(`pk.*`)이 가진 순간을 알려 줘요.
     */
    private fun momentAnswer(t: String): String? {
        if (o.moments.isEmpty() || !hasAny(t, MOMENT_TOPIC) || !hasAny(t, MOMENT_ASK)) return null
        val m = findMoment(t)
        return if (m != null) momentOne(m) else momentSummary()
    }

    private fun findMoment(t: String): Node? {
        var best: Pair<Node, Int>? = null
        for (m in o.moments) for (w in MOMENT_WORDS[m.id.removePrefix("mo.")].orEmpty()) {
            if (t.contains(w) && (best == null || w.length > best.second)) best = m to w.length
        }
        return best?.first
    }

    private fun packOf() = o.packs.firstOrNull()

    private fun momentOne(m: Node): String {
        val pack = packOf()
        val modeWord = if (m.props["mode"] == "push") "ION이 값을 지켜보다가 먼저 알려요" else "방금 한 일에 맞춰 나와요"
        if (!o.offerable(m.id)) {
            val mark = o.momentMark(m.id)
            return "「${m.label}」: ${mark.mark} 소리를 못 내요. ${o.momentReason(m.id).orEmpty().ifEmpty { "이 앱이 아직 읽지 않아요" }}."
        }
        val keys = o.needs(m.id).filter { it.role == Role.KEY && it.concept.id != "cn.signature_pack" }.joinToString(" · ") { it.concept.label }
        val has = pack?.let { if (o.packMoments(it.id).contains(m.id.removePrefix("mo."))) " 「${it.label}」에는 이 순간의 목소리가 있어요." else " 「${it.label}」에는 이 순간의 목소리가 없어요." }.orEmpty()
        return "「${m.label}」: ● ${m.props["signal"]} 나와요($modeWord). 쓰는 값: $keys.$has"
    }

    private fun momentSummary(): String {
        val pack = packOf()
        if (pack != null) {
            val ids = o.packMoments(pack.id)
            val labels = ids.mapNotNull { o.nodes["mo.$it"]?.label }
            val react = ids.count { o.momentMode("mo.$it") == "reaction" }
            val push = ids.count { o.momentMode("mo.$it") == "push" }
            return "${app.roadmemory.car.brain.Korean.eun("「${pack.label}」(${pack.props["speaker"]})")} ${labels.take(5).joinToString(" · ")}${if (labels.size > 5) " 외 ${labels.size - 5}개" else ""}에 나와요. " +
                "방금 한 일에 맞춰 나오는 것 ${react}개, ION이 먼저 알려 주는 것 ${push}개이고, 나올 때마다 화면에 글로도 알려요."
        }
        val ok = o.moments.filter { o.offerable(it.id) }
        return "적용한 시그니처 송이 없어요. 이 차의 신호로 알아차릴 수 있는 순간은 ${o.moments.size}개 중 ${ok.size}개예요: ${ok.take(4).joinToString(" · ") { it.label }} 외 ${(ok.size - 4).coerceAtLeast(0)}개. 설정에서 시그니처 송을 골라 보세요."
    }

    private fun byName(t: String): String? {
        val fn = findFunction(t)
        val cn = findConcept(t)
        // 기능 이름이 더 길게(구체적으로) 들어 있으면 기능 질문으로 봐요
        if (fn != null && (cn == null || fn.second >= cn.second)) {
            val f = fn.first
            return when {
                hasAny(t, NEEDS) -> needsOf(f)
                hasAny(t, FEASIBLE) -> feasibility(f)
                else -> null
            }
        }
        if (cn != null) {
            val c = cn.first
            return when {
                hasAny(t, USED_BY) -> usedBy(c)
                hasAny(t, READABLE) -> conceptStatus(c)
                else -> null
            }
        }
        return null
    }

    private fun usedBy(c: Node): String {
        val fns = o.functionsUsing(c.id)
        val st = o.statusOf(c.id)
        val head = if (fns.isEmpty()) "${eul(c.label)} 쓰는 기능은 아직 없어요." else "${eul(c.label)} 쓰는 기능은 ${fns.size}개예요: ${fns.joinToString(", ") { it.label }}."
        return "$head ${eun(c.label)} ${st.label}.${permissionSentence(c.id)}"
    }

    private fun conceptStatus(c: Node): String {
        val st = o.statusOf(c.id)
        val how = c.props["note"]?.let { " ($it)" }.orEmpty()
        return "${eun(c.label)} ${st.label}.$how${permissionSentence(c.id)}"
    }

    private fun needsOf(f: Node): String {
        val used = o.needs(f.id).filter { it.role != Role.EXTRA }
        val key = used.filter { it.role == Role.KEY }
        val sup = used.filter { it.role == Role.SUPPORT }
        fun list(xs: List<Need>) = xs.joinToString(" · ") { "${it.concept.label}(${it.status.label})" }
        return buildString {
            append("「${f.label}」에는 ${app.roadmemory.car.brain.Korean.iga(list(key))} 꼭 필요해요.")
            if (sup.isNotEmpty()) append(" ${list(sup)}도 써요.")
        }
    }

    private fun feasibility(f: Node): String {
        val v = o.verdict(f.id)
        val miss = o.missing(f.id)
        val why = when (v) {
            Verdict.CAN -> "닿는 데이터로 모두 돼요."
            else -> miss.take(3).joinToString(" ") { "${app.roadmemory.car.brain.Korean.eun(it.concept.label)} ${it.status.label}." }
        }
        return "「${f.label}」: ${v.mark} ${v.label}. $why"
    }

    /** 더 선언해야 할 권한이 있으면 한 문장으로. 권한 이름의 괄호 설명은 빼요. */
    private fun permissionSentence(conceptId: String): String {
        val perms = o.permissionsToDeclare(conceptId)
        if (perms.isEmpty()) return ""
        val names = perms.joinToString(" · ") { it.label.substringBefore('(').trim() }
        val approval = perms.firstNotNullOfOrNull { it.props["approval"] }?.let { " ($it)" }.orEmpty()
        return " 권한 $names 선언이 더 필요해요$approval."
    }

    private fun unreachableSummary(): String {
        val byStatus = o.concepts.groupBy { o.statusOf(it.id) }
        fun names(s: Status) = byStatus[s].orEmpty().map { it.label }
        val outside = names(Status.OUTSIDE); val absent = names(Status.ABSENT); val blocked = names(Status.BLOCKED)
        return "이 앱(APK)이 못 읽는 데이터는 ${outside.size + absent.size + blocked.size}가지예요. " +
            "폰 · 마이현대에 있는 것 ${outside.size}가지(${outside.take(3).joinToString(", ")} 등), " +
            "이 차량에 없는 것 ${absent.size}가지(${absent.take(3).joinToString(", ")} 등), " +
            "권한이 막는 것 ${blocked.size}가지(${blocked.take(2).joinToString(", ")})예요."
    }

    private fun feasibleSummary(): String {
        val c = o.counts()
        val can = o.functions.filter { o.verdict(it.id) == Verdict.CAN }.map { it.label }
        return "ION이 하는 ${o.functions.size}가지 일 중 닿는 데이터로 만들 수 있는 것은 ${c[Verdict.CAN]}개, 일부만 되는 것 ${c[Verdict.PARTLY]}개, " +
            "이 앱만으로는 못 하는 것 ${c[Verdict.NOT]}개예요. 만들 수 있는 것: ${can.take(4).joinToString(", ")} 외 ${(can.size - 4).coerceAtLeast(0)}개."
    }

    // ── 이름 찾기: 더 긴 이름이 들어 있을수록 우선 ──
    private fun findFunction(t: String): Pair<Node, Int>? = best(o.functions, t)
    private fun findConcept(t: String): Pair<Node, Int>? = best(o.concepts, t)

    private fun best(nodes: List<Node>, t: String): Pair<Node, Int>? {
        var hit: Pair<Node, Int>? = null
        for (n in nodes) for (name in listOf(n.label) + n.aliases) {
            val k = norm(name)
            if (k.length >= 2 && t.contains(k) && (hit == null || k.length > hit.second)) hit = n to k.length
        }
        return hit
    }

    companion object {
        /** 대화창에 보여 주는 질문 예시(눌러도 같은 길로 가요). */
        val EXAMPLES = listOf("외기온을 쓰는 기능은?", "충전 경유 제안에는 뭐가 필요해?", "경고등 해설은 왜 못 해?", "이 앱이 못 읽는 데이터는?", "만들 수 있는 기능은?", "시그니처 송은 언제 나와?")

        private val MOMENT_TOPIC = listOf("시그니처", "목소리", "소리", "팩")
        private val MOMENT_ASK = listOf("언제", "왜", "안나", "나와", "나올", "나는", "못내", "어떤", "몇", "뭐", "어느")
        private val MOMENT_WORDS: Map<String, List<String>> = mapOf(
            "board" to listOf("탑승", "탔을", "탈때", "앉았을"), "start" to listOf("출발"), "door" to listOf("문열", "문을열", "트렁크"),
            "park" to listOf("주차"), "wiper" to listOf("와이퍼"), "climate" to listOf("에어컨", "히터", "온도바꿀", "온도를바꿀"),
            "charge" to listOf("충전시작", "충전할때", "충전을시작"), "window" to listOf("창문열", "창문을", "창문", "창을"),
            "unlock" to listOf("잠금해제", "잠금풀", "문열어둘"), "lock" to listOf("문잠글", "문잠금", "잠글때", "문을잠글"),
            "near" to listOf("곧도착", "도착직전", "도착전", "곧도착할때"), "arrive" to listOf("도착했", "도착할때", "도착"),
            "soc_low" to listOf("배터리부족", "배터리가모자", "배터리낮", "방전"), "charge_done" to listOf("충전끝", "충전완료", "충전이끝", "충전다"),
            "rest" to listOf("오래달", "쉬어", "두시간"), "cold" to listOf("추운", "추워", "영하"),
        )

        private val UNREACHABLE = listOf("못읽는데이터", "안되는데이터", "닿지않는데이터", "못닿는데이터", "없는데이터", "못가져오는")
        private val FEASIBLE_ALL = listOf("만들수있는기능", "할수있는일은", "뭘만들", "어떤기능을만들", "기능은몇", "기능몇개", "안되는기능", "못하는기능", "못만드는기능", "안되는건뭐")
        private val NEEDS = listOf("필요한데이터", "필요한게", "뭐가필요", "무슨데이터", "어떤데이터", "뭐필요", "필요해")
        private val FEASIBLE = listOf("만들수", "가능", "돼", "되나", "왜안", "왜못", "못해", "할수있", "볼수있", "보여")
        private val USED_BY = listOf("쓰는기능", "쓰는일", "쓰나", "필요한기능", "쓰이는")
        private val READABLE = listOf("읽을수", "읽혀", "접근할수", "가져올수")

        private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        private fun hasAny(t: String, words: List<String>) = words.any { it.isNotEmpty() && t.contains(it) }

        private fun hasBatchim(s: String): Boolean {
            val c = s.lastOrNull { it.isLetterOrDigit() } ?: return false
            return c in '가'..'힣' && (c.code - 0xAC00) % 28 != 0
        }

        /** 받침에 맞춰 을/를 · 은/는 */
        private fun eul(s: String) = s + if (hasBatchim(s)) "을" else "를"
        private fun eun(s: String) = s + if (hasBatchim(s)) "은" else "는"
    }
}
