package app.roadmemory.car

import app.roadmemory.car.data.ToneMode
import app.roadmemory.car.data.ToneSettings
import app.roadmemory.car.data.Weather
import app.roadmemory.car.studio.Look
import app.roadmemory.car.studio.LookColors
import app.roadmemory.car.studio.Theme
import app.roadmemory.car.ui.RainTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 비 오면 비 테마로: 서 있을 때만 바뀌고, 사람이 적용한 테마는 건드리지 않고, 비가 그치면 돌아와요. */
class RainThemeTest {
    private val c = LookColors(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
    private fun look() = Look("t", c, c, null, null, "photo.jpg", null)
    private fun theme(id: String, vararg tags: String, withLook: Boolean = true) =
        Theme(id, "$id 테마", "창작자", "", tags.toList(), 0, "cover.jpg", null, null, if (withLook) look() else null)

    private val sunny = theme("w_spring", "영화", "벚꽃")
    private val rainy = theme("w_rain", "드라마", "비", "청록")
    private val all = listOf(sunny, rainy)

    @Test fun picksOnlyADownloadedRainThemeWithALook() {
        assertEquals(rainy, RainTheme.pick(all, setOf("w_spring", "w_rain")))
        assertNull("내려받지 않았으면 쓰지 않아요", RainTheme.pick(all, setOf("w_spring")))
        assertNull("그림이 없는 테마는 쓰지 않아요", RainTheme.pick(listOf(theme("w_rain", "비", withLook = false)), setOf("w_rain")))
        assertNull("태그에 비가 없으면 비 테마가 아니에요", RainTheme.pick(listOf(sunny), setOf("w_spring")))
    }

    @Test fun wantsRainThemeOnlyWhenRainingAndSwitchedOn() {
        val on = ToneSettings()
        assertTrue(RainTheme.wanted(on, Weather.RAIN))
        for (w in listOf(Weather.CLEAR, Weather.CLOUDY, Weather.SNOW, Weather.FOG, null)) assertFalse("$w", RainTheme.wanted(on, w))
        assertFalse("스위치를 끄면 바꾸지 않아요", RainTheme.wanted(on.copy(rainTheme = false), Weather.RAIN))
        assertTrue("톤을 고정해 둬도 비 테마는 따로예요", RainTheme.wanted(on.copy(mode = ToneMode.CALM, followWeather = false), Weather.RAIN))
    }

    @Test fun neverChangesWhileDriving() {
        assertFalse("비가 와도 주행 중에는 그대로", RainTheme.next(current = false, wanted = true, driving = true))
        assertTrue("서면 바뀌어요", RainTheme.next(current = false, wanted = true, driving = false))
        assertTrue("비가 그쳐도 주행 중에는 그대로", RainTheme.next(current = true, wanted = false, driving = true))
        assertFalse("서면 돌아와요", RainTheme.next(current = true, wanted = false, driving = false))
    }

    @Test fun resolveKeepsThePersonsThemeAndFallsBackToIt() {
        assertEquals("비 테마가 덮어요", "w_rain", RainTheme.resolve(true, rainy, "w_spring"))
        assertEquals("비가 그치면 사람이 적용한 테마예요", "w_spring", RainTheme.resolve(false, rainy, "w_spring"))
        assertEquals("비 테마가 없으면 그대로예요", "w_spring", RainTheme.resolve(true, null, "w_spring"))
        assertNull("적용한 테마가 없었으면 기본 화면으로 돌아와요", RainTheme.resolve(false, rainy, null))
        assertEquals("적용한 테마가 없어도 비가 오면 비 테마예요", "w_rain", RainTheme.resolve(true, rainy, null))
    }

    @Test fun settingSurvivesSaveAndOldValuesDefaultOn() {
        val s = ToneSettings(mode = ToneMode.CRISP, followWeather = false, followTaste = true, rainTheme = false)
        assertEquals(s, ToneSettings.decode(s.encode()))
        assertTrue("예전에 저장한 값(필드 세 개)은 켠 채로 읽어요", ToneSettings.decode("AUTO|1|0").rainTheme)
        assertEquals(ToneSettings(), ToneSettings.decode(null))
    }

    @Test fun catalogHasARainThemeToDownload() {
        val raw = java.io.File("src/main/assets/studio/catalog.json").readText()
        assertTrue("카탈로그에 태그 '비'가 있는 테마가 있어야 해요", Regex(""""tags"\s*:\s*\[[^\]]*"비"""").containsMatchIn(raw))
    }
}
