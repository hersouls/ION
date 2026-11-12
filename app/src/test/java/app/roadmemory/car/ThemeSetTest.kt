package app.roadmemory.car

import app.roadmemory.car.studio.CatalogParser
import app.roadmemory.car.ui.ThemeCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 디스플레이 테마의 카드 세트: ION 홈의 카드 열네 장이 테마의 그림을 입어요(서비스 여덟 + 화면 여섯).
 * 세트는 sets.json에 따로 있어서, Studio의 catalog.json을 다시 뽑아도 남아요.
 */
class ThemeSetTest {
    private val dir = File("src/main/assets/studio")
    private val setsText = File(dir, "sets.json").takeIf { it.exists() }?.readText()
    private val plain = CatalogParser.parse(File(dir, "catalog.json").readText())
    private val cat = CatalogParser.parse(File(dir, "catalog.json").readText(), setsText)
    private val withSet get() = cat.themes.filter { ThemeCards.isSet(it.look) }

    @Test fun theCardsAreTheHomesFourteen() {
        assertEquals(14, ThemeCards.all.size)
        assertEquals(ThemeCards.all.size, ThemeCards.all.map { it.first }.toSet().size)
        // 서비스 여덟이 앞에, 화면 여섯이 뒤에: ION 홈의 차례와 같아요
        assertEquals(app.roadmemory.car.ui.Services.all.map { it.pane }, ThemeCards.all.take(8).map { it.first })
    }

    /** docs/theme_cards.json은 ION Studio와 나누는 약속이에요. 앱의 카드 목록(이름 · 순서)과 같아야 해요. */
    @Test fun theContractFileForStudioMatchesTheCards() {
        // 약속 파일은 앱 폴더 밖(기획 저장소의 docs/)에 있어요. 앱만 담은 제출 저장소에서는 건너뛰어요.
        val contract = File("../../docs/theme_cards.json")
        org.junit.Assume.assumeTrue("docs/theme_cards.json이 이 저장소에 없어요", contract.exists())
        val o = org.json.JSONObject(contract.readText())
        val a = o.getJSONArray("cards")
        val fromFile = List(a.length()) { i -> a.getJSONObject(i).let { it.getString("key") to it.getString("label") } }
        assertEquals(ThemeCards.all, fromFile)
        assertEquals(768, o.getJSONObject("card_image").getInt("width"))
        assertEquals(4, o.getJSONObject("previews").getJSONArray("items").length())
    }

    @Test fun everyThemeBelongsToAKind_andTheKindsCoverTheCatalog() {
        val kinds = cat.themes.map { it.id to app.roadmemory.car.ui.ThemeKind.of(it) }
        assertEquals("갈래가 없는 테마: " + kinds.filter { it.second == null }.map { it.first }, emptyList<String>(), kinds.filter { it.second == null }.map { it.first })
        app.roadmemory.car.ui.ThemeKind.entries.forEach { k -> assertTrue("${k.label}에 테마가 없어요", kinds.any { it.second == k }) }
        assertEquals(cat.themes.size, app.roadmemory.car.ui.ThemeKind.entries.sumOf { k -> kinds.count { it.second == k } })
    }

    /** 2026-11-12부터 앱 안의 테마는 모두 카드 세트를 가져요(세 개는 art/post_theme_sets.py, 나머지는 tools/import_studio_sets.py가 ION Studio에서 가져와요). */
    @Test fun everyThemeCarriesAFullSet() {
        assertEquals(cat.themes.map { it.id }, withSet.map { it.id })
        withSet.forEach { assertEquals("${it.title}에 빠진 카드", emptyList<String>(), ThemeCards.missing(it.look)) }
    }

    @Test fun everyCardIsAFileInsideTheApp() {
        withSet.forEach { t -> t.look!!.cards.forEach { (key, path) -> assertTrue("${t.id} $key → $path", File(dir, path).isFile) } }
    }

    @Test fun everyPreviewIsAFileInsideTheApp_andComesWithTheDownload() {
        // 앱에서 캡처한 미리 보기는 처음 세 테마에만 있어요(Studio에서 가져온 세트는 카드 14장뿐이에요)
        assertEquals(setOf("w_demo13", "w_demo21", "w_demo35"), withSet.filter { it.look!!.previews.isNotEmpty() }.map { it.id }.toSet())
        withSet.forEach { t ->
            val look = t.look!!
            look.previews.forEach { (title, path) ->
                assertTrue("${t.id} $title → $path", File(dir, path).isFile)
                assertTrue(path in t.files)
            }
        }
    }

    @Test fun cardsAreNamedAfterTheScreenTheyDress() {
        val valid = ThemeCards.all.map { it.first }.toSet()
        withSet.forEach { t -> assertTrue(t.look!!.cards.keys.all { it in valid }) }
    }

    @Test fun downloadingATheme_copiesItsCardsToo() {
        withSet.forEach { t -> assertTrue(t.look!!.cards.values.all { it in t.files }) }
    }

    @Test fun withoutSets_aThemeIsWhatItWasBefore() {
        assertTrue(plain.themes.all { it.look?.cards?.isEmpty() ?: true })
        assertFalse(plain.themes.any { ThemeCards.isSet(it.look) })
        assertEquals(plain.themes.map { it.id }, cat.themes.map { it.id })
    }

    @Test fun aThemeWithoutAFullSetIsNotCalledOne() {
        val partial = CatalogParser.parse(
            File(dir, "catalog.json").readText(),
            """{"${plain.themes.first().id}":{"cards":{"charge":"img/x.jpg"}}}""",
        )
        assertFalse(ThemeCards.isSet(partial.themes.first().look))
    }
}
