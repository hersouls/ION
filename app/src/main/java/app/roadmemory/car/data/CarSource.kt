package app.roadmemory.car.data

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 차의 신호가 어디서 오든 앱은 이 모양으로만 봐요(Pleos SDK, 또는 차가 아닌 기기에서는 빈 값). */
interface CarSource {
    val kind: SourceKind
    val car: StateFlow<CarSnapshot>
    val nav: StateFlow<NavState>
    /** 차가 알려 주는 환경 신호(와이퍼 · 전조등 · 안개등 · 김서림 제거). 비 · 안개 추정은 engine/EnvEngine.kt가 해요. */
    val env: StateFlow<EnvSignals>
    /** 운전자석(1열 왼쪽)의 공조 설정. 운전자의 취향이 색 톤에 닿아요(승객 구역은 읽지 않아요). */
    val cabin: StateFlow<DriverCabin>
    /** 내비가 알려 주는 도착(경유지 · 목적지). 마이 트립이 정류장에 도착했는지 알아차리는 데 써요. */
    val arrivals: SharedFlow<NaviArrival>
    /** 주행 규제(CarUxRestrictions). 차가 아니거나 못 읽으면 known=false(기어로만 모드를 나눠요). */
    val ux: StateFlow<UxLimit>
    fun start()
    fun stop()
}
