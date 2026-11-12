package app.roadmemory.car

import app.roadmemory.car.data.ActiveTelltale
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.CheckupReading
import app.roadmemory.car.data.TelltaleText
import app.roadmemory.car.data.TirePressure
import app.roadmemory.car.data.Wheel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 점검 화면의 읽은 값 우선: 경고등 · 타이어 공기압을 차가 읽어 주면 그 값을, 못 읽으면 null을 돌려줘서 화면이 기록으로 돌아가요. */
class CheckupReadingTest {
    private fun all(v: Float) = Wheel.entries.associateWith { v }

    @Test fun onlyWarningsGetAUserWordAndIndicatorsAreDropped() {
        assertEquals("타이어 공기압 경고", TelltaleText.label("TPMS"))
        assertNull("방향지시등 같은 표시등은 경고가 아니에요", TelltaleText.label("TURN_LEFT"))
        assertNull(TelltaleText.label("HIGH_BEAM"))
        val on = listOf(ActiveTelltale("HIGH_BEAM"), ActiveTelltale("TPMS"), ActiveTelltale("TPMS", blinking = true), ActiveTelltale("ABS"))
        assertEquals(listOf("TPMS", "ABS"), TelltaleText.warningsOf(on).map { it.type })
        assertEquals("타이어 공기압 경고 · ABS 경고", TelltaleText.sentence(on))
        assertNull(TelltaleText.sentence(listOf(ActiveTelltale("HIGH_BEAM"))))
    }

    @Test fun warningsAreNullWhenTheCarCannotBeRead() {
        assertNull("못 읽으면 기록을 써요", CheckupReading.warnings(CarSnapshot()))
        assertEquals("읽었는데 없으면 빈 목록(= 경고 없음)이에요", emptyList<String>(), CheckupReading.warnings(CarSnapshot(telltales = emptyList())))
        assertEquals(listOf("에어백 경고"), CheckupReading.warnings(CarSnapshot(telltales = listOf(ActiveTelltale("AIRBAG")))))
        assertTrue(CheckupReading.tireWarning(CarSnapshot(telltales = listOf(ActiveTelltale("TPMS")))))
        assertFalse(CheckupReading.tireWarning(CarSnapshot()))
    }

    @Test fun pressureUnitIsPickedByTheSizeOfTheValue() {
        assertEquals(2.5, TirePressure.toBar(2.5f)!!, 0.0)        // bar
        assertEquals(2.5, TirePressure.toBar(250f)!!, 0.0)        // kPa
        assertEquals(2.5, TirePressure.toBar(36.3f)!!, 0.0)       // psi
        assertNull(TirePressure.toBar(0f)); assertNull(TirePressure.toBar(null)); assertNull(TirePressure.toBar(-3f)); assertNull(TirePressure.toBar(9000f))
    }

    @Test fun halfAReadingIsNeverShown() {
        assertEquals(4, TirePressure.allBar(all(2.4f))!!.size)
        assertNull(TirePressure.allBar(all(2.4f) - Wheel.REAR_LEFT))
        assertNull(TirePressure.allBar(all(2.4f) + (Wheel.REAR_LEFT to 0f)))
        assertNull(CheckupReading.tiresBar(CarSnapshot()))
        assertEquals(2.4, CheckupReading.tiresBar(CarSnapshot(tirePressureRaw = all(2.4f)))!![Wheel.FRONT_LEFT]!!, 0.0)
    }

    @Test fun theNoteSaysWhichValuesTheCarGivesDirectly() {
        val none = CheckupReading.sourceNote(CarSnapshot())
        assertTrue(none, none.contains("문 · 트렁크 · 후드") && !none.contains("경고등") && !none.contains("타이어"))
        val both = CheckupReading.sourceNote(CarSnapshot(telltales = emptyList(), tirePressureRaw = all(2.5f)))
        assertTrue(both, both.contains("문 · 트렁크 · 후드 · 경고등 · 타이어 공기압") && both.contains("나머지는 서비스센터가 남긴 기록"))
    }
}
