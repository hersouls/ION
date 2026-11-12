package app.roadmemory.car.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.roadmemory.car.ion.IonFrom
import app.roadmemory.car.ion.IonState
import app.roadmemory.car.ion.IonUi
import app.roadmemory.car.ontology.OntologyAnswers
import app.roadmemory.car.voice.VoiceCommands
import app.roadmemory.car.voice.VoiceKind

/**
 * 왼쪽 ION 메뉴. 가로(넓은 창)이면 앱 창의 왼쪽 가장자리에 세로 막대로, 세로(한 창)이면 아래쪽 막대로 앉아요.
 * (에뮬레이터 화면의 맨 왼쪽 띠는 시스템의 주행 화면이라 앱이 그리지 못해요. 앱이 그릴 수 있는 가장 왼쪽이 여기예요.)
 * 안에는 ION 마크(지금 상태) · 말하기 · 오늘 · 지난 여행이 있고, 말하는 동안에는 ION 창이 열려요.
 * 가이드: 입력은 음성 · 프리셋 우선, 주행 중에는 목록 화면을 열지 않아요(지난 여행은 서 있을 때만).
 */
/**
 * 서 있을 때의 작업 화면(ui/Hero.kt Workspace)에서 왼쪽 ION 칸이 쓰는 ION의 상태와 단추. IonShell이 내려줘요.
 * 작업 화면에서는 왼쪽 ION 메뉴(막대)가 따로 없고 그 일(말하기 · 홈)을 이 칸이 맡아요: 화면이 ION 칸 + 내용 칸 둘로만 나뉘도록이에요.
 * 같은 내용을 겹쳐 띄우는 ION 창(IonPanel)도 이 화면에서는 열지 않아요(대화창이 대신해요).
 */
class IonChat(
    val ion: IonUi, val onMic: () -> Unit, val onExample: (String) -> Unit, val onHome: () -> Unit,
    /** 디스플레이가 "ION" 글자 옆에 큰 글자로 보여 줄 한 값(예: "#48%"). 모르면 null이고 글자만 보여요. */
    val info: DisplayInfo? = null,
    /** 시그니처 송: 잠깐 보이는 순간(ION의 문장 + 목소리 자막). 없으면 null. */
    val moment: app.roadmemory.car.pack.MomentNote? = null,
    /** 첫마디 두 줄(상황에 맞게 바뀌어요)과 지금 할 수 있는 말. */
    val greeting: List<String> = emptyList(),
    val chips: List<String> = emptyList(),
)
val LocalIonChat = staticCompositionLocalOf<IonChat?> { null }

@Composable
fun IonShell(
    ion: IonUi,
    driving: Boolean,
    onMic: () -> Unit,
    onToday: () -> Unit,
    onMemories: () -> Unit,
    onSettings: () -> Unit,
    onExample: (String) -> Unit,
    onClose: () -> Unit,
    /** true면 서 있을 때의 작업 화면: 왼쪽 메뉴 막대를 그리지 않고 화면 전체를 ION 칸 + 내용 칸으로 나눠요. */
    workspace: Boolean = false,
    /** 작업 화면의 ION 디스플레이가 보여 줄 값(차가 준 값만, 예: 배터리 "#48%"). */
    info: DisplayInfo? = null,
    /** 시그니처 송의 순간(작업 화면의 대화창이 말풍선으로 보여 줘요). */
    moment: app.roadmemory.car.pack.MomentNote? = null,
    greeting: List<String> = emptyList(),
    chips: List<String> = emptyList(),
    content: @Composable BoxScope.() -> Unit,
) {
    val windowWide = LocalWide.current
    val body: @Composable () -> Unit = {
        // 메뉴가 자리를 차지한 만큼 남은 폭으로 가로 · 세로 배치를 다시 정해요.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth.value > maxHeight.value * 1.2f
            val contentWidth = maxWidth.value
            CompositionLocalProvider(LocalWide provides wide, LocalIonChat provides IonChat(ion, onMic, onExample, onToday, info, moment, greeting, chips)) {
                Box(Modifier.fillMaxSize()) {
                    content()
                    if (ion.panelOpen && !workspace) {
                        // 가로에서는 화면의 왼쪽 칸(TwoPane의 그림 칸) 폭 안에만 둬요: 오른쪽 칸의 카드를 가리지 않아요.
                        // TwoPane = 가로 여백 44dp 두 개 + 칸 사이 36dp, 왼쪽 : 오른쪽 = 1 : 1.618(황금비)
                        val leftPane = ((contentWidth - 44f * 2 - 36f) / (1f + GOLDEN)).dp
                        IonPanel(
                            ion, onExample, onClose,
                            if (wide) Modifier.align(Alignment.TopStart).padding(start = 44.dp, top = 36.dp).width(leftPane)
                            else Modifier.align(Alignment.TopStart).padding(24.dp),
                        )
                    }
                }
            }
        }
    }
    if (workspace) {
        Box(Modifier.fillMaxSize()) { body() }
    } else if (windowWide) {
        Row(Modifier.fillMaxSize()) {
            SideRail(ion, driving, onMic, onToday, onMemories, onSettings)
            Box(Modifier.weight(1f).fillMaxHeight()) { body() }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) { body() }
            BottomBar(ion, driving, onMic, onToday, onMemories, onSettings)
        }
    }
}

@Composable
private fun SideRail(ion: IonUi, driving: Boolean, onMic: () -> Unit, onToday: () -> Unit, onMemories: () -> Unit, onSettings: () -> Unit) {
    val p = LocalPalette.current
    // 창이 낮으면(작은 화면 · 앱 두 개 나란히) 그림과 보조 글을 줄여서 단추 넷이 모두 보이게 해요
    BoxWithConstraints(Modifier.fillMaxHeight()) {
    val compact = maxHeight < 880.dp
    val tiny = maxHeight < 640.dp
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier.width(IonSize.XL).fillMaxHeight().background(p.backgroundPrimary).padding(horizontal = 20.dp, vertical = if (tiny) 10.dp else if (compact) 16.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 왼쪽 막대의 ION 디스플레이. 이름은 아래 글자가 말해요.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                IonAvatar(ion, Modifier.size(if (tiny) IonSize.S else if (compact) IonSize.M else IonSize.L))
                Spacer(Modifier.height(if (tiny) 2.dp else 8.dp))
                Text("ION", color = p.textPrimary, style = Ivi.titleSmall)
                if (!tiny) Text(ion.state.label, color = p.textTertiary, style = Ivi.labelMedium)
            }
            Spacer(Modifier.weight(1f))
            MicButton(ion, Modifier.fillMaxWidth(), onMic)
            Spacer(Modifier.height(12.dp))
            RailButton("홈", enabled = true, onClick = onToday, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            RailButton("지난 여행", enabled = !driving, onClick = onMemories, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            RailButton("설정", enabled = !driving, onClick = onSettings, modifier = Modifier.fillMaxWidth())
            if (driving && !compact) {
                Spacer(Modifier.height(8.dp))
                Text("서 있을 때 열려요", color = p.textTertiary, style = Ivi.labelSmall)
            }
        }
        Box(Modifier.width(2.dp).fillMaxHeight().background(p.surfaceHigh))
    }
    }
}

@Composable
private fun BottomBar(ion: IonUi, driving: Boolean, onMic: () -> Unit, onToday: () -> Unit, onMemories: () -> Unit, onSettings: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().background(p.backgroundPrimary)) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(p.surfaceHigh))
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                IonAvatar(ion, Modifier.size(IonSize.M))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("ION", color = p.textPrimary, style = Ivi.bodyLargeStrong)
                    Text(ion.state.label, color = p.textTertiary, style = Ivi.labelMedium)
                }
            }
            MicButton(ion, Modifier.width(200.dp), onMic)
            Spacer(Modifier.width(12.dp))
            RailButton("홈", enabled = true, onClick = onToday, modifier = Modifier.width(110.dp))
            Spacer(Modifier.width(12.dp))
            RailButton(if (driving) "서 있을 때" else "지난 여행", enabled = !driving, onClick = onMemories, modifier = Modifier.width(150.dp))
            Spacer(Modifier.width(12.dp))
            RailButton("설정", enabled = !driving, onClick = onSettings, modifier = Modifier.width(110.dp))
        }
    }
}

/**
 * 왼쪽 막대의 작은 ION 디스플레이(주행 중에 보이는 ION). 이름은 곁의 글자가 해서 "ION" 글자는 넣지 않아요. 늘 ION 구예요(상황별 장면은 2026-11-11에 뺐어요).
 * 주행 중에도 숨 쉬듯 움직여요(오너 2026-11-11 "주행 중에도 ION 구가 움직이게 해줘"). 기기에서 애니메이션을 끄면 멈춰요.
 */
@Composable
internal fun IonAvatar(ion: IonUi, modifier: Modifier) {
    IonDisplay(ion, info = null, modifier = modifier, animated = true, description = "ION · ${ion.state.label}", scene = IonScene())
}

// ───────── 단추 ─────────

/** 말하기. 듣는 중에는 "그만"으로 바뀌어요. 높이는 가이드의 Large(100dp). */
@Composable
internal fun MicButton(ion: IonUi, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val listening = ion.state == IonState.LISTENING
    Row(
        modifier
            .height(IviSize.buttonLarge.dp)
            .iviButton(
                RoundedCornerShape(IviSize.buttonLarge.dp / 2),
                if (listening) p.buttonBasic else p.buttonFilled,
                if (listening) p.buttonBasicPressed else p.buttonFilledPressed,
                onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IonMark("voice", if (listening) p.textPrimary else p.textOnFilled, Modifier.size(44.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (listening) "그만" else "말하기", color = if (listening) p.textPrimary else p.textOnFilled, style = Ivi.bodyLargeStrong)
    }
}

/**
 * 메뉴 단추(Medium 80dp). 쓸 수 없을 때는 채움을 빼고 윤곽선만 남기며 눌러도 반응하지 않아요.
 * 글자를 흐리게(alpha) 하지 않아요: 쓸 수 없는 단추의 글자도 바탕 위에서 4.5:1 이상이어야 해서,
 * 바탕(backgroundPrimary) 위에서 이미 그 조건을 지키는 textTertiary를 써요.
 */
@Composable
internal fun RailButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(IviSize.buttonMedium.dp / 2)
    Box(
        modifier
            .height(IviSize.buttonMedium.dp)
            .semantics { if (!enabled) disabled() }
            .iviButton(shape, if (enabled) p.buttonBasic else Color.Transparent, p.buttonBasicPressed, onClick, enabled = enabled)
            .then(if (enabled) Modifier else Modifier.border(2.dp, p.surfaceHigh, shape)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) p.textPrimary else p.textTertiary, style = Ivi.bodySmallStrong)
    }
}

/** 말하기 예시(프리셋). Small 64dp. */
@Composable
/** 말해 볼 예시 한 줄. [link]면 답에서 바로 가는 화면 링크라서 따옴표 없이 화살표가 보이고, 강조색(채움)으로 그려요. */
internal fun ExampleChip(text: String, modifier: Modifier, link: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .height(IviSize.buttonSmall.dp)
            .iviButton(RoundedCornerShape(IviSize.buttonSmall.dp / 2), if (link) p.buttonFilled else p.buttonBasic, if (link) p.buttonFilledPressed else p.buttonBasicPressed, onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (link) text else "“$text”", color = if (link) p.textOnFilled else p.textPrimary, style = if (link) Ivi.bodySmallStrong else Ivi.bodySmall, maxLines = 1)
    }
}

// ───────── ION 창 ─────────

/**
 * 말하는 동안 열리는 창: 들은 말 · 목소리 크기 · 답 · 말하기 예시.
 * 차의 음성 인식이 있으면 목소리를 받아 적고, 마이크가 없는 기기에서는 예시를 눌러 말을 대신해요(시연).
 * 움직이는 그림은 없어요(가이드: 주행 중 애니메이션 금지). 막대는 지금 목소리 크기만 보여 줘요.
 */
@Composable
private fun IonPanel(ion: IonUi, onExample: (String) -> Unit, onClose: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(32.dp)
    val showChips = ion.state == IonState.LISTENING || ion.state == IonState.UNCLEAR
    Column(
        modifier.widthIn(max = 760.dp).fillMaxWidth().background(p.backgroundPrimary, shape).border(2.dp, p.surfaceHigh, shape).padding(32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IonDisplay(ion, info = null, modifier = Modifier.size(IonSize.S), animated = true, description = "ION · ${ion.state.label}")
            Spacer(Modifier.width(16.dp))
            Text(
                if (ion.from == IonFrom.GLEO) "Gleo가 불렀어요 · ${ion.state.label}" else "ION · ${ion.state.label}",
                color = p.textTertiary, style = Ivi.bodySmallStrong, modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.height(IviSize.buttonSmall.dp)
                    .iviButton(RoundedCornerShape(IviSize.buttonSmall.dp / 2), p.buttonBasic, p.buttonBasicPressed, onClose)
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) { Text("닫기", color = p.textPrimary, style = Ivi.bodySmallStrong) }
        }
        Spacer(Modifier.height(20.dp))
        when (ion.state) {
            IonState.LISTENING -> {
                Text(ion.heard.ifEmpty { "말씀하세요" }, color = if (ion.heard.isEmpty()) p.textTertiary else p.textPrimary, style = Ivi.titleSmall)
                Spacer(Modifier.height(16.dp))
                LevelBar(ion.level)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (ion.voice == VoiceKind.DEMO) "이 기기에는 마이크가 없어요. 아래 말을 눌러 말해 보세요" else "차의 음성 인식이 듣고 있어요",
                    color = p.textTertiary, style = Ivi.labelMedium,
                )
            }
            IonState.THINKING -> Text("‘${ion.heard}’", color = p.textPrimary, style = Ivi.titleSmall)
            else -> {
                if (ion.heard.isNotEmpty()) {
                    Text(if (ion.from == IonFrom.GLEO) ion.heard else "들은 말  ‘${ion.heard}’", color = p.textTertiary, style = Ivi.labelMedium)
                    Spacer(Modifier.height(8.dp))
                }
                ion.reply?.let { Text(it, color = p.textPrimary, style = Ivi.titleSmall) }
            }
        }
        if (showChips) {
            Spacer(Modifier.height(24.dp))
            Text("이렇게 말해 보세요", color = p.textTertiary, style = Ivi.labelMedium)
            Spacer(Modifier.height(10.dp))
            // 왼쪽 칸 폭에서는 두 칸이 좁아서 글이 잘려요. 한 줄에 하나씩 쌓아요.
            (VoiceCommands.EXAMPLES + OntologyAnswers.EXAMPLES.take(1)).forEach { ExampleChip(it, Modifier.fillMaxWidth().padding(bottom = 12.dp)) { onExample(it) } }
        }
    }
}

@Composable
private fun LevelBar(level: Float) {
    val p = LocalPalette.current
    Box(Modifier.fillMaxWidth().height(12.dp).background(p.surfaceHigh, RoundedCornerShape(6.dp))) {
        Box(Modifier.fillMaxWidth(level.coerceIn(0f, 1f)).fillMaxHeight().background(p.informativeActive, RoundedCornerShape(6.dp)))
    }
}

// ───────── ION 마크 ─────────

/** 웹 기획서의 ION 마크의 구 부분이에요(떨어져 나온 한 점은 앱에서 그리지 않아요. IonMarks는 tools/make_ion_mark.py --kotlin이 만들어요). */
@Composable
fun IonMark(state: String, tint: Color, modifier: Modifier = Modifier, description: String? = null) {
    val mark = IonMarks.all[state] ?: return
    val sem = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Canvas(modifier.then(sem).aspectRatio(mark.columns.toFloat() / mark.rows.size)) {
        val pitch = minOf(size.width / mark.columns, size.height / mark.rows.size)
        val side = pitch * IonMarks.CELL_SIZE / IonMarks.CELL_PITCH
        val inset = (pitch - side) / 2f
        mark.rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                val level = ch - '0'
                if (level > 0) drawRect(tint.copy(alpha = IonMarks.LEVELS[level]), Offset(c * pitch + inset, r * pitch + inset), Size(side, side))
            }
        }
    }
}
