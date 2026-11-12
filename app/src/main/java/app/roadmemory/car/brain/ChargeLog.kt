package app.roadmemory.car.brain

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 충전 한 번의 기록: 어디서(내비가 준 이름, 모르면 "충전소") · 몇 %에서 몇 %까지 · 몇 분 · 분당 몇 %. 이 기기에만 남아요. */
data class ChargeSession(val at: Long, val place: String, val startSoc: Int, val endSoc: Int, val minutes: Int) {
    /** 분당 올라간 %(2% · 1분 이상 쟀을 때만). */
    val pctPerMin: Double? get() = if (minutes >= 1 && endSoc - startSoc >= 2) (endSoc - startSoc).toDouble() / minutes else null
    fun encode() = listOf(at, place.replace('|', ' ').replace(',', ' '), startSoc, endSoc, minutes).joinToString("|")
    companion object {
        fun decode(s: String): ChargeSession? {
            val p = s.split('|'); if (p.size != 5) return null
            return runCatching { ChargeSession(p[0].toLong(), p[1], p[2].toInt(), p[3].toInt(), p[4].toInt()) }.getOrNull()
        }
    }
}

/**
 * 충전소 이력으로 고르기(하이엔드의 규칙 버전): 충전할 때마다 속도를 재서 남기고, 같은 이름의 충전소가 평소보다 느렸으면 말해요.
 * 속도는 잔량 변화를 시간으로 나눈 값이에요. 세 번 이상 재야 "평소"를 말해요.
 */
data class ChargeHistory(val sessions: List<ChargeSession> = emptyList()) {
    val measured: List<ChargeSession> get() = sessions.filter { it.pctPerMin != null }
    val usualPace: Double? get() = measured.takeIf { it.size >= 3 }?.map { it.pctPerMin!! }?.average()
    fun at(place: String) = measured.filter { it.place.contains(place) || place.contains(it.place) }
    fun paceAt(place: String): Double? = at(place).takeIf { it.isNotEmpty() }?.map { it.pctPerMin!! }?.average()
    /** 이 충전소가 평소의 70%보다 느렸으면 true. 평소를 모르거나 이 충전소 기록이 없으면 null. */
    fun slowerThanUsual(place: String): Boolean? {
        val u = usualPace ?: return null; val p = paceAt(place) ?: return null
        return p < u * 0.7
    }
    /** "지난번 이 충전소는 분당 0.8%였어요(평소 1.4%)" 같은 한 줄. 기록이 없으면 null. */
    fun line(place: String): String? {
        val p = paceAt(place) ?: return null
        val u = usualPace
        return "지난번 ${Korean.eun(place)} 분당 ${"%.1f".format(p)}%" + (u?.let { "(평소 ${"%.1f".format(it)}%)" } ?: "") + if (slowerThanUsual(place) == true) " · 평소보다 느렸어요" else ""
    }
    fun add(s: ChargeSession) = copy(sessions = (sessions + s).takeLast(200))
    fun encode() = sessions.joinToString("\n") { it.encode() }
    companion object { fun decode(raw: String?) = ChargeHistory(raw.orEmpty().lines().mapNotNull { if (it.isBlank()) null else ChargeSession.decode(it) }) }
}

object ChargeLog {
    var history: ChargeHistory by mutableStateOf(ChargeHistory())
        private set
    private var loaded = false
    private var open: Triple<Long, String, Int>? = null   // 시작 때 · 장소 · 시작 %
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("ion_charge_log", Context.MODE_PRIVATE)
    fun ensure(c: Context) { if (!loaded) { loaded = true; history = ChargeHistory.decode(prefs(c).getString("log", null)) } }

    /** 충전이 시작됐어요. 배터리를 모르면 재지 않아요. */
    fun begin(c: Context, place: String?, soc: Int?, now: Long) { ensure(c); open = if (soc == null) null else Triple(now, place?.takeIf { it.isNotBlank() } ?: "충전소", soc) }

    /** 충전이 끝났어요. 1분 미만이거나 배터리를 모르면 남기지 않아요. */
    fun end(c: Context, soc: Int?, now: Long) {
        val (t0, place, s0) = open ?: return
        open = null
        if (soc == null) return
        val minutes = ((now - t0) / 60_000L).toInt()
        if (minutes < 1) return
        val s = ChargeSession(t0, place, s0, soc, minutes)
        history = history.add(s)
        prefs(c).edit().putString("log", history.encode()).apply()
    }
    fun clear(c: Context) { history = ChargeHistory(); prefs(c).edit().remove("log").apply() }

    /** 지금 하는 충전의 분당 %(1분 · 2% 넘게 재야 말해요). 충전 중이 아니거나 아직이면 null. */
    fun currentPace(soc: Int?, now: Long): Double? {
        val (t0, _, s0) = open ?: return null
        if (soc == null) return null
        val minutes = (now - t0) / 60_000.0
        return if (minutes >= 1 && soc - s0 >= 2) (soc - s0) / minutes else null
    }
}
