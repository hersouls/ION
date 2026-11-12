package app.roadmemory.car

import app.roadmemory.car.brain.ChatCommand
import app.roadmemory.car.brain.Conversation
import app.roadmemory.car.studio.AlbumPhoto
import app.roadmemory.car.studio.CatalogParser
import app.roadmemory.car.studio.SlideOrder
import app.roadmemory.car.studio.Slideshow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** 앨범 슬라이드쇼의 규칙(2026-11-11): ION이 고르는 순서 · 이유 한 줄 · 시간 안내 · 달리는 중 금지 · 말로 시작 / 멈춤. */
class SlideshowTest {
    private fun ph(id: String, region: String, month: String) = AlbumPhoto(id, "사진 $id", "img/photo/$id.jpg", region, month)
    private val today = LocalDate.of(2026, 11, 11)

    private val a = ph("a", "강원", "2021.03")   // 가는 곳 근처
    private val b = ph("b", "경기", "2023.11")   // 이맘때(같은 달, 3년 전)
    private val c = ph("c", "제주", "2024.12")   // 이맘때(한 달 차이, 2년 전)
    private val d = ph("d", "경남", "2025.05")   // 그 밖
    private val e = ph("e", "강원", "2025.11")   // 근처이면서 이맘때 → 근처가 먼저
    private val f = ph("f", "", "2026.10")       // 올해 사진은 이맘때가 아니에요
    private val album = listOf(d, f, c, b, a, e)

    @Test fun ionOrderIsNearThenSeasonThenTheRest() {
        val plan = Slideshow.plan(album, SlideOrder.ION, "강원 속초 · 고성", today)
        assertEquals(listOf("a", "e"), plan.take(2).map { it.photo.id })        // 근처(앨범에 든 순서)
        assertEquals(listOf("b", "c"), plan.drop(2).take(2).map { it.photo.id }) // 이맘때: 같은 달이 먼저, 한 달 차이가 다음
        assertEquals(setOf("d", "f"), plan.drop(4).map { it.photo.id }.toSet())  // 나머지
        assertEquals("가는 곳 근처 · 2021.03 · 강원", plan[0].note)
        assertEquals("3년 전 이맘때 · 2023.11 · 경기", plan[2].note)
        assertEquals("2년 전 이맘때 · 2024.12 · 제주", plan[3].note)
        assertTrue(plan.drop(4).all { !it.note.contains("근처") && !it.note.contains("이맘때") })
        assertEquals("올해 사진은 이맘때라고 하지 않아요", "2026.10", plan.first { it.photo.id == "f" }.note)
    }

    @Test fun withoutATripNothingIsCalledNear() {
        val plan = Slideshow.plan(album, SlideOrder.ION, null, today)
        assertTrue(plan.none { it.note.contains("가는 곳 근처") })
        assertEquals(listOf("e", "b", "c"), plan.take(3).map { it.photo.id })    // e(2025.11, 1년 전 이맘때, 차이 0 · 최근 해가 먼저) → b(0) → c(1)
    }

    @Test fun everyPhotoAppearsExactlyOnceInEveryOrder() {
        for (o in SlideOrder.entries) {
            val plan = Slideshow.plan(album, o, "강원", today, seed = 7)
            assertEquals("$o", album.map { it.id }.sorted(), plan.map { it.photo.id }.sorted())
        }
        val big = (1..100).map { ph("p$it", if (it % 3 == 0) "강원" else "경기", "20${10 + it % 16}.${"%02d".format(1 + it % 12)}") }
        for (o in SlideOrder.entries) assertEquals(100, Slideshow.plan(big, o, "강원", today, seed = 3).map { it.photo.id }.toSet().size)
    }

    @Test fun sequenceKeepsAlbumOrderAndShuffleFollowsTheSeed() {
        val big = (1..100).map { ph("p$it", "", "2020.01") }
        assertEquals(big.map { it.id }, Slideshow.plan(big, SlideOrder.SEQUENCE, null, today).map { it.photo.id })
        val one = Slideshow.plan(big, SlideOrder.SHUFFLE, null, today, seed = 1).map { it.photo.id }
        assertEquals("같은 씨앗이면 같은 순서", one, Slideshow.plan(big, SlideOrder.SHUFFLE, null, today, seed = 1).map { it.photo.id })
        assertNotEquals("씨앗이 다르면 순서가 달라요", one, Slideshow.plan(big, SlideOrder.SHUFFLE, null, today, seed = 2).map { it.photo.id })
        assertNotEquals(big.map { it.id }, one)
    }

    @Test fun seasonAcrossTheNewYear() {
        val jan = LocalDate.of(2026, 1, 5)
        assertEquals(1, Slideshow.yearsAgoThisSeason(ph("x", "", "2025.12"), jan))   // 12월과 1월은 한 달 차이
        assertNull(Slideshow.yearsAgoThisSeason(ph("y", "", "2025.07"), jan))
        assertNull("월을 모르는 사진은 이맘때라고 하지 않아요", Slideshow.yearsAgoThisSeason(ph("z", "", ""), jan))
    }

    @Test fun timesAreSaidPlainly() {
        assertEquals("45초", Slideshow.duration(45))
        assertEquals("8분", Slideshow.duration(480))
        assertEquals("11분 40초", Slideshow.duration(700))
        assertEquals("사진 100장 · 한 바퀴 11분 40초", Slideshow.lapText(100, 7))
        assertEquals("충전 80%까지 약 5분 · 그동안 42장을 봐요", Slideshow.chargeFit(5, 100, 7))
        assertEquals("충전 80%까지 약 20분 · 그동안 한 바퀴 넘게 돌아요", Slideshow.chargeFit(20, 100, 7))
        assertNull("남은 시간을 모르면 말하지 않아요", Slideshow.chargeFit(null, 100, 7))
        assertNull(Slideshow.chargeFit(0, 100, 7))
    }

    @Test fun neverPlaysWhileDriving() {
        assertFalse(Slideshow.allowed(driving = true))
        assertTrue(Slideshow.allowed(driving = false))
    }

    @Test fun theRealAlbumPlansAllHundred() {
        val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
        for (o in SlideOrder.entries) assertEquals(100, Slideshow.plan(cat.album, o, "강원 속초 · 고성", today, seed = 5).map { it.photo.id }.toSet().size)
        val ion = Slideshow.plan(cat.album, SlideOrder.ION, "강원 속초 · 고성", today)
        assertTrue("가는 곳이 강원이면 강원 사진이 맨 앞", ion.first().note.startsWith("가는 곳 근처"))
        assertTrue("이맘때 사진이 있어요", ion.any { it.note.contains("년 전 이맘때") })
        assertTrue(ion.all { it.note.length <= 40 })
    }

    @Test fun anUnreadVoiceCommandExpiresAfterFifteenSeconds() {
        var now = 1_000_000L
        app.roadmemory.car.studio.SlideshowControl.clock = { now }
        try {
            app.roadmemory.car.studio.SlideshowControl.start()
            assertEquals(app.roadmemory.car.studio.SlideshowControl.Cmd.START, app.roadmemory.car.studio.SlideshowControl.command)
            now += 14_000
            assertEquals("14초 뒤에도 읽을 수 있어요", app.roadmemory.car.studio.SlideshowControl.Cmd.START, app.roadmemory.car.studio.SlideshowControl.command)
            now += 2_000
            assertNull("16초가 지나면 없는 것으로 쳐요(화면이 늦게 열려도 뜬금없이 시작하지 않아요)", app.roadmemory.car.studio.SlideshowControl.command)
            app.roadmemory.car.studio.SlideshowControl.stop()
            assertEquals(app.roadmemory.car.studio.SlideshowControl.Cmd.STOP, app.roadmemory.car.studio.SlideshowControl.command)
            app.roadmemory.car.studio.SlideshowControl.clear()
            assertNull(app.roadmemory.car.studio.SlideshowControl.command)
        } finally {
            app.roadmemory.car.studio.SlideshowControl.clock = { System.currentTimeMillis() }
            app.roadmemory.car.studio.SlideshowControl.clear()
        }
    }

    @Test fun voiceStartsAndStopsTheSlideshow() {
        assertEquals(ChatCommand.Slideshow(true), Conversation.command("슬라이드쇼 틀어 줘"))
        assertEquals(ChatCommand.Slideshow(true), Conversation.command("앨범 슬라이드쇼 보여 줘"))
        assertEquals(ChatCommand.Slideshow(true), Conversation.command("사진 돌려 줘"))
        assertEquals("멈춰가 곡 멈춤으로 새지 않아요", ChatCommand.Slideshow(false), Conversation.command("슬라이드쇼 멈춰 줘"))
        assertEquals(ChatCommand.Slideshow(false), Conversation.command("슬라이드쇼 꺼 줘"))
        assertEquals(ChatCommand.Slideshow(false), Conversation.command("슬라이드쇼 그만"))
        // 다른 명령은 그대로예요
        assertEquals(ChatCommand.StopMusic, Conversation.command("음악 멈춰"))
        assertEquals(ChatCommand.Open(app.roadmemory.car.Pane.SVC_ALBUM, "디지털 앨범"), Conversation.command("앨범 보여 줘"))
    }
}
