package app.roadmemory.car.data

import java.time.YearMonth

/** 이 기기에 저장한 여행 한 건(도착할 때 쌓인 것, 또는 시연용 가상 여행 [DemoTrips]: 번호가 음수). 사진은 없고 기록(언제 · 어디로 · 몇 km · 몇 명 · 들은 곡 · 충전)만 남아요. */
data class SavedTrip(
    val id: Long,
    val at: Long,                 // 도착한 때(epoch ms)
    val month: String,            // "2026.11"
    val destName: String?,        // 내비에서 정한 목적지 이름(모르면 null)
    val lat: Double?,
    val lon: Double?,
    val km: Int,
    val people: Int,
    val tracks: List<String>,     // 앱이 틀어 준 곡의 제목
    val charges: Int,
    /** 출발한 때(epoch ms). 모르면 null(버전 3 이전에 쌓인 기록). */
    val startedAt: Long? = null,
)

/**
 * "기억"의 바탕: 이 기기에 저장된 여행들(도착할 때 쌓인 것 + 처음 열 때 한 번 들어가는 시연용 가상 여행 [DemoTrips]).
 * 같은 곳으로 길을 정하면 전에 다녀온 기록이 그 길의 기억이 돼요. 인터넷도 서버도 없이 기기 안에서만 돌아요(저장은 data/TripStore.kt).
 */
object TripBook {
    /** 저장된 여행, 최근 것이 앞이에요. 컨트롤러가 시작할 때 읽고 도착할 때마다 새로 읽어요. */
    @Volatile var saved: List<SavedTrip> = emptyList()

    /** 목적지가 같은 곳이라고 볼 거리(m). */
    const val SAME_PLACE_RADIUS_M = 1500

    /** 지금 가는 곳을 전에 다녀온 여행들(최근 것이 앞). */
    fun tripsTo(nav: NavState): List<SavedTrip> = tripsTo(saved, nav.destinationName, nav.destLat, nav.destLon)

    fun tripsTo(trips: List<SavedTrip>, name: String?, lat: Double?, lon: Double?): List<SavedTrip> =
        trips.filter { sameDestination(it, name, lat, lon) }.sortedByDescending { it.at }

    /** 이름이 같거나 한쪽이 다른 쪽을 품거나, 좌표가 반경 안이면 같은 곳이에요. */
    private fun sameDestination(t: SavedTrip, name: String?, lat: Double?, lon: Double?): Boolean {
        val a = t.destName
        if (!name.isNullOrBlank() && !a.isNullOrBlank() && (name.contains(a) || a.contains(name))) return true
        val tl = t.lat; val tn = t.lon
        if (lat == null || lon == null || tl == null || tn == null || lat.isNaN() || lon.isNaN()) return false
        return distanceM(lat, lon, tl, tn) <= SAME_PLACE_RADIUS_M
    }

    /** 다녀온 곳의 이름들(음성 명령이 "푸른 바닷가 기억"처럼 장소를 알아듣는 데 써요). */
    fun placeNames(trips: List<SavedTrip> = saved): List<String> = trips.mapNotNull { it.destName }.distinct()

    // ── 카드의 말 ──

    /** "2024.10" → 지금(now)에서 얼마나 지났는지 한 마디. 읽지 못하면 "예전"이에요. */
    fun agoLabel(month: String, now: YearMonth = YearMonth.now()): String {
        val ym = runCatching { YearMonth.of(month.substringBefore('.').toInt(), month.substringAfter('.').toInt()) }.getOrNull() ?: return "예전"
        val months = (now.year * 12 + now.monthValue) - (ym.year * 12 + ym.monthValue)
        return when {
            months >= 12 -> "${months / 12}년 전"
            months >= 2 -> "${months}달 전"
            months == 1 -> "지난달"
            else -> "이번 달"
        }
    }

    /** 함께 탄 사람 수를 한 마디로. 모르면(0) 빈 글이에요. */
    fun peopleWord(n: Int): String = when {
        n <= 0 -> ""
        n == 1 -> "혼자"
        n == 2 -> "둘이"
        n == 3 -> "셋이"
        n == 4 -> "넷이"
        n == 5 -> "다섯이"
        else -> "${n}명이"
    }

    private fun distanceM(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLon = Math.toRadians(bLon - aLon)
        val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(aLat)) * Math.cos(Math.toRadians(bLat)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(h))
    }
}
