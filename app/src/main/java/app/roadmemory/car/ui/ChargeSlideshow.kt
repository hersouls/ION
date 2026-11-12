package app.roadmemory.car.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.roadmemory.car.Pane
import app.roadmemory.car.UiState
import app.roadmemory.car.data.Tracks
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.studio.ChargePlan
import app.roadmemory.car.studio.ChargeShow
import app.roadmemory.car.studio.ChargeShowPrefs
import app.roadmemory.car.studio.ChargeSlideKind
import app.roadmemory.car.studio.ChargeSource
import app.roadmemory.car.studio.SlideOrder
import app.roadmemory.car.studio.Slideshow
import app.roadmemory.car.studio.SlideshowControl
import app.roadmemory.car.studio.SlideshowPrefs
import app.roadmemory.car.studio.StudioLibrary
import app.roadmemory.car.studio.StudioStore
import java.time.LocalDate
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ───────── 충전 화면의 슬라이드쇼: 충전하는 사이 사진이 돌고, 전체 창 슬라이드쇼로 크게 봐요 ─────────

/**
 * 충전 화면의 "충전하는 사이 · 슬라이드쇼" 칸. 동기화해 가져온 디지털 앨범과 걸어 둔 디지털 아트가 위쪽 미리 보기에서 저절로 돌고,
 * "슬라이드쇼 보기"를 누르면 앱 창 가득([ChargeShowDialog]) 돌아요. 규칙은 [ChargeShow]에 있어요(충전 시간에 맞추기 · 아트 섞기 · 충전 상태 줄 · 밤에 어둡게 · 곡 함께).
 * 앨범과 걸어 둔 아트가 둘 다 있으면 "보여 줄 것"에서 디지털 아트 · 디지털 앨범 · 디지털 아트앨범(둘 다) 가운데 골라요(이 기기에 남아요).
 * 앨범도 걸어 둔 아트도 없으면 아무것도 그리지 않아요. 말로 "슬라이드쇼 틀어 줘 · 멈춰"([SlideshowControl])도 이 칸이 받아요.
 * [onPlay] · [onStop]은 함께 틀 곡을 틀고 멈추는 길이에요(슬라이드쇼가 튼 곡만 슬라이드쇼가 끝날 때 멈춰요).
 */
@Composable
internal fun ChargeSlideshowCard(ui: UiState, onPlay: (List<Tracks.Track>) -> Unit, onStop: () -> Unit) {
    val ctx = LocalContext.current
    StudioLibrary.ensure(ctx)
    SlideshowPrefs.ensure(ctx)
    ChargeShowPrefs.ensure(ctx)
    val cat = remember(app.roadmemory.car.studio.StudioLink.rev) { StudioStore.catalog(ctx) }
    val lib = StudioLibrary.state
    val p = LocalPalette.current
    val charging = ui.mode == Mode.CHARGING
    val album = if (lib.has("sync_${Pane.SVC_ALBUM}")) cat.album else emptyList()
    val art = cat.art(lib.appliedId(Pane.SVC_ART))
    // 가는 일정의 지역(그 근처 사진을 먼저 보여 줘요)
    val region = TripRun.current(lib)?.let { r -> cat.trips.firstOrNull { it.id == r.tripId }?.region }
    val today = remember { LocalDate.now() }
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val shuffleSeed = remember { System.nanoTime() }
    val order = SlideshowPrefs.order
    val seconds = SlideshowPrefs.seconds
    val fit = ChargeShowPrefs.fit
    val source = ChargeShow.effective(ChargeShowPrefs.source, album.isNotEmpty(), art != null)
    val minLeft = ui.chargeMinLeft.takeIf { charging }
    val offer = if (lib.has("sync_${Pane.SVC_MUSIC}")) ChargeShow.music(ui, hour, today.monthValue) else null

    var show by remember { mutableStateOf<ChargePlan?>(null) }
    var startedCharging by remember { mutableStateOf(false) }
    var musicStarted by remember { mutableStateOf(false) }

    fun startShow() {
        if (!Slideshow.allowed(ui.mode == Mode.DRIVING)) return
        val seed = if (SlideshowPrefs.order == SlideOrder.SHUFFLE) System.nanoTime() else LocalDate.now().toEpochDay()
        val plan = ChargeShow.plan(album, art, SlideshowPrefs.order, region, LocalDate.now(), minLeft, SlideshowPrefs.seconds, ChargeShowPrefs.fit, seed, ChargeShowPrefs.source)
        if (plan.slides.isEmpty()) return
        startedCharging = charging
        // 이미 트는 곡이 있으면 건드리지 않아요
        if (ChargeShowPrefs.music && offer != null && ui.nowPlaying == null) { onPlay(listOf(offer.first)); musicStarted = true }
        show = plan
    }

    fun endShow() {
        show = null
        if (musicStarted) { onStop(); musicStarted = false }
    }

    // 충전 화면이 닫히면 슬라이드쇼가 튼 곡도 멈춰요
    DisposableEffect(Unit) { onDispose { if (musicStarted) onStop() } }

    // 말로 시작 · 멈춤("슬라이드쇼 틀어 줘")
    val cmd = SlideshowControl.command
    LaunchedEffect(cmd) {
        when (cmd) {
            SlideshowControl.Cmd.START -> { startShow(); SlideshowControl.clear() }
            SlideshowControl.Cmd.STOP -> { endShow(); SlideshowControl.clear() }
            null -> {}
        }
    }

    val plan = ChargeShow.plan(album, art, order, region, today, minLeft, seconds, fit, if (order == SlideOrder.SHUFFLE) shuffleSeed else today.toEpochDay(), source)
    if (plan.slides.isEmpty()) return

    SectionTitle("충전하는 사이 · 슬라이드쇼")
    Card {
        ChargePreview(plan, seconds, paused = show != null) { startShow() }
        Spacer(Modifier.height(14.dp))
        // 앨범 · 아트가 한 바퀴에 몇 장인지는 충전 시간에 맞춰 자르기 전의 전체 장수로 말해요
        val full = ChargeShow.plan(album, art, order, region, today, null, seconds, false, if (order == SlideOrder.SHUFFLE) shuffleSeed else today.toEpochDay(), source)
        Text(ChargeShow.sourceLine(full.albumCount, full.artCount), color = p.textPrimary, style = Ivi.titleSmall)
        Text(ChargeShow.timingLine(plan, charging, minLeft, fit), color = p.textSecondary, style = Ivi.bodyMedium)
        Spacer(Modifier.height(12.dp))
        if (ChargeShow.canChoose(album.isNotEmpty(), art != null)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("보여 줄 것", color = p.textTertiary, style = Ivi.labelMedium)
                ChargeSource.entries.forEach { c -> PillButton(c.label, { ChargeShowPrefs.setSource(ctx, c) }, filled = source == c) }
            }
            Spacer(Modifier.height(8.dp))
        }
        // 순서는 앨범 사진의 순서예요. 아트만 볼 때는 고를 것이 없어요.
        if (source != ChargeSource.ART) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("순서", color = p.textTertiary, style = Ivi.labelMedium)
                SlideOrder.entries.forEach { o -> PillButton(o.label, { SlideshowPrefs.setOrder(ctx, o) }, filled = order == o) }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("넘기는 시간", color = p.textTertiary, style = Ivi.labelMedium)
            Slideshow.SECONDS.forEach { s -> PillButton("${s}초", { SlideshowPrefs.setSeconds(ctx, s) }, filled = seconds == s) }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("충전 시간에 맞춰요", color = p.textTertiary, style = Ivi.labelMedium)
            PillButton("켜짐", { ChargeShowPrefs.setFit(ctx, true) }, filled = fit)
            PillButton("꺼짐", { ChargeShowPrefs.setFit(ctx, false) }, filled = !fit)
        }
        if (offer != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("곡도 함께", color = p.textTertiary, style = Ivi.labelMedium)
                PillButton("켜짐", { ChargeShowPrefs.setMusic(ctx, true) }, filled = ChargeShowPrefs.music)
                PillButton("꺼짐", { ChargeShowPrefs.setMusic(ctx, false) }, filled = !ChargeShowPrefs.music)
            }
            Text(
                if (ChargeShowPrefs.music) "${app.roadmemory.car.brain.Korean.eul(offer.first.title)} 틀어요 · ${offer.second}에 맞아요" else "켜면 지금 분위기에 맞는 곡(${offer.first.title})을 함께 틀어요",
                color = p.textSecondary, style = Ivi.bodyMedium, modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            if (source == ChargeSource.ART) "걸어 둔 작품의 사진만 차례대로 틀어요"
            else when (order) {
                SlideOrder.ION ->
                    if (region != null) "지금 가는 곳($region) 근처 사진 → 이맘때 사진 → 나머지 순서로 틀고, 사진마다 이유를 달아요"
                    else "이맘때 사진 → 나머지(오늘 날짜로 섞어요) 순서로 틀어요. 가는 일정이 있으면 그 근처 사진을 먼저 보여 줘요"
                SlideOrder.SEQUENCE -> "앨범에 든 순서대로 틀어요"
                SlideOrder.SHUFFLE -> "틀 때마다 새로 섞어요"
            } + if (art != null && source == ChargeSource.BOTH) ". 걸어 둔 아트는 앨범 사진 ${ChargeShow.ART_EVERY}장마다 한 장씩 끼워요" else "",
            color = p.textSecondary, style = Ivi.bodyMedium, modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.height(16.dp))
        BigButton("슬라이드쇼 보기", true, { startShow() }, Modifier.fillMaxWidth())
    }
    show?.let { pl -> ChargeShowDialog(pl, ui, startedCharging, hour, onStopMusic = onStop, onClose = { endShow() }) }
}

/** 칸 위쪽 미리 보기: 정한 시간마다 저절로 넘어가고, 누르면 슬라이드쇼를 열어요. 슬라이드쇼가 열려 있는 동안([paused])은 멈춰요. */
@Composable
private fun ChargePreview(plan: ChargePlan, seconds: Int, paused: Boolean, onOpen: () -> Unit) {
    val motionOff = rememberMotionOff()
    // 바탕 · 캡션은 앱 팔레트(적용한 테마가 있으면 그 테마 색)를 따라요. 사진 양옆 빈 곳이 검게 보이지 않아요.
    val p = LocalPalette.current
    val slides = plan.slides
    var i by remember(slides.size) { mutableStateOf(0) }
    LaunchedEffect(slides.size, seconds, paused) {
        while (!paused && slides.size > 1) {
            delay(seconds * 1000L)
            i = (i + 1) % slides.size
        }
    }
    val fade: FiniteAnimationSpec<Float> = if (motionOff) snap() else tween(600)
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)).background(p.surfaceHigh)
        .clickable(role = Role.Button, onClickLabel = "슬라이드쇼 보기") { onOpen() }) {
        Crossfade(targetState = i.coerceIn(0, slides.lastIndex), animationSpec = fade, label = "charge-preview") { idx ->
            StudioImage(slides[idx].image, Modifier.fillMaxSize(), slides[idx].title, ContentScale.Fit, plain = true)
        }
        val s = slides[i.coerceIn(0, slides.lastIndex)]
        Text(
            s.note, color = p.textPrimary, style = Ivi.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp).background(p.backgroundPrimary, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/**
 * 충전하는 동안의 전체 창 슬라이드쇼. 앱 창 가득, 사진은 자르지 않고 다 보여요. 위에 제목 · 장수 · 이전 · 다음 · 멈춤 · 닫기, 그 아래에 충전 상태 한 줄이 있어요
 * (배터리 · 80%까지 남은 시간 · 80%까지 찬 정도 막대, 80%가 되면 알려요). 좌우로 밀어도 넘어가고(단추만으로도 다 돼요), 뒤로 가기도 닫아요.
 * 사진은 부드럽게 겹치며 바뀌고 천천히 조금 커져요(기기에서 애니메이션을 끄면 바로 바뀌어요). 밤에는 사진을 조금 어둡게 비춰요.
 * 창 바탕 · 글 · 막대는 앱 팔레트를 따르고, 디스플레이 테마를 적용했으면 앱 창과 같은 테마 바탕 그림과 가림색을 깔아요(검은 바탕이 아니에요).
 * 충전 시간에 맞춰 잘랐으면([ChargePlan.fitted]) 막대는 전체 시간을 따라 차고, 끝까지 보면 끝났다고 말하고 멈춰요(처음부터 다시 볼 수 있어요).
 * 달리기 시작하면 저절로 닫아요. 함께 튼 곡은 "곡 끄기"로 멈춰요.
 */
@Composable
private fun ChargeShowDialog(plan: ChargePlan, ui: UiState, startedCharging: Boolean, hour: Int, onStopMusic: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val motionOff = rememberMotionOff()
    val p = LocalPalette.current
    val look = LocalLook.current
    val lookColors = LocalLookColors.current
    val dark = isSystemInDarkTheme()
    val slides = plan.slides
    val n = slides.size
    val seconds = plan.seconds
    var i by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf(n > 1) }
    var ended by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val charging = ui.mode == Mode.CHARGING
    val hud = ChargeShow.hud(ui.car.soc, charging, ui.chargeMinLeft, startedCharging)
    val dim = ChargeShow.dim(hour)

    fun go(to: Int) {
        if (plan.fitted && to >= n) { ended = true; playing = false; return }
        i = (to + n) % n
    }

    // 달리기 시작하면 사진을 틀지 않아요
    LaunchedEffect(ui.mode) { if (!Slideshow.allowed(ui.mode == Mode.DRIVING)) onClose() }
    // 앞 · 뒤 사진을 미리 읽어 두면 넘길 때 깜빡이지 않아요
    LaunchedEffect(i) {
        withContext(Dispatchers.IO) {
            StudioImages.get(ctx, slides[(i + 1) % n].image)
            StudioImages.get(ctx, slides[(i - 1 + n) % n].image)
        }
    }
    // 한 장을 보여 주는 동안 막대가 차고, 다 차면 다음 사진으로. 손으로 넘기면 다시 처음부터 세요.
    LaunchedEffect(i, playing, ended) {
        progress.snapTo(0f)
        if (playing && !ended && n > 1) {
            if (motionOff) delay(seconds * 1000L) else progress.animateTo(1f, tween(seconds * 1000, easing = LinearEasing))
            go(i + 1)
        }
    }
    val fade: FiniteAnimationSpec<Float> = if (motionOff) snap() else tween(700)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        var drag by remember { mutableStateOf(0f) }
        Box(Modifier.fillMaxSize().background(p.backgroundSecondary)) {
            if (look != null && lookColors != null) {
                // 테마의 바탕 그림 위에 가림색을 짙게 덮어서 사진이 돋보이고 글이 읽혀요(앱 창 바탕과 같은 그림 · 같은 가림색)
                StudioImage(look.photo ?: (if (dark) look.bgNight else look.bgDay), Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
                val veil = Color(lookColors.veil.toInt())
                Box(Modifier.fillMaxSize().background(veil.copy(alpha = (veil.alpha + 0.25f).coerceAtMost(0.94f))))
            }
            Column(
                Modifier.fillMaxSize()
                    .pointerInput(n) {
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = { if (n > 1) { if (drag > 120f) { ended = false; go(i - 1) } else if (drag < -120f) go(i + 1) } },
                            onHorizontalDrag = { _, dx -> drag += dx },
                        )
                    }
                    .padding(horizontal = 32.dp, vertical = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(slides[i].title, color = p.textPrimary, style = Ivi.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${i + 1} / $n" + if (plan.fitted) " · 충전 시간에 맞춰 ${Slideshow.duration(plan.totalSec)}" else "",
                            color = p.textTertiary, style = Ivi.labelMedium,
                        )
                    }
                    if (n > 1) {
                        PillButton("이전", { ended = false; go(i - 1) })
                        PillButton("다음", { go(i + 1) })
                        PillButton(if (playing) "멈춤" else "슬라이드쇼", { if (ended) { ended = false; i = 0 }; playing = !playing }, filled = !playing)
                    }
                    PillButton("닫기", onClose)
                }
                // 충전 상태: 배터리 · 80%까지 남은 시간 · 80%까지 찬 정도, 함께 튼 곡
                if (hud.text != null || ui.nowPlaying != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        hud.text?.let { t ->
                            Text(t, color = if (hud.reached) p.textPrimary else p.textSecondary, style = if (hud.reached) Ivi.bodyMediumStrong else Ivi.bodyMedium)
                            ChargeShow.fraction(ui.car.soc)?.takeIf { charging }?.let { f ->
                                Box(Modifier.width(220.dp).height(6.dp).background(p.surfaceHigh, RoundedCornerShape(3.dp))) {
                                    Box(Modifier.fillMaxWidth(f).height(6.dp).background(p.buttonFilled, RoundedCornerShape(3.dp)))
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        ui.nowPlaying?.let { song ->
                            Text("♪ $song", color = p.textSecondary, style = Ivi.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            PillButton("곡 끄기", onStopMusic)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 막대: 충전 시간에 맞춘 쇼는 전체 시간을, 아니면 지금 사진을 보여 준 정도를 따라 차요
                val fraction = if (ended) 1f else if (plan.fitted) ((i + progress.value) / n).coerceIn(0f, 1f) else progress.value
                Box(Modifier.fillMaxWidth().height(4.dp).background(p.surfaceHigh, RoundedCornerShape(2.dp))) {
                    if (playing || ended) Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(p.buttonFilled, RoundedCornerShape(2.dp)))
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                    Crossfade(targetState = i, animationSpec = fade, label = "charge-slide") { idx ->
                        // 슬라이드쇼 중에는 사진이 천천히 조금 커져요(켄 번스)
                        val zoom = remember { Animatable(1f) }
                        LaunchedEffect(playing, motionOff, seconds) {
                            zoom.snapTo(1f)
                            if (playing && !motionOff) zoom.animateTo(1.07f, tween(seconds * 1000 + 700, easing = LinearEasing))
                        }
                        StudioImage(slides[idx].image, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }, slides[idx].title, ContentScale.Fit, plain = true)
                    }
                    // 밤(저녁 7시 ~ 아침 6시)에는 사진을 조금 어둡게 비춰요
                    if (dim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
                    if (!ended) {
                        Text(
                            slides[i].note, color = p.textPrimary, style = Ivi.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp).background(p.backgroundPrimary, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                    if (ended) {
                        Column(
                            Modifier.align(Alignment.Center).background(p.backgroundPrimary, RoundedCornerShape(28.dp)).padding(horizontal = 36.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("충전하는 시간만큼 다 봤어요", color = p.textPrimary, style = Ivi.titleSmall)
                            Text(hud.text ?: "사진 ${n}장을 봤어요", color = p.textSecondary, style = Ivi.bodyLarge)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PillButton("처음부터", { ended = false; i = 0; playing = true }, filled = true)
                                PillButton("닫기", onClose)
                            }
                        }
                    }
                }
            }
        }
    }
}
