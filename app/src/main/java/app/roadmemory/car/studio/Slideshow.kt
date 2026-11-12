package app.roadmemory.car.studio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.util.Random

/**
 * 디지털 앨범 슬라이드쇼의 규칙(서버 · 모델 없이 이 기기에서 돌아요). 하이엔드의 "ION이 그날의 사진을 골라요"를 규칙 버전으로 한 거예요.
 *
 *  · ION이 골라서: 가는 일정의 지역과 같은 지역의 사진 → 같은 철(이맘때, 지난 해들)의 사진 → 나머지(오늘 날짜로 섞은 순서). 사진마다 고른 이유를 한 줄로 붙여요.
 *  · 순서대로: 앨범에 든 순서 그대로. 섞어서: 시작할 때마다 새로 섞어요.
 *  · 서 있을 때만 틀어요(달리는 중에는 사진을 틀지 않아요). 끝나면 처음부터 다시 돌아요.
 */
enum class SlideOrder(val label: String) { ION("ION이 골라서"), SEQUENCE("순서대로"), SHUFFLE("섞어서") }

/** 슬라이드 한 장: 사진과, 화면 아래에 띄우는 한 줄(고른 이유 · 찍은 달 · 지역). */
data class Slide(val photo: AlbumPhoto, val note: String)

object Slideshow {
    /** 한 장을 보여 주는 시간(초) 선택지와 처음 값. */
    val SECONDS = listOf(4, 7, 12)
    const val DEFAULT_SECONDS = 7

    /** 달리는 중에는 틀지 않아요. */
    fun allowed(driving: Boolean) = !driving

    private fun year(p: AlbumPhoto): Int? = p.month.substringBefore('.').toIntOrNull()
    private fun month(p: AlbumPhoto): Int? = p.month.substringAfter('.', "").toIntOrNull()

    /** 가는 일정의 지역에 든 사진인지(일정 지역이 "강원 속초 · 고성"이고 사진 지역이 "강원"이면 맞아요). */
    fun isNear(p: AlbumPhoto, tripRegion: String?) = tripRegion != null && p.region.isNotBlank() && tripRegion.contains(p.region)

    /** 지난 해들의 이맘때(오늘 달에서 앞뒤 한 달 안)이면 몇 해 전인지, 아니면 null. 올해 사진은 이맘때라고 하지 않아요. */
    fun yearsAgoThisSeason(p: AlbumPhoto, today: LocalDate): Int? {
        val y = year(p) ?: return null
        val m = month(p) ?: return null
        val diff = Math.abs(m - today.monthValue).let { minOf(it, 12 - it) }
        return (today.year - y).takeIf { it >= 1 && diff <= 1 }
    }

    private fun plain(p: AlbumPhoto) = if (p.region.isNotBlank()) "${p.month} · ${p.region}" else p.month

    /** 보여 줄 순서. 사진은 한 장도 빠지거나 겹치지 않고 모두 한 번씩 들어가요([seed]가 같으면 같은 순서예요). */
    fun plan(album: List<AlbumPhoto>, order: SlideOrder, tripRegion: String?, today: LocalDate, seed: Long = today.toEpochDay()): List<Slide> = when (order) {
        SlideOrder.SEQUENCE -> album.map { Slide(it, plain(it)) }
        SlideOrder.SHUFFLE -> album.shuffled(Random(seed)).map { Slide(it, plain(it)) }
        SlideOrder.ION -> {
            val near = album.filter { isNear(it, tripRegion) }
            val nearIds = near.map { it.id }.toSet()
            val season = album.filter { it.id !in nearIds && yearsAgoThisSeason(it, today) != null }
                .sortedWith(compareBy({ Math.abs((month(it) ?: 0) - today.monthValue).let { d -> minOf(d, 12 - d) } }, { -(year(it) ?: 0) }))
            val seasonIds = season.map { it.id }.toSet()
            val rest = album.filter { it.id !in nearIds && it.id !in seasonIds }.shuffled(Random(seed))
            near.map { Slide(it, "가는 곳 근처 · ${plain(it)}") } +
                season.map { Slide(it, "${yearsAgoThisSeason(it, today)}년 전 이맘때 · ${plain(it)}") } +
                rest.map { Slide(it, plain(it)) }
        }
    }

    /** "11분 40초" · "8분" · "45초". */
    fun duration(totalSec: Int): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return when {
            m == 0 -> "${s}초"
            s == 0 -> "${m}분"
            else -> "${m}분 ${s}초"
        }
    }

    /** 앨범 한 바퀴 시간 안내: "사진 100장 · 한 바퀴 11분 40초". */
    fun lapText(count: Int, seconds: Int) = "사진 ${count}장 · 한 바퀴 ${duration(count * seconds)}"

    /** 충전 중이고 80%까지 남은 시간을 쟀으면, 그동안 몇 장을 볼 수 있는지. 모르면 null(지어내지 않아요). */
    fun chargeFit(minLeft: Int?, count: Int, seconds: Int): String? {
        if (minLeft == null || minLeft <= 0 || count <= 0) return null
        val n = minLeft * 60 / seconds
        return if (n >= count) "충전 80%까지 약 ${minLeft}분 · 그동안 한 바퀴 넘게 돌아요" else "충전 80%까지 약 ${minLeft}분 · 그동안 ${n}장을 봐요"
    }
}

/** 슬라이드쇼의 고른 값(순서 · 시간)은 이 기기에 남아요. 앨범 화면과 크게 보기가 같은 값을 써요. */
object SlideshowPrefs {
    var order: SlideOrder by mutableStateOf(SlideOrder.ION)
        private set
    var seconds: Int by mutableStateOf(Slideshow.DEFAULT_SECONDS)
        private set
    private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("slideshow", Context.MODE_PRIVATE)

    fun ensure(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        order = SlideOrder.entries.firstOrNull { it.name == p.getString("order", null) } ?: SlideOrder.ION
        seconds = p.getInt("seconds", Slideshow.DEFAULT_SECONDS).takeIf { it in Slideshow.SECONDS } ?: Slideshow.DEFAULT_SECONDS
    }

    fun setOrder(c: Context, o: SlideOrder) { order = o; prefs(c).edit().putString("order", o.name).apply() }
    fun setSeconds(c: Context, s: Int) { seconds = s; prefs(c).edit().putInt("seconds", s).apply() }
}

/**
 * 말로 "슬라이드쇼 틀어 줘 · 멈춰"를 하면 앨범 화면(충전 중이면 충전 화면)이 이 값을 보고 시작하거나 멈춰요(한 번 읽으면 [clear]로 비워요).
 * 말을 받은 화면이 열리지 않아 읽지 못한 명령이 나중에 화면이 열릴 때 뜬금없이 시작되지 않게, 15초가 지난 명령은 없는 것으로 쳐요.
 */
object SlideshowControl {
    enum class Cmd { START, STOP }

    /** 시험에서 시계를 바꿔 끼우는 자리. */
    internal var clock: () -> Long = { System.currentTimeMillis() }
    private const val VALID_MS = 15_000L

    private var raw: Cmd? by mutableStateOf(null)
    private var at = 0L

    val command: Cmd? get() = raw?.takeIf { clock() - at <= VALID_MS }

    fun start() { at = clock(); raw = Cmd.START }
    fun stop() { at = clock(); raw = Cmd.STOP }
    fun clear() { raw = null }
}
