package app.roadmemory.car.family

/**
 * 가족 위치 → 경로 안내의 틀. 이 파일에는 네트워크 코드도 주소도 이름도 없어요.
 *
 * 마이현대가 "위치를 가족에게 보여 주기"를 줄 때까지는 위치를 읽어 오는 쪽을 [FamilyLocationSource]가 맡고, 이 빌드에서는
 * 아무도 채우지 않아요(= [FamilyLink.source]가 null이면 화면에 아무것도 그리지 않아요). 시험용 빌드(debug)만 우회 경로로 채워요.
 * 마이현대가 주면 [FamilyLocationSource] 구현 하나만 바꿔 끼우고, 화면 · 내비 호출은 그대로예요.
 */

/** 가족 한 사람이 위치를 보여 준 순간의 값. 화면에는 별칭 · 역할만 보여요(실명은 담지 않아요). */
data class FamilyPlace(
    val alias: String,
    val lat: Double,
    val lng: Double,
    /** 이름 붙은 장소에 머물고 있으면 그 이름. 이동 중이거나 이름이 없으면 null. */
    val placeName: String?,
    val moving: Boolean,
    /** 위치를 받은 지 몇 초(모르면 null). */
    val ageSec: Long?,
)

sealed interface FamilyRead {
    /** [notSharing]은 위치 보여 주기를 꺼 둔 가족 수(이름 · 위치는 읽지 않아요). 모르면 0. */
    data class Ready(val people: List<FamilyPlace>, val notSharing: Int = 0) : FamilyRead
    /** 위치를 보여 주는 가족이 없어요(모두 꺼 두었거나 위치를 아직 못 받았어요). */
    data object Nobody : FamilyRead
    data class Failed(val why: String) : FamilyRead
}

/** 위치를 읽어 오는 쪽. 운전자가 물었을 때만 한 번 불러요(따라다니며 다시 묻지 않아요). 백그라운드 스레드에서 불러도 돼요. */
interface FamilyLocationSource {
    suspend fun read(): FamilyRead
}

enum class RouteFail { NO_PERMISSION, NO_NAVI, TOO_FAR, TOO_CLOSE, LOW_BATTERY, OTHER }

sealed interface RouteResult {
    /** 내비가 길을 받아 경로 후보를 열었어요. 운전자가 고르지 않으면 몇 초 뒤 내비가 저절로 안내를 시작해요(에뮬레이터 내비에서 확인). */
    data object Sent : RouteResult
    data class Failed(val why: RouteFail) : RouteResult
}

/** 내비 앱에 길을 보내는 쪽. 운전자가 "경로 안내"를 누른 뒤에만 불러요. 결과는 메인 스레드로 알려요. */
interface RouteLauncher {
    fun guide(to: FamilyPlace, done: (RouteResult) -> Unit)
}

/**
 * 두 쪽을 이어 두는 자리. 시험 빌드의 시작 코드가 실제 위치(우회 경로)로 채우고, 제출 빌드에서는 비어 있어요.
 * 비어 있으면 위치 동기화 서비스 화면은 [DemoFamilySource](마이현대가 가족 위치를 동기화해 준다고 가정한 시연 값)를 쓰고 내비에는 [NaviLauncher]로 길을 보내요.
 */
object FamilyLink {
    @Volatile var source: FamilyLocationSource? = null
    @Volatile var router: RouteLauncher? = null
}

/** 카드에 쓰는 말. 한 줄은 두 줄 이하, 사람의 말로 써요. */
object FamilyText {
    /** 이 시간(초)을 넘으면 오래된 위치라고 먼저 말해요(설계 값, 바꿀 수 있어요). */
    const val STALE_SEC = 30 * 60L

    /** 받침이 있으면 "은", 없으면 "는". 한글이 아니면 "은(는)". */
    fun eunNeun(word: String): String {
        val c = word.lastOrNull() ?: return "은(는)"
        if (c !in '가'..'힣') return "은(는)"
        return if ((c.code - 0xAC00) % 28 != 0) "은" else "는"
    }

    /** 받침이 있으면 "이", 없으면 "가". */
    fun iGa(word: String): String = if (eunNeun(word) == "은") "이" else "가"

    /** 내비에 보낼 목적지 이름: 머무는 곳이 있으면 "엄마 · 경포대", 이동 중이면 별칭만. 좌표는 이름에 넣지 않아요. */
    fun destName(p: FamilyPlace): String = p.placeName?.let { "${p.alias} · $it" } ?: p.alias

    fun state(p: FamilyPlace): String = when {
        p.placeName != null -> "${p.placeName}에 있어요"
        p.moving -> "이동 중이에요"
        else -> "한 곳에 머물러 있어요"
    }

    fun age(ageSec: Long?): String = when {
        ageSec == null -> "받은 시각을 몰라요"
        ageSec < 60 -> "방금 받은 위치예요"
        ageSec < STALE_SEC -> "${ageSec / 60}분 전 위치예요"
        ageSec < 3600 -> "${ageSec / 60}분 전 위치예요 · 오래된 위치예요"
        ageSec < 48 * 3600 -> "${ageSec / 3600}시간 전 위치예요 · 오래된 위치예요"
        else -> "${ageSec / 86400}일 전 위치예요 · 오래된 위치예요"
    }

    /** 한 줄 설명: "이동 중이에요 · 2분 전 위치예요". */
    fun hint(p: FamilyPlace): String = "${state(p)} · ${age(p.ageSec)}"

    fun routeNote(alias: String, r: RouteResult): String = when (r) {
        RouteResult.Sent -> "내비에 ${alias}${iGa(alias)} 있는 곳을 보냈어요. 경로를 고르지 않으면 곧 안내가 시작돼요"
        is RouteResult.Failed -> when (r.why) {
            RouteFail.NO_PERMISSION -> "이 빌드는 내비에 길을 보낼 수 없어요"
            RouteFail.NO_NAVI -> "내비 앱에 연결하지 못했어요"
            RouteFail.TOO_FAR -> "너무 멀어서 내비가 길을 만들지 못했어요"
            RouteFail.TOO_CLOSE -> "아주 가까워요. 걸어서 만날 수 있어요"
            RouteFail.LOW_BATTERY -> "배터리가 모자라 내비가 길을 만들지 못했어요"
            RouteFail.OTHER -> "내비가 길을 만들지 못했어요"
        }
    }
}
