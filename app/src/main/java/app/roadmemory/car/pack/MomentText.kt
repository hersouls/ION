package app.roadmemory.car.pack

import app.roadmemory.car.data.CarPart
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.DriverCabin
import app.roadmemory.car.data.NavState
import app.roadmemory.car.engine.CardEngine

/** 문장의 빈자리에 넣을 값. 차가 준 것만 들어가고, 모르는 값은 null이에요. */
data class MomentValues(
    val soc: Int? = null,
    val rangeKm: Int? = null,
    val part: CarPart? = null,
    val tripKm: Int? = null,
    val temp: String? = null,
    val minutes: Int? = null,
    val destination: String? = null,
    val driveTime: String? = null,
    val outside: String? = null,
) {
    companion object {
        /** 지금 차 · 내비 값에서 만들어요. 값이 없으면 null로 두어 그 자리가 든 문장은 빠져요. */
        fun of(car: CarSnapshot, nav: NavState, cabin: DriverCabin, part: CarPart?, driveMinutes: Int?): MomentValues = MomentValues(
            soc = car.soc,
            rangeKm = car.rangeKm?.takeIf { it > 0f }?.let { Math.round(it) },
            part = part,
            tripKm = car.tripKm?.takeIf { it >= 0.5f }?.let { Math.round(it) },
            temp = cabin.setTempC?.let { CardEngine.tempText(it, car.tempUnit) },
            minutes = nav.remainTimeS?.let { Math.max(1, (it + 59) / 60) },
            destination = nav.destinationName?.takeIf { it.isNotBlank() },
            driveTime = driveMinutes?.takeIf { it >= 1 }?.let { CardEngine.restWord(it) },
            outside = car.outsideTempC?.let { CardEngine.tempText(it, car.tempUnit) },
        )
    }
}

/**
 * 순간마다 ION이 화면에 쓰는 문장(ION-Link의 `info` 틀)을 차가 준 값으로 채워요. 값을 지어내지 않아요:
 * 빈자리의 값을 모르면 그 자리가 든 문장을 통째로 빼고, 남는 문장이 없으면 null이에요(그러면 화면은 목소리 자막만 보여 줘요).
 * 문장은 ". "로 나눠요. `{dest}`만 예외로, 모르면 빈 글이고 알면 " · 이름"이에요(문장을 빼지 않아요).
 */
object MomentText {
    private val slot = Regex("\\{([a-z]+)\\}")

    fun fill(template: String?, v: MomentValues): String? {
        if (template.isNullOrBlank()) return null
        val out = template.split(". ").mapNotNull { sentence -> fillSentence(sentence.trim().trimEnd('.'), v) }
        return out.takeIf { it.isNotEmpty() }?.joinToString(". ")
    }

    private fun fillSentence(sentence: String, v: MomentValues): String? {
        if (sentence.isEmpty()) return null
        var missing = false
        // 이름 바로 뒤의 "이"는 받침에 맞춰 이/가로 바꿔요("조수석 문이" · "트렁크가")
        val s = sentence.replace("{part}이", "{partiga}")
        val out = slot.replace(s) { m ->
            val value: String? = when (m.groupValues[1]) {
                "soc" -> v.soc?.toString()
                "range" -> v.rangeKm?.toString()
                "part" -> v.part?.label
                "partiga" -> v.part?.label?.let { it + if (hasBatchim(it)) "이" else "가" }
                "km" -> v.tripKm?.toString()
                "temp" -> v.temp
                "min" -> v.minutes?.toString()
                "dest" -> v.destination?.let { " · $it" } ?: ""
                "time" -> v.driveTime
                "out" -> v.outside
                else -> null
            }
            if (value == null) { missing = true; "" } else value
        }
        return if (missing) null else out
    }

    private fun hasBatchim(s: String): Boolean {
        val c = s.lastOrNull() ?: return false
        return c in '가'..'힣' && (c.code - 0xAC00) % 28 != 0
    }
}
