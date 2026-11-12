package app.roadmemory.car

import app.roadmemory.car.data.DemoTrips
import app.roadmemory.car.data.KoreaMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 여행 기록 세부 화면의 작은 지도: 도착지 좌표가 그림 안에 정확히 놓이는지. */
class KoreaMapTest {
    @Test fun everyDemoDestinationLandsInsideTheMap() {
        DemoTrips.all.filter { it.lat != null }.forEach { t ->
            val (x, y) = KoreaMap.project(t.lat!!, t.lon!!)!!
            assertTrue("${t.destName} $x,$y", x in 0f..1f && y in 0f..1f)
        }
    }

    @Test fun directionsAreRight() {
        val seoul = KoreaMap.project(37.5665, 126.978)!!
        val busan = KoreaMap.project(35.1796, 129.0756)!!
        val mokpo = KoreaMap.project(34.8118, 126.3922)!!
        val sokcho = KoreaMap.project(38.2058, 128.5909)!!
        assertTrue("부산은 서울의 오른쪽 아래", busan.first > seoul.first && busan.second > seoul.second)
        assertTrue("목포는 서울의 왼쪽 아래", mokpo.first < busan.first && mokpo.second > seoul.second)
        assertTrue("속초는 서울의 오른쪽 위", sokcho.first > seoul.first && sokcho.second < seoul.second)
    }

    @Test fun outsideTheMapIsNull() {
        assertNull(KoreaMap.project(35.68, 139.69))      // 밖(도쿄)
        assertNull(KoreaMap.project(33.50, 126.53))      // 제주는 지도 밖
        assertNull(KoreaMap.project(Double.NaN, 127.0))
        assertNotNull(KoreaMap.project(KoreaMap.LAT_MIN, KoreaMap.LON_MIN))
    }

    @Test fun outlineHasEnoughPointsAndStaysInside() {
        assertTrue(KoreaMap.coast.size >= 40)
        (KoreaMap.coast + KoreaMap.dmz).forEach { (lat, lon) -> assertNotNull("$lat,$lon", KoreaMap.project(lat, lon)) }
        KoreaMap.cities.forEach { (n, lat, lon) -> assertNotNull(n, KoreaMap.project(lat, lon)) }
        assertTrue("가로 ÷ 세로가 한반도 남쪽다워야 해요", KoreaMap.aspect in 0.55..0.75)
    }

    @Test fun coastRunsFromTheEastDmzToTheWestDmz() {
        assertEquals(128.36, KoreaMap.coast.first().second, 0.01)
        assertEquals(126.76, KoreaMap.coast.last().second, 0.01)
        assertTrue("동쪽 끝이 서쪽 끝보다 동쪽", KoreaMap.coast.first().second > KoreaMap.coast.last().second)
    }
}
