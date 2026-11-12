package app.roadmemory.car.debug

import app.roadmemory.car.family.FamilyRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 위치 서비스 답에서 허용 목록의 사람만 고르는지. 이름 · 좌표는 모두 지어낸 값이에요. */
class PositionParseTest {
    private val alias = linkedMapOf("가나" to "하나", "다라" to "둘")

    private fun dev(person: String?, t: Long, state: String = "move", place: String? = null, hidden: Boolean = false, paused: Boolean = false, fix: Boolean = true): String {
        val fixJson = if (fix) """{"t":$t,"lat":37.1,"lng":127.1,"acc":10.0}""" else "null"
        val placeJson = if (place == null) "null" else """{"name":"$place"}"""
        val personJson = if (person == null) "null" else "\"$person\""
        return """{"id":"x","person":$personJson,"hidden":$hidden,"paused":$paused,"fix":$fixJson,"now":{"state":"$state","place":$placeJson}}"""
    }

    private fun body(vararg devices: String) = """{"now":1000000,"devices":[${devices.joinToString(",")}]}"""

    @Test fun picksOnlyAllowedPeople_inAliasOrder() {
        val r = PositionParse.people(body(dev("다라", 990_000), dev("남", 990_000), dev("가나", 940_000, "stay", "집")), alias) as FamilyRead.Ready
        assertEquals(listOf("하나", "둘"), r.people.map { it.alias })
        assertEquals("집", r.people[0].placeName)
        assertEquals(60L, r.people[0].ageSec)
        assertEquals(10L, r.people[1].ageSec)
    }

    @Test fun skipsHiddenPausedAndNoFix() {
        val r = PositionParse.people(body(dev("가나", 990_000, hidden = true), dev("다라", 990_000, paused = true), dev("가나", 0, fix = false)), alias)
        assertTrue(r is FamilyRead.Nobody)
    }

    @Test fun usesFreshestOfSeveralDevices() {
        val r = PositionParse.people(body(dev("가나", 900_000), dev("가나", 995_000)), alias) as FamilyRead.Ready
        assertEquals(1, r.people.size)
        assertEquals(5L, r.people[0].ageSec)
    }

    @Test fun movingIgnoresPlaceName() {
        val r = PositionParse.people(body(dev("가나", 990_000, "move", "집")), alias) as FamilyRead.Ready
        assertTrue(r.people[0].moving)
        assertEquals(null, r.people[0].placeName)
    }

    @Test fun driverIsNotListed() {
        val r = PositionParse.people(body(dev("가나", 990_000), dev("다라", 990_000)), alias, selfAlias = "하나") as FamilyRead.Ready
        assertEquals(listOf("둘"), r.people.map { it.alias })
    }

    @Test fun badBodyFails() {
        assertTrue(PositionParse.people("not json", alias) is FamilyRead.Failed)
        assertTrue(PositionParse.people("{}", alias) is FamilyRead.Failed)
    }

    @Test fun aliasParseKeepsOrderAndDropsEmpty() {
        val m = FamilyConfig.parseAlias("""[["가","하나"],["나"," "],["다","셋"]]""")
        assertEquals(listOf("가", "다"), m.keys.toList())
    }
}
