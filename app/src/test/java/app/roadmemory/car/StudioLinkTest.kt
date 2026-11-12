package app.roadmemory.car

import app.roadmemory.car.studio.CatalogMerge
import app.roadmemory.car.studio.CatalogParser
import app.roadmemory.car.studio.LibraryState
import app.roadmemory.car.studio.LinkRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/** ION Studio와 바로 잇는 길: 주소 · 키 · 경로의 규칙, Studio가 주는 목록(`ion-car/1`) 읽기, 앱 안 목록에 얹기. */
class StudioLinkTest {
    private val colors = """{"card":"rgba(255,255,255,0.86)","cardSolid":"#f9fafe","cardLine":"rgba(255,255,255,0.7)","veil":"rgba(255,255,255,0.5)","pill":"rgba(255,255,255,0.74)","bubble":"rgba(255,255,255,0.78)","textPrimary":"#05070b","textSecondary":"#3c3e41","textTertiary":"#696a6d","buttonFilled":"#366de2","textOnFilled":"#ffffff","barA":"#3D7BFF","barB":"#9FE7FF","informativeActive":"#02C265","informativeNegative":"#FE3D16","orb":"#3D7BFF"}"""
    // backend/car.py가 주는 모양 그대로(ION_Studio_v1.0/docs/API.md 「차량 ION 앱 연동」)
    private val remoteText = """{"format":"ion-car/1","rev":"0123456789abcdef","at":"2026-11-12T09:00:00+09:00","member":"문유","about":"",
      "themes":[
        {"id":"w_new","title":"새 테마","creator":"가람","summary":"s","tags":["야구"],"price":3000,"cover":"a/as_1.jpg","day":"a/as_2.jpg","night":null,
         "look":{"name":"새 테마","day":$colors,"night":$colors,"bgDay":"look/w_new/bg_day.jpg","bgNight":null,"photo":"look/w_new/photo.jpg","emblem":"look/w_new/emblem.png"},
         "cards":{"pack":"a/as_3.jpg","svc_theme":"a/as_4.jpg"},"previews":[{"title":"메인 홈 · 낮","image":"a/as_5.jpg"}]},
        {"id":"w_plain","title":"올린 테마","creator":"창작자","summary":"","tags":[],"price":0,"cover":"a/as_6.png","day":null,"night":null,"look":null,"cards":{},"previews":[]}],
      "arts":[{"id":"w_art","title":"작품","artist":"공방","summary":"","tags":[],"price":0,"cover":"a/as_7.jpg","photos":["a/as_8.jpg","a/as_9.jpg"]}],
      "packs":[{"id":"w_pack","title":"묶음","speaker":"선수","creator":"구단","summary":"","price":2000,"cover":"a/as_10.jpg","moments":5,"bytes":1000,"zip":"pack/w_pack.zip"}],
      "trips":[
        {"id":"t_mine","title":"내 일정","region":"강원","km":null,"status":"registered","mine":true,"days":[[{"name":"전망대","address":"","kind":"place","note":"","lat":37.5,"lng":128.5}]]},
        {"id":"t_text","title":"이름만 적은 일정","region":"","km":null,"status":"registered","mine":true,"days":[[{"name":"어딘가","address":"","kind":"place","note":""}]]},
        {"id":"t_shared","title":"공유 일정","region":"제주","km":120.5,"status":"shared","mine":false,"days":[[{"name":"가","address":"","kind":"place","note":"","lat":33.4,"lng":126.5}],[{"name":"나","address":"","kind":"food","note":"","lat":33.5,"lng":126.6}]]}]}"""

    @Test fun addressBecomesHttpsAndRefusesWhatIsNotAHost() {
        assertEquals("https://ion-studio.moonwave.kr", LinkRules.base(" ion-studio.moonwave.kr/ "))
        assertEquals("https://studio.example:8443", LinkRules.base("https://studio.example:8443"))
        listOf("", "http://studio.example", "studio.example/path", "user@studio.example", "스튜디오", "12345").forEach { assertNull(it, LinkRules.base(it)) }
    }

    @Test fun keyHasTheShapeStudioGives() {
        val key = "ion_test_" + "aB3_-xyz".repeat(4)
        assertEquals(key, LinkRules.key("  $key\n"))
        listOf("", "ion_test_short", "ion_live_" + "a".repeat(32), "ion_test_" + "가".repeat(30)).forEach { assertNull(it, LinkRules.key(it)) }
        assertEquals("ion_test_aB3_-…", LinkRules.hint(key))
        assertFalse(LinkRules.hint(key).contains(key.takeLast(8)))
    }

    @Test fun pathsPointAtTheCarFilesOfStudio() {
        assertEquals("https://h/api/v1/car/files/a/as_1.jpg", LinkRules.url("https://h", "link/a/as_1.jpg"))
        assertEquals("https://h/api/v1/car/catalog", LinkRules.catalogUrl("https://h"))
        assertTrue(LinkRules.isLinked("link/a/x.jpg")); assertFalse(LinkRules.isLinked("img/theme/x.jpg")); assertFalse(LinkRules.isLinked(null))
    }

    @Test fun packZipEntriesStayInsideThePackFolder() {
        assertEquals("pack.json", LinkRules.packEntry("w_pack", "w_pack/pack.json"))
        assertEquals("start.m4a", LinkRules.packEntry("w_pack", "w_pack/start.m4a"))
        listOf("other/pack.json", "w_pack/../x.json", "w_pack/sub/x.m4a", "w_pack/", "/etc/passwd", "w_pack/noext").forEach { assertNull(it, LinkRules.packEntry("w_pack", it)) }
    }

    @Test fun errorsSayWhatToDo() {
        assertTrue(LinkRules.message(401).contains("키"))
        assertTrue(LinkRules.message(429).contains("잦"))
        assertTrue(LinkRules.message(503).contains("503"))
    }

    @Test fun whenLabelSaysTodayYesterdayOrTheDate() {
        val zone = ZoneId.of("Asia/Seoul")
        fun at(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("오늘 09:12", LinkRules.whenLabel(at(12, 9, 12), at(12, 23, 0), zone))
        assertEquals("어제 21:40", LinkRules.whenLabel(at(11, 21, 40), at(12, 0, 5), zone))
        assertEquals("11월 9일 08:05", LinkRules.whenLabel(at(9, 8, 5), at(12, 9, 0), zone))
    }

    @Test fun studioCatalogIsReadWithThePrefix() {
        val c = CatalogParser.parse(remoteText, prefix = LinkRules.PREFIX)
        assertEquals("0123456789abcdef" to "문유", c.rev to c.member)
        val t = c.themes.first()
        assertEquals("link/a/as_1.jpg", t.cover); assertEquals("link/a/as_2.jpg", t.day); assertNull(t.night)
        assertEquals("link/look/w_new/bg_day.jpg", t.look?.bgDay); assertNull(t.look?.bgNight)
        assertEquals(mapOf("pack" to "link/a/as_3.jpg", "svc_theme" to "link/a/as_4.jpg"), t.look?.cards)
        assertEquals(listOf("메인 홈 · 낮" to "link/a/as_5.jpg"), t.look?.previews)
        assertTrue(t.files.all { LinkRules.isLinked(it) })
        assertNull(c.themes[1].look)                                   // 창작자가 올린 테마: 색이 없어요
        assertEquals(listOf("link/a/as_8.jpg", "link/a/as_9.jpg"), c.arts.single().photos)
        val k = c.packs.single()
        assertEquals(listOf("w_pack", "link/pack/w_pack.zip", "link/a/as_10.jpg"), listOf(k.id, k.zip, k.cover))
        assertEquals(5, k.moments)
        assertEquals(listOf(true, true, false), c.trips.map { it.mine })
        assertEquals(listOf(true, false, true), c.trips.map { it.guidable })
    }

    @Test fun studioListGoesOnTopOfTheBundledOne() {
        val bundled = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText(), File("src/main/assets/studio/sets.json").readText())
        assertEquals(bundled, CatalogMerge.merge(bundled, null))
        val remote = CatalogParser.parse(remoteText, prefix = LinkRules.PREFIX)
        val m = CatalogMerge.merge(bundled, remote)
        assertEquals(listOf("w_new", "w_plain") + bundled.themes.map { it.id }, m.themes.map { it.id })
        assertEquals(listOf("w_art") + bundled.arts.map { it.id }, m.arts.map { it.id })
        assertEquals(listOf("t_mine", "t_shared") + bundled.trips.map { it.id }, m.trips.map { it.id })      // 안내할 수 없는 일정은 빠져요
        assertEquals(1, CatalogMerge.skippedTrips(remote))
        assertEquals(bundled.album to bundled.calendar, m.album to m.calendar)                                 // 마이현대 쪽은 그대로
        assertEquals(remote.packs, m.packs)
        // 같은 id면 Studio 것이 이겨요
        val same = remote.copy(themes = listOf(remote.themes.first().copy(id = bundled.themes.first().id)))
        val m2 = CatalogMerge.merge(bundled, same)
        assertEquals(bundled.themes.size, m2.themes.size)
        assertEquals("새 테마", m2.theme(bundled.themes.first().id)?.title)
    }

    @Test fun everyTripOfTheBundledCatalogCanBeGuided() {
        val bundled = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
        assertTrue(bundled.trips.all { it.guidable })
    }

    @Test fun libraryKeepsWorkingWithStudioIds() {
        val lib = LibraryState().download("w_new").apply("svc_theme", "w_new")
        assertEquals("w_new", LibraryState.decode(lib.encode()).appliedId("svc_theme"))
    }
}
