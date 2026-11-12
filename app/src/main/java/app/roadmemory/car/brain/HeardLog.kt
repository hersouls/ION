package app.roadmemory.car.brain

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** ION이 들은 말 한 줄: 들은 그대로 · 대명사를 푼 문장 · 알아들었는지 · 답의 앞머리. 좌표 · 이름표 같은 건 없어요. */
data class HeardLine(val at: Long, val heard: String, val resolved: String, val understood: Boolean, val reply: String) {
    fun encode() = listOf(at, clean(heard), clean(resolved), if (understood) "1" else "0", clean(reply).take(80)).joinToString("\t")
    companion object {
        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').trim()
        fun decode(s: String): HeardLine? {
            val p = s.split('\t'); if (p.size != 5) return null
            return runCatching { HeardLine(p[0].toLong(), p[1], p[2], p[3] == "1", p[4]) }.getOrNull()
        }
    }
}

/**
 * ION이 들은 말을 이 기기에만 남겨요(하이엔드 제안 4: "학습"에 가장 가까운 정직한 길).
 * 모델은 없어요. 사람이 실제로 한 말을 모아 두었다가 다음 질문 은행(`QnaBankTest.run3`)으로 돌려 규칙을 넓히는 데 써요.
 * 설정에서 끌 수 있고(끄면 아무것도 적지 않아요) · 지울 수 있고 · 내보내면 `Android/data/app.roadmemory.car/files/ion-heard.txt`에 써요(adb pull로 가져가요).
 * 저장소 밖으로 저절로 나가는 일은 없어요. 500줄까지만 두고 오래된 것부터 지워요.
 */
object HeardLog {
    const val LIMIT = 500
    const val EXPORT_NAME = "ion-heard.txt"
    const val TAG = "ION.heard"

    var on: Boolean by mutableStateOf(true); private set
    var lines: List<HeardLine> by mutableStateOf(emptyList()); private set
    /** 마지막으로 내보낸 파일의 경로(없으면 null). 설정 줄에 보여 줘요. */
    var lastExport: String? by mutableStateOf(null); private set
    val count: Int get() = lines.size
    val unknownCount: Int get() = lines.count { !it.understood }

    private var loaded = false
    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("ion_heard_log", Context.MODE_PRIVATE)

    fun ensure(c: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(c)
        on = p.getBoolean("on", true)
        lines = p.getString("log", null).orEmpty().lines().mapNotNull { if (it.isBlank()) null else HeardLine.decode(it) }
    }

    fun add(c: Context, heard: String, resolved: String, understood: Boolean, reply: String, now: Long = System.currentTimeMillis()) {
        ensure(c)
        if (!on || heard.isBlank()) return
        lines = (lines + HeardLine(now, heard, resolved, understood, reply)).takeLast(LIMIT)
        save(c)
    }

    fun setOn(c: Context, v: Boolean) { ensure(c); on = v; prefs(c).edit().putBoolean("on", v).apply() }

    fun clear(c: Context) { ensure(c); lines = emptyList(); lastExport = null; prefs(c).edit().remove("log").apply(); runCatching { File(c.getExternalFilesDir(null), EXPORT_NAME).delete() } }

    /** 외부 앱 전용 폴더에 한 줄씩 써요. 쓸 곳이 없으면 null. */
    fun export(c: Context): File? {
        ensure(c)
        val dir = c.getExternalFilesDir(null) ?: return null
        val f = File(dir, EXPORT_NAME)
        return runCatching {
            f.writeText(lines.joinToString("\n") { it.encode() } + "\n")
            // 다른 사용자(운전자 프로필)의 앱 폴더는 adb가 못 읽어서, 개발 중엔 logcat으로도 같은 줄을 내보내요(tools/pull_heard.sh가 읽어요). 들은 말뿐, 좌표 · 이름표는 없어요.
            android.util.Log.i(TAG, "BEGIN ${lines.size}")
            lines.forEach { android.util.Log.i(TAG, it.encode()) }
            android.util.Log.i(TAG, "END")
            lastExport = f.absolutePath; f
        }.getOrNull()
    }

    private fun save(c: Context) { prefs(c).edit().putString("log", lines.joinToString("\n") { it.encode() }).apply() }
}
