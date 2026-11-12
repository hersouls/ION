package app.roadmemory.car.data

/**
 * 이번 트립의 거리(km)를 오도미터(`Odometer.getCurrentDistanceDriven`)의 차이로 재요.
 * Pleos 에뮬레이터 2.1.0은 SDK의 트립 거리(`getTripCurrentDrivingDistance`)를 주지 않지만(ERROR_NOT_SUPPORTED_PROPERTY) 오도미터는 줘요(docs/DATA.md).
 * 기어가 P에서 벗어나는 순간의 오도미터가 출발점이에요. 앱을 주행 중에 켰다면 처음 읽은 값이 출발점이에요.
 */
class TripMeter {
    private var start: Float? = null
    private var odometer: Float? = null
    private var parked = true

    /** 기어가 바뀌었어요. 출발하는 순간(P → 그 밖)이면 0km를, 아니면 null을 돌려줘요. */
    fun onGear(park: Boolean): Float? {
        val departing = parked && !park
        parked = park
        if (!departing) return null
        start = odometer
        return if (odometer != null) 0f else null
    }

    /** 오도미터(km)가 새로 왔어요. 이번 트립 km를 돌려줘요(출발점을 모르면 null). */
    fun onOdometer(km: Float): Float? {
        odometer = km
        if (start == null && !parked) start = km
        return start?.let { (km - it).coerceAtLeast(0f) }
    }
}
