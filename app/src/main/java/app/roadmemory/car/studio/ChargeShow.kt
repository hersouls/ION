package app.roadmemory.car.studio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.roadmemory.car.UiState
import app.roadmemory.car.brain.TrackMood
import app.roadmemory.car.data.Tracks
import java.time.LocalDate

/**
 * 충전 화면의 슬라이드쇼(하이엔드의 규칙 버전). 서버 · 모델 없이 이 기기에서, 차가 실제로 준 값과 이 기기의 사진으로만 돌아요.
 * 앨범 슬라이드쇼([Slideshow])의 고르기(가는 곳 근처 → 이맘때 → 나머지, 사진마다 이유 한 줄)를 그대로 쓰고, 충전하는 동안이라서 더해요:
 *  · 충전 시간에 맞춰요: 80%까지 남은 시간을 쟀으면 그 시간만큼만 틀고 끝내요(앞에서 고른 사진이 남아요). 못 쟀으면 한 바퀴를 돌리고 다시 처음부터 돌아요(시간을 지어내지 않아요).
 *  · 걸어 둔 디지털 아트를 앨범 사진 네 장마다 한 장씩 섞어요(한 바퀴에 작품 사진마다 한 번).
 *  · 슬라이드쇼 위에 충전 상태 한 줄(배터리 · 80%까지 남은 시간)이 같이 떠서 언제 가야 하는지 보여요. 80%가 되면 알려요.
 *  · 저녁 7시부터 아침 6시까지는 사진을 조금 어둡게 비춰요(밤의 차 안에서 눈부시지 않게).
 *  · 마이현대에서 가져온 곡이 있으면, 지금 분위기(날씨 · 시간 · 충전 중)에 맞는 곡을 함께 틀 수 있어요(끄는 것이 기본이에요).
 *  · 무엇을 볼지는 사람이 골라요([ChargeSource]): 디지털 아트 · 디지털 앨범 · 디지털 아트앨범(둘 다). 고른 쪽이 비어 있으면 있는 것을 보여 줘요.
 * 달리는 동안에는 틀지 않아요([Slideshow.allowed]).
 */
enum class ChargeSlideKind { ALBUM, ART }

/** 충전하는 사이 무엇을 볼지. 앨범과 걸어 둔 아트가 둘 다 있을 때 고를 수 있어요. */
enum class ChargeSource(val label: String) { ART("디지털 아트"), ALBUM("디지털 앨범"), BOTH("디지털 아트앨범") }

/** 슬라이드 한 장: 그림 경로 · 위에 띄우는 제목 · 아래에 띄우는 한 줄(고른 이유 · 찍은 달 · 지역) · 앨범인지 걸어 둔 아트인지. */
data class ChargeSlide(val image: String, val title: String, val note: String, val kind: ChargeSlideKind)

/** 보여 줄 차례. [fitted]가 true면 충전 남은 시간에 맞춰 잘랐다는 뜻이고, 이때는 끝까지 보면 끝나요(처음부터 다시 돌지 않아요). */
data class ChargePlan(val slides: List<ChargeSlide>, val fitted: Boolean, val seconds: Int) {
    val totalSec: Int get() = slides.size * seconds
    val albumCount: Int get() = slides.count { it.kind == ChargeSlideKind.ALBUM }
    val artCount: Int get() = slides.count { it.kind == ChargeSlideKind.ART }
}

/** 슬라이드쇼 위에 띄우는 충전 상태 한 줄. [reached]는 80%에 닿았다는 뜻이에요([text]가 null이면 띄울 말이 없어요). */
data class ChargeHud(val text: String?, val reached: Boolean)

object ChargeShow {
    /** 충전 화면이 말하는 목표 배터리(%). */
    const val TARGET_SOC = 80
    /** 앨범 사진 이만큼마다 걸어 둔 아트 한 장을 섞어요(설계 값). */
    const val ART_EVERY = 4
    /** 밤에 사진 위에 덮는 어둠(0~1, 설계 값). */
    const val NIGHT_DIM = 0.3f

    /** 밤(저녁 7시 ~ 아침 6시)이면 사진을 조금 어둡게 비춰요. 테마 고르기와 같은 기준이에요. */
    fun night(hour: Int) = hour < 6 || hour >= 19

    fun dim(hour: Int) = if (night(hour)) NIGHT_DIM else 0f

    /** 고른 것이 실제로 있는지 보고 정해요: 고른 쪽이 비어 있으면(동기화를 안 했거나 건 작품이 없으면) 있는 것을 다 보여 줘요. */
    fun effective(source: ChargeSource, hasAlbum: Boolean, hasArt: Boolean): ChargeSource = when {
        source == ChargeSource.ALBUM && !hasAlbum -> ChargeSource.BOTH
        source == ChargeSource.ART && !hasArt -> ChargeSource.BOTH
        else -> source
    }

    /** 고를 것이 있는지: 앨범과 걸어 둔 아트가 둘 다 있을 때만이에요. */
    fun canChoose(hasAlbum: Boolean, hasArt: Boolean) = hasAlbum && hasArt

    /** 걸어 둔 디지털 아트의 사진들(없으면 비어 있어요). 작품 사진이 없으면 표지 한 장이에요. */
    fun artSlides(art: Art?): List<ChargeSlide> = art?.let { a ->
        a.photos.ifEmpty { listOf(a.cover) }.map { ChargeSlide(it, "${a.title} · ${a.artist}", "걸어 둔 디지털 아트", ChargeSlideKind.ART) }
    }.orEmpty()

    /** 앨범 사진 [ART_EVERY]장마다 아트 한 장을 섞고, 남은 아트는 끝에 붙여요(한 장도 빠지거나 겹치지 않아요). 한쪽이 없으면 다른 쪽만이에요. */
    fun mix(album: List<ChargeSlide>, art: List<ChargeSlide>): List<ChargeSlide> {
        if (art.isEmpty()) return album
        if (album.isEmpty()) return art
        val out = ArrayList<ChargeSlide>(album.size + art.size)
        var a = 0
        album.forEachIndexed { k, s ->
            out += s
            if ((k + 1) % ART_EVERY == 0 && a < art.size) out += art[a++]
        }
        out += art.drop(a)
        return out
    }

    /**
     * 보여 줄 차례를 짜요. [minLeft]는 80%까지 남은 분(차가 안 알려 줘서 앱이 충전 속도를 재서 어림한 값, 못 쟀으면 null)이고,
     * [fit]이 켜져 있고 남은 시간을 알면 그 시간 안에 볼 장수([seconds]초씩)만큼만 앞에서 잘라요. 아니면 모두 한 번씩이에요.
     * [source]로 앨범만 · 아트만 볼 수 있어요([effective]: 고른 쪽이 비어 있으면 있는 것을 보여 줘요).
     */
    fun plan(
        album: List<AlbumPhoto>, art: Art?, order: SlideOrder, region: String?, today: LocalDate,
        minLeft: Int?, seconds: Int, fit: Boolean, seed: Long = today.toEpochDay(), source: ChargeSource = ChargeSource.BOTH,
    ): ChargePlan {
        val src = effective(source, album.isNotEmpty(), art != null)
        val albumSlides = if (src == ChargeSource.ART) emptyList()
        else Slideshow.plan(album, order, region, today, seed).map { ChargeSlide(it.photo.image, it.photo.title, it.note, ChargeSlideKind.ALBUM) }
        val mixed = mix(albumSlides, if (src == ChargeSource.ALBUM) emptyList() else artSlides(art))
        val n = if (fit && minLeft != null && minLeft > 0 && seconds > 0) (minLeft * 60 / seconds).coerceAtLeast(1) else null
        return if (n != null && n < mixed.size) ChargePlan(mixed.take(n), true, seconds) else ChargePlan(mixed, false, seconds)
    }

    /** "디지털 앨범 100장 · 걸어 둔 아트 3장". */
    fun sourceLine(albumCount: Int, artCount: Int): String = listOfNotNull(
        albumCount.takeIf { it > 0 }?.let { "디지털 앨범 ${it}장" },
        artCount.takeIf { it > 0 }?.let { "걸어 둔 아트 ${it}장" },
    ).joinToString(" · ")

    /**
     * 시간 한 줄. [plan]은 지금 설정으로 짠 차례이고, 충전 중이 아니면 한 바퀴 시간만 말해요. 충전 남은 시간을 모르면 모른다고 말하고 한 바퀴를 돌려요.
     * [fit]이 켜져 있어 남은 시간만큼 잘랐으면 "그동안 N장을 보고 끝나요"예요.
     */
    fun timingLine(plan: ChargePlan, charging: Boolean, minLeft: Int?, fit: Boolean): String {
        val lap = Slideshow.duration(plan.totalSec)
        return when {
            plan.slides.isEmpty() -> ""
            !charging -> "한 바퀴 $lap"
            minLeft == null || minLeft <= 0 -> "충전 남은 시간을 재는 중이에요 · 한 바퀴 $lap"
            plan.fitted -> "80%까지 약 ${minLeft}분 · 그동안 ${plan.slides.size}장을 보고 끝나요"
            fit -> "80%까지 약 ${minLeft}분 · 한 바퀴($lap)를 돌고 다시 처음부터 돌아요"
            else -> "80%까지 약 ${minLeft}분 · 한 바퀴 $lap, 끝나면 처음부터 다시 돌아요"
        }
    }

    /**
     * 슬라이드쇼 위의 충전 상태. 충전 중이면 배터리와 80%까지 남은 시간(못 쟀으면 재는 중이라고), 80%가 되면 알려요.
     * 충전이 끝난 뒤([startedCharging]인데 지금은 충전 중이 아님)에는 끝났다고만 말해요. 충전과 상관없이 열었으면 말하지 않아요.
     */
    fun hud(soc: Int?, charging: Boolean, minLeft: Int?, startedCharging: Boolean): ChargeHud = when {
        charging && soc != null && soc >= TARGET_SOC -> ChargeHud("충전 ${soc}% · ${TARGET_SOC}%가 됐어요", true)
        charging && soc != null -> ChargeHud("충전 ${soc}% · " + (minLeft?.takeIf { it > 0 }?.let { "${TARGET_SOC}%까지 약 ${it}분" } ?: "충전 속도를 재는 중이에요"), false)
        charging -> ChargeHud("충전 중이에요", false)
        startedCharging -> ChargeHud(soc?.let { "충전을 마쳤어요 · 배터리 $it%" } ?: "충전을 마쳤어요", false)
        else -> ChargeHud(null, false)
    }

    /** 80%를 향해 얼마나 찼는지(0~1). 배터리를 모르면 null. */
    fun fraction(soc: Int?): Float? = soc?.let { (it / TARGET_SOC.toFloat()).coerceIn(0f, 1f) }

    /** 함께 틀 곡: 지금 분위기(날씨 · 시간 · 충전 중)에 맞는 곡과 이유. 맞는 곡이 없으면 null(아무 곡이나 고르지 않아요). */
    fun music(ui: UiState, hour: Int, month: Int): Pair<Tracks.Track, String>? =
        TrackMood.pick(ui, hour, month)?.let { (track, hit) -> track to "분위기 ${hit.joinToString(" · ")}" }
}

/** 충전 슬라이드쇼의 고른 값: 무엇을 볼지 · 충전 시간에 맞출지 · 곡을 함께 틀지. 이 기기에 남아요. 순서 · 넘기는 시간은 앨범과 같은 [SlideshowPrefs]를 써요. */
object ChargeShowPrefs {
    var fit: Boolean by mutableStateOf(true)
        private set
    var music: Boolean by mutableStateOf(false)
        private set
    var source: ChargeSource by mutableStateOf(ChargeSource.BOTH)
        private set
    private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("charge_show", Context.MODE_PRIVATE)

    fun ensure(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        fit = p.getBoolean("fit", true)
        music = p.getBoolean("music", false)
        source = ChargeSource.entries.firstOrNull { it.name == p.getString("source", null) } ?: ChargeSource.BOTH
    }

    fun setFit(c: Context, v: Boolean) { fit = v; prefs(c).edit().putBoolean("fit", v).apply() }
    fun setSource(c: Context, v: ChargeSource) { source = v; prefs(c).edit().putString("source", v.name).apply() }
    fun setMusic(c: Context, v: Boolean) { music = v; prefs(c).edit().putBoolean("music", v).apply() }
}
