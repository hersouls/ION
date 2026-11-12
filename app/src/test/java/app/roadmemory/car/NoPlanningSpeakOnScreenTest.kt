package app.roadmemory.car

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 사용자가 보는 글에 기획 말투나 개발자 말투가 남지 않게 지켜요(사용자 관점 점검, 2026-11-11).
 * 기획서에서 쓰는 말("하이엔드 제안이에요" · "기획서의 제안")이나 값의 이름("좌표가 없어요")은 화면에 나오지 않아요: 사이트에서 말해요.
 * 주석 · KDoc은 화면에 나오지 않아서 검사하지 않아요(NoDemoTextOnScreenTest와 같은 방식).
 */
class NoPlanningSpeakOnScreenTest {
    private val words = listOf("하이엔드 제안", "기획서", "좌표가 없어요")

    @Test fun noKotlinStringLiteralSpeaksLikeAPlan() {
        val hits = mutableListOf<String>()
        File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                val t = line.trim()
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return@forEachIndexed
                val code = line.substringBefore(" // ")
                words.forEach { w -> if (Regex("\"[^\"\\n]*$w[^\"\\n]*\"").containsMatchIn(code)) hits += "${f.name}:${i + 1}: '$w' ${t.take(90)}" }
            }
        }
        assertTrue("화면 글에 기획 · 개발자 말투가 있어요:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
