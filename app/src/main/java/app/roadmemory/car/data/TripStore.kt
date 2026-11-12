package app.roadmemory.car.data

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 도착할 때 쌓는 여행 기록을 이 기기의 SQLite(앱 안에서만 열려요)에 남겨요. 서버도 계정도 인터넷도 쓰지 않아요.
 * 읽기와 쓰기는 입출력 스레드에서 불러요.
 *
 * 버전 2(2026-11-11): 기록이 비어 있으면 시연용 가상 여행([DemoTrips])을 한 번 넣어요. 새로 설치할 때와, 비어 있는 채로 올라올 때만이에요.
 * 이미 쌓인 기록이 있으면 섞지 않고, "모두 지우기"로 지운 뒤에는 다시 넣지 않아요.
 * 버전 3(2026-11-11): 출발한 때(`started_at`)를 더했어요(여행 기록 세부 화면의 걸린 시간). 예전 기록은 null이고, 시연용 여행만 값을 채워요.
 */
class TripStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "roadmemory.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE trips(
                id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, month TEXT NOT NULL,
                dest_name TEXT, lat REAL, lon REAL,
                km INTEGER NOT NULL, people INTEGER NOT NULL, tracks TEXT NOT NULL, charges INTEGER NOT NULL, started_at INTEGER)""",
        )
        seedDemo(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) db.execSQL("ALTER TABLE trips ADD COLUMN started_at INTEGER")
        if (oldVersion < 2 && DatabaseUtils.queryNumEntries(db, "trips") == 0L) seedDemo(db)
        // 버전 2에서 넣어 둔 시연용 여행(번호 음수)에 출발 시각을 채워요
        if (oldVersion in 2..2) DemoTrips.all.forEach { t -> t.startedAt?.let { db.execSQL("UPDATE trips SET started_at = ? WHERE id = ?", arrayOf<Any>(it, t.id)) } }
    }

    private fun seedDemo(db: SQLiteDatabase) {
        DemoTrips.all.forEach { db.insertWithOnConflict("trips", null, values(it, withId = true), SQLiteDatabase.CONFLICT_IGNORE) }
    }

    private fun values(t: SavedTrip, withId: Boolean = false) = ContentValues().apply {
        if (withId) put("id", t.id)
        put("at", t.at); put("month", t.month); put("dest_name", t.destName)
        if (t.lat != null) put("lat", t.lat)
        if (t.lon != null) put("lon", t.lon)
        put("km", t.km); put("people", t.people); put("tracks", t.tracks.joinToString("\n")); put("charges", t.charges)
        if (t.startedAt != null) put("started_at", t.startedAt)
    }

    fun insert(t: SavedTrip): Long = writableDatabase.insert("trips", null, values(t))

    /** 최근 것이 앞이에요. */
    fun all(): List<SavedTrip> = readableDatabase.rawQuery(
        "SELECT id, at, month, dest_name, lat, lon, km, people, tracks, charges, started_at FROM trips ORDER BY at DESC, id DESC", null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(
                SavedTrip(
                    id = c.getLong(0), at = c.getLong(1), month = c.getString(2),
                    destName = c.getString(3), lat = if (c.isNull(4)) null else c.getDouble(4), lon = if (c.isNull(5)) null else c.getDouble(5),
                    km = c.getInt(6), people = c.getInt(7), tracks = c.getString(8).split('\n').filter { it.isNotBlank() }, charges = c.getInt(9),
                    startedAt = if (c.isNull(10)) null else c.getLong(10),
                ),
            )
        }
    }

    /** 한 건만 지워요(번호로). */
    fun delete(id: Long) { writableDatabase.delete("trips", "id = ?", arrayOf(id.toString())) }

    fun clear() { writableDatabase.delete("trips", null, null) }
}
