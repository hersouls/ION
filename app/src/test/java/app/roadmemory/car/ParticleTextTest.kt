package app.roadmemory.car

import app.roadmemory.car.brain.Korean
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 이름 뒤 조사: 받침을 보고 골라요. 이름이 바뀔 때마다 "을(를)" 같은 자리 표시가 화면에 나오지 않게 소스에서 찾아요. */
class ParticleTextTest {
    @Test fun particlesFollowTheLastLetter() {
        assertEquals("가람 물총새 테마를", Korean.eul("가람 물총새 테마"))
        assertEquals("속초 관광수산시장을", Korean.eul("속초 관광수산시장"))
        assertEquals("경포대로", Korean.euro("경포대"))
        assertEquals("초당할머니순두부로", Korean.euro("초당할머니순두부"))
        assertEquals("강릉 경포해변으로", Korean.euro("강릉 경포해변"))
        assertEquals("충전 화면이", Korean.iga("충전 화면"))
    }

    @Test fun noPlaceholderParticleAfterANameInScreenText() {
        val bad = Regex("""\$\{[^{}]+\}(을\(를\)|\(으\)로|은\(는\)|이\(가\)|과\(와\))""")
        val hits = File("src/main/java").walkTopDown().filter { it.extension == "kt" }
            .flatMap { f -> f.readLines().withIndex().filter { bad.containsMatchIn(it.value) }.map { "${f.name}:${it.index + 1} ${it.value.trim().take(90)}" } }.toList()
        assertTrue("이름 뒤에 고정 조사가 남았어요(Korean.eul · euro · eun · iga를 써요):\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
