package app.roadmemory.car

import app.roadmemory.car.studio.CatalogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 디지털 앨범의 사진 100장(Photo에서 고른 것, 2026-11-11): 파일 · 제목 · 크기 · 개인정보 규칙. */
class AlbumPhotosTest {
    private val cat = CatalogParser.parse(File("src/main/assets/studio/catalog.json").readText())
    private val photos = cat.album.filter { it.id.startsWith("p") }

    @Test fun hundredPhotosEachWithBigAndThumbFiles() {
        assertEquals(100, photos.size)
        assertEquals("앨범은 사진뿐이에요(3D 그림은 지웠어요)", cat.album.size, photos.size)
        assertEquals("id가 겹치면 안 돼요", 100, cat.album.map { it.id }.toSet().size)
        assertEquals("제목이 겹치면 칸에서 구분이 안 돼요", 100, photos.map { it.title }.toSet().size)
        for (p in photos) {
            val big = File("src/main/assets/studio/${p.image}"); val thumb = File("src/main/assets/studio/${p.thumb}")
            assertTrue("${p.id}: 큰 사진이 없어요", big.isFile); assertTrue("${p.id}: 작은 사진이 없어요", thumb.isFile)
            assertTrue("${p.id}: 작은 사진이 큰 사진보다 작아야 해요", thumb.length() < big.length())
            assertTrue("${p.id}: 한 장이 600KB를 넘으면 앱이 무거워져요", big.length() < 600_000)
            assertTrue("${p.id}: 제목은 한 줄에 들어가야 해요", p.title.length in 2..14)
            assertTrue("${p.id}: 달은 YYYY.MM이에요", Regex("""\d{4}\.\d{2}""").matches(p.month))
        }
    }

    @Test fun noPersonalInfoInTitlesOrFileNames() {
        // 사람 이름 · 집 · 가족 호칭이 제목에 들어가면 안 돼요(앨범 폴더 이름을 그대로 쓰지 않았어요)
        val banned = Regex("문유|우리집|본가|집 안|아파트|가족|아기|아이")
        for (p in photos) assertFalse("${p.id}: ${p.title}", banned.containsMatchIn(p.title))
        // 실제 가족 이름은 공개 저장소에 두지 않아요: 기획 저장소의 docs/album_private_names.txt(한 줄에 하나)에만 있고, 없으면 이 확인만 건너뛰어요.
        val names = File("../../docs/album_private_names.txt").takeIf { it.isFile }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        for (p in photos) for (n in names) assertFalse("${p.id}: 제목에 가족 이름이 있어요", p.title.contains(n))
        for (p in photos) assertTrue("파일 이름에 원래 이름이 남으면 안 돼요: ${p.image}", Regex("""img/photo/(t/)?p\d{3}\.jpg""").matches(p.image) || Regex("""img/photo/(t/)?p\d{3}\.jpg""").matches(p.thumb))
    }

    @Test fun jpegsCarryNoExif() {
        // EXIF(촬영 위치 · 기기)는 APP1 "Exif" 표지로 시작해요. 다시 저장하면서 버렸어요.
        for (p in photos) for (f in listOf(p.image, p.thumb)) {
            val head = File("src/main/assets/studio/$f").inputStream().use { it.readNBytes(64) }
            val s = String(head, Charsets.ISO_8859_1)
            assertFalse("$f: EXIF가 남아 있어요", s.contains("Exif"))
        }
    }
}
