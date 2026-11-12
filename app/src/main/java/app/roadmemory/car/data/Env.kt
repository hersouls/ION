package app.roadmemory.car.data

/** 와이퍼 스위치의 크기만 줄여 둔 것. MIST(워셔액 한 번 쓸기)는 비로 세지 않아요. */
enum class WiperLevel { OFF, MIST, INTERMITTENT, CONTINUOUS, AUTO }

/**
 * 차가 알려 주는 환경 신호. Vehicle State SDK의 공개 API에서 읽는 것만 담아요(null = 아직 없거나 이 차가 지원하지 않아요).
 * 외기온 · 우적 센서 · 조도 · ABS/ESC · 눈길 모드는 공개 API가 없어서 여기에 없어요(docs/PLEOS.md §3.4).
 */
data class EnvSignals(
    val wiperOn: Boolean? = null,       // Wiper.getCurrentWiperState(앞유리) == ON
    val wiperLevel: WiperLevel? = null, // Wiper.getCurrentWiperSwitch(앞유리)
    val lowBeam: Boolean? = null,       // Light.getCurrentLowBeamLightState == ON
    val frontFog: Boolean? = null,      // Light.getCurrentFrontFogLightState == ON
    val rearFog: Boolean? = null,       // Light.getCurrentRearFogLightState == ON
    val defrost: Boolean? = null,       // HVAC.getHvacDefrosterState(앞유리)
    val maxDefrost: Boolean? = null,    // HVAC.getHvacMaxDefrostState(운전석)
)

/** 신호에서 추정한 환경. weather가 null이면 알 수 없어요(그때는 기본 색 톤이에요). reasons는 시연 패널의 "근거"예요. */
data class EnvState(val weather: Weather? = null, val reasons: List<String> = emptyList())

/**
 * 운전자석(1열 왼쪽) 공조 설정. 운전자의 취향을 읽는 자리라서 동승석 · 뒷좌석 구역의 설정은 읽지 않아요(운전자 중심).
 * setTempC는 섭씨로 읽어요(HVAC.getHvacTemperature(운전석, CELSIUS)). 공조가 꺼져 있으면 취향을 말하지 않은 것으로 봐요.
 */
data class DriverCabin(val setTempC: Float? = null, val hvacOn: Boolean? = null)
