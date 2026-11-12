package app.roadmemory.car

import app.roadmemory.car.data.CarPart
import app.roadmemory.car.ontology.Ontology
import app.roadmemory.car.pack.MomentText
import app.roadmemory.car.pack.MomentValues
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** ION이 화면에 쓰는 문장은 차가 준 값으로만 채워요. 값을 모르면 그 자리가 든 문장을 빼고, 지어내지 않아요. */
class MomentTextTest {
    private val o = Ontology.parse(File("src/main/assets/ontology.json").readText(Charsets.UTF_8))
    private fun info(id: String) = o.momentInfo("mo.$id")

    @Test fun fillsWithWhatTheCarGave() {
        assertEquals("출발해요. 배터리 48%로 312km 갈 수 있어요", MomentText.fill(info("start"), MomentValues(soc = 48, rangeKm = 312)))
        assertEquals("충전을 시작해요. 배터리는 48%예요", MomentText.fill(info("charge"), MomentValues(soc = 48)))
        assertEquals("8분 뒤에 도착해요", MomentText.fill(info("near"), MomentValues(minutes = 8)))
        assertEquals("2시간 10분째 달렸어요. 쉬어 가요", MomentText.fill(info("rest"), MomentValues(driveTime = "2시간 10분")))
        assertEquals("바깥이 영하 3도예요. 갈 수 있는 거리가 줄 수 있어요", MomentText.fill(info("cold"), MomentValues(outside = "영하 3도")))
        assertEquals("설정 온도를 22도로 바꿨어요", MomentText.fill(info("climate"), MomentValues(temp = "22도")))
    }

    @Test fun dropsTheSentenceWhoseValueIsUnknown() {
        assertEquals("출발해요", MomentText.fill(info("start"), MomentValues()))
        assertEquals("두 값 중 하나만 알아도 그 자리가 든 문장은 빠져요", "출발해요", MomentText.fill(info("start"), MomentValues(soc = 48)))
        assertEquals("주차했어요", MomentText.fill(info("park"), MomentValues()))
        assertEquals("주차했어요. 이번에는 12km 달렸어요", MomentText.fill(info("park"), MomentValues(tripKm = 12)))
        assertNull("문장이 하나뿐이고 값을 모르면 글이 없어요", MomentText.fill(info("near"), MomentValues()))
        assertEquals("값이 든 문장만 빠지고 값이 없는 문장은 남아요(추운 날 순간은 바깥 온도를 알 때만 나와요)", "갈 수 있는 거리가 줄 수 있어요", MomentText.fill(info("cold"), MomentValues()))
        assertNull(MomentText.fill(null, MomentValues(soc = 1)))
    }

    @Test fun destinationIsOptionalAndPartAgreesWithItsEnding() {
        assertEquals("도착했어요 · 푸른 바닷가", MomentText.fill(info("arrive"), MomentValues(destination = "푸른 바닷가")))
        assertEquals("도착했어요", MomentText.fill(info("arrive"), MomentValues()))
        assertEquals("조수석 문이 열렸어요", MomentText.fill(info("door"), MomentValues(part = CarPart.PASSENGER_DOOR)))
        assertEquals("트렁크가 열렸어요", MomentText.fill(info("door"), MomentValues(part = CarPart.TRUNK)))
        assertEquals("후드가 열렸어요", MomentText.fill(info("door"), MomentValues(part = CarPart.HOOD)))
        assertNull(MomentText.fill(info("door"), MomentValues()))
    }

    @Test fun noMomentLeavesAnOpenSlotBehind() {
        val full = MomentValues(1, 2, CarPart.TRUNK, 3, "22도", 4, "집", "1시간", "영하 1도")
        for (m in o.moments) {
            for (v in listOf(full, MomentValues())) {
                val t = MomentText.fill(m.props["info"], v) ?: continue
                assertFalse("${m.label}: 빈자리가 남았어요 → $t", t.contains('{') || t.contains('}'))
            }
        }
        assertTrue(o.moments.all { it.props["info"]?.isNotBlank() == true })
    }
}
