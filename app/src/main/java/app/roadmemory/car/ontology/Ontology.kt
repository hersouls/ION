package app.roadmemory.car.ontology

import android.content.Context
import org.json.JSONObject

/** 개념(뜻 있는 데이터)의 상태. 읽기 API의 실측과 앱이 하는 계산에서 엔진이 정해요. */
enum class Status(val label: String, val usable: Boolean) {
    OK("읽혀요", true),
    DERIVED("계산해서 써요", true),
    ESTIMATED("추정해서 써요", false),
    UNTESTED("값을 아직 못 봤어요", false),
    BLOCKED("권한이 막아요", false),
    ABSENT("이 차량에 없어요", false),
    OUTSIDE("폰 · 마이현대에 있어요", false);

    /** 이 앱(APK)만으로는 얻을 길이 없는 상태 */
    val unreachable: Boolean get() = this == BLOCKED || this == ABSENT || this == OUTSIDE
}

/** 기능 판정. ● 만들 수 있어요 · ◐ 일부만 · ○ 이 앱만으로는 못 해요 */
enum class Verdict(val mark: String, val label: String) {
    CAN("●", "만들 수 있어요"),
    PARTLY("◐", "일부만 돼요"),
    NOT("○", "이 앱만으로는 못 해요"),
}

/** key = 이게 없으면 그 일이 아니에요 · support = 있으면 더 잘해요 · extra = 있으면 하이엔드에서 더해져요(판정에 안 써요) */
enum class Role { KEY, SUPPORT, EXTRA }

data class Node(val id: String, val type: String, val label: String, val props: Map<String, String>) {
    val aliases: List<String> get() = props["aliases"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

data class Edge(val from: String, val rel: String, val to: String, val props: Map<String, String>)

data class Need(val concept: Node, val role: Role, val status: Status)

/**
 * ION의 데이터 ION-Link. 서버 없이 APK 안의 `assets/ontology.json`(tools/make_ontology.py가 에뮬레이터 실측 로그로 만들어요)을 앱이 직접 읽어서 돌려요.
 * 개념의 상태와 기능의 판정은 파일에 적혀 있지 않고 여기서 규칙으로 계산해요:
 *  - 개념 상태: 폰 · 마이현대에 있으면 OUTSIDE, 앱이 만들거나 가지면 OK(앱), 읽기 API가 하나라도 읽히면 OK, 아니면 오도미터처럼 대신 쓸 개념이 있는지,
 *    그다음 아직 못 본 것 · 권한이 막는 것 · 없는 것 순서예요.
 *  - 기능 판정: key 개념 중 하나라도 닿을 길이 없으면(OUTSIDE · ABSENT · BLOCKED) ○, key와 support가 모두 쓸 수 있으면(OK · DERIVED) ●, 나머지는 ◐.
 */
class Ontology private constructor(val nodes: Map<String, Node>, private val edges: List<Edge>, val generated: String, val basis: String) {
    private val out = edges.groupBy { it.from to it.rel }
    private val into = edges.groupBy { it.to to it.rel }
    private val statusCache = HashMap<String, Status>()

    val functions: List<Node> = nodes.values.filter { it.type == "function" }
    val concepts: List<Node> = nodes.values.filter { it.type == "concept" }
    /** 시그니처 송이 나오는 순간(`mo.*`). 기능과 같은 규칙으로 판정해요(uses 관계). */
    val moments: List<Node> = nodes.values.filter { it.type == "moment" }
    /** 앱에 넣어 적용한 시그니처 송 팩(`pk.*`). 파일에는 없고 [withPack]이 실행 중에 더해요. */
    val packs: List<Node> = nodes.values.filter { it.type == "pack" }

    private fun targets(id: String, rel: String) = out[id to rel].orEmpty().map { it.to }

    fun statusOf(conceptId: String): Status = statusCache.getOrPut(conceptId) { computeStatus(conceptId) }

    private fun computeStatus(id: String): Status {
        val n = nodes[id] ?: return Status.ABSENT
        when (n.props["fixed"]) {
            "outside" -> return Status.OUTSIDE
            "app" -> return Status.OK
            "estimated" -> return Status.ESTIMATED
            "absent" -> return Status.ABSENT
        }
        val dps = targets(id, "providedBy").mapNotNull { nodes[it]?.props?.get("status") }
        if ("ok" in dps) return Status.OK
        if (targets(id, "derivedFrom").any { statusOf(it).usable }) return Status.DERIVED
        if (dps.isEmpty() && targets(id, "derivedFrom").isEmpty() && targets(id, "approximatedBy").isEmpty()) return Status.ABSENT
        if (targets(id, "approximatedBy").any { statusOf(it).usable }) return Status.ESTIMATED
        return when {
            "partial" in dps -> Status.UNTESTED
            "untested" in dps -> Status.UNTESTED
            "blocked" in dps -> Status.BLOCKED
            else -> Status.ABSENT
        }
    }

    fun needs(functionId: String): List<Need> = out[functionId to "uses"].orEmpty().mapNotNull { e ->
        val c = nodes[e.to] ?: return@mapNotNull null
        Need(c, Role.valueOf((e.props["role"] ?: "key").uppercase()), statusOf(c.id))
    }

    fun verdict(functionId: String): Verdict {
        val used = needs(functionId).filter { it.role != Role.EXTRA }
        val key = used.filter { it.role == Role.KEY }
        return when {
            key.any { it.status.unreachable } -> Verdict.NOT
            used.all { it.status.usable } -> Verdict.CAN
            else -> Verdict.PARTLY
        }
    }

    /** 판정을 낮추는 개념(key · support 중 쓸 수 없는 것). key를 앞에 둬요. */
    fun missing(functionId: String): List<Need> =
        needs(functionId).filter { it.role != Role.EXTRA && !it.status.usable }.sortedBy { it.role.ordinal }

    /** 이 개념을 쓰는 기능(시그니처 송의 순간은 기능이 아니라서 빼요). extra로만 쓰는 것은 빼요(하이엔드에서 더해지는 것). */
    fun functionsUsing(conceptId: String): List<Node> =
        into[conceptId to "uses"].orEmpty().filter { (it.props["role"] ?: "key") != "extra" }.mapNotNull { nodes[it.from]?.takeIf { n -> n.type == "function" } }

    fun counts(): Map<Verdict, Int> = Verdict.entries.associateWith { v -> functions.count { verdict(it.id) == v } }

    // ── 시그니처 송의 순간 ──

    /** 순간을 알아차리는 코드가 이 앱에 들어 있는지(`wired`). 값이 읽혀도 코드가 없으면 내놓지 못해요. */
    fun isWired(momentId: String): Boolean = nodes[momentId]?.props?.get("wired") == "true"

    /** 내놓을 수 있는 순간: 판정이 ●(닿는 데이터로 모두 돼요)이고 앱에 알아차리는 코드가 있어요. */
    fun offerable(momentId: String): Boolean = nodes[momentId]?.type == "moment" && isWired(momentId) && verdict(momentId) == Verdict.CAN

    /** 화면에 그릴 표시: 내놓을 수 있으면 ●, 차는 알려 주는데 코드가 아직 없으면 ◐, 닿을 길이 없으면 ○예요. */
    fun momentMark(momentId: String): Verdict = when {
        offerable(momentId) -> Verdict.CAN
        verdict(momentId) == Verdict.NOT -> Verdict.NOT
        else -> Verdict.PARTLY
    }

    /** 내놓지 못하는 까닭 한 줄(내놓을 수 있으면 null). */
    fun momentReason(momentId: String): String? {
        if (offerable(momentId)) return null
        val miss = missing(momentId).firstOrNull()
        return when {
            miss != null -> "${miss.concept.label}: ${(miss.concept.props["note"] ?: miss.status.label).replace(Regex("\\s*\\(docs/[^)]*\\)"), "")}"
            !isWired(momentId) -> "차는 알려 주는데 이 앱이 아직 읽지 않아요"
            else -> null
        }
    }

    fun momentMode(momentId: String): String = nodes[momentId]?.props?.get("mode").orEmpty()

    /** 이 순간에 ION이 화면에 쓰는 문장의 틀(`{soc}` 같은 자리가 있어요). 없으면 null. */
    fun momentInfo(momentId: String): String? = nodes[momentId]?.props?.get("info")?.takeIf { it.isNotEmpty() }

    /** 팩이 소리를 가진 순간 id(`board`처럼 mo. 를 뺀 이름). */
    fun packMoments(packNodeId: String): List<String> = targets(packNodeId, "reactsTo").map { it.removePrefix("mo.") }

    /**
     * 적용한 팩을 그래프에 더한 새 그래프를 돌려줘요(불러온 그래프는 건드리지 않아요).
     * 팩 노드(`pk.<id>`)와 팩 → 순간 `reactsTo` 관계가 더해져서 "이 팩은 언제 나와?" 같은 물음에 같은 그래프로 답해요.
     */
    fun withPack(id: String, title: String, speaker: String, voice: String, momentIds: Collection<String>): Ontology {
        val pk = "pk.$id"
        val added = LinkedHashMap(nodes)
        added[pk] = Node(pk, "pack", title, mapOf("speaker" to speaker, "voice" to voice))
        val more = momentIds.filter { "mo.$it" in nodes }.map { Edge(pk, "reactsTo", "mo.$it", emptyMap()) }
        return Ontology(added, edges.filter { it.from != pk } + more, generated, basis)
    }

    /** 이 개념을 읽으려면 더 선언해야 하는 권한 노드(있으면). */
    fun permissionsToDeclare(conceptId: String): List<Node> =
        targets(conceptId, "providedBy").flatMap { dp -> targets(dp, "needsPermission") }
            .mapNotNull { nodes[it] }.filter { it.props["declared"] == "false" }.distinct()

    /** 이 개념을 읽으려면 더 선언해야 하는 권한 이야기(있으면). */
    fun permissionNotes(conceptId: String): List<String> =
        targets(conceptId, "providedBy").flatMap { dp -> targets(dp, "needsPermission") }
            .mapNotNull { nodes[it] }.filter { it.props["declared"] == "false" }
            .map { p -> buildString { append(p.label); p.props["approval"]?.let { append(" · ").append(it) } } }
            .distinct()

    companion object {
        fun parse(json: String): Ontology {
            val root = JSONObject(json)
            fun props(o: JSONObject, skip: Set<String>) = o.keys().asSequence().filter { it !in skip }.associateWith { o.optString(it) }
            val nodeMap = LinkedHashMap<String, Node>()
            val na = root.getJSONArray("nodes")
            for (i in 0 until na.length()) {
                val o = na.getJSONObject(i)
                nodeMap[o.getString("id")] = Node(o.getString("id"), o.getString("type"), o.getString("label"), props(o, setOf("id", "type", "label")))
            }
            val ea = root.getJSONArray("edges")
            val edgeList = (0 until ea.length()).map { i ->
                val o = ea.getJSONObject(i)
                Edge(o.getString("from"), o.getString("rel"), o.getString("to"), props(o, setOf("from", "rel", "to")))
            }
            return Ontology(nodeMap, edgeList, root.optString("generated"), root.optString("basis"))
        }

        fun load(context: Context): Ontology =
            parse(context.assets.open("ontology.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
}
