package app.roadmemory.car.brain

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 조용함 배우기(하이엔드의 규칙 버전): 제안마다 수락 · 거절 · 무시를 세고, 같은 종류를 두 번 거절하면 7일 동안 그 제안을 하지 않아요.
 * 숫자는 이 기기에만 남고, 사람이 보고 지울 수 있어요. LLM 없이 세기만 해요.
 */
data class QuietState(val accepted: Map<String, Int> = emptyMap(), val dismissed: Map<String, Int> = emptyMap(), val mutedUntil: Map<String, Long> = emptyMap()) {
    fun muted(kind: String, now: Long) = (mutedUntil[kind] ?: 0L) > now
    fun accept(kind: String) = copy(accepted = accepted + (kind to (accepted[kind] ?: 0) + 1))
    fun dismiss(kind: String, now: Long): QuietState {
        val n = (dismissed[kind] ?: 0) + 1
        val mute = if (n % 2 == 0) mutedUntil + (kind to now + MUTE_MS) else mutedUntil
        return copy(dismissed = dismissed + (kind to n), mutedUntil = mute)
    }
    /** "이번 달 제안 N번 · 수락 a · 거절 b" 같은 한 줄. */
    val summary: String get() {
        val a = accepted.values.sum(); val d = dismissed.values.sum()
        return if (a + d == 0) "아직 제안한 적이 없어요" else "지금까지 제안 ${a + d}번 · 수락 $a · 거절 $d" + (if (mutedUntil.isNotEmpty()) " · 쉬는 제안 ${mutedUntil.size}가지" else "")
    }
    fun encode() = listOf(accepted, dismissed, mutedUntil).joinToString(";") { m -> m.entries.joinToString(",") { "${it.key}=${it.value}" } }
    companion object {
        const val MUTE_MS = 7L * 24 * 3600 * 1000
        private fun <T> part(s: String?, f: (String) -> T?) = s.orEmpty().split(',').filter { it.contains('=') }.mapNotNull { e -> f(e.substringAfter('=').trim())?.let { e.substringBefore('=') to it } }.toMap()
        fun decode(raw: String?): QuietState {
            if (raw.isNullOrBlank()) return QuietState()
            val p = raw.split(';')
            return QuietState(part(p.getOrNull(0)) { it.toIntOrNull() }, part(p.getOrNull(1)) { it.toIntOrNull() }, part(p.getOrNull(2)) { it.toLongOrNull() })
        }
    }
}

object Quiet {
    var state: QuietState by mutableStateOf(QuietState())
        private set
    private var loaded = false
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("ion_quiet", Context.MODE_PRIVATE)
    fun ensure(c: Context) { if (!loaded) { loaded = true; state = QuietState.decode(prefs(c).getString("state", null)) } }
    private fun commit(c: Context, s: QuietState) { state = s; prefs(c).edit().putString("state", s.encode()).apply() }
    fun accept(c: Context, kind: String) = commit(c, state.accept(kind))
    fun dismiss(c: Context, kind: String) = commit(c, state.dismiss(kind, System.currentTimeMillis()))
    fun reset(c: Context) = commit(c, QuietState())
}
