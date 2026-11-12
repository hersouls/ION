package app.roadmemory.car

import ai.pleos.playground.stt.constant.ErrorType
import app.roadmemory.car.gleo.GleoBridge
import app.roadmemory.car.ion.IonAssistant
import app.roadmemory.car.ion.IonState
import app.roadmemory.car.voice.Command
import app.roadmemory.car.voice.LevelMeter
import app.roadmemory.car.voice.PleosVoice
import app.roadmemory.car.voice.VoiceCommands
import app.roadmemory.car.voice.VoiceFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 말 → 일 규칙(시연)과 ION 창의 상태 · 오류 문구. 들은 말은 받아 적은 글이라 띄어쓰기가 제각각이에요. */
class VoiceCommandsTest {
    // 장소는 저장된 진짜 여행에서만 나와요. 테스트는 다녀온 곳 목록을 직접 넘겨요.
    private val visited = listOf("푸른 바닷가", "은빛 호수")
    private fun gleo(text: String, places: List<String> = visited) = VoiceCommands.route(text, places) as? Command.Gleo

    @Test fun everyExampleReachesTheRightFunction() {
        val expected = mapOf(
            "이 길의 기억 틀어 줘" to GleoBridge.SHOW_ROAD_MEMORY,
            "지난 여행 보여 줘" to GleoBridge.SHOW_ROAD_MEMORY,
            "도착하면 배터리 얼마야?" to GleoBridge.SUGGEST_CHARGING_STOP,
            "충전 화면 열어 줘" to GleoBridge.OPEN_CHARGING_LOUNGE,
        )
        assertEquals("예시가 바뀌면 이 표도 고쳐요", expected.keys, VoiceCommands.EXAMPLES.toSet())
        expected.forEach { (said, action) -> assertEquals(said, action, gleo(said)?.action) }
    }

    @Test fun routesToTheSameFunctionsGleoCalls() {
        VoiceCommands.EXAMPLES.forEach { said ->
            val a = gleo(said)!!.action
            assertTrue("$said → $a 는 Gleo 함수여야 해요", a in GleoBridge.FUNCTIONS)
        }
    }

    @Test fun spacingAndPunctuationDoNotMatter() {
        assertEquals(GleoBridge.SUGGEST_CHARGING_STOP, gleo("충 전 소  들러야해?!")?.action)
        assertEquals(GleoBridge.OPEN_CHARGING_LOUNGE, gleo("충전쉼터 열어줘.")?.action)
        assertEquals(GleoBridge.SHOW_ROAD_MEMORY, gleo("지난여행   보여줘~")?.action)
    }

    @Test fun lounge_beatsStop_whenSaidTogether() {
        // "충전소"가 들어 있어도 쉼터를 말하면 쉼터예요
        assertEquals(GleoBridge.OPEN_CHARGING_LOUNGE, gleo("충전소 쉼터 열어 줘")?.action)
    }

    @Test fun chargeAloneOpensTheLounge_andChargeWithAQuestionAsksForAStop() {
        assertEquals(GleoBridge.OPEN_CHARGING_LOUNGE, gleo("충전")?.action)
        assertEquals(GleoBridge.SUGGEST_CHARGING_STOP, gleo("충전해야 해?")?.action)
        assertEquals(GleoBridge.SUGGEST_CHARGING_STOP, gleo("배터리가 목적지까지 충분해?")?.action)
        assertNull("배터리만 말하면 무엇을 원하는지 몰라요", VoiceCommands.route("배터리"))
    }

    @Test fun placeIsPickedFromTheWordsOfVisitedPlacesOnly() {
        assertEquals("푸른 바닷가", gleo("푸른 바닷가 기억 보여 줘")?.place)
        assertEquals("은빛 호수", gleo("은빛호수 여행 틀어 줘")?.place)
        assertNull(gleo("지난 여행 보여 줘")?.place)
        assertNull("다녀온 적 없는 곳 이름은 장소로 알아듣지 않아요", gleo("단풍 숲 기억 보여 줘")?.place)
        assertNull("저장된 여행이 없으면 장소가 없어요", gleo("푸른 바닷가 기억 보여 줘", places = emptyList())?.place)
        assertEquals("장소가 없어도 기억 말은 알아들어요", GleoBridge.SHOW_ROAD_MEMORY, gleo("기억 보여 줘", places = emptyList())?.action)
    }

    @Test fun closeAndToday() {
        assertEquals(Command.Close, VoiceCommands.route("그만 닫아 줘"))
        assertEquals(Command.Close, VoiceCommands.route("취소"))
        assertEquals(Command.Today, VoiceCommands.route("오늘 화면으로 돌아가"))
    }

    @Test fun unknownOrEmptyIsNull_notGuessed() {
        assertNull(VoiceCommands.route(""))
        assertNull(VoiceCommands.route("   ?!  "))
        assertNull(VoiceCommands.route("에어컨 온도 올려 줘"))      // 이 앱이 하지 않는 일은 지어내지 않아요(차량 제어 없음)
    }

    @Test fun stateFollowsTheReplyStatus() {
        fun s(status: String) = IonAssistant.stateOf(GleoBridge.Reply("X", status, "m"))
        assertEquals(IonState.DONE, s("success"))
        assertEquals(IonState.ASKING, s("asking"))
        assertEquals(IonState.NOTICE, s("fail"))
    }

    @Test fun everyIonStateHasAMarkInTheGeneratedFile() {
        // IonMarks.kt는 tools/make_ion_mark.py --kotlin 이 만들어요. 상태 이름이 어긋나면 화면에 마크가 안 나와요.
        val src = java.io.File("src/main/java/app/roadmemory/car/ui/IonMarks.kt").readText()
        IonState.entries.forEach { assertTrue("${it.mark} 마크가 없어요", src.contains("\"${it.mark}\" to Mark(")) }
        assertTrue(src.contains("\"voice\" to Mark("))
    }

    @Test fun failureTextsAreSpokenInHaeyoStyle() {
        VoiceFailure.entries.forEach {
            assertTrue("${it.name}: ${it.korean}", it.korean.isNotBlank() && (it.korean.endsWith("요") || it.korean.endsWith("까요?")))
        }
    }

    @Test fun everySdkErrorMapsToAReason() {
        ErrorType.entries.forEach { assertNotNull(it.name, PleosVoice.failureOf(it)) }
        assertEquals(VoiceFailure.MIC, PleosVoice.failureOf(ErrorType.MIC_ERROR))
        assertEquals(VoiceFailure.PERMISSION, PleosVoice.failureOf(ErrorType.PERMISSION_DENIED))
        assertEquals(VoiceFailure.TIMEOUT, PleosVoice.failureOf(ErrorType.TIMEOUT))
    }

    @Test fun levelMeter_isZeroWhenQuiet_andFollowsTheLoudness_inAnyUnit() {
        for (scale in listOf(1.0, 100.0)) {                 // 0..1이든 0..100이든 같은 모양
            val m = LevelMeter()
            repeat(20) { assertEquals(0f, m.level(3.0 * scale), 0.01f) }          // 조용하면 0(값이 0이 아니어도)
            val loud = m.level(40.0 * scale)
            assertTrue("크게 말하면 막대가 차요: $loud", loud > 0.9f)
            assertTrue("그 뒤 조금 작아지면 막대도 줄어요", m.level(20.0 * scale) < loud)
        }
        val db = LevelMeter()                                // 음수(dB)여도 넘치지 않아요
        listOf(-60.0, -58.0, -20.0, -35.0, -90.0).forEach { assertTrue(it.toString(), db.level(it) in 0f..1f) }
        assertEquals(0f, LevelMeter().level(Double.NaN), 0f)
    }
}
