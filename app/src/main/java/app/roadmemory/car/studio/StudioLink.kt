package app.roadmemory.car.studio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/**
 * ION Studio와 바로 잇는 길의 순수한 규칙(주소 · 키 · 경로 · 오류 말).
 * 상품이 차로 오는 본래 길은 블루링크 스토어예요. 이 길은 Studio에 올라온 것이 차에 닿는 모습을 보여 주려고 둔 것이에요(오너 2026-11-12, docs/ION.md).
 */
object LinkRules {
    const val DEFAULT_HOST = "ion-studio.moonwave.kr"
    const val PREFIX = "link/"
    private val KEY = Regex("ion_test_[A-Za-z0-9_-]{24,64}")
    private val HOST = Regex("[A-Za-z0-9.-]+(:[0-9]{1,5})?")
    /** 묶음 zip 안의 파일 이름: 폴더 없이 글자 · 숫자 · `_` `-` `.`만. */
    private val PACK_FILE = Regex("[A-Za-z0-9_-]+(\\.[A-Za-z0-9]+)+")

    /** 적은 주소를 `https://호스트`로. 암호화되지 않은 길(http)과 호스트가 아닌 글은 받지 않아요. */
    fun base(input: String): String? {
        val t = input.trim().trimEnd('/')
        if (t.startsWith("http://", ignoreCase = true)) return null
        val host = t.removePrefix("https://")
        return if (HOST.matches(host) && host.any { it.isLetter() }) "https://$host" else null
    }

    fun key(input: String): String? = input.trim().takeIf { KEY.matches(it) }
    /** 화면에 보여 주는 키: 앞 14자만. */
    fun hint(key: String): String = key.take(14) + "…"
    fun isLinked(path: String?): Boolean = path?.startsWith(PREFIX) == true
    /** 받은 목록의 경로(`link/a/…`)가 가리키는 Studio의 주소. */
    fun url(base: String, path: String): String = "$base/api/v1/car/files/" + path.removePrefix(PREFIX)
    fun catalogUrl(base: String): String = "$base/api/v1/car/catalog"

    /** 묶음 zip의 한 항목(`<id>/<파일>`)이 풀릴 파일 이름. 다른 폴더를 가리키거나 이름이 이상하면 null(풀지 않아요). */
    fun packEntry(id: String, entry: String): String? {
        val name = entry.removePrefix("$id/")
        return name.takeIf { entry.startsWith("$id/") && PACK_FILE.matches(it) && ".." !in it }
    }

    fun message(code: Int): String = when (code) {
        401 -> "API 키가 맞지 않거나 폐기됐어요. ION Studio의 연동에서 키를 확인해 주세요"
        404 -> "ION Studio에서 찾지 못했어요. 주소를 확인해 주세요"
        429 -> "요청이 너무 잦았어요. 잠시 뒤에 다시 받아 주세요"
        else -> "ION Studio가 답하지 못했어요($code). 잠시 뒤에 다시 받아 주세요"
    }
    const val UNREACHABLE = "ION Studio에 닿지 못했어요. 주소와 네트워크를 확인해 주세요"
    const val BAD_ADDRESS = "주소를 다시 확인해 주세요"
    const val BAD_KEY = "API 키의 모양이 맞지 않아요. ion_test_로 시작하는 키를 그대로 적어 주세요"
    const val BAD_CATALOG = "받은 목록을 읽지 못했어요. ION Studio와 앱의 판이 맞는지 확인해 주세요"

    /** "오늘 09:12" · "어제 21:40" · "11월 9일 08:05". */
    fun whenLabel(at: Long, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val t = java.time.Instant.ofEpochMilli(at).atZone(zone)
        val days = java.time.temporal.ChronoUnit.DAYS.between(t.toLocalDate(), java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
        val hm = "%02d:%02d".format(t.hour, t.minute)
        return when (days) { 0L -> "오늘 $hm"; 1L -> "어제 $hm"; else -> "${t.monthValue}월 ${t.dayOfMonth}일 $hm" }
    }
}

/** 화면이 지켜보는 연결 상태. [syncedAt]이 0이면 아직 받은 적이 없어요. */
data class LinkState(
    val linked: Boolean = false, val base: String = "", val keyHint: String = "", val member: String = "", val syncedAt: Long = 0L,
    val busy: Boolean = false, val error: String? = null,
    val themes: Int = 0, val arts: Int = 0, val packs: Int = 0, val trips: Int = 0, val myTrips: Int = 0, val skippedTrips: Int = 0,
)

/**
 * ION Studio와 이어서 목록과 파일을 받아요(`GET /api/v1/car/catalog` · `/api/v1/car/files/…`, 머리글 `X-ION-Key`).
 * 주소와 키는 이 기기에만 남고, 받은 것은 `files/studio/link/`에 놓여요. 연결을 끊으면 받은 목록과 파일을 지우고 앱 안 목록으로 돌아가요.
 * 읽기만 해요: 이 앱이 Studio에 보내는 것은 키뿐이에요(차의 값 · 위치는 보내지 않아요).
 */
object StudioLink {
    var state: LinkState by mutableStateOf(LinkState())
        private set
    /** 목록이 바뀔 때마다 올라가요: 화면이 이 값을 읽고 목록을 다시 불러요. */
    var rev: Int by mutableStateOf(0)
        private set
    /** 받는 중인 것(상품 id). */
    var pending: Set<String> by mutableStateOf(emptySet())
        private set

    private var key = ""
    private var loaded = false
    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("studio_link", Context.MODE_PRIVATE)
    private fun dir(c: Context) = File(c.applicationContext.filesDir, "studio/${LinkRules.PREFIX}")
    private fun catalogFile(c: Context) = File(dir(c), "catalog.json")
    internal fun file(c: Context, path: String) = File(c.applicationContext.filesDir, "studio/$path")

    fun ensure(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        key = p.getString("key", "").orEmpty()
        val base = p.getString("base", "").orEmpty()
        if (key.isEmpty() || base.isEmpty()) return
        state = LinkState(linked = true, base = base, keyHint = LinkRules.hint(key), syncedAt = p.getLong("synced_at", 0L)).counted(remote(c))
    }

    private fun LinkState.counted(cat: Catalog?): LinkState = if (cat == null) this else copy(
        member = cat.member, themes = cat.themes.size, arts = cat.arts.size, packs = cat.packs.size,
        trips = cat.trips.count { it.guidable }, myTrips = cat.trips.count { it.mine && it.guidable }, skippedTrips = CatalogMerge.skippedTrips(cat),
    )

    /** 받아 둔 목록(이어져 있을 때만). 읽지 못하면 null이라 앱 안 목록만 보여요. */
    fun remote(c: Context): Catalog? {
        ensure(c)
        if (!state.linked) return null
        return runCatching { CatalogParser.parse(catalogFile(c).readText(Charsets.UTF_8), prefix = LinkRules.PREFIX) }.getOrNull()
    }

    /** 주소와 키로 처음 이어요. 목록을 받아 보고, 받아지면 그때 주소와 키를 남겨요. */
    fun connect(c: Context, baseInput: String, keyInput: String) {
        val base = LinkRules.base(baseInput) ?: return fail(LinkRules.BAD_ADDRESS)
        val k = LinkRules.key(keyInput) ?: return fail(LinkRules.BAD_KEY)
        pull(c.applicationContext, base, k, first = true)
    }

    /** 이어져 있을 때 목록을 새로 받아요. */
    fun sync(c: Context) {
        ensure(c)
        if (state.linked && !state.busy) pull(c.applicationContext, state.base, key, first = false)
    }

    /** Studio에서 파일을 받아 내려받은 것(상품 id). 끊을 때 함께 지워요: 받은 파일이 사라지면 앱 안 목록의 같은 상품과 모습이 달라질 수 있어서요. */
    fun markGot(c: Context, id: String) {
        val p = prefs(c)
        p.edit().putStringSet("got", p.getStringSet("got", emptySet()).orEmpty() + id).apply()
    }

    fun disconnect(c: Context) {
        StudioLibrary.ensure(c)
        prefs(c).getStringSet("got", emptySet()).orEmpty().forEach { StudioLibrary.remove(c, it, emptyList()) }
        prefs(c).edit().clear().apply()
        key = ""
        val gone = dir(c)
        io.execute { gone.deleteRecursively() }
        state = LinkState()
        changed()
    }

    private fun fail(message: String) { state = state.copy(busy = false, error = message) }
    private fun changed() { StudioStore.invalidate(); rev++ }

    private fun pull(app: Context, base: String, k: String, first: Boolean) {
        state = state.copy(busy = true, error = null)
        io.execute {
            val got = runCatching { get(LinkRules.catalogUrl(base), k) }
            val text = got.getOrNull()?.takeIf { it.code == 200 }?.body?.toString(Charsets.UTF_8)
            val cat = text?.let { t -> runCatching { require(JSONObject(t).optString("format") == "ion-car/1"); CatalogParser.parse(t, prefix = LinkRules.PREFIX) }.getOrNull() }
            if (cat == null) {
                val why = when { got.isFailure -> LinkRules.UNREACHABLE; got.getOrThrow().code != 200 -> LinkRules.message(got.getOrThrow().code); else -> LinkRules.BAD_CATALOG }
                main.post { fail(why) }
                return@execute
            }
            catalogFile(app).apply { parentFile?.mkdirs() }.writeText(text, Charsets.UTF_8)
            val now = System.currentTimeMillis()
            val p = prefs(app)
            p.edit().putString("base", base).putString("key", k).putLong("synced_at", now).apply()
            // Studio에 내가 등록한 일정은 처음 볼 때 한 번 내 일정에 담아요(이 기기에서 지운 뒤에 다시 살아나지 않게 본 것을 적어 둬요)
            val seen = p.getStringSet("seen_trips", emptySet()).orEmpty()
            val fresh = cat.trips.filter { it.mine && it.guidable && it.id !in seen }.map { it.id }
            if (fresh.isNotEmpty()) p.edit().putStringSet("seen_trips", seen + fresh).apply()
            main.post {
                key = k
                state = LinkState(linked = true, base = base, keyHint = LinkRules.hint(k), syncedAt = now, busy = true).counted(cat)
                changed()
                fresh.forEach { StudioLibrary.download(app, it, emptyList()); markGot(app, it) }      // 끊으면 내 일정에서도 빠져요
            }
            // 목록에 보이는 표지는 미리 받아 둬요(네트워크가 끊겨도 목록이 보이게). 나머지 그림은 내려받기를 누를 때 받아요.
            (cat.themes.map { it.cover } + cat.arts.map { it.cover } + cat.packs.mapNotNull { it.cover }).forEach { fetch(app, it) }
            main.post { state = state.copy(busy = false); rev++ }
        }
    }

    private class Reply(val code: Int, val body: ByteArray)

    @Throws(IOException::class)
    private fun get(url: String, k: String, retry: Boolean = true): Reply {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 20_000; instanceFollowRedirects = false
            setRequestProperty("X-ION-Key", k); setRequestProperty("Accept", "*/*"); setRequestProperty("User-Agent", "ION-App")
        }
        try {
            val code = conn.responseCode
            if (code == 429 && retry) { Thread.sleep(2_000); return get(url, k, retry = false) }
            return Reply(code, if (code == 200) conn.inputStream.use { it.readBytes() } else ByteArray(0))
        } finally { conn.disconnect() }
    }

    /** 받은 목록의 그림 한 장을 이 기기에 놓아요(이미 있으면 그대로). 부르는 쪽은 화면 스레드가 아니어야 해요. */
    fun fetch(c: Context, path: String): File? {
        if (!LinkRules.isLinked(path)) return null
        val out = file(c, path)
        if (out.isFile && out.length() > 0) return out
        val base = state.base.ifEmpty { prefs(c).getString("base", "").orEmpty() }
        val k = key.ifEmpty { prefs(c).getString("key", "").orEmpty() }
        if (base.isEmpty() || k.isEmpty()) return null
        return runCatching {
            val r = get(LinkRules.url(base, path), k)
            if (r.code != 200 || r.body.isEmpty()) return null
            out.parentFile?.mkdirs()
            val tmp = File(out.path + ".part")
            tmp.writeBytes(r.body)
            if (tmp.renameTo(out)) out else null
        }.getOrNull()
    }

    /** 상품의 파일을 모두 받아요. 다 받았으면 [done](true), 하나라도 못 받았으면 false(화면 스레드에서 불러요). */
    fun fetchAll(c: Context, id: String, paths: List<String>, done: (Boolean) -> Unit) {
        val app = c.applicationContext
        pending = pending + id
        state = state.copy(error = null)
        io.execute {
            val ok = paths.all { fetch(app, it) != null }
            main.post {
                pending = pending - id
                if (!ok) fail(LinkRules.UNREACHABLE)
                done(ok)
            }
        }
    }

    /** 시그니처 송 묶음을 받아 `files/packs/<id>/`에 풀어요(앱이 기기에 넣은 묶음으로 읽는 자리). */
    fun installPack(c: Context, pack: StudioPack, done: (Boolean) -> Unit) {
        val app = c.applicationContext
        pending = pending + pack.id
        state = state.copy(error = null)
        io.execute {
            val ok = runCatching {
                val r = get(LinkRules.url(state.base, pack.zip), key)
                if (r.code != 200) return@runCatching false
                val folder = File(app.filesDir, "packs/${pack.id}")
                val tmp = File(app.filesDir, "packs/.${pack.id}.part").apply { deleteRecursively(); mkdirs() }
                var manifest = false
                ZipInputStream(r.body.inputStream()).use { zip ->
                    generateSequence { zip.nextEntry }.filter { !it.isDirectory }.forEach { e ->
                        val name = LinkRules.packEntry(pack.id, e.name) ?: return@forEach
                        File(tmp, name).outputStream().use { zip.copyTo(it) }
                        if (name == "pack.json") manifest = true
                    }
                }
                if (!manifest) { tmp.deleteRecursively(); return@runCatching false }
                folder.deleteRecursively()
                tmp.renameTo(folder)
            }.getOrDefault(false)
            main.post {
                pending = pending - pack.id
                if (!ok) fail(LinkRules.UNREACHABLE)
                done(ok)
            }
        }
    }

    /** Studio에서 받아 푼 묶음을 이 기기에서 지워요. */
    fun removePack(c: Context, id: String, done: () -> Unit) {
        val folder = File(c.applicationContext.filesDir, "packs/$id")
        io.execute { folder.deleteRecursively(); main.post(done) }
    }

    fun hasPackFolder(c: Context, id: String): Boolean = File(c.applicationContext.filesDir, "packs/$id/pack.json").isFile
}
