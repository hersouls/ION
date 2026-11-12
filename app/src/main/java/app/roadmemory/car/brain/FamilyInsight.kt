package app.roadmemory.car.brain

import app.roadmemory.car.data.NavState
import app.roadmemory.car.family.FamilyPlace
import app.roadmemory.car.family.FamilyText
import app.roadmemory.car.studio.CalendarEvent
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 위치 동기화의 하이엔드(규칙 버전): 가족 위치 한 사람마다 ION이 이어서 보는 한마디. 쓴 값에서만 말하고([Line.reason]), 모르는 값으로는 말하지 않아요.
 *  · 가는 중이면 내비가 준 값(남은 거리 · 시간 · 도착 때 배터리 · 넣은 충전소)만 이어요. ION이 거리를 어림하지 않아요(앱은 차의 위치를 읽지 않아요).
 *  · 오래된 위치(30분 넘음)면 다시 가져오라고 먼저 말해요.
 *  · 가져온 캘린더에 가족 일정이 있고 그 장소와 가까우면 이어서 말해요(캘린더를 동기화하지 않았으면 쓰지 않아요).
 *  · 이동 중이면 안내하는 곳이 지금 있는 곳이라는 것을 말해요.
 * ION은 먼저 경로를 바꾸지 않아요. 안내는 운전자가 "경로 안내"를 누른 뒤에만 가요.
 */
object FamilyInsight {
    /** 약속 장소에 "있다"고 말하는 거리 · "근처"라고 말하는 거리(km, 설계 값). */
    const val AT_KM = 0.3
    const val NEAR_KM = 2.0
    /** 도착 때 배터리가 이 값(%) 아래로 내비가 계산하면 충전을 권해요(설계 값, 바꿀 수 있어요). */
    const val LOW_SOC = 15

    data class Line(val text: String, val reason: String)

    /** 직선 거리(km). 길 굽이는 곱하지 않아요(이웃한 곳인지만 보는 값이라서). */
    fun km(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(bLat - aLat); val dLng = Math.toRadians(bLng - aLng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun fmt(km: Double) = if (km < 10) "%.1f".format(km) else "${Math.round(km)}"

    /** 이 사람에게 가는 길이 내비에 켜져 있는지(목적지 이름에 별칭이 들어 있어요). */
    fun guiding(p: FamilyPlace, nav: NavState): Boolean = nav.routeActive && nav.destinationName?.contains(p.alias) == true

    /** 가족이 함께하는 일정인지: 사람(with) · 제목에 별칭 또는 "가족" · "부모님"이 있어요. */
    fun isFamilyEvent(e: CalendarEvent, p: FamilyPlace): Boolean =
        listOf(e.with, e.title).any { t -> t.contains(p.alias) || t.contains("가족") || t.contains("부모님") }

    /** 가는 중: 내비가 준 값만 이어요. 값이 없으면 말하지 않아요. */
    fun live(p: FamilyPlace, nav: NavState): List<Line> {
        if (!guiding(p, nav)) return emptyList()
        val km = nav.remainDistanceM?.let { it / 1000.0 }
        val min = nav.remainTimeS?.let { ceil(it / 60.0).toInt() }
        if (km == null && min == null) return listOf(Line("내비가 길을 계산하는 중이에요", "내비가 남은 거리 · 시간을 아직 주지 않았어요"))
        val out = mutableListOf<Line>()
        out += Line("가는 중 · " + listOfNotNull(km?.let { "남은 ${fmt(it)}km" }, min?.let { "약 ${it}분" }).joinToString(" · "), "내비가 알려 준 값이에요")
        nav.estimatedSocAtDest?.let { soc ->
            out += if (soc < LOW_SOC) Line("도착하면 배터리 약 ${soc}%예요 · 가는 길에 충전을 권해요", "내비가 계산한 도착 때 배터리가 ${LOW_SOC}%보다 낮아요. 충전소는 내비가 정하고 ION은 경로를 바꾸지 않아요")
            else Line("도착하면 배터리 약 ${soc}%예요 · 충전 없이 가요", "내비가 계산한 도착 때 배터리예요")
        }
        nav.chargeStops.firstOrNull()?.let { out += Line("내비가 ${it.name}에서 충전하도록 넣었어요", "내비가 경로에 넣은 충전소예요") }
        return out
    }

    /**
     * 위치 한 건에 대한 ION의 한마디들(앞이 중요). [calendar]는 마이현대에서 동기화해 가져온 캘린더 일정이고, 가져오지 않았으면 null이에요.
     */
    fun lines(p: FamilyPlace, nav: NavState, calendar: List<CalendarEvent>?): List<Line> {
        val out = mutableListOf<Line>()
        val liveLines = live(p, nav)
        out += liveLines
        val age = p.ageSec
        if (age != null && age >= FamilyText.STALE_SEC) {
            out += Line("오래된 위치예요 · 다시 가져오면 지금 있는 곳을 알 수 있어요", "위치를 받은 지 ${age / 60}분이 지났어요. 30분을 넘으면 오래된 위치라고 말해요")
        }
        calendar?.filter { it.lat != null && it.lng != null && isFamilyEvent(it, p) }
            ?.map { it to km(p.lat, p.lng, it.lat!!, it.lng!!) }
            ?.filter { (_, d) -> d <= NEAR_KM }
            ?.minByOrNull { (_, d) -> d }
            ?.let { (e, d) ->
                out += Line(
                    if (d <= AT_KM) "\u2018${e.title}\u2019 약속 장소(${e.place})에 있어요 · ${e.whenText}" else "\u2018${e.title}\u2019 약속 장소(${e.place}) 근처에 있어요 · ${e.whenText}",
                    if (d <= AT_KM) "가져온 캘린더 일정의 장소와 ${Math.round(AT_KM * 1000)}m 안이에요" else "가져온 캘린더 일정의 장소와 약 ${fmt(d)}km 떨어져 있어요",
                )
            }
        if (p.moving && liveLines.isEmpty()) {
            out += Line("이동 중이라 안내하는 곳은 지금 있는 곳이에요 · 가까워지면 다시 가져와요", "마이현대가 준 값이 이동 중이에요. 내비로 보내는 좌표는 위치를 받은 그때의 자리예요")
        }
        return out
    }
}
