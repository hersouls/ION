package app.roadmemory.car

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pleos API policy(2026-11-13 대조): 권한은 앱 마켓이 승인한 범위에서만 선언하고 쓰며, 승인되지 않은 권한을 선언하거나 그 API를 부르면 앱 심사를 통과하지 못해요.
 * 지원 여부는 도메인별 checkXxxCapabilities로 확인해요. 이 시험은 제출 매니페스트와 차 신호 코드가 그 약속을 지키는지 지켜요.
 */
class PermissionPolicyTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
    private val declared = Regex("""<uses-permission[^>]*android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
    private val source = File("src/main/java/app/roadmemory/car/data/PleosCarSource.kt").readText()

    @Test fun noPrivateOrUnusedPermission() {
        assertTrue("PRIVATE_ 권한은 공개 범위가 아니라 선언하지 않아요: $declared", declared.none { it.substringAfterLast('.').startsWith("PRIVATE_") })
        assertTrue("타이어 공기압을 읽지 않아서 CAR_TIRES는 선언하지 않아요", "pleos.car.permission.CAR_TIRES" !in declared)
        assertEquals("같은 권한을 두 번 적지 않아요", declared.size, declared.toSet().size)
    }

    @Test fun declaredSetIsTheReviewedOne() {
        // 바꾸면 docs/DATA.md "권한 근거" 표와 사이트의 권한 문단도 함께 고쳐요
        val expected = setOf(
            "android.permission.INTERNET",
            "pleos.car.permission.CAR_ENERGY", "pleos.car.permission.CAR_SEATS", "pleos.car.permission.CAR_POWERTRAIN",
            "pleos.car.permission.CAR_DRIVE", "pleos.car.permission.CAR_LIGHTS", "pleos.car.permission.CAR_WINDOWS", "pleos.car.permission.CAR_CLIMATE",
            "pleos.car.permission.CAR_DOORS", "pleos.car.permission.CAR_SAFETY", "pleos.car.permission.CAR_INFO",
            "pleos.car.permission.CAR_MILEAGE", "pleos.car.permission.READ_WINDSHIELD_WIPERS", "pleos.car.permission.READ_CAR_DISPLAY_UNITS",
            "android.car.permission.CAR_EXTERIOR_ENVIRONMENT", "android.car.permission.CAR_ENERGY_PORTS",
            "pleos.car.permission.NAVI_ROUTE", "pleos.car.permission.NAVI_ROUTE_SEARCH", "pleos.car.permission.STT_SERVICE",
        )
        assertEquals(expected, declared.toSet())
    }

    @Test fun nonPublicApisAreNotCalled() {
        val code = source.lines().filterNot { it.trim().startsWith("*") || it.trim().startsWith("//") }.joinToString("\n")
        for (api in listOf("getTelltalesInfo", "registerTelltales", "getTirePressure", "registerTirePressure", "getCapabilityInfo")) {
            assertTrue("공식 샘플이 공개 목록에서 막은 $api 를 부르지 않아요", !code.contains("$api("))
        }
    }

    @Test fun everyDomainAsksCapabilityFirst() {
        // 묻는 API가 없는 기어 · 남은 주행거리만 바로 읽어요
        val asks = listOf(
            "checkEvBatteryStateOfChargeCapabilities", "checkEvChargePortOpenCapabilities", "checkSeatOccupancyCapabilities",
            "checkSeatBeltBuckledCapabilities", "checkTripCurrentDrivingDistanceCapabilities", "checkCurrentDistanceDrivenCapabilities",
            "checkWiperStateCapabilities", "checkWiperSwitchCapabilities", "checkLowBeamLightStateCapabilities",
            "checkFrontFogLightStateCapabilities", "checkRearFogLightStateCapabilities", "checkHvacDefrosterCapabilities",
            "checkHvacMaxDefrostCapabilities", "checkDoorPositionCapabilities", "checkLowWasherFluidWarningStateCapabilities",
            "checkHvacTemperatureCurrentCapabilities", "checkTemperatureDisplayUnitCapabilities", "checkHvacTemperatureCapabilities",
            "checkHvacPowerCapabilities",
        )
        val missing = asks.filterNot { source.contains("$it(") }
        assertTrue("지원 여부를 묻지 않는 도메인: $missing", missing.isEmpty())
    }
}
