package app.roadmemory.car

import app.roadmemory.car.data.DemoCheckup
import app.roadmemory.car.studio.CatalogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/** 점검 화면을 채우는 지어낸 정비 값: 모양이 말이 되고, 캘린더 동기화의 "정기 점검 예약"과 같은 약속인지 지켜요. */
class DemoCheckupTest {
    @Test fun theNextCheckIsAlwaysATuesdayAfterToday() {
        var d = LocalDate.of(2026, 11, 8)
        repeat(21) {
            val n = DemoCheckup.nextCheck(d)
            assertEquals(DayOfWeek.TUESDAY, n.dayOfWeek)
            assertTrue("$d → $n", n.isAfter(d) && n.isBefore(d.plusDays(8)))
            d = d.plusDays(1)
        }
        assertEquals(LocalDate.of(2026, 11, 17), DemoCheckup.nextCheck(LocalDate.of(2026, 11, 11)))   // 수요일 → 다음 주 화요일
    }

    @Test fun theNextCheckMatchesTheCalendarAppointment() {
        val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
        val e = cat.calendar.first { it.title.contains("정기 점검") }
        assertTrue("캘린더는 화요일 오후 2시예요: ${e.whenText}", e.whenText.contains("화요일") && e.whenText.contains("오후 2시"))
        assertTrue("장소도 같아요: ${e.place}", e.place == DemoCheckup.PLACE)
    }

    @Test fun theValuesAreSane() {
        assertTrue(DemoCheckup.wheels.size == 4 && DemoCheckup.wheels.all { DemoCheckup.pressureWord(it) == "적정" })
        assertTrue(DemoCheckup.BATTERY_HEALTH_PERCENT in 1..100)
        assertTrue(DemoCheckup.consumables.all { it.leftPercent in 0..100 })
        assertEquals("가까운 순이에요", DemoCheckup.due.map { it.inDays }, DemoCheckup.due.map { it.inDays }.sorted())
        assertEquals("최근 기록이 먼저예요", DemoCheckup.records.map { it.daysAgo }, DemoCheckup.records.map { it.daysAgo }.sorted())
        assertTrue(DemoCheckup.records.all { it.daysAgo > 0 && it.title.isNotBlank() })
    }

    @Test fun theHistoryAndTheDueDatesAgree() {
        // 마지막 점검에서 공기압을 맞췄고, 같은 일을 곧 또 해야 한다고 말하지 않아요
        val last = DemoCheckup.records.first()
        assertTrue(last.title.contains("공기압"))
        val dueNames = DemoCheckup.due.map { it.name }
        assertTrue("방금 한 일을 곧 또 하라고 하면 안 돼요", DemoCheckup.records.filter { it.daysAgo < 60 }.none { r -> dueNames.any { d -> r.title.contains(d.removeSuffix(" 교환")) && r.title.contains("교환") } })
        // 곧 해야 하는 교환은 이미 한 적이 있는 일이에요(주기가 6개월 안팎)
        DemoCheckup.due.forEach { d ->
            val key = d.name.removeSuffix(" 교환")
            val done = DemoCheckup.records.firstOrNull { r -> (r.title + r.detail).contains(key) }
            assertTrue("${d.name}을 한 기록이 없어요", done != null)
            val cycle = done!!.daysAgo + d.inDays
            assertTrue("${d.name} 주기가 ${cycle}일이에요", cycle in 150..260)
        }
    }

    @Test fun thePressureWordNamesLowAndHigh() {
        assertEquals("낮아요", DemoCheckup.pressureWord(app.roadmemory.car.data.WheelPressure("x", 2.0)))
        assertEquals("높아요", DemoCheckup.pressureWord(app.roadmemory.car.data.WheelPressure("x", 3.0)))
        assertEquals("적정", DemoCheckup.pressureWord(app.roadmemory.car.data.WheelPressure("x", 2.4)))
    }

    @Test fun relativeTimesReadNaturally() {
        assertEquals("24일 전", DemoCheckup.ago(24)); assertEquals("4개월 전", DemoCheckup.ago(131)); assertEquals("1년 전", DemoCheckup.ago(380)); assertEquals("오늘", DemoCheckup.ago(0))
        assertEquals("내일", DemoCheckup.inDays(1)); assertEquals("6일 뒤", DemoCheckup.inDays(6)); assertEquals("약 2개월 뒤", DemoCheckup.inDays(62))
        assertEquals("2026.11.17(화)", DemoCheckup.dateLabel(LocalDate.of(2026, 11, 17)))
    }
}
