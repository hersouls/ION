package app.roadmemory.car.studio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * 이 기기에 내려받은 것과 적용한 것(순수한 값). 서비스 workflow의 "내려받아요 → 적용해요 → 지워요"가 여기에 남아요.
 * 마이현대에서 동기화해 가져온 것은 서비스 단위 표시(`sync_<화면 이름>`)로 남겨요.
 */
data class LibraryState(val downloaded: Set<String> = emptySet(), val applied: Map<String, String> = emptyMap()) {
    fun has(id: String) = id in downloaded
    fun appliedId(service: String): String? = applied[service]
    fun download(id: String) = copy(downloaded = downloaded + id)
    /** 적용 값은 "내려받은 것의 id" 또는 "id#진행"(마이 트립의 정류장 차례)이에요. */
    private fun base(v: String) = v.substringBefore('#')
    /** 지우면 적용도 풀려요. */
    fun remove(id: String) = copy(downloaded = downloaded - id, applied = applied.filterValues { base(it) != id })
    /** 내려받은 것만 적용해요. */
    fun apply(service: String, id: String) = if (base(id) in downloaded) copy(applied = applied + (service to id)) else this
    fun unapply(service: String) = copy(applied = applied - service)
    fun countIn(ids: Collection<String>) = ids.count { it in downloaded }

    fun encode(): String = "d:" + downloaded.sorted().joinToString(",") + ";a:" + applied.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }

    companion object {
        fun decode(raw: String?): LibraryState {
            if (raw.isNullOrBlank()) return LibraryState()
            val parts = raw.split(';').associate { it.substringBefore(':') to it.substringAfter(':', "") }
            val d = parts["d"].orEmpty().split(',').filter { it.isNotBlank() }.toSet()
            val a = parts["a"].orEmpty().split(',').filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
            return LibraryState(d, a.filterValues { it.substringBefore('#') in d })
        }
    }
}

/**
 * 내려받기는 앱 안의 시연 상품을 이 기기의 앱 저장소(files/studio/)로 실제로 복사해요. 스토어에서 받는 대신 앱 안에서 가져오는 것만 달라요.
 * 상태는 Compose가 지켜보는 값이고 SharedPreferences에 남아서 앱을 다시 켜도 이어져요.
 */
object StudioLibrary {
    var state: LibraryState by mutableStateOf(LibraryState())
        private set
    private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("studio_library", Context.MODE_PRIVATE)

    fun ensure(c: Context) {
        if (loaded) return
        loaded = true
        state = LibraryState.decode(prefs(c).getString("state", null))
    }

    private fun commit(c: Context, s: LibraryState) { state = s; prefs(c).edit().putString("state", s.encode()).apply() }

    /**
     * 앱 안의 것은 바로 복사하고, ION Studio에서 받은 목록의 것(`link/…`)은 Studio에서 받아요([StudioLink.fetchAll]).
     * Studio의 파일을 다 받은 뒤에야 "내려받았어요"가 되고, 못 받으면 그대로 남아요. [then]은 내려받은 뒤에 이어서 할 일(적용)이에요.
     */
    fun download(c: Context, id: String, files: List<String>, then: () -> Unit = {}) {
        val (linked, bundled) = files.partition { LinkRules.isLinked(it) }
        bundled.forEach { f ->
            runCatching {
                val out = File(c.applicationContext.filesDir, "studio/$f").apply { parentFile?.mkdirs() }
                c.applicationContext.assets.open("studio/$f").use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
        if (linked.isEmpty()) { commit(c, state.download(id)); then(); return }
        if (id in StudioLink.pending) return
        val app = c.applicationContext
        StudioLink.fetchAll(app, id, linked) { ok -> if (ok) { StudioLink.markGot(app, id); commit(app, state.download(id)); then() } }
    }

    fun remove(c: Context, id: String, files: List<String>) {
        files.forEach { File(c.applicationContext.filesDir, "studio/$it").delete() }
        commit(c, state.remove(id))
    }

    fun apply(c: Context, service: String, id: String) = commit(c, state.apply(service, id))
    fun unapply(c: Context, service: String) = commit(c, state.unapply(service))
}
