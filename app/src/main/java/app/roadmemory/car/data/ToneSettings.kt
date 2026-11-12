package app.roadmemory.car.data

import android.content.Context

/** 화면 톤을 어떻게 정할지: 자동은 날씨와 운전자의 취향을 따르고, 나머지는 운전자가 한 가지로 고정해요. */
enum class ToneMode(val label: String) {
    AUTO("자동"),
    STANDARD("기본"),
    CRISP("선명"),
    CALM("차분"),
}

/**
 * 운전자가 설정에서 정한 화면 톤. 정차 · 충전 중에만 바꿀 수 있고(주행 중에는 설정이 열리지 않아요), 이 기기(차 안 앱 저장소)에만 남아요.
 * 자동일 때만 두 스위치가 뜻을 가져요: 날씨에 맞출지, 운전자가 맞춰 둔 실내 온도에 맞출지.
 * [rainTheme]은 톤과 따로예요: 서 있을 때 비를 짐작하면 내려받아 둔 비 테마로 화면이 바뀌었다가, 비가 그치면 사람이 적용해 둔 테마로 돌아와요.
 */
data class ToneSettings(
    val mode: ToneMode = ToneMode.AUTO,
    val followWeather: Boolean = true,
    val followTaste: Boolean = true,
    val rainTheme: Boolean = true,
) {
    fun encode(): String = "${mode.name}|${if (followWeather) 1 else 0}|${if (followTaste) 1 else 0}|${if (rainTheme) 1 else 0}"

    companion object {
        /** 저장된 글에서 되살려요. 깨졌거나 없으면 기본(자동 · 둘 다 켬)이에요. */
        fun decode(raw: String?): ToneSettings {
            val p = raw?.split('|') ?: return ToneSettings()
            return ToneSettings(
                mode = ToneMode.entries.firstOrNull { it.name == p.getOrNull(0) } ?: ToneMode.AUTO,
                followWeather = p.getOrNull(1) != "0",
                followTaste = p.getOrNull(2) != "0",
                rainTheme = p.getOrNull(3) != "0",
            )
        }
    }
}

/** 설정을 이 기기에 남겨요(SharedPreferences, 앱 안에서만 읽혀요). 계정이나 클라우드에 올리지 않아요. */
class ToneStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tone", Context.MODE_PRIVATE)
    fun load(): ToneSettings = ToneSettings.decode(prefs.getString("settings", null))
    fun save(s: ToneSettings) { prefs.edit().putString("settings", s.encode()).apply() }
}
