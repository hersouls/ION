package app.roadmemory.car.data

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 접수 시연용 가상 여행(오너 2026-11-11 "여행 기록 내용이 없음. 가상으로 여러개 만들어줘").
 * 에뮬레이터에서는 기어를 D로 달려 도착해 보지 않으면 여행 기록이 쌓이지 않아서, 처음 열 때 이 기기의 여행 기록에 한 번 넣어 둬요([TripStore]).
 * 장소 이름과 좌표(대략 100m 안)만 실제 장소이고, 날짜 · 거리 · 탄 사람 · 곡 · 충전은 지어낸 값이에요.
 * 지어낸 것이라는 표시는 번호(`id < 0`)로만 남겨요. 도착해서 쌓이는 진짜 기록은 1부터 번호가 붙어서 섞이지 않아요.
 * 곡은 앱에 든 연주곡([Tracks])의 제목만 써요. 출발 시각은 걸린 시간(멈춘 시간 포함)을 지어서 도착 시각에서 뺀 값이에요.
 */
object DemoTrips {
    private val seoul: ZoneId = ZoneId.of("Asia/Seoul")

    private fun trip(
        id: Long, y: Int, m: Int, d: Int, h: Int, min: Int,
        dest: String?, lat: Double?, lon: Double?, km: Int, people: Int, tracks: List<String>, charges: Int, minutes: Int,
    ): SavedTrip {
        val at = LocalDateTime.of(y, m, d, h, min).atZone(seoul).toInstant().toEpochMilli()
        return SavedTrip(
            id = id, at = at, month = "%04d.%02d".format(y, m), destName = dest, lat = lat, lon = lon,
            km = km, people = people, tracks = tracks, charges = charges, startedAt = at - minutes * 60_000L,
        )
    }

    /** 최근 것이 앞이에요([TripStore.all]과 같은 순서). 같은 곳(남이섬)을 두 번 넣어서 "이 길, N 전에"가 나오게 했어요. */
    val all: List<SavedTrip> = listOf(
        trip(-1, 2026, 11, 2, 17, 40, "가평 남이섬 선착장", 37.7913, 127.5259, 98, 3, listOf("October Maple Loop", "Paju Drive Breeze"), 0, 130),
        trip(-2, 2026, 10, 18, 15, 5, "양평 두물머리", 37.5453, 127.3145, 86, 2, listOf("October Maple Loop"), 0, 110),
        trip(-3, 2026, 9, 27, 12, 30, "담양 메타세쿼이아 가로수길", 35.3213, 126.9868, 298, 2, listOf("Yeongil Breeze Loop", "Rose & Pond Loop"), 1, 280),
        trip(-4, 2026, 8, 15, 11, 50, "강릉 경포해변", 37.8059, 128.9082, 241, 4, listOf("Solcho Sea Loop", "Harbor Breeze Lullaby"), 1, 250),
        trip(-5, 2026, 7, 19, 19, 10, null, null, null, 44, 1, emptyList(), 0, 65),
        trip(-6, 2026, 6, 6, 10, 20, "태안 꽃지해수욕장", 36.5020, 126.3366, 148, 4, listOf("Harbor Breeze Lullaby", "Yeongil Breeze Loop"), 0, 150),
        trip(-7, 2026, 4, 11, 14, 35, "전주 한옥마을", 35.8151, 127.1530, 214, 2, listOf("Rose & Pond Loop"), 1, 215),
        trip(-8, 2026, 1, 24, 13, 15, "평창 대관령 양떼목장", 37.6893, 128.7516, 187, 3, listOf("Paju Drive Breeze"), 1, 200),
        trip(-9, 2025, 11, 9, 16, 25, "속초 관광수산시장", 38.2058, 128.5909, 262, 3, listOf("Solcho Sea Loop", "Yeongil Breeze Loop"), 1, 270),
        trip(-10, 2025, 10, 12, 16, 50, "가평 남이섬 선착장", 37.7913, 127.5259, 101, 2, listOf("October Maple Loop"), 0, 125),
        trip(-11, 2025, 10, 4, 15, 40, "경주 불국사", 35.7898, 129.3320, 366, 2, listOf("October Maple Loop", "Rose & Pond Loop"), 2, 420),
        trip(-12, 2025, 5, 5, 13, 0, "파주 임진각", 37.8895, 126.7400, 61, 4, listOf("Paju Drive Breeze"), 0, 80),
    )

    /** 지어낸 여행인지(번호가 음수). */
    fun isDemo(t: SavedTrip): Boolean = t.id < 0
}
