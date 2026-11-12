package app.roadmemory.car.data

/** 차가 알려 주는 네 자리. Pleos 에뮬레이터 Virtual Console의 좌석과 같아요(뒷좌석 가운데는 없어요). */
enum class SeatPos(val label: String) {
    DRIVER("운전석"),
    PASSENGER("조수석"),
    REAR_LEFT("뒷좌석 왼쪽"),
    REAR_RIGHT("뒷좌석 오른쪽"),
}

enum class GearPos { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** 값이 어디서 오는지. PLEOS = 차(또는 Pleos 에뮬레이터), NONE = 차에 연결되지 않은 기기(값이 없어요. 가상 값을 만들지 않아요). */
enum class SourceKind { PLEOS, NONE }

data class SeatState(val occupied: Boolean? = null, val belted: Boolean? = null)

/** 열고 닫는 곳. 문 네 개와 트렁크 · 후드(Vehicle State SDK의 Door 영역). */
enum class CarPart(val label: String) {
    DRIVER_DOOR("운전석 문"), PASSENGER_DOOR("조수석 문"), REAR_LEFT_DOOR("뒷좌석 왼쪽 문"), REAR_RIGHT_DOOR("뒷좌석 오른쪽 문"),
    TRUNK("트렁크"), HOOD("후드"),
}

/** 온도를 어떤 단위로 말할지. 차의 표시 단위(Display.getCurrentTemperatureDisplayUnit)를 따라요. */
enum class TempUnit { CELSIUS, FAHRENHEIT }

/** Vehicle State SDK에서 읽는 값만 담아요. 값이 아직 안 왔으면 null이에요. */
data class CarSnapshot(
    val soc: Int? = null,
    val gear: GearPos? = null,
    val chargePortOpen: Boolean? = null,
    val seats: Map<SeatPos, SeatState> = emptyMap(),
    val tripKm: Float? = null,
    /** true = 열려 있음. 아직 모르는 곳은 맵에 없어요. */
    val open: Map<CarPart, Boolean> = emptyMap(),
    val washerLow: Boolean? = null,
    /**
     * 켜져 있는 경고등 중 경고만(Safety.getTelltalesInfo · 권한 PRIVATE_CAR_SAFETY를 받았다고 가정). null = 못 읽었어요(권한 · 차에 없음),
     * 빈 목록 = 읽었고 켜진 경고가 없어요.
     */
    val telltales: List<ActiveTelltale>? = null,
    /** 바퀴별 타이어 공기압의 원래 값(Tire.getTirePressure, 단위는 [TirePressure]가 가려요). 못 읽은 바퀴는 없어요. */
    val tirePressureRaw: Map<Wheel, Float> = emptyMap(),
    /** 실내 온도(섭씨). HVAC.getHvacTemperatureCurrent. */
    val cabinTempC: Float? = null,
    /** 외기온(섭씨): 차량 표준(AAOS) 속성 ENV_OUTSIDE_TEMPERATURE(CAR_EXTERIOR_ENVIRONMENT 권한). 못 읽으면 null. */
    val outsideTempC: Float? = null,
    /** 남은 주행거리(km): Vehicle SDK Odometer.getRangeRemainingDistance(미터로 와요). */
    val rangeKm: Float? = null,
    /** 충전 플러그가 꽂혀 있는지(차량 표준 EV_CHARGE_PORT_CONNECTED). 충전구가 열린 것과 달라요. */
    val plugConnected: Boolean? = null,
    val tempUnit: TempUnit = TempUnit.CELSIUS,
) {
    fun isOpen(p: CarPart) = open[p] == true
    val anyOpen: Boolean get() = open.values.any { it }
    fun occupied(p: SeatPos) = seats[p]?.occupied == true
    val rearOccupied: Boolean get() = occupied(SeatPos.REAR_LEFT) || occupied(SeatPos.REAR_RIGHT)
    val occupantCount: Int get() = seats.values.count { it.occupied == true }
}

/** NaviHelper의 경로 이벤트에서 읽는 값만 담아요(경로 좌표는 SDK가 주지 않아요). */
data class NavState(
    val routeActive: Boolean = false,
    val destinationName: String? = null,
    val destLat: Double? = null,
    val destLon: Double? = null,
    val remainDistanceM: Int? = null,
    val remainTimeS: Int? = null,
    val estimatedSocAtDest: Int? = null,
    /** 내비가 경로에 넣은 충전소(onDrivingInfo): 이름 · 도착 때 → 충전 뒤 배터리(%). 없으면 빈 목록. */
    val chargeStops: List<ChargeStop> = emptyList(),
)

/** 내비가 경로에 넣은 충전소 하나. 좌표 · 시간 단위는 SDK가 확실히 주지 않아서 이름과 배터리(%)만 써요. */
data class ChargeStop(val name: String, val startSoc: Int?, val endSoc: Int?)
