package app.roadmemory.car.data

import kotlin.math.cos

/**
 * 여행 기록 세부 화면의 작은 지도: 대한민국 윤곽(개략도)과 위도 · 경도 → 그림 좌표.
 * 지도 타일 · 인터넷 없이 그려요. 윤곽은 해안선의 주요 지점을 손으로 옮긴 개략도(수 km 오차)라서 길 찾기 · 거리에 쓰지 않고, 도착지가 한반도 어디쯤인지만 보여 줘요.
 * 점의 위치(위도 · 경도)는 여행 기록에 저장된 내비 목적지 좌표 그대로예요. 출발지는 읽지 않아서 지도에 경로선은 그리지 않아요.
 */
object KoreaMap {
    /** 그림이 덮는 범위(차로 갈 수 있는 한반도 남쪽. 제주는 빼요). */
    const val LAT_MIN = 34.05
    const val LAT_MAX = 38.75
    const val LON_MIN = 125.95
    const val LON_MAX = 129.75

    /** 위도 36° 근처에서 경도 1°는 위도 1°의 이만큼이에요. */
    private val lonScale = cos(Math.toRadians(36.0))

    /** 그림 상자의 가로 ÷ 세로. */
    val aspect: Double = (LON_MAX - LON_MIN) * lonScale / (LAT_MAX - LAT_MIN)

    /** (위도, 경도) → 상자 안 좌표(0..1, 왼쪽 위가 0). 범위 밖이면 null. */
    fun project(lat: Double, lon: Double): Pair<Float, Float>? {
        if (lat.isNaN() || lon.isNaN() || lat < LAT_MIN || lat > LAT_MAX || lon < LON_MIN || lon > LON_MAX) return null
        return ((lon - LON_MIN) / (LON_MAX - LON_MIN)).toFloat() to ((LAT_MAX - lat) / (LAT_MAX - LAT_MIN)).toFloat()
    }

    /** 동쪽 휴전선 끝에서 남쪽 · 서쪽 해안을 돌아 서쪽 휴전선 끝까지(위도, 경도). 부드러운 곡선으로 이어 그려요. */
    val coast: List<Pair<Double, Double>> = listOf(
        38.62 to 128.36, 38.45 to 128.43, 38.20 to 128.59, 37.97 to 128.76, 37.75 to 128.96, 37.50 to 129.10, 37.20 to 129.33,
        36.95 to 129.40, 36.68 to 129.46, 36.35 to 129.40, 36.10 to 129.40, 36.07 to 129.58, 35.90 to 129.52, 35.60 to 129.46,
        35.35 to 129.36, 35.15 to 129.22, 35.05 to 129.05, 35.00 to 128.85, 34.92 to 128.65, 34.85 to 128.45, 34.93 to 128.10,
        34.80 to 127.95, 34.72 to 127.75, 34.65 to 127.50, 34.45 to 127.30, 34.55 to 127.10, 34.40 to 126.90, 34.30 to 126.52,
        34.48 to 126.28, 34.70 to 126.30, 34.85 to 126.35, 35.10 to 126.40, 35.40 to 126.45, 35.62 to 126.55, 35.85 to 126.60,
        36.00 to 126.62, 36.30 to 126.52, 36.55 to 126.32, 36.85 to 126.15, 36.98 to 126.38, 37.10 to 126.50, 37.22 to 126.68,
        37.38 to 126.62, 37.55 to 126.60, 37.72 to 126.45, 37.88 to 126.68, 37.95 to 126.76,
    )

    /** 서쪽에서 동쪽으로 가는 휴전선의 가운데 지점들(곧은 선으로 이어요). */
    val dmz: List<Pair<Double, Double>> = listOf(38.10 to 127.10, 38.28 to 127.50, 38.33 to 127.95, 38.50 to 128.20)

    /** 길잡이로 찍는 큰 도시(이름, 위도, 경도). */
    val cities: List<Triple<String, Double, Double>> = listOf(Triple("서울", 37.5665, 126.9780), Triple("부산", 35.1796, 129.0756))
}
