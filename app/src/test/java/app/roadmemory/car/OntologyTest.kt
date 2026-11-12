package app.roadmemory.car

import app.roadmemory.car.ontology.Ontology
import app.roadmemory.car.ontology.OntologyAnswers
import app.roadmemory.car.ontology.Role
import app.roadmemory.car.ontology.Status
import app.roadmemory.car.ontology.Verdict
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** APK에 들어가는 `assets/ontology.json`을 앱의 엔진이 읽어서 개념 상태 · 기능 판정 · 질문 답을 규칙으로 계산하는지. */
class OntologyTest {
    private val o = Ontology.parse(File("src/main/assets/ontology.json").readText(Charsets.UTF_8))
    private val answers = OntologyAnswers(o)

    private fun fn(label: String) = o.functions.first { it.label == label }
    private fun verdict(label: String) = o.verdict(fn(label).id)
    private fun status(id: String) = o.statusOf("cn.$id")

    @Test fun graphIsWholeAndEveryFunctionHasAKeyConcept() {
        assertEquals(30, o.functions.size)
        for (f in o.functions) {
            val needs = o.needs(f.id)
            assertTrue("${f.label}: 개념이 하나도 없어요", needs.isNotEmpty())
            assertTrue("${f.label}: key가 없어요", needs.any { it.role == Role.KEY })
        }
    }

    @Test fun verdictsAreComputedFromTheGraphAndMatchTheDataDoc() {
        val c = o.counts()
        assertEquals(9, c[Verdict.CAN]); assertEquals(12, c[Verdict.PARTLY]); assertEquals(9, c[Verdict.NOT])
        assertEquals(Verdict.CAN, verdict("쉬어 가기 제안"))
        assertEquals(Verdict.CAN, verdict("충전 경유 제안"))
        assertEquals(Verdict.CAN, verdict("뒷좌석 하차 알림"))
        assertEquals(Verdict.PARTLY, verdict("충전 시간 활용"))
        assertEquals(Verdict.PARTLY, verdict("추운 날 항속 보정"))
        assertEquals(Verdict.PARTLY, verdict("스쿨존 안내"))
        assertEquals(Verdict.NOT, verdict("경고등 해설"))
        assertEquals(Verdict.NOT, verdict("일정에 맞춘 출발 제안"))
        assertEquals(Verdict.NOT, verdict("비상 때 알릴 사람"))
    }

    @Test fun conceptStatusesComeFromTheMeasuredReadsNotFromTheFile() {
        assertEquals(Status.OK, status("outside_temp"))          // AAOS 표준 속성이 읽혔어요
        assertEquals(Status.OK, status("seat_occupancy"))        // 좌석 하나씩은 읽혀요
        assertEquals(Status.OK, status("cabin_set_temp"))
        assertEquals(Status.DERIVED, status("trip_distance"))    // SDK 트립 거리는 없고 오도미터 차이로 계산
        assertEquals(Status.ESTIMATED, status("ev_efficiency"))  // 전비 API 없음 → 내비 경로 예상 에너지로 어림
        assertEquals(Status.ESTIMATED, status("charge_time_left"))
        assertEquals(Status.ABSENT, status("fog_light"))
        assertEquals(Status.ABSENT, status("washer_fluid"))
        assertEquals(Status.BLOCKED, status("telltales"))
        assertEquals(Status.UNTESTED, status("caution_point"))
        assertEquals(Status.OUTSIDE, status("calendar"))
    }

    @Test fun outsideTemperatureNeedsAPermissionNote() {
        val notes = o.permissionNotes("cn.outside_temp")
        assertTrue(notes.joinToString().contains("CAR_EXTERIOR_ENVIRONMENT"))
        assertTrue(o.permissionNotes("cn.soc").isEmpty())   // 선언한 권한으로 읽혀요
    }

    @Test fun whoUsesOutsideTemperature() {
        val names = o.functionsUsing("cn.outside_temp").map { it.label }.toSet()
        assertEquals(setOf("추운 날 항속 보정", "정차 중 에너지 계산"), names)
    }

    @Test fun answersQuestionsAboutData() {
        assertNotNull(answers.answer("외기온을 쓰는 기능은?")!!.also {
            assertTrue(it, it.contains("추운 날 항속 보정") && it.contains("정차 중 에너지 계산") && it.contains("CAR_EXTERIOR_ENVIRONMENT"))
        })
        assertTrue(answers.answer("경고등 해설은 왜 못 해?")!!.let { it.contains("○") && it.contains("경고등") })
        assertTrue(answers.answer("충전 경유 제안에는 뭐가 필요해?")!!.let { it.contains("도착 예상 배터리") && it.contains("배터리 잔량") })
        assertTrue(answers.answer("이 앱이 못 읽는 데이터는?")!!.contains("폰"))
        assertTrue(answers.answer("만들 수 있는 기능은?")!!.contains("9개"))
        assertTrue(answers.answer("쉬어 가기 제안 만들 수 있어?")!!.contains("●"))
    }

    @Test fun momentsAreJudgedByTheSameRulesAsFunctions() {
        assertEquals(30, o.functions.size)
        assertEquals(16, o.moments.size)
        val c = o.counts()
        assertEquals(9, c[Verdict.CAN]); assertEquals(12, c[Verdict.PARTLY]); assertEquals(9, c[Verdict.NOT])   // 순간은 기능 숫자에 들어가지 않아요
        for (id in listOf("board", "start", "door", "park", "wiper", "climate", "charge", "near", "arrive", "soc_low", "charge_done", "rest", "cold")) {
            assertEquals("mo.$id", Verdict.CAN, o.verdict("mo.$id")); assertTrue("mo.$id", o.offerable("mo.$id")); assertNull(o.momentReason("mo.$id"))
        }
        for (id in listOf("unlock", "lock")) {
            assertEquals(Verdict.NOT, o.verdict("mo.$id")); assertFalse(o.offerable("mo.$id")); assertEquals(Verdict.NOT, o.momentMark("mo.$id"))
            assertTrue(o.momentReason("mo.$id")!!.contains("문 잠금"))
        }
        // 창 위치는 차가 알려 주지만 이 앱에 알아차리는 코드가 없어서 ● 이 아니라 ◐ 로 다뤄요
        assertEquals(Verdict.CAN, o.verdict("mo.window")); assertFalse(o.isWired("mo.window")); assertFalse(o.offerable("mo.window"))
        assertEquals(Verdict.PARTLY, o.momentMark("mo.window")); assertTrue(o.momentReason("mo.window")!!.contains("아직"))
        assertEquals("reaction", o.momentMode("mo.door")); assertEquals("push", o.momentMode("mo.soc_low"))
        assertEquals(setOf("reaction", "push"), o.moments.map { it.props["mode"] }.toSet())
    }

    @Test fun anAppliedPackIsLayeredOnWithoutTouchingTheLoadedGraph() {
        val with = o.withPack("w_demo22", "벼락의 타석", "벼락", "synthetic", listOf("board", "start", "nope"))
        assertEquals(listOf("pk.w_demo22"), with.packs.map { it.id })
        assertEquals(listOf("board", "start"), with.packMoments("pk.w_demo22"))   // 그래프에 없는 순간은 담지 않아요
        assertTrue(o.packs.isEmpty())                                          // 불러온 그래프는 그대로예요
        assertEquals(30, with.functions.size); assertEquals(16, with.moments.size)
        assertEquals(o.verdict("mo.start"), with.verdict("mo.start"))
        // 같은 팩을 다시 더해도 하나예요
        assertEquals(1, with.withPack("w_demo22", "벼락의 타석", "벼락", "synthetic", listOf("board")).packs.size)
    }

    @Test fun answersAboutTheSignatureSong() {
        fun ask(a: OntologyAnswers, q: String, vararg parts: String) {
            val r = a.answer(q)
            assertNotNull("$q → 답이 없어요", r)
            for (x in parts) assertTrue("$q → $r (\"$x\"가 없어요)", r!!.contains(x))
        }
        ask(answers, "시그니처 송은 언제 나와?", "적용한 시그니처 송이 없어요", "13개")
        ask(answers, "왜 문 잠글 때는 소리가 안 나?", "문을 잠글 때", "○", "문 잠금")
        assertFalse(answers.answer("왜 문 잠글 때는 소리가 안 나?")!!.contains("docs/"))
        ask(answers, "와이퍼 켤 때 목소리는 언제 나와?", "●", "와이퍼")
        ask(answers, "창문 열 때 소리는 왜 안 나?", "◐", "아직")
        ask(answers, "곧 도착할 때 소리는 언제 나와?", "10분")
        val applied = OntologyAnswers(o.withPack("w_demo22", "벼락의 타석", "벼락", "synthetic", listOf("board", "start", "arrive")))
        ask(applied, "시그니처 송은 언제 나와?", "벼락의 타석", "탑승했을 때", "글로도")
        ask(applied, "도착했을 때 소리는 언제 나와?", "목소리가 있어요")
        ask(applied, "주차할 때 소리는 언제 나와?", "목소리가 없어요")
        // 명령은 가로채지 않아요
        for (cmd in listOf("소리 줄여 줘", "시그니처 송 틀어 줘", "목소리 꺼 줘", "팩 적용해 줘")) assertNull("명령을 가로챘어요: $cmd", answers.answer(cmd))
    }

    @Test fun neverHijacksTheOrdinaryCommands() {
        for (cmd in listOf("이 길의 기억 틀어 줘", "지난 여행 보여 줘", "충전소 들러야 해?", "충전 쉼터 열어 줘", "푸른 바닷가 기억 보여 줘", "오늘 화면으로", "그만", "충전하면 목적지까지 닿아?")) {
            assertNull("명령을 가로챘어요: $cmd", answers.answer(cmd))
        }
        assertFalse(OntologyAnswers.EXAMPLES.any { answers.answer(it) == null })   // 대화창의 질문 칩은 모두 답이 있어요
    }
}
