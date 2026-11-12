package app.roadmemory.car

import app.roadmemory.car.ontology.Ontology
import app.roadmemory.car.pack.PackFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시그니처 송 팩(ion-pack/1)을 읽는 규칙, 그리고 앱에 번들로 넣은 팩이 ION-Link와 맞는지. */
class PackFileTest {
    private val good = """
        {"format":"ion-pack/1","id":"w_demo22","title":"벼락의 타석","speaker":"벼락","viral":"가상 숏폼 '벼락 타석 루틴'","voice":"synthetic","cover":"cover.jpg",
         "moments":[{"moment":"board","line":"안녕! 오늘도 같이 가자!","file":"board.m4a","ms":4200},{"moment":"start","line":"출발!","file":"start.m4a","ms":1800}]}
    """.trimIndent()

    @Test fun readsAWholePack() {
        val p = PackFile.parse(good)!!
        assertEquals("w_demo22", p.id); assertEquals("벼락의 타석", p.title); assertEquals("벼락", p.speakerName)
        assertTrue(p.synthetic); assertEquals("cover.jpg", p.cover)
        assertEquals(listOf("board", "start"), p.moments.map { it.moment })
        assertEquals(4200, p.momentOf("board")!!.ms); assertNull(p.momentOf("door"))
    }

    @Test fun refusesWhatIsNotAPack() {
        assertNull(PackFile.parse("깨진 글"))
        assertNull("형식이 달라요", PackFile.parse(good.replace("ion-pack/1", "ion-pack/9")))
        assertNull("id가 폴더 이름으로 쓸 수 없어요", PackFile.parse(good.replace("w_demo22", "../x")))
        assertNull("순간이 하나도 없어요", PackFile.parse("""{"format":"ion-pack/1","id":"a","moments":[]}"""))
    }

    @Test fun skipsMomentsThatPointOutsideTheFolderOrRepeat() {
        val p = PackFile.parse(
            """{"format":"ion-pack/1","id":"a","title":"가","moments":[
              {"moment":"board","line":"하나","file":"../board.m4a"},{"moment":"start","line":"둘","file":"sub/start.m4a"},
              {"moment":"door","line":"셋","file":"door.m4a"},{"moment":"door","line":"넷","file":"door2.m4a"},{"moment":"","line":"빈","file":"x.m4a"}]}""",
        )!!
        assertEquals(listOf("door"), p.moments.map { it.moment })
        assertEquals("먼저 나온 것을 써요", "셋", p.moments.single().line)
    }

    @Test fun voiceDefaultsToSyntheticAndNameFallsBackToTheTitle() {
        val p = PackFile.parse("""{"format":"ion-pack/1","id":"a","title":"제목","moments":[{"moment":"start","line":"","file":"s.m4a"}]}""")!!
        assertTrue(p.synthetic); assertEquals("제목", p.speakerName)
        assertFalse(PackFile.parse(good.replace("synthetic", "recorded"))!!.synthetic)
    }

    /** 앱에 번들로 든 팩(assets/packs)은 모두 읽히고, 파일이 있고, 목소리가 있는 순간이 ION-Link에서 내놓을 수 있는 순간이에요. */
    @Test fun bundledPacksMatchTheGraph() {
        val o = Ontology.parse(File("src/main/assets/ontology.json").readText(Charsets.UTF_8))
        val root = File("src/main/assets/packs")
        val dirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        for (d in dirs) {
            val p = assertNotNull("${d.name}: pack.json을 읽지 못했어요", PackFile.parse(File(d, "pack.json").readText(Charsets.UTF_8)))
            assertEquals(d.name, p.id)
            assertTrue("${p.id}: 필수 순간 start가 없어요", p.momentOf("start") != null)
            for (m in p.moments) {
                assertTrue("${p.id}/${m.file}: 파일이 없어요", File(d, m.file).length() > 0)
                assertTrue("${p.id}: ${m.moment}은(는) 이 앱이 내놓을 수 없는 순간이에요", o.offerable("mo.${m.moment}"))
                assertTrue("${p.id}/${m.moment}: 자막이 비었어요", m.line.isNotBlank())
            }
        }
    }

    private fun <T> assertNotNull(msg: String, v: T?): T { org.junit.Assert.assertNotNull(msg, v); return v!! }
}
