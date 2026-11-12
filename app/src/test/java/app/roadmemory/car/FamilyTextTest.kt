package app.roadmemory.car

import app.roadmemory.car.family.FamilyPlace
import app.roadmemory.car.family.FamilyText
import app.roadmemory.car.family.RouteFail
import app.roadmemory.car.family.RouteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 가족 위치 카드의 말: 조사, 시각, 오래된 위치, 내비 결과. 이름 · 좌표는 모두 지어낸 값이에요. */
class FamilyTextTest {
    private fun place(placeName: String? = null, moving: Boolean = false, age: Long? = 120) =
        FamilyPlace("하나", 37.0, 127.0, placeName, moving, age)

    @Test fun josa_followsTheLastSyllable() {
        assertEquals("은", FamilyText.eunNeun("막내딸"))      // ㄹ 받침
        assertEquals("는", FamilyText.eunNeun("어머니"))
        assertEquals("는", FamilyText.eunNeun("하나"))
        assertEquals("은(는)", FamilyText.eunNeun("ABC"))
        assertEquals("이", FamilyText.iGa("첫째아들"))
        assertEquals("가", FamilyText.iGa("하나"))
    }

    @Test fun state_namedPlaceThenMovingThenStay() {
        assertEquals("집에 있어요", FamilyText.state(place(placeName = "집")))
        assertEquals("이동 중이에요", FamilyText.state(place(moving = true)))
        assertEquals("한 곳에 머물러 있어요", FamilyText.state(place()))
    }

    @Test fun age_saysStaleBeyondThirtyMinutes() {
        assertEquals("방금 받은 위치예요", FamilyText.age(20))
        assertEquals("2분 전 위치예요", FamilyText.age(120))
        assertEquals("29분 전 위치예요", FamilyText.age(29 * 60L))
        assertTrue(FamilyText.age(31 * 60L).contains("오래된 위치예요"))
        assertTrue(FamilyText.age(2 * 3600L).startsWith("2시간 전"))
        assertTrue(FamilyText.age(47 * 3600L).startsWith("47시간 전"))
        assertTrue(FamilyText.age(5 * 86400L).startsWith("5일 전"))
        assertEquals("받은 시각을 몰라요", FamilyText.age(null))
    }

    @Test fun hint_isOneShortLine() {
        val h = FamilyText.hint(place(moving = true, age = 120))
        assertEquals("이동 중이에요 · 2분 전 위치예요", h)
    }

    @Test fun routeNote_coversEveryOutcome() {
        assertEquals("내비에 하나가 있는 곳을 보냈어요. 경로를 고르지 않으면 곧 안내가 시작돼요", FamilyText.routeNote("하나", RouteResult.Sent))
        RouteFail.entries.forEach { why ->
            val s = FamilyText.routeNote("하나", RouteResult.Failed(why))
            assertTrue("$why has a sentence", s.isNotBlank() && s.length <= 32)
        }
    }
}
