package app.roadmemory.car

import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.studio.AlbumPhoto
import app.roadmemory.car.studio.Art
import app.roadmemory.car.studio.ChargeShow
import app.roadmemory.car.studio.ChargeSlideKind
import app.roadmemory.car.studio.ChargeSource
import app.roadmemory.car.studio.SlideOrder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 충전 화면 슬라이드쇼의 규칙: 충전 시간에 맞춰 자르기, 걸어 둔 아트 섞기, 충전 상태 줄, 밤에 어둡게. 사진 · 작품 이름은 모두 지어낸 값이에요.
 * 남은 시간을 모르면 지어내지 않아요.
 */
class ChargeShowTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun ph(n: Int) = AlbumPhoto("a$n", "사진 $n", "img/a$n.jpg", if (n % 2 == 0) "강원" else "경기", "2025.${n % 12 + 1}")
    private val album = (1..100).map { ph(it) }
    private val art = Art("art1", "새벽 호수", "바람결 공방", "", emptyList(), 0, "img/art/c.jpg", listOf("img/art/1.jpg", "img/art/2.jpg", "img/art/3.jpg"))
    private fun plan(minLeft: Int?, seconds: Int = 7, fit: Boolean = true, a: Art? = art, ph: List<AlbumPhoto> = album, order: SlideOrder = SlideOrder.SEQUENCE, region: String? = null) =
        ChargeShow.plan(ph, a, order, region, today, minLeft, seconds, fit, seed = 3)

    @Test fun artIsMixedInAfterEveryFourthAlbumPhotoAndEachOnce() {
        val p = plan(null)
        assertEquals(103, p.slides.size)
        assertEquals(listOf(4, 9, 14), p.slides.withIndex().filter { it.value.kind == ChargeSlideKind.ART }.map { it.index })
        assertEquals("앨범 사진의 순서는 그대로예요", album.map { it.image }, p.slides.filter { it.kind == ChargeSlideKind.ALBUM }.map { it.image })
        assertEquals("작품 사진이 모두 한 번씩이에요", art.photos, p.slides.filter { it.kind == ChargeSlideKind.ART }.map { it.image })
        assertEquals(100, p.albumCount); assertEquals(3, p.artCount)
        assertEquals("걸어 둔 디지털 아트", p.slides[4].note)
        assertEquals("새벽 호수 · 바람결 공방", p.slides[4].title)
    }

    @Test fun artLeftOverGoesAtTheEndAndNothingIsDroppedOrRepeated() {
        val p = plan(null, ph = album.take(5))
        assertEquals(8, p.slides.size)
        assertEquals(8, p.slides.map { it.image }.toSet().size)
        assertEquals(ChargeSlideKind.ART, p.slides[4].kind)
        assertTrue(p.slides.takeLast(2).all { it.kind == ChargeSlideKind.ART })
    }

    @Test fun oneSideAloneAndNothingAtAll() {
        assertEquals(100, plan(null, a = null).slides.size)
        assertTrue(plan(null, a = null).slides.all { it.kind == ChargeSlideKind.ALBUM })
        assertEquals(3, plan(null, ph = emptyList()).slides.size)
        assertTrue(plan(null, a = null, ph = emptyList()).slides.isEmpty())
        assertEquals("", ChargeShow.timingLine(plan(null, a = null, ph = emptyList()), true, 5, true))
    }

    @Test fun thePersonChoosesAlbumOrArtOrBoth() {
        fun of(src: ChargeSource, a: Art? = art, ph: List<AlbumPhoto> = album) =
            ChargeShow.plan(ph, a, SlideOrder.SEQUENCE, null, today, null, 7, true, seed = 3, source = src)
        assertEquals(103, of(ChargeSource.BOTH).slides.size)
        val onlyAlbum = of(ChargeSource.ALBUM)
        assertEquals(100, onlyAlbum.slides.size); assertEquals(0, onlyAlbum.artCount)
        val onlyArt = of(ChargeSource.ART)
        assertEquals(art.photos, onlyArt.slides.map { it.image }); assertEquals(0, onlyArt.albumCount)
        // 고른 쪽이 비어 있으면 있는 것을 보여 줘요(칸이 사라지지 않아요)
        assertEquals(100, of(ChargeSource.ART, a = null).slides.size)
        assertEquals(3, of(ChargeSource.ALBUM, ph = emptyList()).slides.size)
        assertEquals(ChargeSource.BOTH, ChargeShow.effective(ChargeSource.ART, hasAlbum = true, hasArt = false))
        assertEquals(ChargeSource.ART, ChargeShow.effective(ChargeSource.ART, hasAlbum = true, hasArt = true))
        // 둘 다 있을 때만 고를 것이 있어요
        assertTrue(ChargeShow.canChoose(true, true))
        assertFalse(ChargeShow.canChoose(true, false)); assertFalse(ChargeShow.canChoose(false, true))
        assertEquals(listOf("디지털 아트", "디지털 앨범", "디지털 아트앨범"), ChargeSource.entries.map { it.label })
        // 아트만 골라도 충전 시간에 맞춰요
        assertEquals(3, ChargeShow.plan(album, art, SlideOrder.SEQUENCE, null, today, 5, 7, true, seed = 3, source = ChargeSource.ART).slides.size)
    }

    @Test fun fitCutsToTheMinutesLeftAndKeepsTheBestPicksFirst() {
        val p = plan(minLeft = 5, seconds = 7)
        assertTrue(p.fitted)
        assertEquals(42, p.slides.size)
        assertTrue("충전 시간(5분)을 넘지 않아요", p.totalSec <= 5 * 60)
        assertEquals("앞에서 고른 사진이 남아요", plan(null).slides.take(42), p.slides)
        // ION이 골라서: 가는 곳 근처 사진이 맨 앞이고 자르고도 남아요
        val ion = plan(minLeft = 5, seconds = 7, order = SlideOrder.ION, region = "강원 속초 · 고성")
        assertTrue(ion.slides.first().note.startsWith("가는 곳 근처"))
        assertEquals(42, ion.slides.size)
    }

    @Test fun withoutAMeasuredTimeOrWithFitOffEverythingStaysAndLoops() {
        assertFalse(plan(minLeft = null).fitted)
        assertEquals(103, plan(minLeft = null).slides.size)
        assertEquals("재지 않았으면 지어내서 자르지 않아요", 103, plan(minLeft = 0).slides.size)
        assertFalse(plan(minLeft = 5, fit = false).fitted)
        assertEquals(103, plan(minLeft = 5, fit = false).slides.size)
        // 시간이 사진보다 넉넉하면 자를 것이 없어요
        assertFalse(plan(minLeft = 120).fitted)
        assertEquals(103, plan(minLeft = 120).slides.size)
    }

    @Test fun fitKeepsAtLeastOneSlide() {
        assertEquals(5, plan(minLeft = 1, seconds = 12).slides.size)
        assertEquals(1, plan(minLeft = 1, seconds = 12, ph = album.take(1), a = null).slides.size)
    }

    @Test fun timingLineSaysWhatIsKnownOnly() {
        val cut = plan(minLeft = 5)
        assertEquals("80%까지 약 5분 · 그동안 42장을 보고 끝나요", ChargeShow.timingLine(cut, true, 5, true))
        val all = plan(minLeft = 120)
        assertEquals("80%까지 약 120분 · 한 바퀴(12분 1초)를 돌고 다시 처음부터 돌아요", ChargeShow.timingLine(all, true, 120, true))
        assertEquals("80%까지 약 5분 · 한 바퀴 12분 1초, 끝나면 처음부터 다시 돌아요", ChargeShow.timingLine(plan(minLeft = 5, fit = false), true, 5, false))
        assertEquals("충전 남은 시간을 재는 중이에요 · 한 바퀴 12분 1초", ChargeShow.timingLine(plan(null), true, null, true))
        assertEquals("한 바퀴 12분 1초", ChargeShow.timingLine(plan(null), false, null, true))
    }

    @Test fun sourceLine() {
        assertEquals("디지털 앨범 100장 · 걸어 둔 아트 3장", ChargeShow.sourceLine(100, 3))
        assertEquals("디지털 앨범 100장", ChargeShow.sourceLine(100, 0))
        assertEquals("걸어 둔 아트 3장", ChargeShow.sourceLine(0, 3))
    }

    @Test fun hudShowsBatteryAndMinutesLeftAndSaysWhen80Comes() {
        assertEquals("충전 62% · 80%까지 약 8분", ChargeShow.hud(62, true, 8, true).text)
        assertEquals("재는 중이면 그렇게 말해요", "충전 62% · 충전 속도를 재는 중이에요", ChargeShow.hud(62, true, null, true).text)
        val reached = ChargeShow.hud(80, true, 0, true)
        assertEquals("충전 80% · 80%가 됐어요", reached.text)
        assertTrue(reached.reached)
        assertTrue(ChargeShow.hud(93, true, null, true).reached)
        assertFalse(ChargeShow.hud(62, true, 8, true).reached)
        assertEquals("충전 중이에요", ChargeShow.hud(null, true, null, true).text)
    }

    @Test fun afterChargingEndsItSaysSoAndOtherwiseStaysQuiet() {
        assertEquals("충전을 마쳤어요 · 배터리 81%", ChargeShow.hud(81, false, null, true).text)
        assertEquals("충전을 마쳤어요", ChargeShow.hud(null, false, null, true).text)
        assertNull("충전과 상관없이 열었으면 말하지 않아요", ChargeShow.hud(62, false, null, false).text)
    }

    @Test fun fractionIsTowardEightyPercent() {
        assertEquals(0.5f, ChargeShow.fraction(40)!!, 0.001f)
        assertEquals(1f, ChargeShow.fraction(80)!!, 0.001f)
        assertEquals(1f, ChargeShow.fraction(100)!!, 0.001f)
        assertNull(ChargeShow.fraction(null))
    }

    @Test fun nightIsSevenInTheEveningToSixInTheMorning() {
        for (h in listOf(19, 22, 23, 0, 3, 5)) assertTrue("$h 시는 밤", ChargeShow.night(h))
        for (h in listOf(6, 9, 12, 18)) assertFalse("$h 시는 낮", ChargeShow.night(h))
        assertEquals(ChargeShow.NIGHT_DIM, ChargeShow.dim(23), 0f)
        assertEquals(0f, ChargeShow.dim(12), 0f)
    }

    @Test fun musicOfferFollowsTheMoodAndGivesItsReason() {
        val ui = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.CHARGING)
        val (track, why) = ChargeShow.music(ui, 23, 11)!!
        assertNotNull(track.title)
        assertTrue(why.startsWith("분위기 "))
        assertTrue("충전 중 · 밤에 맞는 곡이에요: $why", why.contains("충전") && why.contains("밤"))
    }
}
