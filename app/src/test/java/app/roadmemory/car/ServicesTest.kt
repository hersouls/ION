package app.roadmemory.car

import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.pack.PackInfo
import app.roadmemory.car.pack.PackSource
import app.roadmemory.car.ui.ServiceOrigin
import app.roadmemory.car.ui.Services
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ION 홈의 서비스 칸: 기획서(ION Service)의 여덟 가지와 같아야 하고, 이어지지 않은 서비스를 이어졌다고 말하지 않아요.
 */
class ServicesTest {
    private val base = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED)

    @Test fun eightServicesInTheSameOrderAsTheSite() {
        assertEquals(
            listOf("시그니처 송", "디스플레이 테마", "디지털 아트", "마이 트립", "디지털 앨범", "디지털 뮤직", "캘린더 동기화", "위치 동기화"),
            Services.all.map { it.title },
        )
    }

    @Test fun fourFromStudioAndFourFromMyHyundai() {
        assertEquals(4, Services.all.count { it.origin == ServiceOrigin.STUDIO })
        assertEquals(4, Services.all.count { it.origin == ServiceOrigin.MYHYUNDAI })
        assertEquals(listOf("시그니처 송", "디스플레이 테마", "디지털 아트", "마이 트립"), Services.all.filter { it.origin == ServiceOrigin.STUDIO }.map { it.title })
    }

    @Test fun settingsAndMemoryAreNotServices() {
        assertTrue(Services.all.none { it.title == "설정" || it.title == "기억" })
    }

    @Test fun everyServiceOpensARealPane() {
        for (s in Services.all) {
            assertTrue("${s.title} 화면 이름이 Pane.all에 없어요", s.pane in Pane.all)
            assertTrue("${s.title}을 눌러 여는 화면이 Pane.all에 없어요", Services.target(s) in Pane.all)
            assertNotNull(Services.byPane(s.pane))
        }
        assertEquals(Services.all.size, Services.all.map { it.pane }.toSet().size)
        assertNull(Services.byPane(Pane.CHARGE))
        assertNull(Services.byPane(null))
    }

    @Test fun signatureSongOpensThePackScreen() {
        assertEquals(Pane.PACK, Services.target(Services.byPane(Pane.PACK)!!))
    }

    @Test fun withoutACatalogServicesSaySoAndNeverClaimAConnection() {
        for (id in listOf(Pane.SVC_THEME, Pane.SVC_ART, Pane.SVC_TRIP, Pane.SVC_ALBUM, Pane.SVC_CALENDAR)) {   // 연주곡은 앱에 들어 있어서 카탈로그 없이도 곡 수를 알아요
            val s = Services.byPane(id)!!
            val st = Services.status(base, s)
            assertFalse("${s.title}: 이어지지 않았는데 준비됐다고 했어요", st.ready)
            assertEquals("${s.origin.label}와 아직 이어지지 않았어요", st.line)
            assertEquals("${s.origin.label} 연결 전", st.tile)
            assertTrue("${s.title}: 칸에 한 줄로 들어가야 해요", st.tile.length <= 16)
        }
    }

    @Test fun locationNeverClaimsALinkWithoutData() {
        val s = Services.byPane(Pane.SVC_LOCATION)!!
        // 제출 빌드: 실제 위치를 읽는 쪽(시험 빌드)은 없어요
        assertNull(app.roadmemory.car.family.FamilyLink.source)
        // 시연 값이 없으면 이어졌다고 말하지 않아요
        assertFalse(Services.status(base, s).ready)
        assertEquals("마이현대와 아직 이어지지 않았어요", Services.status(base, s).line)
        // 값이 있으면 위치는 누르기 전에는 말하지 않아요(몇 명이 보여 주는지만)
        val st = Services.status(base, s, catalog)
        assertTrue(st.ready)
        assertFalse(st.line.contains("시연"))
        assertEquals("가족 3명", st.tile)
        assertTrue(st.tile.length <= 16)
        // 자기 서비스 화면을 열어요(가족 · 자리 화면이 아니에요)
        assertEquals(Pane.SVC_LOCATION, Services.target(s))
    }

    @Test fun tripProgressIsKeptAndAdvances() {
        val t = catalog.trips.first()
        var lib = app.roadmemory.car.studio.LibraryState().download(t.id)
        assertNull(app.roadmemory.car.ui.TripRun.current(lib))
        lib = lib.apply(Pane.SVC_TRIP, app.roadmemory.car.ui.TripRun.encode(app.roadmemory.car.ui.TripProgress(t.id, 0)))
        assertEquals(app.roadmemory.car.ui.TripProgress(t.id, 0), app.roadmemory.car.ui.TripRun.current(lib))
        assertEquals(3, Services.progress(Services.byPane(Pane.SVC_TRIP)!!, catalog, lib))
        assertTrue(Services.status(base, Services.byPane(Pane.SVC_TRIP)!!, catalog, lib).tile.startsWith("가는 중 1/${t.stops.size}"))
        assertNull("깨진 값은 진행으로 읽지 않아요", app.roadmemory.car.ui.TripRun.parse("t_demo1#x"))
        assertEquals(app.roadmemory.car.studio.LibraryState.decode(lib.encode()), lib)
    }

    @Test fun calendarEventsWithPlacesHaveCoordinates() {
        val withCoords = catalog.calendar.filter { it.lat != null && it.lng != null }
        assertTrue("좌표 있는 약속이 있어야 안내 단추가 생겨요", withCoords.size >= 2)
        assertTrue(withCoords.all { it.lat!! in 33.0..39.0 && it.lng!! in 124.0..132.0 })
    }

    @Test fun hungArtShowsInStatus() {
        val a = catalog.arts.first()
        val lib = app.roadmemory.car.studio.LibraryState().download(a.id).apply(Pane.SVC_ART, a.id)
        assertEquals("걸어 둠 · ${a.title}", Services.status(base, Services.byPane(Pane.SVC_ART)!!, catalog, lib).tile)
        assertEquals(3, Services.progress(Services.byPane(Pane.SVC_ART)!!, catalog, lib))
    }

    @Test fun signatureStatusFollowsTheRealPacks() {
        val s = Services.byPane(Pane.PACK)!!
        assertEquals("이 기기에 목소리 묶음이 없어요", Services.status(base, s).line)
        assertEquals("묶음이 없어요", Services.status(base, s).tile)
        val pack = PackInfo("w_demo", "바다 응원가", "가상 선수", "", true, PackSource.BUNDLED, listOf("start"))
        assertEquals("묶음 1개 · 적용하지 않았어요", Services.status(base.copy(packs = listOf(pack)), s).line)
        assertEquals("묶음 1개 · 적용 안 함", Services.status(base.copy(packs = listOf(pack)), s).tile)
        val applied = Services.status(base.copy(packs = listOf(pack), pack = pack), s)
        assertEquals("적용 중 · 바다 응원가", applied.line)
        assertTrue(applied.ready)
    }

    // ── 시연 데이터와 workflow ──
    private val catalog = app.roadmemory.car.studio.CatalogParser.parse(java.io.File("src/main/assets/studio/catalog.json").readText())

    @Test fun catalogHoldsTheDemoData() {
        assertEquals(23, catalog.themes.size)
        assertEquals(17, catalog.arts.size)
        assertEquals(13, catalog.trips.size)
        assertEquals(100, catalog.album.size)   // 사진 100장(3D 그림은 지웠어요)
        assertEquals(5, catalog.calendar.size)
        assertEquals(4, catalog.family.size)
        assertTrue(catalog.trips.all { it.stops.isNotEmpty() && it.stops.all { s -> s.lat in 33.0..39.0 && s.lng in 124.0..132.0 } })
        assertTrue("카탈로그가 가리키는 그림 파일이 모두 있어야 해요", (catalog.themes.flatMap { it.files } + catalog.arts.flatMap { it.files } + catalog.album.flatMap { listOf(it.image, it.thumb) })
            .all { java.io.File("src/main/assets/studio/$it").isFile })
    }

    @Test fun statusLinesNeverSayDemo() {
        val lib = app.roadmemory.car.studio.LibraryState()
        for (id in listOf(Pane.SVC_THEME, Pane.SVC_ART, Pane.SVC_TRIP)) {
            val st = Services.status(base, Services.byPane(id)!!, catalog, lib)
            assertFalse("${st.line}: 화면에 시연이라는 글자를 쓰지 않아요(오너 지시 2026-11-11)", st.line.contains("시연"))
            assertFalse(st.tile.contains("시연"))
        }
        assertEquals("동기화 전 · 사진 100장", Services.status(base, Services.byPane(Pane.SVC_ALBUM)!!, catalog, lib).tile)
        assertEquals("동기화 전 · 내 곡 22곡", Services.status(base, Services.byPane(Pane.SVC_MUSIC)!!, catalog, lib).tile)
        assertEquals("동기화 전 · 일정 5개", Services.status(base, Services.byPane(Pane.SVC_CALENDAR)!!, catalog, lib).tile)
    }

    @Test fun libraryWorkflowDownloadApplyRemove() {
        val theme = catalog.themes.first()
        var lib = app.roadmemory.car.studio.LibraryState()
        assertEquals("내려받지 않은 것은 적용할 수 없어요", lib, lib.apply(Pane.SVC_THEME, theme.id))
        lib = lib.download(theme.id)
        assertEquals(2, Services.progress(Services.byPane(Pane.SVC_THEME)!!, catalog, lib))
        lib = lib.apply(Pane.SVC_THEME, theme.id)
        assertEquals(3, Services.progress(Services.byPane(Pane.SVC_THEME)!!, catalog, lib))
        assertEquals("적용 중 · ${theme.title}", Services.status(base, Services.byPane(Pane.SVC_THEME)!!, catalog, lib).tile)
        lib = lib.remove(theme.id)
        assertNull("지우면 적용도 풀려요", lib.appliedId(Pane.SVC_THEME))
        assertEquals(0, Services.progress(Services.byPane(Pane.SVC_THEME)!!, catalog, lib))
    }

    @Test fun libraryStateSurvivesRestart() {
        val a = catalog.themes[0].id; val b = catalog.arts[0].id
        val lib = app.roadmemory.car.studio.LibraryState().download(a).download(b).apply(Pane.SVC_THEME, a)
        assertEquals(lib, app.roadmemory.car.studio.LibraryState.decode(lib.encode()))
        assertEquals(app.roadmemory.car.studio.LibraryState(), app.roadmemory.car.studio.LibraryState.decode(null))
        assertEquals(app.roadmemory.car.studio.LibraryState(), app.roadmemory.car.studio.LibraryState.decode("깨진 글"))
    }

    @Test fun everyServiceDefinesItsWorkflow() {
        for (s in Services.all) {
            assertTrue("${s.title}: 단계가 넷 이상이어야 해요", s.steps.size >= 4)
            assertTrue("${s.title}: 하이엔드 설명이 있어야 해요", s.highEnd.isNotBlank())
            assertTrue("${s.title}: 접수 시연 · 시험 빌드에 없는 단계는 제안이라고 밝혀야 해요", s.steps.all { it.where in app.roadmemory.car.ui.StepWhere.entries })
        }
        // 위치 동기화: 가족이 켜는 첫 단계는 마이현대의 일(제안)이고, 앱이 하는 단계는 접수 시연(시연 값)이에요
        val loc = Services.byPane(Pane.SVC_LOCATION)!!.steps
        assertEquals(app.roadmemory.car.ui.StepWhere.PROPOSAL, loc.first().where)
        assertTrue(loc.drop(1).all { it.where == app.roadmemory.car.ui.StepWhere.DEMO })
    }

    // ── 테마를 적용하면 앱 창이 실제로 바뀌어요 ──
    @Test fun cssColorsParse() {
        assertEquals(0xFF082038L, app.roadmemory.car.studio.parseCssColor("#082038"))
        assertEquals(0xDB_FFFFFFL, app.roadmemory.car.studio.parseCssColor("rgba(255,255,255,0.86)"))
        assertEquals(0x80_000000L, app.roadmemory.car.studio.parseCssColor("rgba(0, 0, 0, 0.5)"))
        assertEquals(0xFF0E8082L, app.roadmemory.car.studio.parseCssColor("rgb(14,128,130)"))
    }

    @Test fun everyThemeCarriesItsLook() {
        for (t in catalog.themes) {
            val look = t.look ?: error("${t.title}: look이 없어요")
            assertTrue("${t.title}: 창 바탕 그림", look.bgDay != null && look.bgNight != null)
            assertTrue("${t.title}: 사진과 엠블럼", look.photo != null && look.emblem != null)
            assertTrue("${t.title}: 내려받을 파일이 모두 있어야 해요", t.files.all { java.io.File("src/main/assets/studio/$it").isFile })
            // 글자는 카드 바탕(불투명한 쪽) 위에서 읽혀야 해요(가이드 4.5:1)
            for ((name, c) in listOf("낮" to look.day, "밤" to look.night)) {
                assertTrue("${t.title} $name: 글자 대비", contrast(c.textPrimary, c.cardSolid) >= 4.5)
                assertTrue("${t.title} $name: 단추 글자 대비", contrast(c.textOnFilled, c.buttonFilled) >= 4.5)
            }
        }
    }

    @Test fun appliedThemeChangesThePalette() {
        // 테마가 정한 색이 Palette의 자리에 그대로 들어가요(색 이름이 같아요)
        val look = catalog.themes.first().look!!
        assertTrue("낮과 밤의 카드 색이 달라야 해요", look.day.card != look.night.card)
        assertTrue("테마마다 단추 색이 다른 것이 있어야 해요", catalog.themes.map { it.look!!.day.buttonFilled }.toSet().size > 1)
    }

    private fun lum(argb: Long): Double {
        fun ch(v: Int): Double { val c = v / 255.0; return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(((argb shr 16) and 255).toInt()) + 0.7152 * ch(((argb shr 8) and 255).toInt()) + 0.0722 * ch((argb and 255).toInt())
    }
    private fun contrast(a: Long, b: Long): Double { val x = lum(a); val y = lum(b); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05) }
}
