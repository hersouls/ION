package app.roadmemory.car.studio

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import java.time.LocalDate

/** 마이 트립의 다녀온 기록: 일정을 끝까지 돌고 "마치기"를 누른 날(일정 id → 날짜). 이 기기에만 남아요. 일정을 지우면 같이 지워요. */
object TripDone {
    private val days = mutableStateMapOf<String, LocalDate>()
    private var loaded = false

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("trip_done", Context.MODE_PRIVATE)

    fun ensure(c: Context) {
        if (loaded) return
        loaded = true
        prefs(c).all.forEach { (id, v) -> (v as? Long)?.let { days[id] = LocalDate.ofEpochDay(it) } }
    }

    fun on(tripId: String): LocalDate? = days[tripId]

    fun mark(c: Context, tripId: String, date: LocalDate = LocalDate.now()) {
        days[tripId] = date
        prefs(c).edit().putLong(tripId, date.toEpochDay()).apply()
    }

    fun clear(c: Context, tripId: String) {
        days.remove(tripId)
        prefs(c).edit().remove(tripId).apply()
    }
}
