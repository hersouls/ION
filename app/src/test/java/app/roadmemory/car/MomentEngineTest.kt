package app.roadmemory.car

import app.roadmemory.car.data.CarPart
import app.roadmemory.car.data.CarSnapshot
import app.roadmemory.car.data.DriverCabin
import app.roadmemory.car.data.EnvSignals
import app.roadmemory.car.data.GearPos
import app.roadmemory.car.data.NavState
import app.roadmemory.car.data.SeatPos
import app.roadmemory.car.data.SeatState
import app.roadmemory.car.data.WiperLevel
import app.roadmemory.car.ontology.Ontology
import app.roadmemory.car.pack.Fired
import app.roadmemory.car.pack.MomentEngine
import app.roadmemory.car.pack.MomentInput
import app.roadmemory.car.pack.MomentMode
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 차의 값이 바뀌는 것을 보고 시그니처 송이 나올 순간을 고르는 규칙(순수 함수). 시계는 직접 넣어요. */
class MomentEngineTest {
    private val minute = 60_000L

    /** 값을 차례로 넣으며 그때 나온 순간 id를 모아요. 처음 5초(워밍업)는 지나게 첫 입력을 0, 다음을 6초에 둬요. */
    private class Run {
        var st = MomentEngine.State()
        var t = 0L
        val all = ArrayList<Fired>()
        fun feed(at: Long, input: MomentInput): List<String> {
            val (n, f) = MomentEngine.step(st, input.copy(now = at)); st = n; t = at; all += f
            return f.map { it.moment }
        }
    }

    private fun car(
        gear: GearPos? = GearPos.PARK, soc: Int? = 60, driver: Boolean? = true, port: Boolean? = false, plug: Boolean? = false,
        out: Float? = 10f, open: Map<CarPart, Boolean> = mapOf(CarPart.DRIVER_DOOR to false, CarPart.TRUNK to false),
    ) = CarSnapshot(soc = soc, gear = gear, chargePortOpen = port, plugConnected = plug, outsideTempC = out, open = open, seats = mapOf(SeatPos.DRIVER to SeatState(occupied = driver)))

    private fun input(c: CarSnapshot = car(), nav: NavState = NavState(), env: EnvSignals = EnvSignals(), cabin: DriverCabin = DriverCabin(), min: Int? = null) =
        MomentInput(c, nav, env, cabin, min)

    private fun warmed(first: MomentInput = input()): Run = Run().also { it.feed(0, first); it.feed(6_000, first) }

    @Test fun nothingFiresFromTheFirstValuesOrWhileWarmingUp() {
        val r = Run()
        assertTrue(r.feed(0, input(car(GearPos.DRIVE, soc = 5))).isEmpty())
        assertTrue("켠 지 5초 동안은 푸시도 조용해요", r.feed(3_000, input(car(GearPos.DRIVE, soc = 5))).isEmpty())
        assertEquals("워밍업이 끝나면 배터리 부족은 알려요", listOf("soc_low"), r.feed(6_000, input(car(GearPos.DRIVE, soc = 5))))
    }

    @Test fun unknownValuesNeverFire() {
        val r = warmed(input(CarSnapshot()))
        assertTrue(r.feed(20_000, input(CarSnapshot())).isEmpty())
        assertTrue(r.feed(40_000, input(CarSnapshot(soc = null, gear = GearPos.DRIVE))).isEmpty())   // 이전 기어를 몰라서 출발이 아니에요
    }

    @Test fun startsWhenTheGearGoesFromParkToDrive() {
        val r = warmed()
        assertEquals(listOf("start"), r.feed(10_000, input(car(GearPos.DRIVE))))
        assertTrue("D에서 D로는 아니에요", r.feed(15_000, input(car(GearPos.DRIVE))).isEmpty())
    }

    @Test fun boardsWhenTheDriverSeatGetsOccupied() {
        val r = warmed(input(car(driver = false)))
        assertEquals(listOf("board"), r.feed(10_000, input(car(driver = true))))
        assertTrue(r.feed(12_000, input(car(driver = true))).isEmpty())
    }

    @Test fun doorOpensOnlyWhileStandingAndNamesThePart() {
        val r = warmed()
        r.feed(8_000, input(car(open = mapOf(CarPart.DRIVER_DOOR to false, CarPart.TRUNK to true))))
        assertEquals(CarPart.TRUNK, r.all.single { it.moment == "door" }.part)
        val d = warmed(input(car(GearPos.DRIVE)))
        assertTrue("달리는 중에는 문 순간이 아니에요", d.feed(10_000, input(car(GearPos.DRIVE, open = mapOf(CarPart.DRIVER_DOOR to true, CarPart.TRUNK to false)))).isEmpty())
    }

    @Test fun parkAndArriveComeTogetherAndArriveWinsOnlyWhenThePackHasIt() {
        val route = NavState(routeActive = true, destinationName = "푸른 바닷가", remainTimeS = 2000)
        val r = warmed(input(car(GearPos.DRIVE), route))
        val fired = r.feed(30_000, input(car(GearPos.PARK), NavState(routeActive = false)))
        assertEquals(listOf("arrive", "park"), fired)
        assertEquals("푸른 바닷가", r.all.first { it.moment == "arrive" }.destination)
        assertEquals("arrive", MomentEngine.choose(r.all) { true }!!.moment)
        assertEquals("park", MomentEngine.choose(r.all) { it != "arrive" }!!.moment)
        assertNull(MomentEngine.choose(r.all) { false })
        // 경로 없이 달리다 서면 주차만이에요
        val q = warmed(input(car(GearPos.DRIVE)))
        assertEquals(listOf("park"), q.feed(30_000, input(car(GearPos.PARK))))
    }

    @Test fun arriveForgetsTheRouteAfterwards() {
        val r = warmed(input(car(GearPos.DRIVE), NavState(routeActive = true, destinationName = "a")))
        assertTrue("arrive" in r.feed(30_000, input(car(GearPos.PARK))))
        r.feed(40_000, input(car(GearPos.DRIVE))); r.feed(100_000, input(car(GearPos.DRIVE)))
        assertEquals("경로가 없던 다음 주행은 도착이 아니에요", listOf("park"), r.feed(200_000, input(car(GearPos.PARK))))
    }

    @Test fun wiperTurnsOnButMistAndAutoStandbyDoNot() {
        val r = warmed(input(env = EnvSignals(wiperOn = false, wiperLevel = WiperLevel.OFF)))
        assertTrue(r.feed(8_000, input(env = EnvSignals(wiperOn = true, wiperLevel = WiperLevel.MIST))).isEmpty())
        assertTrue(r.feed(12_000, input(env = EnvSignals(wiperOn = false, wiperLevel = WiperLevel.AUTO))).isEmpty())
        assertEquals(listOf("wiper"), r.feed(16_000, input(env = EnvSignals(wiperOn = false, wiperLevel = WiperLevel.CONTINUOUS))))
        assertTrue("켜진 채로는 다시 나오지 않아요", r.feed(40_000, input(env = EnvSignals(wiperOn = true, wiperLevel = WiperLevel.CONTINUOUS))).isEmpty())
    }

    @Test fun climateNeedsAWholeDegreeFromTheReference() {
        val r = warmed(input(cabin = DriverCabin(setTempC = 22f)))
        assertTrue(r.feed(8_000, input(cabin = DriverCabin(setTempC = 22.5f))).isEmpty())
        assertEquals(listOf("climate"), r.feed(10_000, input(cabin = DriverCabin(setTempC = 23f))))
        assertTrue("기준이 23도로 옮겨 갔어요", r.feed(40_000, input(cabin = DriverCabin(setTempC = 23.5f))).isEmpty())
        assertEquals(listOf("climate"), r.feed(70_000, input(cabin = DriverCabin(setTempC = 21f))))
    }

    @Test fun chargeStartsWhenThePortOpensOrThePlugConnects() {
        val a = warmed()
        assertEquals(listOf("charge"), a.feed(10_000, input(car(port = true))))
        val b = warmed()
        assertEquals(listOf("charge"), b.feed(10_000, input(car(plug = true))))
        val c = warmed(input(car(GearPos.DRIVE, port = false)))
        assertTrue("달리는 중에는 아니에요", c.feed(10_000, input(car(GearPos.DRIVE, port = true))).isEmpty())
    }

    @Test fun chargeIsDoneWhenTheBatteryReaches80WhileCharging() {
        val r = warmed(input(car(soc = 78, port = true)))
        assertTrue(r.feed(10_000, input(car(soc = 79, port = true))).isEmpty())
        assertEquals(listOf("charge_done"), r.feed(20_000, input(car(soc = 80, port = true))))
        assertTrue(r.feed(30_000, input(car(soc = 81, port = true))).isEmpty())
    }

    @Test fun nearFiresOncePerEpisodeAndRearmsWhenItClears() {
        val r = warmed(input(car(GearPos.DRIVE), NavState(routeActive = true, remainTimeS = 1500)))
        assertTrue(r.feed(10_000, input(car(GearPos.DRIVE), NavState(routeActive = true, remainTimeS = 900))).isEmpty())
        assertEquals(listOf("near"), r.feed(20_000, input(car(GearPos.DRIVE), NavState(routeActive = true, remainTimeS = 600))))
        assertTrue(r.feed(30_000, input(car(GearPos.DRIVE), NavState(routeActive = true, remainTimeS = 300))).isEmpty())
        r.feed(40_000, input(car(GearPos.DRIVE), NavState(routeActive = false)))
        // 같은 조건이 다시 맞아도 10분 안이면 나오지 않아요
        assertTrue(r.feed(50_000, input(car(GearPos.DRIVE), NavState(routeActive = true, remainTimeS = 300))).isEmpty())
        r.feed(60_000, input(car(GearPos.DRIVE), NavState(routeActive = false)))
        assertEquals(listOf("near"), r.feed(20_000 + 11 * minute, input(car(GearPos.DRIVE), NavState(routeActive = true, remainTimeS = 500))))
    }

    @Test fun lowBatteryUsesTheCarOrTheRouteEstimateAndStopsWhileCharging() {
        val r = warmed(input(car(GearPos.DRIVE, soc = 30)))
        assertEquals(listOf("soc_low"), r.feed(10_000, input(car(GearPos.DRIVE, soc = 20))))
        assertTrue(r.feed(20_000, input(car(GearPos.DRIVE, soc = 19))).isEmpty())
        val e = warmed(input(car(GearPos.DRIVE, soc = 60), NavState(routeActive = true, estimatedSocAtDest = 30)))
        assertEquals(listOf("soc_low"), e.feed(10_000, input(car(GearPos.DRIVE, soc = 58), NavState(routeActive = true, estimatedSocAtDest = 10))))
        val c = warmed(input(car(GearPos.PARK, soc = 15, port = true)))
        assertTrue("충전 중에는 배터리 부족이 아니에요", c.feed(10_000, input(car(GearPos.PARK, soc = 16, port = true))).isEmpty())
    }

    @Test fun restAndColdFireOnceWhileTheConditionHolds() {
        val r = warmed(input(car(GearPos.DRIVE), min = 119))
        assertEquals(listOf("rest"), r.feed(10_000, input(car(GearPos.DRIVE), min = 120)))
        assertTrue(r.feed(30_000, input(car(GearPos.DRIVE), min = 121)).isEmpty())
        val c = warmed(input(car(out = 3f)))
        assertEquals(listOf("cold"), c.feed(10_000, input(car(out = 0f))))
        assertTrue(c.feed(30_000, input(car(out = -5f))).isEmpty())
    }

    @Test fun debouncesTheSameMomentAndMomentsTooCloseTogether() {
        val r = warmed(input(car(GearPos.PARK, driver = false)))
        assertEquals(listOf("board"), r.feed(10_000, input(car(GearPos.PARK, driver = true))))
        r.feed(11_000, input(car(GearPos.PARK, driver = false)))
        assertTrue("20초 안에는 같은 순간이 다시 나오지 않아요", r.feed(12_000, input(car(GearPos.PARK, driver = true))).isEmpty())
        r.feed(13_000, input(car(GearPos.PARK, driver = false)))
        assertTrue("다른 순간도 2.5초 안에 이어서 나오지 않아요", r.feed(11_500, input(car(GearPos.DRIVE, driver = false))).isEmpty())
    }

    /** 엔진의 mode · 순간 목록이 ION-Link 그래프(mo.* 노드)와 같은지: 한쪽만 바뀌면 여기서 알아요. */
    @Test fun modesAndPriorityAgreeWithTheGraph() {
        val o = Ontology.parse(File("src/main/assets/ontology.json").readText(Charsets.UTF_8))
        for ((id, mode) in MomentEngine.MODES) {
            assertEquals("$id: mode가 그래프와 달라요", if (mode == MomentMode.PUSH) "push" else "reaction", o.momentMode("mo.$id"))
            assertTrue("$id: 이 앱이 알아차린다고 적혀 있지 않아요", o.offerable("mo.$id"))
        }
        assertEquals("알아차리는 순간과 우선순위 목록이 같아야 해요", MomentEngine.MODES.keys, MomentEngine.PRIORITY.toSet())
        val offered = o.moments.filter { o.offerable(it.id) }.map { it.id.removePrefix("mo.") }.toSet()
        assertEquals("그래프가 내놓을 수 있다고 하는 순간을 엔진이 모두 알아차려요", offered, MomentEngine.MODES.keys)
    }
}
