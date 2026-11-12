package app.roadmemory.car

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 오너 지시(2026-11-11): "ion app 전체 화면에 시연 어쩌구저쩌구 텍스트 삭제해줘".
 * 화면에 나오는 글(코드의 문자열, 카탈로그의 제목 · 설명 · 이름)에 "시연"이라는 글자가 들어가면 안 돼요.
 * 주석 · KDoc은 화면에 나오지 않아서 검사하지 않아요.
 */
class NoDemoTextOnScreenTest {
    private val word = "시연"

    @Test fun noKotlinStringLiteralSaysDemo() {
        val hits = mutableListOf<String>()
        File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                val t = line.trim()
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")) return@forEachIndexed
                // 줄 끝 주석은 떼고 문자열 안만 봐요
                val code = line.substringBefore(" // ")
                if (Regex("\"[^\"\\n]*$word[^\"\\n]*\"").containsMatchIn(code)) hits += "${f.name}:${i + 1}: ${t.take(100)}"
            }
        }
        assertTrue("화면 글에 '$word'가 있어요:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun catalogTextNeverSaysDemo() {
        // about은 화면에 나오지 않는 데이터 설명이라 빼고 봐요
        val text = File("src/main/assets/studio/catalog.json").readText().replace(Regex("\"about\":\"[^\"]*\","), "")
        assertTrue("카탈로그의 화면 글에 '$word'가 있어요", !text.contains(word))
    }
}
