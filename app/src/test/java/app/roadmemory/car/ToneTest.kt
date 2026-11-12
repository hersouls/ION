package app.roadmemory.car

import app.roadmemory.car.data.Weather
import app.roadmemory.car.engine.Taste
import app.roadmemory.car.ui.ToneRole
import app.roadmemory.car.ui.Tone
import app.roadmemory.car.ui.Tones
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 날씨 톤이 현대(Pleos) 색 규칙을 지키는지: 실제 res/values(-night)/colors.xml 값으로 재요. */
class ToneTest {
    private fun load(path: String): Map<String, Long> =
        Regex("""<color name="([a-z_]+)">#([0-9A-Fa-f]{6})</color>""").findAll(File(path).readText())
            .associate { it.groupValues[1] to (0xFF000000L or it.groupValues[2].toLong(16)) }

    private val day = load("src/main/res/values/colors.xml")
    private val night = load("src/main/res/values-night/colors.xml")
    private fun colors(dark: Boolean, tone: Tone): Map<ToneRole, Long> {
        val file = if (dark) night else day
        return Tones.tokens(dark, tone).mapValues { (_, token) -> file[token] ?: error("colors.xml에 $token 이 없어요") }
    }

    @Test fun everyToneKeepsTheGuideContrastRules() {
        for (dark in listOf(false, true)) {
            val standard = colors(dark, Tone.STANDARD)
            for (tone in Tone.entries) {
                assertEquals("낮밤=${if (dark) "밤" else "낮"} 톤=$tone", emptyList<String>(), Tones.check(colors(dark, tone), standard))
            }
        }
    }

    @Test fun tonesOnlyChooseExistingTokensNeverStatusColours() {
        for (dark in listOf(false, true)) for (tone in Tone.entries) {
            val file = if (dark) night else day
            Tones.tokens(dark, tone).values.forEach {
                assertTrue("$it 가 colors.xml에 없어요", it in file)
                assertFalse("$it 는 상태를 말하는 색이라 톤에 못 써요", it.startsWith("ivi_informative_"))
            }
        }
    }

    @Test fun standardToneIsExactlyTheDefaultTokens() {
        for (dark in listOf(false, true)) assertEquals(ToneRole.entries.associateWith { it.token }, Tones.tokens(dark, Tone.STANDARD))
    }

    @Test fun nightClearIsLikeStandardBecauseThereIsNoSunGlare() {
        assertEquals(Tones.tokens(true, Tone.STANDARD), Tones.tokens(true, Tone.CRISP))
    }

    @Test fun aToneThatBreaksTheRulesIsCaught() {
        val standard = colors(false, Tone.STANDARD)
        val pale = standard + (ToneRole.TEXT_TERTIARY to day.getValue("ivi_text_quaternary")) // 연한 회색 글자
        assertTrue(Tones.check(pale, standard).any { it.startsWith("TEXT_TERTIARY") })
        val weak = standard + (ToneRole.BUTTON_FILLED to day.getValue("ivi_button_basic_enabled")) // 흰 글자 + 연한 버튼
        assertTrue(Tones.check(weak, standard).any { it.contains("채움 버튼 위 글자") })
    }

    @Test fun driversTasteDecidesOnlyWhenWeatherDoesNot() {
        // 날씨를 모르거나 흐림: 운전자의 취향을 따라요
        for (w in listOf(null, Weather.CLOUDY)) {
            assertEquals(Tone.CRISP, Tones.toneOf(w, Taste.COOL))
            assertEquals(Tone.CALM, Tones.toneOf(w, Taste.WARM))
            assertEquals(Tone.STANDARD, Tones.toneOf(w, Taste.NEUTRAL))
        }
        // 날씨가 가려낸 날은 날씨가 먼저(시야 · 눈부심은 안전 쪽 판단)
        for (t in Taste.entries) {
            assertEquals(Tone.CALM, Tones.toneOf(Weather.RAIN, t))
            assertEquals(Tone.CALM, Tones.toneOf(Weather.FOG, t))
            assertEquals(Tone.CRISP, Tones.toneOf(Weather.CLEAR, t))
        }
    }

    @Test fun weatherPicksTheToneAndDrivingFreezesIt() {
        assertEquals(Tone.CRISP, Tones.toneOf(Weather.CLEAR))
        assertEquals(Tone.CALM, Tones.toneOf(Weather.RAIN))
        assertEquals(Tone.CALM, Tones.toneOf(Weather.SNOW))
        assertEquals(Tone.CALM, Tones.toneOf(Weather.FOG))
        assertEquals(Tone.STANDARD, Tones.toneOf(Weather.CLOUDY))
        assertEquals(Tone.STANDARD, Tones.toneOf(null))
        assertEquals(Tone.STANDARD, Tones.next(Tone.STANDARD, Tone.CALM, driving = true))
        assertEquals(Tone.CALM, Tones.next(Tone.STANDARD, Tone.CALM, driving = false))
        assertEquals(Tone.CALM, Tones.next(Tone.CALM, Tone.CRISP, driving = true))
    }
}
