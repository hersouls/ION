package app.roadmemory.car

import app.roadmemory.car.brain.Conversation
import app.roadmemory.car.data.DemoUser
import app.roadmemory.car.studio.LibraryState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 가상 사용자 "문유": 이름은 한 곳에서 오고, 운전석에 사람이 있을 때만 그 사람을 이름으로 불러요(없는 사람을 지어내지 않아요). */
class DemoUserTest {
    @Test fun ridersAreNamedOnlyWhenTheDriverSeatIsTaken() {
        assertEquals("탄 사람이 없어요", DemoUser.riders(0, false))
        assertEquals("문유님 혼자 탔어요", DemoUser.riders(1, true))
        assertEquals("문유님 외 2명이 탔어요", DemoUser.riders(3, true))
        assertEquals("운전석이 비었으면 이름을 쓰지 않아요", "2명이 탔어요", DemoUser.riders(2, false))
    }

    @Test fun aTripSaysWhoWasAlongOnlyWhenTheCountIsKnown() {
        assertNull(DemoUser.withTrip(0))
        assertEquals("문유님 혼자", DemoUser.withTrip(1))
        assertEquals("문유님 외 최대 2명", DemoUser.withTrip(3))
    }

    @Test fun everyGreetingCallsTheUserByName() {
        val ui = UiState(sourceKind = app.roadmemory.car.data.SourceKind.PLEOS, mode = app.roadmemory.car.engine.Mode.PARKED)
        for (h in listOf(6, 12, 19, 23, 2)) {
            val hello = Conversation.greeting(ui, h, null, LibraryState(), LocalDate.of(2026, 11, 12))[0]
            assertTrue("$h: $hello", hello.contains(DemoUser.CALL))
        }
        assertEquals("문유님", DemoUser.CALL); assertEquals("문유", DemoUser.NAME); assertEquals("moonyou", DemoUser.ID)
        assertFalse(DemoUser.CALL.contains("시연"))
    }
}
