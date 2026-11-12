package app.roadmemory.car

import app.roadmemory.car.brain.Korean
import org.junit.Assert.assertEquals
import org.junit.Test

/** 화면에 "일상와 맞아요"처럼 틀린 조사가 나오지 않게, 받침에 따라 "과 · 와"를 붙여요. */
class KoreanGwaTest {
    @Test fun withFinalConsonantItIsGwa() {
        assertEquals("일상과", Korean.gwa("일상"))
        assertEquals("청록과", Korean.gwa("청록"))
        assertEquals("아침과", Korean.gwa("아침"))
        assertEquals("가을 · 맑음과", Korean.gwa("가을 · 맑음"))
    }

    @Test fun withoutFinalConsonantItIsWa() {
        assertEquals("바다와", Korean.gwa("바다"))
        assertEquals("도시와", Korean.gwa("도시"))
        assertEquals("응원 · 도시와", Korean.gwa("응원 · 도시"))
    }

    @Test fun notHangulKeepsBothForms() {
        assertEquals("Loop(과)와", Korean.gwa("Loop"))
    }
}
