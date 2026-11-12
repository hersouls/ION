package app.roadmemory.car.engine

import app.roadmemory.car.data.AppPrefs
import app.roadmemory.car.data.CarPart
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.GearPos
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.SeatPos
import app.roadmemory.car.data.TempUnit
import app.roadmemory.car.data.TripBook
import app.roadmemory.car.data.UxLimit
import java.time.YearMonth

/** 차 상태가 정하는 세 가지 모드. 사용자가 화면을 고르지 않아요. */
enum class Mode { DRIVING, CHARGING, PARKED }

object ModeEngine {
    /**
     * 주행 규제(CarUxRestrictions)가 "제한 필요"라고 하면 기어와 상관없이 주행이에요(Pleos 가이드: 플랫폼이 내려 주는 값이 기준).
     * 규제를 모르거나 꺼져 있으면 기어(D · R)와 충전구로 나눠요. 규제가 꺼져 있어도 기어가 D면 안전한 쪽인 주행으로 봐요.
     */
    fun modeOf(car: CarSnapshot, ux: UxLimit = UxLimit()): Mode = when {
        ux.known && ux.requiresDo -> Mode.DRIVING
        car.gear == GearPos.DRIVE || car.gear == GearPos.REVERSE -> Mode.DRIVING
        car.gear == GearPos.PARK && car.chargePortOpen == true -> Mode.CHARGING
        else -> Mode.PARKED
    }
}

enum class CardKind {
    /** 달리는 중: 안전벨트 · 충전 · 가는 곳의 기억 · 쉬어 가기 · 추위 · 뒷좌석 */
    SEATBELT_CHECK, CHARGE_VIA, ROAD_MEMORY, REST_SUGGEST, COLD_NOTICE, REAR_SEAT,
    /** 서 있을 때: 뒷좌석 하차 알림 · 주행 후 점검 */
    REAR_ALERT, VEHICLE_CHECK,
}

/** 카드를 고를 때 차 신호 밖에서 필요한 것. 시계 · 기록을 갈아 끼울 수 있게 따로 받아요. */
data class CardContext(
    val prefs: AppPrefs = AppPrefs(),
    /** 이 기기에 저장된 여행(가는 곳의 기억을 찾는 데 써요). */
    val trips: List<SavedTrip> = emptyList(),
    /** 이번 주행을 시작하고 몇 분이 지났는지(모르면 null). */
    val driveMinutes: Int? = null,
    /** "이 길, 2년 전에…"의 "2년 전"을 셈하는 기준 달(시험에서는 고정해요). */
    val month: YearMonth = YearMonth.now(),
)

/** 주행 중에 띄우는 카드 한 장. 두 줄 이하의 글과 버튼 둘이에요. */
data class GlanceCard(
    val kind: CardKind,
    val title: String,
    val sub: String?,
    val primary: String,
    val secondary: String,
    val hint: String,
) { val id: String get() = kind.name }

object CardEngine {
    /** 도착 예상 배터리가 이 값 이하면 충전을 한 번 알려요. */
    const val LOW_SOC_AT_DEST = 10
    /** 이만큼 달리면 쉬어 가기를 한 번 말해요(분). */
    const val REST_AFTER_MIN = 120
    /** 외기온이 이 값 이하(섭씨)이면 추위를 한 번 알려요. */
    const val COLD_C = 0f

    /**
     * 지금 띄울 카드 한 장(또는 없음). 이미 답한 카드는 다시 묻지 않아요("한 번만 물어요").
     * 모든 말은 차가 실제로 준 값에서만 나와요. 값을 모르면 말하지 않아요.
     */
    fun pick(mode: Mode, car: CarSnapshot, nav: NavState, handled: Set<String>, ctx: CardContext = CardContext()): GlanceCard? {
        if (mode == Mode.CHARGING) return null
        val candidates = buildList {
            // 서 있을 때의 두 장. 차 신호만으로 만든 알림이고 안전 기능 인증과는 별개예요.
            if (mode == Mode.PARKED && car.isOpen(CarPart.DRIVER_DOOR) && car.rearOccupied) {
                add(GlanceCard(
                    CardKind.REAR_ALERT, "뒷좌석에 누가 있어요",
                    listOfNotNull(car.cabinTempC?.let { "실내 온도 ${tempText(it, car.tempUnit)}" }, "운전석 문이 열렸어요").joinToString(" · "),
                    "알았어요", "나중에", "차 안 카드와 소리로 알려요",
                ))
            }
            if (mode == Mode.PARKED) vehicleIssues(car).let { issues ->
                if (issues.isNotEmpty()) add(GlanceCard(
                    CardKind.VEHICLE_CHECK, if (issues.size == 1) "한 가지만 살펴봐요" else "${issues.size}가지를 살펴봐요",
                    issues.joinToString(" · "), "알았어요", "다음에 보기", "주행이 끝난 뒤 한 번만 알려요",
                ))
            }
            if (mode == Mode.DRIVING) {
                unbelted(car)?.let { pos ->
                    add(GlanceCard(CardKind.SEATBELT_CHECK, "${pos.label} 안전벨트를 확인해 주세요", "앉아 있는데 아직 채워지지 않았어요", "확인했어요", "나중에", "출발할 때 한 번만 알려요"))
                }
                val est = nav.estimatedSocAtDest
                if (nav.routeActive && est != null && est <= LOW_SOC_AT_DEST) {
                    val stop = nav.chargeStops.firstOrNull()
                    add(GlanceCard(
                        CardKind.CHARGE_VIA, "도착하면 배터리가 ${est}%예요",
                        if (stop != null) "내비가 ${app.roadmemory.car.brain.Korean.eul(stop.name)} 경로에 넣었어요" else "충전이 필요할 수 있어요. 내비에서 충전소를 확인해 주세요",
                        "알았어요", "나중에", "ION은 경로를 바꾸지 않아요. 충전 계획은 내비가 세워요",
                    ))
                }
                if (nav.routeActive) roadMemoryCard(TripBook.tripsTo(ctx.trips, nav.destinationName, nav.destLat, nav.destLon), ctx.month)?.let { add(it) }
                ctx.driveMinutes?.let { m ->
                    if (m >= REST_AFTER_MIN) add(GlanceCard(CardKind.REST_SUGGEST, "${restWord(m)}째 달렸어요", "쉬어 가는 건 어때요?", "알았어요", "나중에", "두 시간마다 한 번만 말해요"))
                }
                car.outsideTempC?.let { t ->
                    if (t <= COLD_C) add(GlanceCard(
                        CardKind.COLD_NOTICE, "바깥이 ${tempText(t, car.tempUnit)}예요", "추운 날에는 갈 수 있는 거리가 줄 수 있어요", "알았어요", "나중에", "외기온은 차량 표준 속성에서 읽어요",
                    ))
                }
                if (car.rearOccupied) add(GlanceCard(CardKind.REAR_SEAT, "뒷좌석에 탔어요", "곡을 틀까요?", "틀기", "나중에", "‘틀어 줘’라고 말해도 돼요"))
            }
        }
        return candidates.firstOrNull { it.id !in handled }
    }

    /**
     * 가는 곳을 다녀온 적이 있으면 그 기억 한 장(이 기기에 저장된 여행만). 말은 가장 최근 여행의 달과 함께 탄 사람에서 나와요.
     * 그때 들은 곡이 있으면 "틀기", 곡이 남지 않은 여행이면 알려 주기만 해요.
     */
    fun roadMemoryCard(trips: List<SavedTrip>, now: YearMonth): GlanceCard? {
        val latest = trips.firstOrNull() ?: return null
        val who = TripBook.peopleWord(latest.people)
        val title = "이 길, ${TripBook.agoLabel(latest.month, now)}에 " + (if (who.isEmpty()) "" else "$who ") + "달렸어요"
        return if (trips.any { it.tracks.isNotEmpty() }) {
            GlanceCard(CardKind.ROAD_MEMORY, title, "그때 들은 곡을 틀까요?", "틀기", "나중에", "‘틀어 줘’라고 말해도 돼요")
        } else {
            GlanceCard(CardKind.ROAD_MEMORY, title, "다녀온 기록이 ${trips.size}건 있어요", "알았어요", "나중에", "서 있을 때 여행 기록에서 볼 수 있어요")
        }
    }

    /** 앉아 있는데 벨트가 채워지지 않은 첫 자리(운전석 → 조수석 → 뒷좌석 왼쪽 → 오른쪽). 모르면(null) 말하지 않아요. */
    fun unbelted(car: CarSnapshot): SeatPos? =
        SeatPos.entries.firstOrNull { car.occupied(it) && car.seats[it]?.belted == false }

    /** 주행 후 점검에서 말할 것: 열려 있는 곳(운전석 문은 빼요). 워셔액 신호는 차가 줄 때만 보태요. */
    fun vehicleIssues(car: CarSnapshot): List<String> = buildList {
        CarPart.entries.filter { car.isOpen(it) && it != CarPart.DRIVER_DOOR }.forEach { add("${it.label}이 열려 있어요") }
        if (car.washerLow == true) add("워셔액이 부족해요")
    }

    /** 온도를 차의 표시 단위로 말해요: 31도 · 화씨 88도 · 영하 3도. */
    fun tempText(c: Float, unit: TempUnit): String {
        if (unit == TempUnit.FAHRENHEIT) return "화씨 ${Math.round(c * 9f / 5f + 32f)}도"
        val r = Math.round(c)
        return if (r < 0) "영하 ${-r}도" else "${r}도"
    }

    /** 120분 → "2시간", 150분 → "2시간 30분". */
    fun restWord(minutes: Int): String {
        val h = minutes / 60; val m = minutes % 60
        return if (m == 0) "${h}시간" else "${h}시간 ${m}분"
    }
}

/**
 * 충전 속도를 실제로 재서(배터리 %가 바뀐 때를 기록해서) 80%까지 남은 시간을 어림해요. 가정한 속도를 쓰지 않아요.
 * 충분히 재기 전에는 null이라서, 화면이 "충전 속도를 재는 중이에요"라고 말해요.
 */
class ChargePace(private val targetSoc: Int = 80) {
    private val samples = ArrayList<Pair<Long, Int>>()

    fun reset() = samples.clear()

    /** 배터리 %를 알려요(바뀐 때만 기록해요). */
    fun onSoc(nowMs: Long, soc: Int?) {
        if (soc == null) return
        if (samples.isEmpty() || samples.last().second != soc) samples += nowMs to soc
    }

    /** 분당 올라가는 %(충분히 재지 못했으면 null). 처음과 지금 사이가 2% 이상 · 1분 이상이어야 믿어요. */
    fun percentPerMinute(): Double? {
        if (samples.size < 2) return null
        val (t0, s0) = samples.first(); val (t1, s1) = samples.last()
        val minutes = (t1 - t0) / 60_000.0
        return if (minutes >= 1.0 && s1 - s0 >= 2) (s1 - s0) / minutes else null
    }

    fun minutesLeft(soc: Int?): Int? {
        val rate = percentPerMinute() ?: return null
        if (soc == null) return null
        return Math.round((targetSoc - soc).coerceAtLeast(0) / rate).toInt()
    }
}
