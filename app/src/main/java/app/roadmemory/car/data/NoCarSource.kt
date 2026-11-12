package app.roadmemory.car.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 차(Android Automotive)가 아닌 기기: 읽을 값이 없어요. 가상 값을 만들어 보여 주지 않고, 화면이 "차에 연결되지 않았어요"라고 말해요. */
class NoCarSource : CarSource {
    override val kind = SourceKind.NONE
    override val car: StateFlow<CarSnapshot> = MutableStateFlow(CarSnapshot()).asStateFlow()
    override val nav: StateFlow<NavState> = MutableStateFlow(NavState()).asStateFlow()
    override val env: StateFlow<EnvSignals> = MutableStateFlow(EnvSignals()).asStateFlow()
    override val cabin: StateFlow<DriverCabin> = MutableStateFlow(DriverCabin()).asStateFlow()
    override val arrivals: SharedFlow<NaviArrival> = MutableSharedFlow<NaviArrival>()
    override val ux: StateFlow<UxLimit> = MutableStateFlow(UxLimit()).asStateFlow()
    override fun start() = Unit
    override fun stop() = Unit
}
