package app.roadmemory.car.brain

import app.roadmemory.car.studio.Stop
import app.roadmemory.car.studio.Trip
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 다음 여행 짜기(하이엔드의 규칙 버전): 정류장 사이 거리를 재서, 지금 갈 수 있는 거리로 충전 없이 가는지 · 어디서 충전이 필요한지 계산해요.
 * 거리는 직선(하버사인)에 길 굽이 1.3배예요(내비 경로 좌표는 SDK가 주지 않아요). 갈 수 있는 거리는 차가 준 값이고, 그 80%만 믿어요.
 */
object TripPlan {
    const val ROAD_FACTOR = 1.3
    const val SAFE_RANGE = 0.8

    data class Leg(val from: Stop, val to: Stop, val km: Double)
    data class Plan(
        val legs: List<Leg>,
        val totalKm: Double,
        /** 갈 수 있는 거리를 몰라서 판단하지 않았으면 null. */
        val chargesNeeded: Int?,
        /** 충전이 필요해지는 구간의 도착 정류장(없으면 비어 있어요). */
        val chargeBefore: List<Stop>,
    ) {
        val summary: String get() = when (chargesNeeded) {
            null -> "남은 정류장 사이 어림 ${Math.round(totalKm)}km · 갈 수 있는 거리를 아직 몰라요"
            0 -> "남은 정류장 사이 어림 ${Math.round(totalKm)}km · 지금 배터리로 충전 없이 가요"
            else -> "남은 정류장 사이 어림 ${Math.round(totalKm)}km · 중간에 ${chargesNeeded}번 충전해요(${chargeBefore.joinToString(" · ") { it.name }} 가기 전)"
        }
    }

    fun km(a: Stop, b: Stop): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat); val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(h), sqrt(1 - h)) * ROAD_FACTOR
    }

    /** [fromIndex]부터 남은 구간만 짜요(진행 중이면 지금 정류장부터). */
    fun plan(trip: Trip, rangeKm: Float?, fromIndex: Int = 0): Plan {
        val stops = trip.stops.drop(fromIndex.coerceIn(0, trip.stops.size))
        val legs = stops.zipWithNext { a, b -> Leg(a, b, km(a, b)) }
        val total = legs.sumOf { it.km }
        if (rangeKm == null || rangeKm <= 0f) return Plan(legs, total, null, emptyList())
        val safe = rangeKm * SAFE_RANGE
        var left = safe.toDouble(); var charges = 0; val before = mutableListOf<Stop>()
        for (l in legs) {
            if (l.km > left) { charges++; before += l.to; left = safe.toDouble() }
            left -= l.km
        }
        return Plan(legs, total, charges, before)
    }
}
