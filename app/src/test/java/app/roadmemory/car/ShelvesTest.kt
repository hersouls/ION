package app.roadmemory.car

import app.roadmemory.car.brain.AlbumShelf
import app.roadmemory.car.brain.ArtShelf
import app.roadmemory.car.brain.MusicShelf
import app.roadmemory.car.brain.ThemeShelf
import app.roadmemory.car.brain.TripLegs
import app.roadmemory.car.brain.TripPlan
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.SavedTrip
import app.roadmemory.car.data.SourceKind
import app.roadmemory.car.data.Tracks
import app.roadmemory.car.data.Weather
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.studio.CatalogParser
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 서비스 화면의 고르기 규칙(2026-11-11 "모두 하이엔드급으로 고도화"): 곡 · 사진 · 작품 · 테마 · 정류장 사이.
 * 고른 것에는 까닭이 붙고, 맞는 것이 없으면 고르지 않아요.
 */
class ShelvesTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val parked = UiState(sourceKind = SourceKind.PLEOS, mode = Mode.PARKED)
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    // ── 디지털 뮤직
    @Test fun musicForNowIsOrderedByHowWellItFitsAndGivesReasons() {
        val rainyNightCharge = parked.copy(mode = Mode.CHARGING, weather = Weather.RAIN)
        val picks = MusicShelf.forNow(rainyNightCharge, 23, 7)
        assertEquals("Harbor Breeze Lullaby", picks.first().track.title)
        assertEquals(setOf("충전", "밤", "비"), picks.first().hits)
        assertTrue("맞는 것이 많은 순서예요", picks.zipWithNext().all { (a, b) -> a.hits.size >= b.hits.size })
        assertTrue("하나도 안 맞는 곡은 없어요", picks.all { it.hits.isNotEmpty() })
        assertEquals("충전 · 비 · 밤", MusicShelf.nowLabel(rainyNightCharge, 23, 7))
        // 한낮 맑은 날에는 달리는 곡이 앞이에요
        assertTrue(MusicShelf.forNow(parked.copy(weather = Weather.CLEAR), 13, 7).first().track.title in MusicShelf.group("달릴 때").map { it.title })
    }

    @Test fun musicGroupsCoverEveryTrackAndLinkToTrips() {
        assertEquals("모든 곡이 어느 묶음엔가 들어 있어요", Tracks.all.map { it.title }.toSet(), MusicShelf.groups.flatMap { MusicShelf.group(it.first) }.map { it.title }.toSet())
        assertEquals(listOf("Suspended Air", "Nah Got A Sea", "Banana & Breeze", "Soul sweet", "Home Is Where We Go", "Sea Remains", "산방산 아침해", "3842 (Aiguille du Midi)", "Light In The Cold Wind", "Hit 'em up", "Blue Curve Walk", "Solcho Sea Loop", "Paju Drive Breeze", "Yeongil Breeze Loop"), MusicShelf.group("달릴 때").map { it.title })
        assertTrue(MusicShelf.group("없는 묶음").isEmpty())
        val trips = listOf(
            SavedTrip(1, day(2024, 11, 10), "2024.11", "경포대", null, null, 120, 3, listOf("Solcho Sea Loop"), 1),
            SavedTrip(2, day(2026, 10, 3), "2026.10", "제주", null, null, 200, 3, listOf("Solcho Sea Loop", "October Maple Loop"), 2),
        )
        assertEquals("여행 2번에 함께했어요 · 마지막 2026.10", MusicShelf.tripLine("Solcho Sea Loop", trips))
        assertNull(MusicShelf.tripLine("Rose & Pond Loop", trips))
        assertEquals(listOf("Solcho Sea Loop" to 2, "October Maple Loop" to 1), MusicShelf.mostWithTrips(trips).map { it.first.title to it.second })
        assertEquals("0:42", MusicShelf.clock(42_300)); assertEquals("1:05", MusicShelf.clock(65_000)); assertEquals("0:00", MusicShelf.clock(-5))
    }

    // ── 디지털 앨범
    @Test fun albumFiltersByRegionAndYear() {
        val regions = AlbumShelf.regions(cat.album)
        assertEquals("강원", regions.first().first)
        assertTrue("지역을 모르는 사진은 세지 않아요", regions.none { it.first.isBlank() })
        assertEquals(cat.album.count { it.region.isNotBlank() }, regions.sumOf { it.second })
        val years = AlbumShelf.years(cat.album)
        assertEquals("최근 해가 앞이에요", years.map { it.first }.sortedDescending(), years.map { it.first })
        assertEquals(cat.album.size, AlbumShelf.filter(cat.album, null, null).size)
        val pick = AlbumShelf.filter(cat.album, "강원", years.first().first)
        assertTrue(pick.all { it.region == "강원" && it.month.startsWith(years.first().first) })
        assertEquals(regions.first().second, AlbumShelf.filter(cat.album, "강원", null).size)
    }

    @Test fun albumPhotosOfTheMonthOfATrip() {
        val ph = cat.album.first()
        val (y, m) = ph.month.split('.').let { it[0].toInt() to it[1].toInt() }
        val trip = SavedTrip(1, day(y, m, 5), "$y.${"%02d".format(m)}", "어딘가", null, null, 10, 1, emptyList(), 0)
        val got = AlbumShelf.ofMonth(cat.album, trip)
        assertTrue(ph in got)
        assertTrue(got.all { it.month == ph.month })
        assertEquals("달을 한 자리로 적어도 같은 달이에요", got, AlbumShelf.ofMonth(cat.album, trip.copy(month = "$y.$m")))
        assertTrue(AlbumShelf.ofMonth(cat.album, trip.copy(month = "1999.01")).isEmpty())
        assertTrue(AlbumShelf.thisSeason(cat.album, LocalDate.of(2026, 10, 3)).all { it.month.endsWith(".10") })
    }

    // ── 디지털 아트
    @Test fun artKindsAndPickByTheHour() {
        val kinds = ArtShelf.kinds(cat.arts)
        assertEquals("축구", kinds.first().first)
        assertTrue(kinds.all { it.second >= 2 })
        val night = ArtShelf.forHour(cat.arts, 22)!!
        assertEquals("밤", night.part)
        assertTrue(night.hits.isNotEmpty() && night.hits.all { it in night.art.tags })
        assertEquals("아침", ArtShelf.forHour(cat.arts, 8)!!.part)
        assertEquals("낮", ArtShelf.forHour(cat.arts, 14)!!.part)
        assertNull("맞는 작품이 없으면 고르지 않아요", ArtShelf.forHour(cat.arts.filter { a -> a.tags.none { it in ArtShelf.wanted(8).second } }, 8))
        assertNull(ArtShelf.forHour(emptyList(), 8))
    }

    // ── 디스플레이 테마
    @Test fun themePickGivesReasonsFromWhatTheCarKnows() {
        val pick = ThemeShelf.pick(parked.copy(weather = Weather.RAIN), cat.themes, 21)!!
        assertTrue(pick.why.first().startsWith("날씨 비"))
        assertTrue(pick.why.any { it == "지금 21시 · 밤이에요" })
        assertTrue(pick.why.last().startsWith("테마의 태그 "))
        assertTrue("날씨를 모르면 날씨로 말하지 않아요", ThemeShelf.pick(parked, cat.themes, 13)!!.why.none { it.startsWith("날씨") })
        assertNull(ThemeShelf.pick(parked, emptyList(), 13))
    }

    // ── 마이 트립
    @Test fun tripLegsSayDistanceAndWhereToChargeFirst() {
        val t = cat.trips.maxByOrNull { it.stops.size }!!
        val rows = TripLegs.rows(t, null)
        assertEquals(t.stops.size, rows.size)
        assertNull("첫 정류장에는 앞 구간이 없어요", rows.first().km)
        assertTrue(rows.drop(1).all { it.km != null && it.km!! >= 0.0 })
        assertEquals(TripPlan.plan(t, null).totalKm, rows.sumOf { it.km ?: 0.0 }, 0.001)
        assertTrue("갈 수 있는 거리를 모르면 충전을 말하지 않아요", rows.none { it.chargeBefore })
        assertNull(TripLegs.line(rows.first()))
        assertTrue(TripLegs.line(rows[1])!!.startsWith("앞 정류장에서 약 "))
        // 갈 수 있는 거리가 짧으면 충전이 필요한 구간에 표시가 붙어요(TripPlan과 같은 곳)
        val longest = rows.maxOf { it.km ?: 0.0 }
        val short = (longest / TripPlan.SAFE_RANGE + 1).toFloat()
        val tight = TripLegs.rows(t, short)
        assertEquals(TripPlan.plan(t, short).chargeBefore.map { it.name }, tight.filter { it.chargeBefore }.map { it.stop.name })
        tight.firstOrNull { it.chargeBefore }?.let { assertTrue(TripLegs.line(it)!!.endsWith("가기 전에 충전해요")) }
        // 가던 중이면 지나온 정류장에는 달지 않아요
        val mid = TripLegs.rows(t, null, fromIndex = 2)
        assertTrue(mid.take(3).all { it.km == null }); assertFalse(mid[3].km == null)
        // 날마다 합은 전체 합과 같아요
        assertEquals(t.days.size, TripLegs.dayKm(t).size)
        assertEquals(TripPlan.plan(t, null).totalKm, TripLegs.dayKm(t).sum(), 0.001)
        assertTrue(TripLegs.doneLine(LocalDate.of(2026, 11, 11), t).startsWith("2026.11.11에 다녀왔어요 · 정류장 ${t.stops.size}곳 · 구간 합 약 "))
    }
}
