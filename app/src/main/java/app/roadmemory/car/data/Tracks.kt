package app.roadmemory.car.data

import androidx.annotation.RawRes
import app.roadmemory.car.R

/**
 * 앱에 들어 있는 곡(ION이 틀어 줘요). 모두 제안자가 AI로 만든 곡이고 제3자 저작물이 없어요(출처 `art/app_music_manifest.json`).
 *  · 노래 16곡: 제안자가 즐겨 듣는 곡(2~4분, 가사 있음). [sung] = true.
 *  · 연주곡 6곡: 60초 연주곡(가사 없음).
 * 파일에는 제목 · 아티스트 같은 메타정보를 넣지 않았어요.
 */
object Tracks {
    data class Track(val title: String, @RawRes val res: Int, val sung: Boolean = false)

    val all = listOf(
        Track("Suspended Air", R.raw.track_suspended_air, true),
        Track("Nah Got A Sea", R.raw.track_nah_got_a_sea, true),
        Track("한강 위, 오늘도", R.raw.track_hangang_today, true),
        Track("Na-Na", R.raw.track_na_na, true),
        Track("Banana & Breeze", R.raw.track_banana_breeze, true),
        Track("Soul sweet", R.raw.track_soul_sweet, true),
        Track("No flight ticket", R.raw.track_no_flight_ticket, true),
        Track("Home Is Where We Go", R.raw.track_home_we_go, true),
        Track("Sea Remains", R.raw.track_sea_remains, true),
        Track("Sizzle So", R.raw.track_sizzle_so, true),
        Track("산방산 아침해", R.raw.track_sanbang_morning, true),
        Track("Maple Syrup Home", R.raw.track_maple_syrup_home, true),
        Track("3842 (Aiguille du Midi)", R.raw.track_aiguille_3842, true),
        Track("Light In The Cold Wind", R.raw.track_light_cold_wind, true),
        Track("Hit 'em up", R.raw.track_hit_em_up, true),
        Track("Blue Curve Walk", R.raw.track_blue_curve_walk, true),
        Track("Solcho Sea Loop", R.raw.track_solcho_sea),
        Track("Paju Drive Breeze", R.raw.track_paju_drive),
        Track("Yeongil Breeze Loop", R.raw.track_yeongil_breeze),
        Track("Harbor Breeze Lullaby", R.raw.track_harbor_lullaby),
        Track("October Maple Loop", R.raw.track_october_maple),
        Track("Rose & Pond Loop", R.raw.track_rose_pond),
    )

    val songs get() = all.count { it.sung }
    val instrumentals get() = all.count { !it.sung }

    fun byTitle(title: String) = all.firstOrNull { it.title == title }
}
