package app.roadmemory.car

import app.roadmemory.car.gleo.GleoBridge
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gleo App Schema 작성 규칙(docs/PLEOS.md §6)을 지키는지 확인해요. */
class GleoSchemaTest {
    private val schema = JSONObject(File("src/main/assets/schema.json").readText())

    @Test fun topLevelFields() {
        assertEquals("app.roadmemory.car", schema.getString("packageName"))
        assertTrue(schema.getString("appDescription").isNotBlank())
        assertTrue(schema.getString("version").isNotBlank())
    }

    @Test fun atMostThreeFunctionsWithValidNamesAndTypes() {
        val calls = schema.getJSONArray("actionCalls")
        assertTrue("actionCalls는 최대 3개", calls.length() in 1..3)
        val allowed = setOf("string", "number", "boolean", "array")
        for (i in 0 until calls.length()) {
            val c = calls.getJSONObject(i)
            assertEquals("function", c.getString("type"))
            val f = c.getJSONObject("function")
            assertTrue(f.getString("name").matches(Regex("[A-Z]+(_[A-Z]+)*")))
            assertTrue(f.getString("component") in setOf("activity", "service"))
            assertTrue("설명은 영어(ASCII)", f.getString("description").all { it.code < 128 })
            val params = f.getJSONObject("parameters")
            assertEquals("object", params.getString("type"))
            val props = params.getJSONObject("properties")
            props.keys().forEach { k -> assertTrue(props.getJSONObject(k).getString("type") in allowed) }
        }
    }

    @Test fun schemaNamesMatchBridgeFunctions() {
        val calls = schema.getJSONArray("actionCalls")
        val names = (0 until calls.length()).map { calls.getJSONObject(it).getJSONObject("function").getString("name") }.toSet()
        assertEquals(GleoBridge.FUNCTIONS, names)
    }

    @Test fun parsesBothQuotedAndPlainActionParam() {
        assertEquals("a", GleoBridge.parseLoose("{\"place\":\"a\"}")?.getString("place"))
        assertEquals("a", GleoBridge.parseLoose("\"{\\\"place\\\":\\\"a\\\"}\"")?.getString("place"))
    }
}
