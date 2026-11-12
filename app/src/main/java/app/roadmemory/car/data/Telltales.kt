package app.roadmemory.car.data

/** 지금 켜져 있는 경고등 하나. [type]은 SDK `TelltaleType`의 이름이고, [blinking]은 깜박이는 중이에요. */
data class ActiveTelltale(val type: String, val blinking: Boolean = false)

/**
 * 경고등(Telltale)을 사용자 말로: **경고**에 해당하는 것만 이름을 붙이고 나머지(방향지시등 · 전조등 · 주행 모드 같은 표시등)는 null로 버려요.
 * 점검 화면은 "지금 남아 있는 경고"만 말하니까요. 이름은 Vehicle SDK 2.3.2의 `TelltaleType`에서 골랐어요.
 * 읽으려면 PRIVATE 권한이 필요해요(`Safety.getTelltalesInfo`, 권한을 받았다고 가정한 구현 · 실차 미확인).
 */
object TelltaleText {
    private val warnings = mapOf(
        "MASTER_WARNING" to "종합 경고",
        "TPMS" to "타이어 공기압 경고",
        "ABS" to "ABS 경고",
        "AIRBAG" to "에어백 경고",
        "MDPS" to "전동 조향 경고",
        "LOW_VOLT_BATTERY" to "보조(12V) 배터리 경고",
        "LOW_EV_BATTERY" to "구동 배터리가 부족해요",
        "EV_SYSTEM" to "전기차 시스템 경고",
        "SEAT_BELT" to "안전벨트 경고",
    )

    /** 경고면 사용자 말, 경고가 아닌 표시등이면 null. */
    fun label(type: String): String? = warnings[type]

    /** 켜진 경고등에서 경고만 골라 [ActiveTelltale]로(같은 종류는 한 번만). */
    fun warningsOf(on: List<ActiveTelltale>): List<ActiveTelltale> = on.filter { label(it.type) != null }.distinctBy { it.type }

    /** 한 줄 말: "타이어 공기압 경고 · 에어백 경고". 없으면 null. */
    fun sentence(on: List<ActiveTelltale>): String? = warningsOf(on).takeIf { it.isNotEmpty() }?.joinToString(" · ") { label(it.type)!! }
}
