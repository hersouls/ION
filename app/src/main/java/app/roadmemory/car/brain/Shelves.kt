package app.roadmemory.car.brain

import app.roadmemory.car.UiState
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.Tracks
import app.roadmemory.car.data.Weather
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.engine.Taste
import app.roadmemory.car.studio.AlbumPhoto
import app.roadmemory.car.studio.Art
import app.roadmemory.car.studio.Stop
import app.roadmemory.car.studio.Theme
import app.roadmemory.car.studio.Trip
import java.time.LocalDate

/**
 * 서비스 화면의 "고르기"(하이엔드의 규칙 버전). 서버 · 모델 없이 이 기기에서, 차가 준 값과 이 기기에 있는 것으로만 골라요.
 * 고른 것에는 늘 까닭이 붙고, 맞는 것이 없으면 고르지 않아요(아무거나 내밀지 않아요).
 *  · [MusicShelf]: 지금 분위기에 맞는 순서 · 분위기 묶음 · 여행과 이어진 곡
 *  · [AlbumShelf]: 지역 · 해로 골라 보기 · 여행을 다녀온 달의 사진
 *  · [ArtShelf]: 갈래 · 지금 시간에 맞는 작품
 *  · [ThemeShelf]: 날씨 · 시간 · 탄 사람 · 취향에 맞는 테마
 *  · [TripLegs]: 정류장 사이 거리 · 날마다 합 · 충전이 필요한 구간
 */
object MusicShelf {
    data class Pick(val track: Tracks.Track, val hits: Set<String>)

    /** 분위기 묶음(이름 → 이 분위기 가운데 하나라도 가진 곡). 순서가 화면의 순서예요. */
    val groups: List<Pair<String, Set<String>>> = listOf(
        "달릴 때" to setOf("드라이브", "바다", "바람"),
        "쉬어 갈 때" to setOf("쉼", "충전"),
        "비 · 밤" to setOf("비", "밤"),
        "저녁 · 가을" to setOf("저녁", "가을", "흐림"),
    )

    fun tags(title: String): Set<String> = TrackMood.of(title)

    fun group(label: String): List<Tracks.Track> {
        val want = groups.firstOrNull { it.first == label }?.second ?: return emptyList()
        return Tracks.all.filter { t -> tags(t.title).any { it in want } }
    }

    /** 지금 분위기(날씨 · 시간 · 달 · 충전 중)에 맞는 곡을 많이 맞는 순서로. 하나도 안 맞는 곡은 빼요. */
    fun forNow(ui: UiState, hour: Int, month: Int): List<Pick> {
        val want = TrackMood.wanted(ui, hour, month)
        return Tracks.all.map { Pick(it, tags(it.title).intersect(want)) }.filter { it.hits.isNotEmpty() }.sortedByDescending { it.hits.size }
    }

    /** 지금 분위기를 말로: "저녁 · 가을 · 맑음". */
    fun nowLabel(ui: UiState, hour: Int, month: Int): String = TrackMood.wanted(ui, hour, month).joinToString(" · ")

    /** 저장한 여행에서 이 곡을 튼 적이 있으면 "여행 3번에 함께했어요 · 마지막 2026.10". 없으면 null. */
    fun tripLine(title: String, trips: List<SavedTrip>): String? {
        val with = trips.filter { title in it.tracks }
        if (with.isEmpty()) return null
        return "여행 ${with.size}번에 함께했어요 · 마지막 ${with.maxByOrNull { it.at }!!.month}"
    }

    /** 여행에서 가장 많이 튼 순서(튼 적이 있는 곡만). */
    fun mostWithTrips(trips: List<SavedTrip>): List<Pair<Tracks.Track, Int>> =
        Tracks.all.map { t -> t to trips.count { t.title in it.tracks } }.filter { it.second > 0 }.sortedByDescending { it.second }

    /** "0:42". */
    fun clock(ms: Int): String = (ms.coerceAtLeast(0) / 1000).let { "${it / 60}:${"%02d".format(it % 60)}" }
}

object AlbumShelf {
    /** 사진이 많은 지역 순서(지역을 모르는 사진은 세지 않아요). */
    fun regions(album: List<AlbumPhoto>): List<Pair<String, Int>> =
        album.filter { it.region.isNotBlank() }.groupingBy { it.region }.eachCount().toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    fun year(p: AlbumPhoto): String? = p.month.substringBefore('.').takeIf { it.length == 4 && it.all(Char::isDigit) }

    /** 최근 해가 앞. */
    fun years(album: List<AlbumPhoto>): List<Pair<String, Int>> =
        album.mapNotNull(::year).groupingBy { it }.eachCount().toList().sortedByDescending { it.first }

    /** 지역 · 해로 걸러요(null이면 그 조건은 보지 않아요). 순서는 그대로예요. */
    fun filter(album: List<AlbumPhoto>, region: String?, year: String?): List<AlbumPhoto> =
        album.filter { (region == null || it.region == region) && (year == null || year(it) == year) }

    private fun ym(month: String): Pair<Int, Int>? {
        val parts = month.split('.')
        val y = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return y to m
    }

    /** 여행을 다녀온 달에 찍은 사진(해와 달이 같아요). 사진의 위치는 읽지 않아서 "그 여행의 사진"이라고는 하지 않아요. */
    fun ofMonth(album: List<AlbumPhoto>, trip: SavedTrip): List<AlbumPhoto> {
        val want = ym(trip.month) ?: return emptyList()
        return album.filter { ym(it.month) == want }
    }

    /** 이맘때(같은 달, 다른 해 포함) 찍은 사진. */
    fun thisSeason(album: List<AlbumPhoto>, today: LocalDate): List<AlbumPhoto> = album.filter { ym(it.month)?.second == today.monthValue }
}

object ArtShelf {
    data class Pick(val art: Art, val hits: List<String>, val part: String)

    /** 갈래로 쓸 태그: 두 점 이상에 붙은 것(많은 순서). */
    fun kinds(arts: List<Art>, min: Int = 2): List<Pair<String, Int>> =
        arts.flatMap { it.tags }.groupingBy { it }.eachCount().filter { it.value >= min }.toList().sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })

    /** 시간대와 그때 어울리는 태그. */
    fun wanted(hour: Int): Pair<String, List<String>> = when {
        hour in 5..10 -> "아침" to listOf("아침", "일상")
        hour >= 19 || hour < 5 -> "밤" to listOf("밤", "은하수", "야영", "야경", "밤하늘", "오로라", "네온")
        else -> "낮" to listOf("쉼", "호수", "피크닉", "바다", "풍경", "하늘", "숲")
    }

    /** 지금 시간에 맞는 작품 한 점(태그가 가장 많이 맞는 것). 맞는 작품이 없으면 null. */
    fun forHour(arts: List<Art>, hour: Int): Pick? {
        val (part, want) = wanted(hour)
        val best = arts.maxByOrNull { a -> a.tags.count { it in want } } ?: return null
        val hits = best.tags.filter { it in want }
        return if (hits.isEmpty()) null else Pick(best, hits, part)
    }
}

object ThemeShelf {
    data class Pick(val theme: Theme, val why: List<String>)

    /** 지금 어울리는 테마 한 개와 까닭. 날씨 → 탄 사람 → 밤낮 순서로 바라는 태그를 정하고 취향(운전석 설정 온도)을 더해요. 맞는 테마가 없으면 null. */
    fun pick(ui: UiState, themes: List<Theme>, hour: Int): Pick? {
        if (themes.isEmpty()) return null
        val night = hour < 6 || hour >= 19
        val people = ui.car.occupantCount
        val want = buildList {
            when {
                ui.weather == Weather.RAIN || ui.weather == Weather.SNOW || ui.weather == Weather.FOG -> addAll(listOf("파스텔", "상아색", "하늘색", "호박색"))
                people >= 3 -> addAll(listOf("응원", "가상구단", "프로야구", "축구"))
                night -> addAll(listOf("네온", "보라", "남색", "무대", "팬라이트"))
                else -> addAll(listOf("응원", "청록", "초록", "바다색"))
            }
            when (ui.taste) { Taste.COOL -> addAll(listOf("청록", "하늘색", "바다색", "남색")); Taste.WARM -> addAll(listOf("호박색", "주황", "루비", "분홍")); else -> Unit }
        }
        val pick = themes.maxByOrNull { t -> t.tags.count { it in want } }?.takeIf { t -> t.tags.any { it in want } } ?: return null
        val why = buildList {
            ui.weather?.let { add("날씨 ${it.label}(와이퍼 · 등 신호로 추정)") }
            add(if (night) "지금 ${hour}시 · 밤이에요" else "지금 ${hour}시 · 낮이에요")
            if (people > 0) add("${people}명이 탔어요")
            if (ui.taste != Taste.NEUTRAL) add("운전석 설정 온도로 본 취향 · ${ui.taste.label}")
            add("화면 톤 ${ui.tone.label}")
            add("테마의 태그 ${Korean.gwa(pick.tags.filter { it in want }.joinToString(" · "))} 맞아요")
        }
        return Pick(pick, why)
    }
}

/**
 * 마이 트립의 정류장 사이: 앞 정류장에서 여기까지의 거리(직선 × 1.3)와, 지금 갈 수 있는 거리의 80%로 가다가 충전이 필요해지는 구간.
 * [TripPlan]과 같은 셈이고 화면이 정류장마다 한 줄씩 쓰기 좋게 풀어 놓은 거예요.
 */
object TripLegs {
    /** [km]은 앞 정류장에서 여기까지(첫 정류장 · 지나온 정류장은 null), [chargeBefore]는 여기 가기 전에 충전이 필요하다는 뜻이에요. */
    data class Row(val stop: Stop, val km: Double?, val chargeBefore: Boolean)

    fun rows(trip: Trip, rangeKm: Float?, fromIndex: Int = 0): List<Row> {
        val from = fromIndex.coerceIn(0, trip.stops.size)
        val plan = TripPlan.plan(trip, rangeKm, from)
        return trip.stops.mapIndexed { i, s ->
            val leg = plan.legs.getOrNull(i - from - 1)?.takeIf { i > from }
            Row(s, leg?.km, plan.chargeBefore.any { it === s })
        }
    }

    /** 정류장 한 줄 아래에 다는 말: "앞 정류장에서 약 12km" (+ " · 가기 전에 충전해요"). 말할 것이 없으면 null. */
    fun line(r: Row): String? = r.km?.let { "앞 정류장에서 약 ${fmt(it)}km" + if (r.chargeBefore) " · 가기 전에 충전해요" else "" }

    /** 날마다 정류장 사이 거리의 합(km). 하루의 첫 정류장으로 오는 길은 앞날 끝에서 오는 길이라 그날 합에 넣어요. */
    fun dayKm(trip: Trip): List<Double> {
        val rows = rows(trip, null)
        var n = 0
        return trip.days.map { day -> day.sumOf { rows[n++].km ?: 0.0 } }
    }

    private fun fmt(km: Double) = if (km < 10) "%.1f".format(km) else "${Math.round(km)}"

    /** 다녀온 기록 한 줄: "2026.11.11에 다녀왔어요 · 정류장 6곳 · 구간 합 약 49km". */
    fun doneLine(date: LocalDate, trip: Trip): String =
        "${date.year}.${date.monthValue}.${date.dayOfMonth}에 다녀왔어요 · 정류장 ${trip.stops.size}곳 · 구간 합 약 ${Math.round(TripPlan.plan(trip, null).totalKm)}km"
}
