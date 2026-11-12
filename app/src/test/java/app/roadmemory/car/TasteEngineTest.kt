package app.roadmemory.car

import app.roadmemory.car.data.DriverCabin
import app.roadmemory.car.engine.Taste
import app.roadmemory.car.engine.TasteEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class TasteEngineTest {
    private fun on(t: Float) = DriverCabin(setTempC = t, hvacOn = true)
    private fun run(start: Taste, vararg temps: Float): Taste = temps.fold(start) { p, t -> TasteEngine.step(p, on(t)) }

    @Test fun lowSettingIsCoolHighIsWarmMiddleIsNeutral() {
        assertEquals(Taste.COOL, run(Taste.NEUTRAL, 18f))
        assertEquals(Taste.COOL, run(Taste.NEUTRAL, 21f))
        assertEquals(Taste.NEUTRAL, run(Taste.NEUTRAL, 22f))
        assertEquals(Taste.NEUTRAL, run(Taste.NEUTRAL, 24f))
        assertEquals(Taste.WARM, run(Taste.NEUTRAL, 25f))
        assertEquals(Taste.WARM, run(Taste.NEUTRAL, 28f))
    }

    @Test fun edgesHaveHysteresisSoColourDoesNotFlicker() {
        assertEquals(Taste.COOL, run(Taste.NEUTRAL, 21f, 21.5f))   // 나오는 값은 22℃
        assertEquals(Taste.NEUTRAL, run(Taste.NEUTRAL, 21f, 22f))
        assertEquals(Taste.WARM, run(Taste.NEUTRAL, 25f, 24.5f))   // 나오는 값은 24℃
        assertEquals(Taste.NEUTRAL, run(Taste.NEUTRAL, 25f, 24f))
        assertEquals(Taste.WARM, run(Taste.COOL, 28f))             // 한 번에 반대쪽으로 가도 돼요
    }

    @Test fun hvacOffOrNoValueSaysNothingAboutTaste() {
        assertEquals(Taste.NEUTRAL, TasteEngine.step(Taste.COOL, DriverCabin(setTempC = 18f, hvacOn = false)))
        assertEquals(Taste.NEUTRAL, TasteEngine.step(Taste.WARM, DriverCabin(setTempC = 28f, hvacOn = null)))
        assertEquals(Taste.NEUTRAL, TasteEngine.step(Taste.COOL, DriverCabin(setTempC = null, hvacOn = true)))
        assertEquals(Taste.NEUTRAL, TasteEngine.step(Taste.NEUTRAL, DriverCabin()))
    }
}

class ToneSettingsTest {
    private fun pick(s: app.roadmemory.car.data.ToneSettings, w: app.roadmemory.car.data.Weather?, t: Taste) =
        app.roadmemory.car.ui.Tones.pick(s, w, t)

    @org.junit.Test fun autoFollowsWhatIsSwitchedOn() {
        val all = app.roadmemory.car.data.ToneSettings()
        assertEquals(app.roadmemory.car.ui.Tone.CALM, pick(all, app.roadmemory.car.data.Weather.RAIN, Taste.COOL))
        assertEquals(app.roadmemory.car.ui.Tone.CRISP, pick(all, null, Taste.COOL))
        val noWeather = all.copy(followWeather = false)
        assertEquals(app.roadmemory.car.ui.Tone.CRISP, pick(noWeather, app.roadmemory.car.data.Weather.RAIN, Taste.COOL))
        val noTaste = all.copy(followTaste = false)
        assertEquals(app.roadmemory.car.ui.Tone.STANDARD, pick(noTaste, null, Taste.COOL))
        assertEquals(app.roadmemory.car.ui.Tone.CALM, pick(noTaste, app.roadmemory.car.data.Weather.FOG, Taste.COOL))
        assertEquals(app.roadmemory.car.ui.Tone.STANDARD, pick(all.copy(followWeather = false, followTaste = false), app.roadmemory.car.data.Weather.RAIN, Taste.WARM))
    }

    @org.junit.Test fun aFixedToneIgnoresWeatherAndTaste() {
        for (w in listOf(null, app.roadmemory.car.data.Weather.RAIN, app.roadmemory.car.data.Weather.CLEAR)) for (t in Taste.entries) {
            assertEquals(app.roadmemory.car.ui.Tone.STANDARD, pick(app.roadmemory.car.data.ToneSettings(app.roadmemory.car.data.ToneMode.STANDARD), w, t))
            assertEquals(app.roadmemory.car.ui.Tone.CRISP, pick(app.roadmemory.car.data.ToneSettings(app.roadmemory.car.data.ToneMode.CRISP), w, t))
            assertEquals(app.roadmemory.car.ui.Tone.CALM, pick(app.roadmemory.car.data.ToneSettings(app.roadmemory.car.data.ToneMode.CALM), w, t))
        }
    }

    @org.junit.Test fun savedSettingsComeBackAndGarbageFallsBackToDefaults() {
        for (m in app.roadmemory.car.data.ToneMode.entries) for (a in listOf(true, false)) for (b in listOf(true, false)) {
            val s = app.roadmemory.car.data.ToneSettings(m, a, b)
            assertEquals(s, app.roadmemory.car.data.ToneSettings.decode(s.encode()))
        }
        assertEquals(app.roadmemory.car.data.ToneSettings(), app.roadmemory.car.data.ToneSettings.decode(null))
        assertEquals(app.roadmemory.car.data.ToneSettings(), app.roadmemory.car.data.ToneSettings.decode("???"))
        assertEquals(app.roadmemory.car.data.ToneMode.AUTO, app.roadmemory.car.data.ToneSettings.decode("MAGENTA|0|1").mode)
    }
}
