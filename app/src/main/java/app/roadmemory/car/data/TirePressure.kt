package app.roadmemory.car.data

/** 바퀴 네 개. SDK `WheelValue`와 짝이에요. */
enum class Wheel(val label: String) {
    FRONT_LEFT("앞 왼쪽"), FRONT_RIGHT("앞 오른쪽"), REAR_LEFT("뒤 왼쪽"), REAR_RIGHT("뒤 오른쪽"),
}

/**
 * 타이어 공기압은 `Tire.getTirePressure`가 Float 하나를 주는데 **단위가 문서에 없어요**(권한을 받았다고 가정한 구현 · 실차 미확인).
 * 그래서 값의 크기로 단위를 가려요: bar(1.0 ~ 4.5) · kPa(100 ~ 450) · psi(20 ~ 65). 어느 범위도 아니면 읽은 값이라고 보지 않아요(null).
 * 이렇게 가린 것은 어림이라, 화면은 "적정 / 낮음" 판정을 하지 않고 숫자와 경고등(TPMS)만 말해요.
 */
object TirePressure {
    fun toBar(raw: Float?): Double? {
        val v = raw?.toDouble() ?: return null
        return when {
            v in 1.0..4.5 -> v
            v in 100.0..450.0 -> v / 100.0
            v in 20.0..65.0 -> v * 0.0689476
            else -> null
        }?.let { Math.round(it * 10) / 10.0 }
    }

    /** 네 바퀴가 모두 가려졌으면 바퀴별 bar, 하나라도 못 읽었으면 null(반쪽 값은 보이지 않아요). */
    fun allBar(raw: Map<Wheel, Float>): Map<Wheel, Double>? {
        val out = Wheel.entries.associateWith { toBar(raw[it]) }
        return if (out.values.all { it != null }) out.mapValues { it.value!! } else null
    }
}
