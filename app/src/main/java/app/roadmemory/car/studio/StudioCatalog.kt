package app.roadmemory.car.studio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 앱 안에 넣어 둔 시연용 상품 · 일정(assets/studio/catalog.json). `tools/export_studio_demo.py`가 시연용 ION Studio에서 뽑아 줄여 만들어요.
 * 창작자 · 작품은 모두 지어낸 것이고(일정의 장소만 지도에서 찾은 실제 장소), 화면에는 "시연"이라는 글자를 쓰지 않아요(오너 지시 2026-11-11).
 */
/** 테마의 낮 또는 밤 색(0xAARRGGBB). 이름은 앱의 Palette와 같아요(시연용 ION Studio의 theme.json이 정한 값이에요). */
data class LookColors(
    val card: Long, val cardSolid: Long, val cardLine: Long, val veil: Long, val pill: Long, val bubble: Long,
    val textPrimary: Long, val textSecondary: Long, val textTertiary: Long, val buttonFilled: Long, val textOnFilled: Long,
    val informativeActive: Long, val informativeNegative: Long, val barA: Long, val barB: Long, val orb: Long,
)

/** 적용하면 앱 창이 입는 것: 창 바탕 그림, 낮 · 밤 색, ION 칸의 사진, 엠블럼. */
data class Look(
    val name: String, val day: LookColors, val night: LookColors,
    val bgDay: String?, val bgNight: String?, val photo: String?, val emblem: String?,
    /**
     * 카드 세트(화면 이름 → 그림): 서비스 여덟 · 화면 여섯 카드마다 테마의 그림 한 장. 실제 블루링크 스토어의 테마가 메뉴 카드를 한 세계관의 그림으로 바꾸듯이,
     * ION 홈의 카드가 이 그림을 입어요(차의 메뉴 · 계기판은 시스템이라 못 바꿔요). 세트가 있는 테마만 가져요([ThemeCards]).
     */
    val cards: Map<String, String> = emptyMap(),
    /** 스토어 상품 화면의 미리 보기(이름 → 그림): 이 테마를 적용한 ION 홈을 앱에서 캡처한 것. 세트가 있는 테마만 가져요. */
    val previews: List<Pair<String, String>> = emptyList(),
) { val files: List<String> get() = listOfNotNull(bgDay, bgNight, photo, emblem) + cards.values + previews.map { it.second } }

data class Theme(
    val id: String, val title: String, val creator: String, val summary: String, val tags: List<String>, val price: Int,
    val cover: String, val day: String?, val night: String?, val look: Look? = null,
) { val files: List<String> get() = listOfNotNull(cover, day, night) + (look?.files ?: emptyList()) }

data class Art(
    val id: String, val title: String, val artist: String, val summary: String, val tags: List<String>, val price: Int,
    val cover: String, val photos: List<String>,
) { val files: List<String> get() = listOf(cover) + photos }

data class Stop(val name: String, val address: String, val kind: String, val note: String, val lat: Double, val lng: Double)

data class Trip(
    val id: String, val title: String, val region: String, val km: Double?, val days: List<List<Stop>>,
    /** ION Studio에서 받은 일정일 때: 이 키를 만든 사람이 Studio에 등록한 일정인지. */
    val mine: Boolean = false,
) {
    val stops: List<Stop> get() = days.flatten()
    /** 내비로 안내하려면 모든 정류장에 자리가 있어야 해요(Studio에서 이름만 적은 정류장은 자리가 없어요). */
    val guidable: Boolean get() = stops.isNotEmpty() && stops.all { !it.lat.isNaN() && !it.lng.isNaN() }
}

/** ION Studio에서 받을 수 있는 시그니처 송 묶음 하나(목록의 한 줄). [zip]을 받아 풀면 앱이 읽는 묶음 폴더가 돼요. */
data class StudioPack(val id: String, val title: String, val speaker: String, val creator: String, val summary: String, val price: Int, val cover: String?, val moments: Int, val zip: String)

data class AlbumPhoto(val id: String, val title: String, val image: String, val region: String = "", val month: String = "", val thumb: String = image)
data class CalendarEvent(val id: String, val title: String, val whenText: String, val place: String, val with: String, val lat: Double? = null, val lng: Double? = null)

/**
 * 위치 동기화의 시연 값: 마이현대가 가족의 위치를 동기화해 준다고 가정한 한 사람. 별칭은 역할이고(실명 없음), 좌표는 시연 일정의 장소(지도에서 찾은 실제 장소)예요.
 * [sharing]이 false면 위치 보여 주기를 꺼 둔 가족이라서 읽지 않아요(좌표도 없어요).
 */
data class FamilyDemo(val alias: String, val sharing: Boolean, val place: String? = null, val lat: Double = 0.0, val lng: Double = 0.0, val moving: Boolean = false, val ageSec: Long? = null)

data class Catalog(
    val about: String, val themes: List<Theme>, val arts: List<Art>, val trips: List<Trip>,
    val album: List<AlbumPhoto>, val calendar: List<CalendarEvent>, val family: List<FamilyDemo> = emptyList(),
    /** ION Studio에서 받은 목록에만 있어요: 받을 수 있는 시그니처 송, 목록의 판(내용이 같으면 같아요), 키를 만든 사람의 표시 이름. */
    val packs: List<StudioPack> = emptyList(), val rev: String = "", val member: String = "",
) {
    fun theme(id: String?) = themes.firstOrNull { it.id == id }
    fun art(id: String?) = arts.firstOrNull { it.id == id }
}

/** CSS 색("#RRGGBB" · "rgb(r,g,b)" · "rgba(r,g,b,a)")을 0xAARRGGBB로. */
fun parseCssColor(text: String): Long {
    val t = text.trim()
    fun pack(a: Int, r: Int, g: Int, b: Int) = (a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    if (t.startsWith("#") && t.length == 7) { val v = t.drop(1).toLong(16); return pack(255, (v shr 16).toInt() and 255, (v shr 8).toInt() and 255, v.toInt() and 255) }
    if (t.startsWith("rgb")) {
        val n = t.substringAfter("(").substringBefore(")").split(",").map { it.trim() }
        val a = if (n.size > 3) Math.round(n[3].toDouble() * 255).toInt().coerceIn(0, 255) else 255
        return pack(a, n[0].toInt(), n[1].toInt(), n[2].toInt())
    }
    error("모르는 색이에요: $text")
}

object CatalogParser {
    private fun colors(o: JSONObject) = LookColors(
        card = parseCssColor(o.getString("card")), cardSolid = parseCssColor(o.getString("cardSolid")), cardLine = parseCssColor(o.getString("cardLine")),
        veil = parseCssColor(o.getString("veil")), pill = parseCssColor(o.getString("pill")), bubble = parseCssColor(o.getString("bubble")),
        textPrimary = parseCssColor(o.getString("textPrimary")), textSecondary = parseCssColor(o.getString("textSecondary")), textTertiary = parseCssColor(o.getString("textTertiary")),
        buttonFilled = parseCssColor(o.getString("buttonFilled")), textOnFilled = parseCssColor(o.getString("textOnFilled")),
        informativeActive = parseCssColor(o.getString("informativeActive")), informativeNegative = parseCssColor(o.getString("informativeNegative")),
        barA = parseCssColor(o.getString("barA")), barB = parseCssColor(o.getString("barB")), orb = parseCssColor(o.getString("orb")),
    )

    private fun JSONObject.str(k: String): String? = if (isNull(k) || !has(k)) null else getString(k).takeIf { it.isNotEmpty() }
    private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
    private fun <T> JSONArray?.items(f: (JSONObject) -> T): List<T> = if (this == null) emptyList() else List(length()) { f(getJSONObject(it)) }

    /** 한 테마의 세트: 카드 그림과 상품 화면 미리 보기. */
    private class SetData(val cards: Map<String, String>, val previews: List<Pair<String, String>>)

    /**
     * 테마 세트(assets/studio/sets.json): { 테마 id: { "cards": { 화면 이름: 그림 }, "previews": [ { "title": 이름, "image": 그림 } ] } }.
     * Studio의 catalog.json을 다시 뽑아도 세트가 남도록 따로 둬요.
     */
    private fun sets(text: String?): Map<String, SetData> {
        if (text.isNullOrBlank()) return emptyMap()
        val o = JSONObject(text)
        return o.keys().asSequence().associateWith { id ->
            val t = o.getJSONObject(id)
            SetData(
                t.optJSONObject("cards")?.let { c -> c.keys().asSequence().associateWith { k -> c.getString(k) } } ?: emptyMap(),
                t.optJSONArray("previews")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { it.getString("title") to it.getString("image") } } } ?: emptyList(),
            )
        }
    }

    /**
     * [prefix]는 그림 경로 앞에 붙는 말이에요: 앱 안 목록은 ""이고, ION Studio에서 받은 목록은 "link/"(내려받은 파일이 `files/studio/link/` 아래에 놓여요).
     * Studio에서 받은 목록은 테마의 카드 세트 · 미리 보기를 테마 안에 담아 와요(앱 안 목록은 sets.json에 따로 있어요).
     */
    fun parse(text: String, setsText: String? = null, prefix: String = ""): Catalog {
        val o = JSONObject(text)
        val sets = sets(setsText)
        fun JSONObject.path(k: String): String? = str(k)?.let { prefix + it }
        fun inlineCards(t: JSONObject): Map<String, String> = t.optJSONObject("cards")?.let { c -> c.keys().asSequence().associateWith { k -> prefix + c.getString(k) } } ?: emptyMap()
        fun inlinePreviews(t: JSONObject): List<Pair<String, String>> = t.optJSONArray("previews")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { it.getString("title") to prefix + it.getString("image") } } } ?: emptyList()
        return Catalog(
            about = o.optString("about"),
            themes = o.optJSONArray("themes").items { t ->
                val lk = if (t.isNull("look") || !t.has("look")) null else t.getJSONObject("look")
                Theme(t.getString("id"), t.getString("title"), t.optString("creator"), t.optString("summary"), t.optJSONArray("tags")?.strings() ?: emptyList(),
                    t.optInt("price"), prefix + t.getString("cover"), t.path("day"), t.path("night"),
                    look = lk?.let { Look(it.optString("name"), colors(it.getJSONObject("day")), colors(it.getJSONObject("night")), it.path("bgDay"), it.path("bgNight"), it.path("photo"), it.path("emblem"),
                        cards = sets[t.getString("id")]?.cards ?: inlineCards(t), previews = sets[t.getString("id")]?.previews ?: inlinePreviews(t)) })
            },
            arts = o.optJSONArray("arts").items { a ->
                Art(a.getString("id"), a.getString("title"), a.optString("artist"), a.optString("summary"), a.optJSONArray("tags")?.strings() ?: emptyList(),
                    a.optInt("price"), prefix + a.getString("cover"), (a.optJSONArray("photos")?.strings() ?: emptyList()).map { prefix + it })
            },
            trips = o.optJSONArray("trips").items { t ->
                Trip(t.getString("id"), t.getString("title"), t.optString("region"), if (t.isNull("km")) null else t.optDouble("km"),
                    t.getJSONArray("days").let { days -> List(days.length()) { d -> days.getJSONArray(d).items { s ->
                        Stop(s.getString("name"), s.optString("address"), s.optString("kind"), s.optString("note"), s.optDouble("lat"), s.optDouble("lng"))
                    } } }, mine = t.optBoolean("mine", false))
            },
            packs = o.optJSONArray("packs").items { k ->
                StudioPack(k.getString("id"), k.getString("title"), k.optString("speaker"), k.optString("creator"), k.optString("summary"), k.optInt("price"), k.path("cover"), k.optInt("moments"), prefix + k.getString("zip"))
            },
            rev = o.optString("rev"), member = o.optString("member"),
            album = o.optJSONArray("album").items { AlbumPhoto(it.getString("id"), it.getString("title"), it.getString("image"), it.optString("region"), it.optString("month"), it.optString("thumb").ifEmpty { it.getString("image") }) },
            calendar = o.optJSONArray("calendar").items { CalendarEvent(it.getString("id"), it.getString("title"), it.optString("when"), it.optString("place"), it.optString("with"), if (it.has("lat")) it.optDouble("lat") else null, if (it.has("lng")) it.optDouble("lng") else null) },
            family = o.optJSONArray("family").items { FamilyDemo(it.getString("alias"), it.optBoolean("sharing", false), it.str("place"), it.optDouble("lat", 0.0), it.optDouble("lng", 0.0), it.optBoolean("moving", false), if (it.has("ageSec")) it.optLong("ageSec") else null) },
        )
    }
}

/** ION Studio에서 받은 목록을 앱 안 목록에 얹어요(순수한 규칙). */
object CatalogMerge {
    private fun <T> join(first: List<T>, rest: List<T>, id: (T) -> String): List<T> {
        val seen = first.map(id).toSet()
        return first + rest.filter { id(it) !in seen }
    }

    /**
     * 테마 · 아트 · 일정은 Studio에서 받은 것이 앞에 오고(같은 id면 Studio 것이 이겨요), 앱 안에만 있는 것이 뒤에 남아요.
     * 내비로 안내할 수 없는 일정(자리가 없는 정류장이 있는 것)은 빼요. 앨범 · 캘린더 · 가족은 Studio에서 오지 않아서 그대로예요.
     */
    fun merge(bundled: Catalog, remote: Catalog?): Catalog {
        if (remote == null) return bundled
        return bundled.copy(
            themes = join(remote.themes, bundled.themes) { it.id },
            arts = join(remote.arts, bundled.arts) { it.id },
            trips = join(remote.trips.filter { it.guidable }, bundled.trips) { it.id },
            packs = remote.packs, rev = remote.rev, member = remote.member,
        )
    }

    fun skippedTrips(remote: Catalog): Int = remote.trips.count { !it.guidable }
}

/** 앱 안 목록(assets)은 한 번만 읽고, ION Studio와 이어져 있으면 받은 목록을 얹어요([StudioLink]가 새로 받을 때마다 [invalidate]). */
object StudioStore {
    @Volatile private var bundled: Catalog? = null
    @Volatile private var merged: Catalog? = null

    private fun bundled(context: Context): Catalog = bundled ?: run {
        val assets = context.applicationContext.assets
        val sets = runCatching { assets.open("studio/sets.json").bufferedReader().use { it.readText() } }.getOrNull()
        CatalogParser.parse(assets.open("studio/catalog.json").bufferedReader().use { it.readText() }, sets).also { bundled = it }
    }

    fun catalog(context: Context): Catalog = merged ?: synchronized(this) {
        merged ?: CatalogMerge.merge(bundled(context), StudioLink.remote(context)).also { merged = it }
    }

    fun invalidate() { merged = null }
}
