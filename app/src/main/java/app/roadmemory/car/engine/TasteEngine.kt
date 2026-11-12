package app.roadmemory.car.engine

import app.roadmemory.car.data.DriverCabin

/** 운전자가 맞춰 둔 실내 온도에서 읽는 취향. 날씨가 아니라 사람의 선택이에요. */
enum class Taste(val label: String) {
    COOL("시원한 편"),
    NEUTRAL("보통"),
    WARM("따뜻한 편"),
}

/**
 * 운전석 설정 온도 → 취향. 경계 근처에서 색이 왔다 갔다 하지 않도록 들어가는 값과 나오는 값을 다르게 둬요(히스테리시스).
 *
 *   시원한 편  21℃ 이하로 맞추면 들어가고, 22℃ 이상이 되면 나와요.
 *   따뜻한 편  25℃ 이상으로 맞추면 들어가고, 24℃ 이하가 되면 나와요.
 *   그 사이(21.5 ~ 24.5℃)는 보통이에요. 공조가 꺼져 있거나 값이 없으면 보통(= 취향을 말하지 않음)이에요.
 *
 * 21℃와 25℃는 가이드의 값이 아니라 이 앱이 정한 기준이에요(공조의 기본값 22~24℃ 둘레를 보통으로 둔 것).
 */
object TasteEngine {
    const val COOL_IN = 21f
    const val COOL_OUT = 22f
    const val WARM_IN = 25f
    const val WARM_OUT = 24f

    fun step(prev: Taste, cabin: DriverCabin): Taste {
        val t = cabin.setTempC
        if (cabin.hvacOn != true || t == null) return Taste.NEUTRAL
        return when (prev) {
            Taste.COOL -> if (t < COOL_OUT) Taste.COOL else fresh(t)
            Taste.WARM -> if (t > WARM_OUT) Taste.WARM else fresh(t)
            Taste.NEUTRAL -> fresh(t)
        }
    }

    private fun fresh(t: Float): Taste = when {
        t <= COOL_IN -> Taste.COOL
        t >= WARM_IN -> Taste.WARM
        else -> Taste.NEUTRAL
    }
}
