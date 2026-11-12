package app.roadmemory.car.ui

import app.roadmemory.car.data.ToneMode
import app.roadmemory.car.data.ToneSettings
import app.roadmemory.car.data.Weather
import app.roadmemory.car.engine.Taste
import kotlin.math.pow

/**
 * 날씨에 따라 달라지는 색 톤. 현대(Pleos Connect UI Design Guide)의 색 규칙을 지키는 조건이 코드에 들어 있어요.
 * 색 값은 res/values(-night)/colors.xml의 ivi_* 토큰이 한 곳의 진실이고, 톤은 "어느 토큰을 쓸지"만 바꿔요(새 색을 만들지 않아요).
 *
 * 지키는 조건
 *  1. 토큰만 써요. 색을 새로 만들지 않고, informative_*(켜짐 · 오류 같은 상태를 말하는 색)는 톤으로 바꾸지 않아요.
 *  2. 글자는 바탕 위에서 4.5:1 이상이고, 채움 버튼 위 글자도 4.5:1 이상이에요(가이드의 대비 기준). 어기면 기본 톤으로 되돌려요 → [check].
 *  3. 낮 · 밤은 차 시스템이 정해요. 톤은 같은 낮(또는 밤) 안에서 한 단계만 움직여요.
 *  4. 주행 중에는 톤을 바꾸지 않아요(일관성 · 한눈에 보기). 바뀔 톤은 정차(P)나 충전 때 적용돼요 → [next].
 *  5. 날씨를 모르면(null) 기본 톤이에요. 톤은 날씨를 말해 주지 않아요(차의 날씨 안내와 겹치지 않아요).
 */
enum class Tone(val label: String) {
    /** 맑은 날 낮: 햇빛에 화면이 바래서, 보조 글자와 채움 버튼을 한 단계 진하게 해요. 밤에는 기본과 같아요. */
    CRISP("선명"),
    STANDARD("기본"),
    /** 비 · 눈 · 안개: 어두운 날 화면의 눈부심을 줄이려고 바탕을 한 단계 낮춰요(낮 · 밤 모두). */
    CALM("차분"),
}

/** 톤이 바꿀 수 있는 자리와 그 기본 토큰이에요. */
enum class ToneRole(val token: String) {
    BACKGROUND_PRIMARY("ivi_background_primary"),
    BACKGROUND_SECONDARY("ivi_background_secondary"),
    SURFACE_HIGH("ivi_surface_high"),
    SURFACE_ACCENT("ivi_surface_accent"),
    TEXT_PRIMARY("ivi_text_primary"),
    TEXT_SECONDARY("ivi_text_secondary"),
    TEXT_TERTIARY("ivi_text_tertiary"),
    TEXT_ON_FILLED("ivi_text_on_filled"),
    BUTTON_FILLED("ivi_button_filled_enabled"),
    BUTTON_BASIC("ivi_button_basic_enabled"),
}

object Tones {
    /**
     * 날씨와 운전자의 취향 → 톤. 날씨가 가려낸 날(맑음 · 비 · 눈 · 안개)은 날씨가 먼저예요(시야 · 눈부심은 안전 쪽 판단이라서요).
     * 날씨를 모르거나 흐린 날에는 운전자가 맞춰 둔 실내 온도의 취향을 따라요: 시원한 편 → 선명, 따뜻한 편 → 차분, 보통 → 기본.
     * 취향 → 톤은 가이드의 규칙이 아니라 이 앱의 디자인 은유예요(시원함은 또렷하게, 따뜻함은 부드럽게). 색상(hue)은 가이드의 회색 눈금 밖으로 가지 않아요.
     */
    fun toneOf(weather: Weather?, taste: Taste = Taste.NEUTRAL): Tone = when (weather) {
        Weather.CLEAR -> Tone.CRISP
        Weather.RAIN, Weather.SNOW, Weather.FOG -> Tone.CALM
        Weather.CLOUDY, null -> when (taste) {
            Taste.COOL -> Tone.CRISP
            Taste.WARM -> Tone.CALM
            Taste.NEUTRAL -> Tone.STANDARD
        }
    }

    /**
     * 운전자의 설정까지 합친 톤. 운전자가 한 가지로 고정했으면 그대로(날씨 · 취향을 보지 않아요), 자동이면 켜 둔 것만 따라요.
     * 사람이 정한 것이 언제나 먼저예요.
     */
    fun pick(settings: ToneSettings, weather: Weather?, taste: Taste): Tone = when (settings.mode) {
        ToneMode.AUTO -> toneOf(if (settings.followWeather) weather else null, if (settings.followTaste) taste else Taste.NEUTRAL)
        ToneMode.STANDARD -> Tone.STANDARD
        ToneMode.CRISP -> Tone.CRISP
        ToneMode.CALM -> Tone.CALM
    }

    /** 주행 중에는 지금 톤을 지키고, 서 있을 때만 바라는 톤으로 바꿔요. */
    fun next(current: Tone, wanted: Tone, driving: Boolean): Tone = if (driving) current else wanted

    /** 낮 · 밤 × 톤에서 자리마다 쓸 토큰이에요. 기본 톤은 모든 자리가 기본 토큰이고, 다른 톤은 몇 자리만 바꿔요. */
    fun tokens(dark: Boolean, tone: Tone): Map<ToneRole, String> {
        val base = ToneRole.entries.associateWith { it.token }
        val swaps: Map<ToneRole, String> = when {
            tone == Tone.CRISP && !dark -> mapOf(
                ToneRole.TEXT_TERTIARY to "ivi_text_secondary",
                ToneRole.BUTTON_FILLED to "ivi_button_filled_pressed",
            )
            tone == Tone.CALM && !dark -> mapOf(
                ToneRole.BACKGROUND_SECONDARY to "ivi_surface_high",
                ToneRole.BACKGROUND_PRIMARY to "ivi_background_secondary",
                ToneRole.TEXT_TERTIARY to "ivi_text_secondary",
            )
            tone == Tone.CALM && dark -> mapOf(ToneRole.BACKGROUND_SECONDARY to "ivi_background_popup")
            else -> emptyMap()
        }
        return base + swaps
    }

    /**
     * 가이드의 색 규칙을 어기는 곳을 말로 돌려줘요(비어 있으면 통과). [values]는 이 톤의 색(ARGB), [baseline]은 같은 낮 · 밤의 기본 톤이에요.
     * 글자 3종은 바탕 3곳 위에서, 채움 버튼 위 글자는 버튼 위에서 4.5:1 이상. 채움 버튼과 바탕의 구분은 기본보다 나빠지지 않아요(4.5:1이면 충분해요).
     */
    fun check(values: Map<ToneRole, Long>, baseline: Map<ToneRole, Long>): List<String> {
        val out = mutableListOf<String>()
        fun v(m: Map<ToneRole, Long>, r: ToneRole) = m.getValue(r)
        val grounds = listOf(ToneRole.BACKGROUND_PRIMARY, ToneRole.BACKGROUND_SECONDARY, ToneRole.SURFACE_HIGH)
        for (t in listOf(ToneRole.TEXT_PRIMARY, ToneRole.TEXT_SECONDARY, ToneRole.TEXT_TERTIARY)) for (g in grounds) {
            val c = contrast(v(values, t), v(values, g))
            if (c < 4.5) out += "${t.name}이 ${g.name} 위에서 ${"%.1f".format(c)}:1이에요(4.5:1 이상)"
        }
        val onFilled = contrast(v(values, ToneRole.TEXT_ON_FILLED), v(values, ToneRole.BUTTON_FILLED))
        if (onFilled < 4.5) out += "채움 버튼 위 글자가 ${"%.1f".format(onFilled)}:1이에요(4.5:1 이상)"
        fun separation(m: Map<ToneRole, Long>) = listOf(ToneRole.BACKGROUND_PRIMARY, ToneRole.BACKGROUND_SECONDARY).minOf { contrast(v(m, ToneRole.BUTTON_FILLED), v(m, it)) }
        val sep = separation(values)
        val floor = minOf(separation(baseline), 4.5)
        if (sep < floor) out += "채움 버튼과 바탕의 구분이 ${"%.1f".format(sep)}:1로 기본(${"%.1f".format(floor)}:1)보다 나빠요"
        return out
    }

    private fun lum(c: Long): Double {
        fun ch(shift: Int): Double {
            val x = ((c shr shift) and 0xFF) / 255.0
            return if (x <= 0.03928) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    fun contrast(a: Long, b: Long): Double {
        val la = lum(a); val lb = lum(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
