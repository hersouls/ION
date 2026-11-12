package app.roadmemory.car.data

import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.control.WiperSwitch
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.RangeRemainingListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.TemperatureDisplayUnitListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.EvChargePortOpenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.HvacDefrosterStateListener
import ai.pleos.playground.vehicle.listener.HvacMaxDefrostStateListener
import ai.pleos.playground.vehicle.listener.HvacPowerStateListener
import ai.pleos.playground.vehicle.listener.HvacTemperatureListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos Connect 차(또는 에뮬레이터)의 신호를 읽어요. Vehicle State SDK(좌석 · 배터리 · 기어 · 충전구)와
 * NaviHelper SDK(목적지 · 도착 예상 배터리) 이벤트만 써요. 위치를 계속 읽지 않아요.
 *
 * 이 기기에 Pleos 서비스가 없으면 [tryStart]가 false를 돌려줘요(그때 앱은 번들 가상 값으로 가요).
 */
class PleosCarSource(private val context: Context) : CarSource {
    override val kind = SourceKind.PLEOS

    private val _car = MutableStateFlow(CarSnapshot())
    private val _nav = MutableStateFlow(NavState())
    override val car: StateFlow<CarSnapshot> = _car.asStateFlow()
    override val nav: StateFlow<NavState> = _nav.asStateFlow()
    private val _env = MutableStateFlow(EnvSignals())
    override val env: StateFlow<EnvSignals> = _env.asStateFlow()
    private val _cabin = MutableStateFlow(DriverCabin())
    override val cabin: StateFlow<DriverCabin> = _cabin.asStateFlow()

    private val _ux = MutableStateFlow(UxLimit())
    override val ux: StateFlow<UxLimit> = _ux.asStateFlow()

    private var vehicle: Vehicle? = null
    private var navi: NaviHelper? = null
    private var uxReader: UxRestrictionsReader? = null
    private val _arrivals = kotlinx.coroutines.flow.MutableSharedFlow<NaviArrival>(extraBufferCapacity = 8)
    override val arrivals: kotlinx.coroutines.flow.SharedFlow<NaviArrival> = _arrivals
    // 구역을 물어본 뒤 콜백 스레드에서도 붙이므로 동기화된 목록이에요
    private val unregister = java.util.Collections.synchronizedList(mutableListOf<() -> Unit>())
    private var naviListener: NaviHelperEventListener? = null

    private val seatAreas = mapOf(
        SeatPos.DRIVER to SeatValue.ROW_1_LEFT,
        SeatPos.PASSENGER to SeatValue.ROW_1_RIGHT,
        SeatPos.REAR_LEFT to SeatValue.ROW_2_LEFT,
        SeatPos.REAR_RIGHT to SeatValue.ROW_2_RIGHT,
    )

    /** SDK를 붙여 봐요. 한 갈래(차량 신호 / 내비)가 실패해도 다른 갈래는 계속 읽어요. 하나라도 붙으면 true. */
    fun tryStart(): Boolean {
        var ok = false
        // 주행 규제(Pleos 가이드 driving-restriction-policy): SDK가 붙든 안 붙든 플랫폼의 CarUxRestrictions를 먼저 구독해요
        runCatching { UxRestrictionsReader(context.applicationContext) { l -> _ux.value = l }.also { it.start(); uxReader = it } }
            .onFailure { Log.w(TAG, "UxRestrictions not connected: ${it.javaClass.simpleName}: ${it.message}") }
        try {
            val v = Vehicle(context.applicationContext)
            v.initialize()
            vehicle = v
            subscribeVehicle(v)
            ok = true
        } catch (t: Throwable) {
            Log.w(TAG, "Vehicle State SDK not connected: ${t.javaClass.simpleName}: ${t.message}")
        }
        try {
            startNavi()
            ok = true
        } catch (t: Throwable) {
            Log.w(TAG, "NaviHelper SDK not connected: ${t.javaClass.simpleName}: ${t.message}")
        }
        return ok
    }

    override fun start() { /* tryStart()로 시작해요 */ }

    override fun stop() {
        unregister.toList().forEach { runCatching { it() } }
        unregister.clear()
        naviListener?.let { l -> runCatching { navi?.removeListener(l) } }
        runCatching { navi?.release() }
        runCatching { vehicle?.release() }
        runCatching { uxReader?.stop() }
        navi = null; vehicle = null; naviListener = null; uxReader = null
    }

    /** 한 영역이 권한이 없거나 이 차에 없어서 바로 예외를 던져도, 나머지 영역은 계속 붙여요(샘플 앱: 권한 누락 · 미지원은 영역마다 따로 드러나요). */
    private inline fun part(name: String, block: () -> Unit) {
        runCatching(block).onFailure { Log.w(TAG, "$name not connected: ${it.javaClass.simpleName}: ${it.message}") }
    }

    /**
     * Pleos API policy(2026-11-13 대조): 지원 여부는 도메인별 checkXxxCapabilities로 먼저 물어요. 읽기(READ · READ_WRITE)를 지원하고
     * 우리가 읽을 구역이 답에 있을 때만 값을 읽고 구독해요. 묻기가 실패하거나 NONE이면 그 값은 null로 남고 화면은 "모름"으로 그려요.
     * 답은 다른 스레드에서 올 수 있어서 붙이는 일도 [part]로 감싸요. 기어 · 남은 주행거리는 SDK 2.3.2에 묻는 API가 없어서 바로 읽어요.
     */
    private fun canRead(name: String, cfg: CapabilityConfig?): Boolean {
        val ok = cfg?.accessType == AccessType.READ || cfg?.accessType == AccessType.READ_WRITE
        Log.i(TAG, "능력 $name=${cfg?.accessType}")
        return ok
    }

    private fun noCapability(name: String): (Exception) -> Unit = { e -> Log.w(TAG, "능력 $name=모름: ${e.javaClass.simpleName}: ${e.message}") }

    private fun Collection<SeatArea>.covers(v: SeatValue) = any { a -> a.values.any { it == v } }
    private fun Collection<DoorArea>.coversDoor(v: DoorValue) = any { a -> a.values.any { it == v } }
    private fun Collection<WindowArea>.coversWindow(v: WindowValue) = any { a -> a.values.any { it == v } }

    private fun subscribeVehicle(v: Vehicle) {
        part("EV battery") {
            val ev = v.getEvBattery()
            ev.checkEvBatteryStateOfChargeCapabilities({ c ->
                if (canRead("EV battery", c)) part("EV battery") {
                    ev.getEvBatteryStateOfCharge({ soc -> _car.update { it.copy(soc = soc) } }, ::fail)
                    val socL = object : EvBatteryStateOfChargeListener {
                        override fun onEvBatteryStateOfChargeUpdated(soc: Int?) { _car.update { it.copy(soc = soc) } }
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    ev.registerEvBatteryStateOfCharge(socL)
                    unregister += { ev.unregisterEvBatteryStateOfCharge(socL) }
                }
            }, noCapability("EV battery"))
            ev.checkEvChargePortOpenCapabilities({ c ->
                if (canRead("Charge port", c)) part("Charge port") {
                    ev.isEvChargePortOpen({ open -> _car.update { it.copy(chargePortOpen = open) } }, ::fail)
                    val portL = object : EvChargePortOpenListener {
                        override fun onEvChargePortOpenUpdated(open: Boolean?) { _car.update { it.copy(chargePortOpen = open) } }
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    ev.registerEvChargePortOpen(portL)
                    unregister += { ev.unregisterEvChargePortOpen(portL) }
                }
            }, noCapability("Charge port"))
        }

        part("Gear") {
            val gear = v.getGear()
            gear.getCurrentGearSelectionState({ g -> onGear(g.toGearPos()) }, ::fail)
            val gearL = object : GearSelectionListener {
                override fun onSelected(g: VehicleGear?) { onGear(g.toGearPos()) }
                override fun onFailed(e: Exception) = fail(e)
            }
            gear.registerGearSelection(gearL)
            unregister += { gear.unregisterGearSelection(gearL) }
        }

        part("Seat") {
            val seat = v.getSeat()
            seat.checkSeatOccupancyCapabilities({ c, areas ->
                if (canRead("Seat occupancy", c)) for ((pos, value) in seatAreas) if (areas.covers(value)) part("Seat occupancy ${pos.name}") {
                    val area = SeatArea(value)
                    seat.getCurrentSeatOccupancy(area, { occ -> setSeat(pos) { it.copy(occupied = seatOccupied(occ)) } }, ::fail)
                    val occL = object : SeatOccupancyListener {
                        override fun onSeatOccupancyUpdated(occupied: Boolean?) { setSeat(pos) { it.copy(occupied = seatOccupied(occupied)) } }
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    seat.registerSeatOccupancy(area, occL)
                    unregister += { seat.unregisterSeatOccupancy(area, occL) }
                }
            }, noCapability("Seat occupancy"))
            seat.checkSeatBeltBuckledCapabilities({ c, areas ->
                if (canRead("Seat belt", c)) for ((pos, value) in seatAreas) if (areas.covers(value)) part("Seat belt ${pos.name}") {
                    val area = SeatArea(value)
                    seat.getCurrentSeatBeltBuckledState(area, { b -> setSeat(pos) { it.copy(belted = b) } }, ::fail)
                    val beltL = object : SeatBeltBuckledStateListener {
                        override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) { setSeat(pos) { it.copy(belted = buckled) } }
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    seat.registerSeatBeltBuckledState(area, beltL)
                    unregister += { seat.unregisterSeatBeltBuckledState(area, beltL) }
                }
            }, noCapability("Seat belt"))
        }

        part("Odometer domain") {
            val odo = v.getOdometer()
            odo.checkTripCurrentDrivingDistanceCapabilities({ c ->
                if (canRead("Trip distance", c)) part("Trip distance") {
                    odo.getTripCurrentDrivingDistance({ d -> tripFromSdk = true; _car.update { it.copy(tripKm = d) } }, ::fail)
                    val distL = object : DistanceDrivenListener {
                        override fun onDistanceDrivenUpdated(d: Float?) { if (d != null) tripFromSdk = true; _car.update { it.copy(tripKm = d) } }
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    odo.registerTripCurrentDrivingDistance(distL)
                    unregister += { odo.unregisterTripCurrentDrivingDistance(distL) }
                }
            }, noCapability("Trip distance"))
            // 이 차량이 트립 거리를 안 주면(Pleos 에뮬레이터 2.1.0) 오도미터의 차이로 재요(TripMeter, docs/DATA.md)
            odo.checkCurrentDistanceDrivenCapabilities({ c ->
                if (canRead("Odometer", c)) part("Odometer") {
                    odo.getCurrentDistanceDriven({ d -> onOdometer(d) }, ::fail)
                    val odoL = object : DistanceDrivenListener {
                        override fun onDistanceDrivenUpdated(d: Float?) { onOdometer(d) }
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    odo.registerDistanceDriven(odoL)
                    unregister += { odo.unregisterDistanceDriven(odoL) }
                }
            }, noCapability("Odometer"))
            part("Range") {
                // 남은 주행거리: 이 SDK는 미터로 줘요(Virtual Console도 remaining_range_m). 묻는 API가 없어서 바로 읽어요.
                odo.getRangeRemainingDistance({ d -> onRange(d) }, ::fail)
                val rangeL = object : RangeRemainingListener {
                    override fun onRangeRemainingChanged(d: Float?) { onRange(d) }
                    override fun onFailed(e: Exception) = fail(e)
                }
                odo.registerRangeRemainingListener(rangeL)
                unregister += { odo.unregisterRangeRemainingListener(rangeL) }
            }
        }

        // 외기온 · 충전 플러그 연결은 Pleos SDK에 없고 차량 표준(AAOS)에서 읽어요(권한이 없거나 차가 아니면 조용히 건너뛰어요)
        runCatching {
            val aaos = AaosReader(context.applicationContext) { t, plug ->
                _car.update { it.copy(outsideTempC = t ?: it.outsideTempC, plugConnected = plug ?: it.plugConnected) }
            }
            aaos.start()
            unregister += { aaos.stop() }
        }.onFailure { Log.w(TAG, "AAOS not connected: ${it.javaClass.simpleName}: ${it.message}") }

        // 환경 신호는 도메인마다 따로 붙여요. 이 차가 지원하지 않거나 권한이 없는 것이 있어도 나머지는 읽어요.
        part("Wiper") { subscribeWiper(v) }
        part("Light") { subscribeLights(v) }
        part("HVAC defrost") { subscribeDefrost(v) }
        part("Body") { subscribeBody(v) }
        part("HVAC driver zone") { subscribeDriverCabin(v) }
    }

    /** 와이퍼: 앞유리가 쓸 때마다(ON) 스위치도 한 번 읽어서, 워셔액 한 번 쓸기(MIST)를 비와 가려요. 상태 · 스위치를 따로 물어요. */
    private fun subscribeWiper(v: Vehicle) {
        val wiper = v.getWiper()
        val front = WindowValue.FRONT_WINDSHIELD
        wiper.checkWiperStateCapabilities({ c, states ->
            if (canRead("Wiper state", c) && states.keys.coversWindow(front)) {
                wiper.checkWiperSwitchCapabilities(
                    { sc, switches -> part("Wiper") { startWiper(wiper, canRead("Wiper switch", sc) && switches.keys.coversWindow(front)) } },
                    { e -> noCapability("Wiper switch")(e); part("Wiper") { startWiper(wiper, withSwitch = false) } },
                )
            }
        }, noCapability("Wiper state"))
    }

    private fun startWiper(wiper: ai.pleos.playground.vehicle.api.Wiper, withSwitch: Boolean) {
        val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
        var readable = true // 이 차가 와이퍼를 지원하지 않으면 한 번만 알리고 더 묻지 않아요
        val failWiper: (Exception) -> Unit = { e -> readable = false; fail(e) }
        fun readSwitch() { if (withSwitch) wiper.getCurrentWiperSwitch(area, { sw -> _env.update { it.copy(wiperLevel = sw.toLevel()) } }, failWiper) }
        fun readState() = wiper.getCurrentWiperState(area, { st -> _env.update { it.copy(wiperOn = st == WiperState.ON) } }, failWiper)
        readState()
        readSwitch()
        // 스위치는 구독할 방법이 없고(상태만 알림이 와요), 연속 모드에서는 상태 알림도 한 번뿐이라서 5초마다 둘을 읽어요
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val poll = object : Runnable {
            override fun run() {
                if (!readable) return
                readState(); readSwitch()
                handler.postDelayed(this, WIPER_POLL_MS)
            }
        }
        handler.postDelayed(poll, WIPER_POLL_MS)
        unregister += { handler.removeCallbacks(poll) }
        val l = object : WiperStateListener {
            override fun onWiperStateUpdated(state: WiperState?) {
                _env.update { it.copy(wiperOn = state == WiperState.ON) }
                readSwitch()
            }
            override fun onFailed(e: Exception) = fail(e)
        }
        wiper.registerWiperState(area, l)
        unregister += { wiper.unregisterWiperState(area, l) }
    }

    /** 전조등(하향) · 앞 안개등 · 뒤 안개등. 주간주행등(DAYTIME_RUNNING)은 켜진 것으로 세지 않아요. 등마다 지원 여부를 따로 물어요. */
    private fun subscribeLights(v: Vehicle) {
        val light = v.getLight()
        fun lamp(name: String, check: ((CapabilityConfig) -> Unit) -> Unit, read: ((LightState?) -> Unit) -> Unit, register: (LightStateListener) -> Unit, unreg: (LightStateListener) -> Unit, set: (EnvSignals, Boolean) -> EnvSignals) {
            check { c ->
                if (canRead(name, c)) part(name) {
                    read { st -> _env.update { set(it, st == LightState.ON) } }
                    val l = object : LightStateListener {
                        override fun onLightStateUpdated(state: LightState?) { _env.update { set(it, state == LightState.ON) } }
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    register(l)
                    unregister += { unreg(l) }
                }
            }
        }
        lamp("Low beam", { ok -> light.checkLowBeamLightStateCapabilities({ c, _ -> ok(c) }, noCapability("Low beam")) },
            { cb -> light.getCurrentLowBeamLightState({ cb(it) }, ::fail) }, light::registerLowBeamLightState, light::unregisterLowBeamLightState) { s, on -> s.copy(lowBeam = on) }
        lamp("Front fog", { ok -> light.checkFrontFogLightStateCapabilities({ c, _ -> ok(c) }, noCapability("Front fog")) },
            { cb -> light.getCurrentFrontFogLightState({ cb(it) }, ::fail) }, light::registerFrontFogLightState, light::unregisterFrontFogLightState) { s, on -> s.copy(frontFog = on) }
        lamp("Rear fog", { ok -> light.checkRearFogLightStateCapabilities({ c, _ -> ok(c) }, noCapability("Rear fog")) },
            { cb -> light.getCurrentRearFogLightState({ cb(it) }, ::fail) }, light::registerRearFogLightState, light::unregisterRearFogLightState) { s, on -> s.copy(rearFog = on) }
    }

    /** 앞유리 김서림 제거(디프로스터)와 최대 제습(운전석). 비 · 습한 날에 켜져요. */
    private fun subscribeDefrost(v: Vehicle) {
        val hvac = v.getHVAC()
        hvac.checkHvacDefrosterCapabilities({ c, windows ->
            if (canRead("Defroster", c) && windows.coversWindow(WindowValue.FRONT_WINDSHIELD)) part("Defroster") {
                val glass = WindowArea(WindowValue.FRONT_WINDSHIELD)
                hvac.getHvacDefrosterState(glass, { on -> _env.update { it.copy(defrost = on) } }, ::fail)
                val dl = object : HvacDefrosterStateListener {
                    override fun onHvacDefrosterStateUpdated(state: Boolean?) { _env.update { it.copy(defrost = state) } }
                    override fun onFailed(e: Exception) = fail(e)
                }
                hvac.registerHvacDefrosterState(glass, dl)
                unregister += { hvac.unregisterHvacDefrosterState(glass, dl) }
            }
        }, noCapability("Defroster"))

        // 최대 제습은 차마다 구역이 달라서(Pleos 에뮬레이터는 실내 전체 한 구역) 지원 구역을 물어서 운전석을 품은 구역을 골라요
        hvac.checkHvacMaxDefrostCapabilities({ c, areas ->
            if (canRead("Max defrost", c)) driverZone(areas)?.let { zone ->
                part("Max defrost") {
                    hvac.getHvacMaxDefrostState(zone, { on -> _env.update { it.copy(maxDefrost = on) } }, ::fail)
                    val ml = object : HvacMaxDefrostStateListener {
                        override fun onHvacMaxDefrostStateUpdated(state: Boolean?) { _env.update { it.copy(maxDefrost = state) } }
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    hvac.registerHvacMaxDefrostState(zone, ml)
                    unregister += { hvac.unregisterHvacMaxDefrostState(zone, ml) }
                }
            }
        }, noCapability("Max defrost"))
    }

    /**
     * 운전석을 품은 구역을 골라요. Pleos 에뮬레이터는 공조 전원 · 최대 제습을 1열 왼쪽 하나가 아니라 실내 전체 한 구역(areaId 0x75)으로 줘서,
     * 1열 왼쪽으로 바로 읽으면 "area 0x1 not supported"가 와요(2026-11-07 에뮬레이터에서 확인). 설정 온도는 구역별(0x1 운전석 · 0x4 동승석)이에요.
     */
    private fun driverZone(areas: Collection<SeatArea>): SeatArea? =
        areas.firstOrNull { a -> a.values.any { it == SeatValue.ROW_1_LEFT } } ?: areas.firstOrNull()

    /**
     * 차의 몸: 문 네 개 · 후드 · 트렁크(열렸는지), 워셔액 부족, 실내 온도, 온도 표시 단위. 안전 제안(하차 알림 · 주행 후 점검)이 써요.
     * 영역마다 따로 붙여서 이 차가 지원하지 않는 곳이 있어도 나머지는 읽어요. 열림은 위치(0이면 닫힘) > 0이에요.
     * 경고등(Safety.*Telltales*)과 바퀴별 공기압(Tire.*TirePressure*)은 공식 샘플이 공개 목록에서 막아 둔 API라서 읽지 않아요
     * (Pleos API policy: 승인되지 않은 권한 선언 · API 호출은 앱 심사 탈락, 2026-11-13). 점검 화면은 서비스센터 기록으로 보여 줘요.
     */
    private fun subscribeBody(v: Vehicle) {
        part("Door") {
            val door = v.getDoor()
            val parts = mapOf(
                CarPart.DRIVER_DOOR to DoorValue.ROW_1_LEFT, CarPart.PASSENGER_DOOR to DoorValue.ROW_1_RIGHT,
                CarPart.REAR_LEFT_DOOR to DoorValue.ROW_2_LEFT, CarPart.REAR_RIGHT_DOOR to DoorValue.ROW_2_RIGHT,
                CarPart.HOOD to DoorValue.HOOD, CarPart.TRUNK to DoorValue.REAR,
            )
            door.checkDoorPositionCapabilities({ c, bounds ->
                if (canRead("Door position", c)) for ((piece, value) in parts) if (bounds.keys.coversDoor(value)) part("Door ${piece.name}") {
                    val area = DoorArea(value)
                    fun set(pos: Int?) = _car.update { s -> s.copy(open = s.open + (piece to ((pos ?: 0) > 0))) }
                    door.getCurrentDoorPosition(area, { set(it) }, ::fail)
                    val l = object : DoorPositionListener {
                        override fun onPositionUpdated(position: Int?) = set(position)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    door.registerDoorPosition(area, l)
                    unregister += { door.unregisterDoorPosition(area, l) }
                }
            }, noCapability("Door position"))
        }

        part("Washer") {
            val safety = v.getSafety()
            safety.checkLowWasherFluidWarningStateCapabilities({ c ->
                if (canRead("Washer", c)) part("Washer") {
                    safety.hasLowWasherFluidWarning({ low -> _car.update { it.copy(washerLow = low) } }, ::fail)
                    val wl = object : LowWasherFluidWaringListener {
                        override fun onLowWasherFluidWaringUpdated(low: Boolean?) { _car.update { it.copy(washerLow = low) } }
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    safety.registerLowWasherFluidWaringState(wl)
                    unregister += { safety.unregisterLowWasherFluidWaringState(wl) }
                }
            }, noCapability("Washer"))
        }

        part("Cabin temperature") {
            val hvac = v.getHVAC()
            hvac.checkHvacTemperatureCurrentCapabilities({ c, areas ->
                if (canRead("Cabin temperature", c)) driverZone(areas)?.let { zone ->
                    part("Cabin temperature") {
                        // 알림의 단위가 문서에 없어서, 알림이 오면 섭씨로 다시 읽어요(운전석 설정 온도와 같은 방법)
                        fun readCabin() = hvac.getHvacTemperatureCurrent(zone, TemperatureUnit.CELSIUS, { t -> _car.update { it.copy(cabinTempC = t) } }, ::fail)
                        readCabin()
                        val tl = object : HvacTemperatureListener {
                            override fun onHvacTemperatureUpdated(temperature: Float?) { readCabin() }
                            override fun onFailed(e: Exception) = fail(e)
                        }
                        hvac.registerHvacTemperatureCurrent(zone, tl)
                        unregister += { hvac.unregisterHvacTemperatureCurrent(zone, tl) }
                    }
                }
            }, noCapability("Cabin temperature"))
        }

        part("Display unit") {
            val display = v.getDisplay()
            display.checkTemperatureDisplayUnitCapabilities({ c ->
                if (canRead("Display unit", c)) part("Display unit") {
                    fun unit(u: TemperatureUnit?) = _car.update { it.copy(tempUnit = if (u == TemperatureUnit.FAHRENHEIT) TempUnit.FAHRENHEIT else TempUnit.CELSIUS) }
                    display.getCurrentTemperatureDisplayUnit({ unit(it) }, ::fail)
                    val ul = object : TemperatureDisplayUnitListener {
                        override fun onTemperatureDisplayUnitUpdated(unit: TemperatureUnit?) = unit(unit)
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    display.registerTemperatureDisplayUnit(ul)
                    unregister += { display.unregisterTemperatureDisplayUnit(ul) }
                }
            }, noCapability("Display unit"))
        }
    }

    /**
     * 운전자 중심: 운전석(1열 왼쪽) 구역의 설정 온도와 공조 전원만 읽어요. 동승석 · 뒷좌석 구역은 읽지 않아요.
     * 설정 온도가 바뀌었다는 알림의 단위가 문서에 없어서, 알림이 오면 섭씨로 다시 읽어요(와이퍼 스위치와 같은 방법).
     */
    private fun subscribeDriverCabin(v: Vehicle) {
        val hvac = v.getHVAC()
        hvac.checkHvacTemperatureCapabilities({ c, bounds ->
            if (canRead("Set temperature", c)) driverZone(bounds.keys)?.let { zone ->
                part("Set temperature") {
                    fun readTemp() = hvac.getHvacTemperature(zone, TemperatureUnit.CELSIUS, { t -> _cabin.update { it.copy(setTempC = t) } }, ::fail)
                    readTemp()
                    val tl = object : HvacTemperatureListener {
                        override fun onHvacTemperatureUpdated(temperature: Float?) { readTemp() }
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    hvac.registerHvacTemperature(zone, tl)
                    unregister += { hvac.unregisterHvacTemperature(zone, tl) }
                }
            }
        }, noCapability("Set temperature"))

        // 공조 전원은 실내 전체의 한 값이라(구역은 차마다 달라요) 지원 구역을 물어서 읽어요. 동승석의 온도 설정을 읽는 게 아니에요.
        hvac.checkHvacPowerCapabilities({ c, areas ->
            if (canRead("HVAC power", c)) driverZone(areas)?.let { zone ->
                part("HVAC power") {
                    hvac.getHvacPowerState(zone, { on -> _cabin.update { it.copy(hvacOn = on) } }, ::fail)
                    val pl = object : HvacPowerStateListener {
                        override fun onHvacPowerStateUpdated(state: Boolean?) { _cabin.update { it.copy(hvacOn = state) } }
                        override fun onFailed(e: Exception) = fail(e)
                    }
                    hvac.registerHvacPowerState(zone, pl)
                    unregister += { hvac.unregisterHvacPowerState(zone, pl) }
                }
            }
        }, noCapability("HVAC power"))
    }

    private fun WiperSwitch?.toLevel(): WiperLevel? = when (this) {
        WiperSwitch.OFF -> WiperLevel.OFF
        WiperSwitch.MIST -> WiperLevel.MIST
        WiperSwitch.INTERMITTENT_LEVEL_1, WiperSwitch.INTERMITTENT_LEVEL_2, WiperSwitch.INTERMITTENT_LEVEL_3,
        WiperSwitch.INTERMITTENT_LEVEL_4, WiperSwitch.INTERMITTENT_LEVEL_5 -> WiperLevel.INTERMITTENT
        WiperSwitch.CONTINUOUS_LEVEL_1, WiperSwitch.CONTINUOUS_LEVEL_2, WiperSwitch.CONTINUOUS_LEVEL_3,
        WiperSwitch.CONTINUOUS_LEVEL_4, WiperSwitch.CONTINUOUS_LEVEL_5 -> WiperLevel.CONTINUOUS
        WiperSwitch.AUTO -> WiperLevel.AUTO
        else -> null // SERVICE · OTHER · 값 없음
    }

    private fun startNavi() {
        val helper = NaviHelper(context.applicationContext)
        helper.initialize()
        val l = object : NaviHelperEventListener {
            override fun onRouteStarted(info: RouteStartInfo) {
                _nav.update { it.copy(routeActive = true, destinationName = info.name) }
                helper.getDestinationInfo()
            }
            override fun onDrivingInfo(info: ai.pleos.playground.navi.data.DrivingInfo) {
                // 내비가 경로에 넣은 충전소(이름과 충전 전 → 후 배터리 %). 좌표 · 시간 단위는 SDK가 확실히 주지 않아서 쓰지 않아요.
                val stops = info.waypoints.filter { it.isChargingStation }.map { w ->
                    ChargeStop(w.name, w.chargingPlan?.startSoc, w.chargingPlan?.endSoc)
                }
                _nav.update { it.copy(chargeStops = stops) }
            }
            override fun onDestinationInfo(info: DestinationInfo) {
                // 안내 종료 뒤에 이름 없는 목적지 정보가 오면(에뮬레이터 내비에서 확인, 2026-11-12) 안내 중인 길로 보지 않아요
                if (info.name.isNullOrBlank()) { _nav.value = NavState(); return }
                _nav.update {
                    it.copy(
                        routeActive = true,
                        destinationName = info.name,
                        destLat = info.latitude,
                        destLon = info.longitude,
                        remainDistanceM = info.remainDistance,
                        remainTimeS = info.remainTime,
                    )
                }
                // 도착 예상 배터리는 requestTrip으로 물어야 onTripInfo로 와요(에뮬레이터에서 확인, 2026-11-07).
                runCatching {
                    helper.requestTrip(
                        TripRequest(
                            latitude = info.latitude, longitude = info.longitude,
                            poiId = info.poiId ?: "", poiName = info.name ?: "", poiSubId = info.poiSubId ?: "0",
                        ),
                    )
                }.onFailure { Log.w(TAG, "requestTrip failed: ${it.javaClass.simpleName}") }
            }
            override fun onTripInfo(info: TripInfo) {
                _nav.update { it.copy(estimatedSocAtDest = info.estimatedSocAtDest) }
            }
            // onRouteEnded(폐기 예정)는 경로 시작 직후에도 와서 쓰지 않아요. 끝은 onRouteCancelled · onDestinationArrived로 알아요.
            override fun onRouteCancelled() { _nav.value = NavState() }
            override fun onWayPointArrived(info: ai.pleos.playground.navi.data.WaypointArrivedInfo) {
                _arrivals.tryEmit(NaviArrival(ArrivalKind.WAYPOINT, info.name, when (info.index) { WaypointIndex.FIRST -> 0; WaypointIndex.SECOND -> 1; else -> null }, System.currentTimeMillis()))
            }
            override fun onDestinationArrived(info: ai.pleos.playground.navi.data.DestinationArrivedInfo) {
                _arrivals.tryEmit(NaviArrival(ArrivalKind.DESTINATION, info.name, null, System.currentTimeMillis()))
                _nav.value = NavState()
            }
        }
        helper.addListener(l)
        navi = helper
        naviListener = l
    }

    /**
     * Vehicle SDK 2.3.2 + Pleos 에뮬레이터 2.1.0에서 SeatOccupancy의 Boolean이 Virtual Console과 반대로 와요
     * (콘솔에서 Occupied로 두면 HAL 값은 2(OCCUPIED)인데 SDK 콜백은 false, Empty면 true). 2026-11-07에 좌석 4곳에서 확인했어요.
     * 실제 차에서도 같은지는 미확인이라, 바뀌면 [SEAT_OCCUPANCY_INVERTED]만 고쳐요. Pleos에 문의할 사항이에요.
     */
    private fun seatOccupied(raw: Boolean?): Boolean? = seatOccupancy(raw, SEAT_OCCUPANCY_INVERTED)

    private fun setSeat(pos: SeatPos, f: (SeatState) -> SeatState) {
        _car.update { c -> c.copy(seats = c.seats + (pos to f(c.seats[pos] ?: SeatState()))) }
    }

    private val tripMeter = TripMeter()
    /** SDK가 트립 거리를 한 번이라도 줬으면 그 값을 믿고, 아니면 오도미터 차이를 써요. */
    @Volatile private var tripFromSdk = false

    private fun onGear(pos: GearPos) {
        val reset = tripMeter.onGear(pos == GearPos.PARK)
        _car.update { it.copy(gear = pos, tripKm = if (!tripFromSdk && reset != null) reset else it.tripKm) }
    }

    private fun onRange(m: Float?) {
        if (m == null) return
        _car.update { it.copy(rangeKm = m / 1000f) }
    }

    private fun onOdometer(km: Float?) {
        if (km == null) return
        val trip = tripMeter.onOdometer(km)
        if (!tripFromSdk && trip != null) _car.update { it.copy(tripKm = trip) }
    }

    private fun fail(e: Exception) { Log.w(TAG, "Vehicle call failed: ${e.javaClass.simpleName}: ${e.message}") }

    private fun VehicleGear?.toGearPos(): GearPos = when (this) {
        VehicleGear.GEAR_PARK -> GearPos.PARK
        VehicleGear.GEAR_REVERSE -> GearPos.REVERSE
        VehicleGear.GEAR_NEUTRAL -> GearPos.NEUTRAL
        VehicleGear.GEAR_DRIVE -> GearPos.DRIVE
        null, VehicleGear.GEAR_UNKNOWN -> GearPos.UNKNOWN
        else -> GearPos.DRIVE // 1~9단은 모두 주행
    }

    private companion object { const val TAG = "RoadMemory"; const val WIPER_POLL_MS = 5_000L; const val SEAT_OCCUPANCY_INVERTED = true }
}

/** SDK가 준 좌석 Boolean을 앱의 "착좌 여부"로 바꿔요. 순수 함수라 단위 테스트가 가능해요. */
internal fun seatOccupancy(raw: Boolean?, inverted: Boolean): Boolean? = raw?.let { if (inverted) !it else it }
