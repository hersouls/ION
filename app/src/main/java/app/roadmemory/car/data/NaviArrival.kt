package app.roadmemory.car.data

/** 내비가 알려 준 도착: 경유지(WAYPOINT)에 닿았는지, 목적지(DESTINATION)에 닿았는지. */
enum class ArrivalKind { WAYPOINT, DESTINATION }

/**
 * NaviHelper의 `onWayPointArrived` · `onDestinationArrived`가 준 값이에요(차의 위치를 읽지 않고 내비가 알려 주는 도착만 써요).
 * [name]은 내비가 도착했다고 말한 곳의 이름이고, [index]는 경유지 번호예요(목적지는 null). 없으면 null.
 */
data class NaviArrival(val kind: ArrivalKind, val name: String?, val index: Int?, val at: Long)
