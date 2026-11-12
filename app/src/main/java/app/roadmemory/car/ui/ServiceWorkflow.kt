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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import app.roadmemory.car.Pane
import app.roadmemory.car.UiState
import app.roadmemory.car.data.Tracks
import app.roadmemory.car.engine.Mode
import app.roadmemory.car.navi.GuideResult
import app.roadmemory.car.navi.NaviLink
import app.roadmemory.car.navi.Place
import app.roadmemory.car.studio.Art
import app.roadmemory.car.studio.SlideOrder
import app.roadmemory.car.studio.Slideshow
import app.roadmemory.car.studio.SlideshowControl
import app.roadmemory.car.studio.SlideshowPrefs
import app.roadmemory.car.studio.StudioLibrary
import app.roadmemory.car.studio.StudioStore
import app.roadmemory.car.studio.Trip

// ───────── 서비스 workflow 화면: 받기 → 적용 → 쓰기 → 지우기. 모든 화면에 "다음에 할 일" 단추가 있어요 ─────────

@Composable
internal fun PillButton(label: String, onClick: () -> Unit, filled: Boolean = false, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Box(
        modifier.height(IviSize.buttonSmall.dp)
            .iviButton(RoundedCornerShape(IviSize.buttonSmall.dp / 2), if (filled) p.buttonFilled else p.buttonBasic, if (filled) p.buttonFilledPressed else p.buttonBasicPressed, onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (filled) p.textOnFilled else p.textPrimary, style = Ivi.bodySmallStrong) }
}

/**
 * 서비스가 처음부터 끝까지 지나가는 길. 단계마다 어디에 있는지(이 앱 · 시험 빌드 · 제안)를 밝히고,
 * [done] 앞의 단계는 한 것, [done] 단계는 지금 할 일이에요(-1이면 표시하지 않아요). 아래에는 하이엔드에서 ION이 더하는 일(제안)을 적어요.
 */
@Composable
internal fun WorkflowCard(def: ServiceDef, done: Int) {
    val p = LocalPalette.current
    Card {
        Text("workflow", color = p.textTertiary, style = Ivi.labelMedium)
        Spacer(Modifier.height(10.dp))
        def.steps.forEachIndexed { i, s ->
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                when {
                    i < done -> MwIcon(MwIcons.Ok, size = 24.dp)
                    i == done -> Box(Modifier.size(24.dp).background(p.informativeActive, RoundedCornerShape(12.dp)))
                    else -> Box(Modifier.size(24.dp).background(p.buttonBasic, RoundedCornerShape(12.dp)))
                }
                Text("${i + 1}. ${s.label}", color = if (i == done) p.textPrimary else p.textSecondary, style = if (i == done) Ivi.bodyLargeStrong else Ivi.bodyMedium, modifier = Modifier.weight(1f))
                Text(s.where.label, color = p.textTertiary, style = Ivi.labelMedium)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("하이엔드(ION Brain · 제안)", color = p.textTertiary, style = Ivi.labelMedium)
        Text(def.highEnd, color = p.textSecondary, style = Ivi.bodyMedium)
    }
}

internal fun won(price: Int) = if (price <= 0) "무료" else "%,d원".format(price)

/**
 * 큰 사진 한 장과 앞 · 뒤 단추. 사진은 자르지 않고 다 보여요(얼굴이 잘리지 않게). 사진을 누르면 앱 창 가득 크게 보여요([PhotoLightbox]).
 * [autoplay]이면 정한 시간마다 저절로 넘어가요(충전하는 사이에 사진이 돌아가요). 크게 보는 동안에는 멈춰요.
 */
@Composable
internal fun PhotoViewer(photos: List<Pair<String, String>>, start: Int, onClose: (() -> Unit)? = null, autoplay: Boolean = false) {
    val ctx = LocalContext.current
    SlideshowPrefs.ensure(ctx)
    val p = LocalPalette.current
    var i by remember(photos) { mutableStateOf(start.coerceIn(0, (photos.size - 1).coerceAtLeast(0))) }
    var zoom by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(autoplay) }
    if (photos.isEmpty()) return
    LaunchedEffect(playing, i, zoom, SlideshowPrefs.seconds) {
        if (playing && !zoom && photos.size > 1) {
            delay(SlideshowPrefs.seconds * 1000L)
            i = (i + 1) % photos.size
        }
    }
    if (zoom) PhotoLightbox(photos, i, { i = it }) { zoom = false }
    Card {
        StudioImage(photos[i].first, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = "${photos[i].second} 크게 보기") { zoom = true }, photos[i].second, ContentScale.Fit)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(photos[i].second, color = p.textPrimary, style = Ivi.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${i + 1} / ${photos.size}", color = p.textTertiary, style = Ivi.labelMedium)
            }
            if (photos.size > 1) {
                PillButton("이전", { i = (i - 1 + photos.size) % photos.size })
                PillButton("다음", { i = (i + 1) % photos.size })
                if (autoplay) PillButton(if (playing) "멈춤" else "돌리기", { playing = !playing })
            }
            PillButton("크게", { zoom = true }, filled = true)
            if (onClose != null) PillButton("닫기", onClose)
        }
    }
}

/**
 * 사진을 앱 창 가득 크게 띄우는 보기이면서 슬라이드쇼 화면이에요. 사진은 자르지 않고 창에 맞춰 다 보여요.
 * 위에 제목 · 순서 · 이전 · 다음 · 슬라이드쇼(멈춤) · 닫기가 있고, 좌우로 밀어도 넘어가요(손짓이 어려운 차에서는 단추만으로 다 돼요). 뒤로 가기도 닫아요.
 * 슬라이드쇼는 정한 시간(4 · 7 · 12초)마다 넘기고, 사진이 부드럽게 겹치며 바뀌어요(기기에서 애니메이션을 끄면 바로 바뀌어요).
 * 끝나면 처음부터 다시 돌아요. 달리기 시작하면([driving]) 저절로 닫아요. [notes]는 사진 아래에 띄우는 한 줄(고른 이유)이에요.
 * [onIndex]는 넘긴 자리를 알려 줘요.
 */
@Composable
internal fun PhotoLightbox(
    photos: List<Pair<String, String>>, start: Int, onIndex: (Int) -> Unit = {},
    notes: List<String?> = emptyList(), autoplay: Boolean = false, driving: Boolean = false, onClose: () -> Unit,
) {
    if (photos.isEmpty()) { onClose(); return }
    val ctx = LocalContext.current
    SlideshowPrefs.ensure(ctx)
    val motionOff = rememberMotionOff()
    // 창 바탕 · 글 · 막대는 앱이 쓰는 팔레트(적용한 디스플레이 테마가 있으면 그 테마 색)를 따라요. Dialog 안에서도 같은 값이 와요.
    val p = LocalPalette.current
    val look = LocalLook.current
    val lookColors = LocalLookColors.current
    val dark = isSystemInDarkTheme()
    var i by remember(photos) { mutableStateOf(start.coerceIn(0, photos.lastIndex)) }
    var playing by remember { mutableStateOf(autoplay && photos.size > 1) }
    val seconds = SlideshowPrefs.seconds
    fun go(to: Int) { i = (to + photos.size) % photos.size; onIndex(i) }
    // 달리기 시작하면 사진을 틀지 않아요
    LaunchedEffect(driving) { if (!Slideshow.allowed(driving)) onClose() }
    // 앞 · 뒤 사진을 미리 읽어 두면 넘길 때 깜빡이지 않아요
    LaunchedEffect(i) {
        withContext(Dispatchers.IO) {
            StudioImages.get(ctx, photos[(i + 1) % photos.size].first)
            StudioImages.get(ctx, photos[(i - 1 + photos.size) % photos.size].first)
        }
    }
    // 슬라이드쇼: 한 장을 보여 주는 동안 막대가 차고, 다 차면 다음 사진으로. 손으로 넘기면 다시 처음부터 세요.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(i, playing, seconds) {
        progress.snapTo(0f)
        if (playing && photos.size > 1) {
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
                .pointerInput(photos.size) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = { if (photos.size > 1) { if (drag > 120f) go(i - 1) else if (drag < -120f) go(i + 1) } },
                        onHorizontalDrag = { _, dx -> drag += dx },
                    )
                }
                .padding(horizontal = 32.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(photos[i].second, color = p.textPrimary, style = Ivi.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${i + 1} / ${photos.size}", color = p.textTertiary, style = Ivi.labelMedium)
                }
                if (photos.size > 1) {
                    PillButton("이전", { go(i - 1) })
                    PillButton("다음", { go(i + 1) })
                    PillButton(if (playing) "멈춤" else "슬라이드쇼", { playing = !playing }, filled = !playing)
                }
                PillButton("닫기", onClose)
            }
            if (photos.size > 1) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("넘기는 시간", color = p.textTertiary, style = Ivi.labelMedium)
                    Slideshow.SECONDS.forEach { s -> PillButton("${s}초", { SlideshowPrefs.setSeconds(ctx, s) }, filled = seconds == s) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).background(p.surfaceHigh, RoundedCornerShape(2.dp))) {
                if (playing) Box(Modifier.fillMaxWidth(progress.value).height(4.dp).background(p.buttonFilled, RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                Crossfade(targetState = i, animationSpec = fade, label = "slide") { idx ->
                    // 슬라이드쇼 중에는 사진이 천천히 조금 커져요(켄 번스)
                    val zoom = remember { Animatable(1f) }
                    LaunchedEffect(playing, motionOff, seconds) {
                        zoom.snapTo(1f)
                        if (playing && !motionOff) zoom.animateTo(1.07f, tween(seconds * 1000 + 700, easing = LinearEasing))
                    }
                    StudioImage(photos[idx].first, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }, photos[idx].second, ContentScale.Fit, plain = true)
                }
                notes.getOrNull(i)?.let { note ->
                    Text(
                        note, color = p.textPrimary, style = Ivi.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomStart).padding(20.dp).background(p.backgroundPrimary, RoundedCornerShape(20.dp)).padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
        }
    }
}

// ───────── 디스플레이 테마 ─────────

@Composable
internal fun ThemeWorkflowPane(ui: UiState, def: ServiceDef, onHome: () -> Unit) {
    val ctx = LocalContext.current
    StudioLibrary.ensure(ctx)
    val cat = remember(app.roadmemory.car.studio.StudioLink.rev) { StudioStore.catalog(ctx) }
    val lib = StudioLibrary.state
    val p = LocalPalette.current
    var open by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(0) }
    PaneFrame(def.title, onHome) {
        LinkNotice()
        NoteLine("결제 없이 받아요. 적용하면 이 앱 창의 바탕 사진 · 색 · 단추 · 배터리 막대 · ION 구 · 엠블럼이 그 테마로 바뀌고, 카드 세트가 있는 테마는 ION 홈의 카드 ${ThemeCards.all.size}장도 그 테마의 그림을 입어요(차의 메뉴 · 계기판 · 상태 막대와 아래 메뉴는 시스템이 그려서 앱이 바꿀 수 없어요).")
        Spacer(Modifier.height(16.dp))
        // 지금 어울리는 테마(규칙 버전): 날씨 · 시간 · 탄 사람 · 취향으로 하나를 고르고 까닭을 달아요. 이미 적용한 테마면 권하지 않아요.
        val hourNow = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
        app.roadmemory.car.brain.ThemeShelf.pick(ui, cat.themes, hourNow)?.takeIf { lib.appliedId(def.pane) != it.theme.id }?.let { (t, why) ->
            SectionTitle("지금 어울리는 테마")
            Card {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StudioImage(t.look?.photo ?: t.cover, Modifier.size(width = 200.dp, height = 150.dp).clip(RoundedCornerShape(16.dp)), t.title, alignment = Alignment.TopCenter)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = p.textPrimary, style = Ivi.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(why.joinToString(" · "), color = p.textSecondary, style = Ivi.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    if (lib.has(t.id)) PillButton("적용", { StudioLibrary.apply(ctx, def.pane, t.id) }, filled = true)
                    else DownloadButton(t.id) { StudioLibrary.download(ctx, t.id, t.files) }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        // 갈래로 골라 봐요: 전체 · 야구 · 축구 · 아이돌 · 영화 · 내려받은 것(0 = 전체, 1.. = ThemeKind, 마지막 = 내려받은 것)
        val kinds = ThemeKind.entries
        val mine = cat.themes.count { lib.has(it.id) }
        val pick = if (filter > kinds.size && mine == 0) 0 else filter
        val shown = when {
            pick == 0 -> cat.themes
            pick <= kinds.size -> cat.themes.filter { ThemeKind.of(it) == kinds[pick - 1] }
            else -> cat.themes.filter { lib.has(it.id) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("전체 ${cat.themes.size}", { filter = 0 }, filled = pick == 0)
            kinds.forEachIndexed { k, kind -> PillButton("${kind.label} ${cat.themes.count { ThemeKind.of(it) == kind }}", { filter = k + 1 }, filled = pick == k + 1) }
            if (mine > 0) PillButton("내려받은 것 $mine", { filter = kinds.size + 1 }, filled = pick == kinds.size + 1)
        }
        shown.forEach { t ->
            val got = lib.has(t.id)
            val on = lib.appliedId(def.pane) == t.id
            Card(Modifier.clickable(role = Role.Button, onClickLabel = "${t.title} 미리 보기") { open = if (open == t.id) null else t.id }) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    StudioImage(t.look?.photo ?: t.cover, Modifier.size(width = 200.dp, height = 150.dp).clip(RoundedCornerShape(16.dp)), t.title, alignment = Alignment.TopCenter)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            t.look?.emblem?.let { StudioImage(it, Modifier.size(36.dp), null, ContentScale.Fit, plain = true) }
                            Text(t.title, color = p.textPrimary, style = Ivi.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${t.creator} · ${won(t.price)}", color = p.textTertiary, style = Ivi.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ThemeCards.isSet(t.look)) Text("카드 세트 ${ThemeCards.all.size}장 · 홈 카드도 바뀌어요", color = p.textSecondary, style = Ivi.bodyMediumStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        Text(t.summary, color = p.textSecondary, style = Ivi.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            on -> PillButton("적용 중 · 해제", { StudioLibrary.unapply(ctx, def.pane) })
                            got -> PillButton("적용", { StudioLibrary.apply(ctx, def.pane, t.id) }, filled = true)
                            else -> DownloadButton(t.id) { StudioLibrary.download(ctx, t.id, t.files) }
                        }
                        if (got) PillButton("지우기", { StudioLibrary.remove(ctx, t.id, t.files) })
                    }
                }
                if (open == t.id) {
                    val set = ThemeCards.isSet(t.look)
                    if (set) ThemeSetPreview(t.look!!, t.title)
                    if (set && t.look!!.previews.isNotEmpty()) {
                        // 적용한 ION 홈을 앱에서 그대로 캡처한 미리 보기(스토어 상품 화면의 "메인 홈 화면"과 같은 자리)
                        Spacer(Modifier.height(14.dp))
                        Text("적용하면 이렇게 보여요(앱에서 캡처)", color = p.textTertiary, style = Ivi.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        t.look.previews.chunked(2).forEach { row ->
                            Row(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { (label, img) ->
                                    Column(Modifier.weight(1f)) {
                                        StudioImage(img, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)), "${t.title} $label", ContentScale.Fit)
                                        Text(label, color = p.textSecondary, style = Ivi.labelMedium, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(14.dp))
                        Text("적용하면 이렇게 보여요(Studio 미리 보기)", color = p.textTertiary, style = Ivi.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("낮" to t.day, "밤" to t.night).forEach { (label, img) ->
                                Column(Modifier.weight(1f)) {
                                    Text(label, color = p.textTertiary, style = Ivi.labelMedium)
                                    StudioImage(img, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)), "${t.title} $label 화면", ContentScale.Fit)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 테마의 카드 세트 미리 보기(스토어 상품 화면의 "메뉴 카드"와 같은 자리): 적용하면 ION 홈의 카드 열네 장이 이 그림을 입어요.
 * 내려받기 전에도 볼 수 있어요(앱 안의 시연 상품이라 그림이 앱에 들어 있어요). 카드 이름은 ION 홈의 카드 이름이에요.
 */
@Composable
internal fun ThemeSetPreview(look: app.roadmemory.car.studio.Look, title: String) {
    val p = LocalPalette.current
    Spacer(Modifier.height(14.dp))
    Text("카드 세트 · 적용하면 ION 홈의 카드 ${ThemeCards.all.size}장이 이 그림을 입어요", color = p.textTertiary, style = Ivi.labelMedium)
    Spacer(Modifier.height(8.dp))
    ThemeCards.all.chunked(5).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (key, label) ->
                Column(Modifier.weight(1f)) {
                    StudioImage(look.cards[key], Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(14.dp)), "$title · $label 카드")
                    Text(label, color = p.textSecondary, style = Ivi.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** 적용한 테마의 사진 카드(ION 칸). 사진을 자르지 않고 다 보여 주고, 엠블럼과 이름이 아래에 붙어요. 적용한 테마가 없으면 아무것도 그리지 않아요. */
@Composable
internal fun ThemePhotoCard() {
    val look = LocalLook.current ?: return
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.backgroundPrimary)) {
        StudioImage(look.photo, Modifier.fillMaxWidth().aspectRatio(1f), look.name, ContentScale.Fit, plain = true)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioImage(look.emblem, Modifier.size(44.dp), null, ContentScale.Fit, plain = true)
            Text(look.name, color = p.textPrimary, style = Ivi.bodyLargeStrong, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("디스플레이 테마", color = p.textTertiary, style = Ivi.labelMedium)
        }
    }
}

// ───────── 디지털 아트: 내려받고 → 걸고(ION 칸 · 충전 쉼터에 보여요) → 열어 봐요 ─────────

/** 걸어 둔 디지털 아트(ION 칸). 사진을 자르지 않아요. 건 작품이 없으면 아무것도 그리지 않아요. */
@Composable
internal fun HungArtCard() {
    val ctx = LocalContext.current
    StudioLibrary.ensure(ctx)
    val art = StudioStore.catalog(ctx).art(StudioLibrary.state.appliedId(Pane.SVC_ART)) ?: return
    val p = LocalPalette.current
    val photo = art.photos.firstOrNull() ?: art.cover
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(p.backgroundPrimary)) {
        StudioImage(photo, Modifier.fillMaxWidth().aspectRatio(4f / 3f), art.title, ContentScale.Fit, plain = true)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(art.title, color = p.textPrimary, style = Ivi.bodyLargeStrong, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${art.artist} · 디지털 아트", color = p.textTertiary, style = Ivi.labelMedium)
        }
    }
}

// ───────── 마이 트립: 정류장을 누르면 내비가 안내해요. 도착하면 다음 정류장으로 이어요 ─────────

/**
 * 어느 일정의 어느 정류장인지(이 기기에 남아요). [stopIndex]는 지금 말하는 정류장이에요: [arrived]가 false면 그곳으로 가는 중이고,
 * true면 내비가 "도착했어요"라고 알려 준 곳이에요. [end]는 내비에 한 번에 보낸 마지막 정류장이에요(경유지 둘과 목적지 한 곳을 함께 보내서,
 * 앞 정류장에 닿아도 내비는 계속 가요).
 */
internal data class TripProgress(val tripId: String, val stopIndex: Int, val end: Int = stopIndex, val arrived: Boolean = false)

internal object TripRun {
    /** "일정id#정류장"(예전 모양) 또는 "일정id#정류장#마지막#1|0"(도착 표시가 있는 모양). 깨진 값은 null. */
    fun parse(raw: String?): TripProgress? {
        val parts = raw?.split('#') ?: return null
        return when (parts.size) {
            2 -> TripProgress(parts[0], parts[1].toIntOrNull() ?: return null)
            4 -> {
                val flag = parts[3].takeIf { it == "0" || it == "1" } ?: return null
                TripProgress(parts[0], parts[1].toIntOrNull() ?: return null, parts[2].toIntOrNull() ?: return null, flag == "1")
            }
            else -> null
        }
    }

    fun encode(p: TripProgress) =
        if (p.end == p.stopIndex && !p.arrived) "${p.tripId}#${p.stopIndex}" else "${p.tripId}#${p.stopIndex}#${p.end}#${if (p.arrived) 1 else 0}"

    fun current(lib: app.roadmemory.car.studio.LibraryState): TripProgress? = parse(lib.appliedId(Pane.SVC_TRIP))

    private fun norm(s: String?) = s.orEmpty().filter { it.isLetterOrDigit() }.lowercase()
    private fun sameName(a: String?, b: String?): Boolean {
        val x = norm(a); val y = norm(b)
        return x.length >= 2 && y.length >= 2 && (x == y || x.contains(y) || y.contains(x))
    }
    private fun meters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1); val dLng = Math.toRadians(lng2 - lng1)
        val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2).let { it * it }
        return 2 * 6_371_000.0 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
    }

    /** 목적지 도착을 좌표로 견줄 때 같은 곳으로 보는 거리(m). */
    const val SAME_PLACE_M = 400.0

    /**
     * 내비의 도착 알림으로 진행을 옮겨요. 이 일정에서 아직 닿지 않은 정류장의 도착이 아니면 null(다른 길의 도착은 건드리지 않아요).
     * 경유지에 닿으면 그 정류장에 "도착"을 달고 내비는 계속 가는 중으로 두고, 목적지에 닿으면 이번에 보낸 구간이 끝나요.
     * [dest]는 마지막으로 안내하던 목적지(좌표가 있으면 이름보다 먼저 견줘요).
     */
    fun onArrival(run: TripProgress, trip: Trip, a: app.roadmemory.car.data.NaviArrival, dest: Place? = null): TripProgress? {
        val stops = trip.stops
        if (stops.isEmpty()) return null
        val from = if (run.arrived) run.stopIndex + 1 else run.stopIndex
        val to = run.end.coerceAtMost(stops.lastIndex)
        if (from > to) return null
        val range = from..to
        fun byName(): Int? = range.firstOrNull { sameName(stops[it].name, a.name) }
        fun byPlace(): Int? = dest?.let { d -> range.firstOrNull { meters(stops[it].lat, stops[it].lng, d.lat, d.lng) <= SAME_PLACE_M } }
        return when (a.kind) {
            app.roadmemory.car.data.ArrivalKind.WAYPOINT -> {
                // 경유지가 없는 구간에서 온 경유지 도착은 이 일정의 것이 아니에요
                if (to == from) return null
                val j = byName() ?: from
                TripProgress(run.tripId, j, run.end, arrived = true)
            }
            app.roadmemory.car.data.ArrivalKind.DESTINATION -> {
                val j = byPlace() ?: byName() ?: return null
                TripProgress(run.tripId, j, j, arrived = true)
            }
        }
    }
}

/** 마이 트립의 안내: i번째 정류장부터 다음 두 곳까지 내비에 한 번에 보내요(SDK는 경유지 둘까지). */
internal object TripGuide {
    class Plan(val dest: Place, val through: List<Place>, val end: Int)

    fun plan(t: Trip, i: Int): Plan? {
        val s = t.stops.getOrNull(i) ?: return null
        val via = t.stops.drop(i + 1).take(2).map { Place(it.name, it.lat, it.lng) }
        val dest = via.lastOrNull() ?: Place(s.name, s.lat, s.lng)
        val through = if (via.isEmpty()) emptyList() else listOf(Place(s.name, s.lat, s.lng)) + via.dropLast(1)
        return Plan(dest, through, i + via.size)
    }
}

@Composable
internal fun TripWorkflowPane(ui: UiState, def: ServiceDef, onHome: () -> Unit) {
    val ctx = LocalContext.current
    StudioLibrary.ensure(ctx)
    app.roadmemory.car.studio.TripDone.ensure(ctx)
    val cat = remember(app.roadmemory.car.studio.StudioLink.rev) { StudioStore.catalog(ctx) }
    val lib = StudioLibrary.state
    val p = LocalPalette.current
    val running = TripRun.current(lib)
    var open by remember { mutableStateOf<String?>(running?.tripId) }
    var note by remember { mutableStateOf<String?>(null) }

    fun guideTo(t: Trip, i: Int) {
        val plan = TripGuide.plan(t, i) ?: return
        val s = t.stops[i]
        note = "내비에 ${app.roadmemory.car.brain.Korean.euro(s.name)} 길을 보내는 중이에요"
        NaviLink.guide(ctx, plan.dest, plan.through) { r ->
            note = when (r) {
                GuideResult.Sent -> if (plan.end == i) "내비가 ${app.roadmemory.car.brain.Korean.euro(s.name)} 안내를 시작해요" else "내비가 ${t.stops.subList(i, plan.end + 1).joinToString(" → ") { it.name }} 순서로 안내를 시작해요"
                is GuideResult.Failed -> r.why.label
            }
        }
        StudioLibrary.apply(ctx, Pane.SVC_TRIP, TripRun.encode(TripProgress(t.id, i, plan.end)))
    }

    PaneFrame(def.title, onHome) {
        LinkNotice()
        running?.let { run ->
            cat.trips.firstOrNull { it.id == run.tripId }?.let { t ->
                val stops = t.stops
                val cur = stops.getOrNull(run.stopIndex)
                val next = run.stopIndex + 1
                val finished = run.arrived && next >= stops.size   // 내비가 마지막 정류장에 도착했다고 알려 줬어요
                Card {
                    Text(if (finished) "여행을 마쳤어요 · ${t.title}" else if (run.arrived) "도착했어요 · ${t.title}" else "가는 중 · ${t.title}", color = p.textTertiary, style = Ivi.labelMedium)
                    Text(cur?.name ?: "—", color = p.textPrimary, style = Ivi.titleSmall)
                    Text(
                        "${run.stopIndex + 1} / ${stops.size} 정류장" + if (run.arrived && !finished && run.stopIndex < run.end && ui.nav.routeActive) " · 내비가 다음 정류장으로 안내하는 중이에요" else "",
                        color = p.textSecondary, style = Ivi.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            finished -> PillButton("마치기", { app.roadmemory.car.studio.TripDone.mark(ctx, t.id); StudioLibrary.unapply(ctx, Pane.SVC_TRIP); note = "${app.roadmemory.car.brain.Korean.eul(t.title)} 다녀온 것으로 적었어요. 다녀온 길은 여행 기록에도 남아요" }, filled = true)
                            next < stops.size -> PillButton(if (run.arrived) "다음 ${app.roadmemory.car.brain.Korean.euro(stops[next].name)} 안내" else "도착했어요 · 다음 ${app.roadmemory.car.brain.Korean.euro(stops[next].name)}", { guideTo(t, next) }, filled = true)
                            else -> PillButton("여행을 마쳤어요", { app.roadmemory.car.studio.TripDone.mark(ctx, t.id); StudioLibrary.unapply(ctx, Pane.SVC_TRIP); note = "${app.roadmemory.car.brain.Korean.eul(t.title)} 다녀온 것으로 적었어요. 다녀온 길은 여행 기록에도 남아요" }, filled = true)
                        }
                        if (!finished) PillButton("그만두기", { StudioLibrary.unapply(ctx, Pane.SVC_TRIP); note = null })
                    }
                }
            }
        }
        note?.let { Card { Text(it, color = p.textPrimary, style = Ivi.bodyLarge) } }
        NoteLine("정류장을 누르면 차의 내비에 그곳으로 길을 보내요(경유지는 둘까지 함께). 운전자가 누른 뒤에만 보내고, ION이 먼저 경로를 바꾸지 않아요. 정류장은 지도에서 찾은 실제 장소예요.")
        Spacer(Modifier.height(16.dp))
        cat.trips.forEach { t ->
            val got = lib.has(t.id)
            Card(Modifier.clickable(role = Role.Button, onClickLabel = "${t.title} 정류장 보기") { open = if (open == t.id) null else t.id }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    MwIcon(MwIcons.Trips, size = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = p.textPrimary, style = Ivi.titleSmall)
                        Text(listOfNotNull(t.region, t.km?.let { "${Math.round(it)}km" }, "정류장 ${t.stops.size}곳", if (t.days.size > 1) "${t.days.size}일" else null).joinToString(" · "), color = p.textSecondary, style = Ivi.bodyMedium)
                        app.roadmemory.car.studio.TripDone.on(t.id)?.let { Text(app.roadmemory.car.brain.TripLegs.doneLine(it, t), color = p.textTertiary, style = Ivi.labelMedium) }
                    }
                    if (got) {
                        PillButton("처음부터 안내", { guideTo(t, 0) }, filled = true)
                        PillButton("지우기", { if (running?.tripId == t.id) StudioLibrary.unapply(ctx, Pane.SVC_TRIP); app.roadmemory.car.studio.TripDone.clear(ctx, t.id); StudioLibrary.remove(ctx, t.id, emptyList()) })
                    } else PillButton("내 일정에 담기", { StudioLibrary.download(ctx, t.id, emptyList()); open = t.id }, filled = true)
                }
                if (open == t.id) {
                    Spacer(Modifier.height(12.dp))
                    // 다음 여행 짜기(규칙 버전): 정류장 사이 거리와 지금 갈 수 있는 거리로 충전 계획을 말해요
                    val plan = app.roadmemory.car.brain.TripPlan.plan(t, ui.car.rangeKm, if (running?.tripId == t.id) running.stopIndex else 0)
                    Text(plan.summary, color = if ((plan.chargesNeeded ?: 0) > 0) p.textPrimary else p.textSecondary, style = Ivi.bodyMediumStrong, modifier = Modifier.padding(bottom = 6.dp))
                    // 정류장 사이: 앞 정류장에서 여기까지의 거리와, 가다가 충전이 필요해지는 구간(지나온 정류장에는 달지 않아요)
                    val legRows = app.roadmemory.car.brain.TripLegs.rows(t, ui.car.rangeKm, if (running?.tripId == t.id) running.stopIndex else 0)
                    val dayKm = app.roadmemory.car.brain.TripLegs.dayKm(t)
                    var n = 0
                    t.days.forEachIndexed { d, stops ->
                        if (t.days.size > 1) Text("${d + 1}일차 · 구간 합 약 ${Math.round(dayKm[d])}km", color = p.textTertiary, style = Ivi.labelMedium, modifier = Modifier.padding(top = 6.dp))
                        stops.forEach { s ->
                            val i = n++
                            val here = running?.tripId == t.id && running.stopIndex == i
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("${i + 1}. ${s.name}" + if (here) " · 가는 중" else "", color = p.textPrimary, style = Ivi.bodyLargeStrong)
                                    Text(listOf(s.address, s.note).filter { it.isNotBlank() }.joinToString(" · "), color = p.textSecondary, style = Ivi.bodyMedium)
                                    legRows.getOrNull(i)?.let { r ->
                                        app.roadmemory.car.brain.TripLegs.line(r)?.let { line ->
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                                if (r.chargeBefore) StateDot(alert = false)
                                                Text(line, color = if (r.chargeBefore) p.textPrimary else p.textTertiary, style = if (r.chargeBefore) Ivi.bodyMediumStrong else Ivi.labelMedium)
                                            }
                                        }
                                    }
                                }
                                if (got) PillButton(if (here) "다시 안내" else "여기로 안내", { guideTo(t, i) }, filled = !here)
                            }
                        }
                    }
                    if (!got) Text("내 일정에 담으면 정류장마다 \"여기로 안내\"가 생겨요", color = p.textTertiary, style = Ivi.labelMedium)
                }
            }
        }
    }
}

// ───────── 마이현대에서 동기화: 디지털 앨범 · 디지털 뮤직 · 캘린더 ─────────

@Composable
internal fun SyncPane(def: ServiceDef, onHome: () -> Unit, files: List<String>, what: String, content: @Composable ColumnScope.() -> Unit) {
    val ctx = LocalContext.current
    StudioLibrary.ensure(ctx)
    val cat = remember(app.roadmemory.car.studio.StudioLink.rev) { StudioStore.catalog(ctx) }
    val lib = StudioLibrary.state
    val synced = lib.has(Services.syncId(def))
    PaneFrame(def.title, onHome) {
        if (!synced) {
            Card {
                Text("마이현대에 등록한 ${what}이 아직 이 앱에 없어요. 동기화하면 이 기기로 가져와요.", color = LocalPalette.current.textPrimary, style = Ivi.bodyLarge)
                Spacer(Modifier.height(16.dp))
                BigButton("마이현대에서 동기화해서 가져오기", true, { StudioLibrary.download(ctx, Services.syncId(def), files) }, Modifier.fillMaxWidth())
            }
        } else {
            content()
            Spacer(Modifier.height(4.dp))
            PillButton("지우기", { StudioLibrary.remove(ctx, Services.syncId(def), files) })
        }
    }
}

/**
 * 디지털 앨범 = 마이현대에 올린 내 사진 · 동영상을 차에서 보는 서비스예요. 서 있을 때는 여기서 크게 넘겨 보거나 슬라이드쇼로 틀고,
 * 충전 중에는 충전 화면의 "충전하는 사이"에 같은 사진이 돌아가요. 슬라이드쇼는 ION이 가는 곳 근처 · 이맘때 사진부터 골라서 사진마다 이유를 한 줄로 달아요
 * (규칙 버전 · 서버 없이 이 기기에서 돌아요. 사진 속 내용을 보고 고르는 일은 하이엔드 제안이에요). 말로 "슬라이드쇼 틀어 줘 · 멈춰"도 돼요. 달리는 중에는 틀지 않아요.
 */
@Composable
internal fun AlbumWorkflowPane(ui: UiState, def: ServiceDef, onHome: () -> Unit) {
    val ctx = LocalContext.current
    StudioLibrary.ensure(ctx)
    SlideshowPrefs.ensure(ctx)
    val cat = remember(app.roadmemory.car.studio.StudioLink.rev) { StudioStore.catalog(ctx) }
    val lib = StudioLibrary.state
    val p = LocalPalette.current
    val synced = lib.has(Services.syncId(def))
    // 가는 일정의 지역(그 근처 사진을 먼저 보여 줘요)
    val region = TripRun.current(lib)?.let { r -> cat.trips.firstOrNull { it.id == r.tripId }?.region }
    var show by remember { mutableStateOf<AlbumShow?>(null) }
    // 골라 보기: 지역 · 해(둘 다 null이면 모두)
    var region_ by remember { mutableStateOf<String?>(null) }
    var year_ by remember { mutableStateOf<String?>(null) }
    fun startShow() {
        val order = SlideshowPrefs.order
        val today = java.time.LocalDate.now()
        val slides = Slideshow.plan(cat.album, order, region, today, if (order == SlideOrder.SHUFFLE) System.nanoTime() else today.toEpochDay())
        if (slides.isNotEmpty()) show = AlbumShow(slides.map { it.photo.image to it.photo.title }, slides.map { it.note }, 0, true)
    }
    // 말로 시작 · 멈춤("슬라이드쇼 틀어 줘")
    val cmd = SlideshowControl.command
    LaunchedEffect(cmd, synced) {
        when (cmd) {
            SlideshowControl.Cmd.START -> { if (synced) startShow(); SlideshowControl.clear() }
            SlideshowControl.Cmd.STOP -> { show = null; SlideshowControl.clear() }
            null -> {}
        }
    }
    val seconds = SlideshowPrefs.seconds
    SyncPane(def, onHome, cat.album.flatMap { listOf(it.image, it.thumb) }.distinct(), "사진 · 동영상") {
        Card {
            Text("무엇을 하는 서비스예요?", color = p.textTertiary, style = Ivi.labelMedium)
            Text("마이현대에 올린 내 사진 · 동영상을 차에서 봐요. 서 있을 때는 여기서 크게 넘겨 보거나 슬라이드쇼로 틀고, 충전 중에는 충전 화면에 같은 사진이 돌아가요.", color = p.textPrimary, style = Ivi.bodyLarge)
        }
        Card {
            Text("슬라이드쇼", color = p.textTertiary, style = Ivi.labelMedium)
            Text(Slideshow.lapText(cat.album.size, seconds), color = p.textPrimary, style = Ivi.bodyLarge)
            if (ui.mode == Mode.CHARGING) Slideshow.chargeFit(ui.chargeMinLeft, cat.album.size, seconds)?.let { Text(it, color = p.textSecondary, style = Ivi.bodyMedium) }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("순서", color = p.textTertiary, style = Ivi.labelMedium)
                SlideOrder.entries.forEach { o -> PillButton(o.label, { SlideshowPrefs.setOrder(ctx, o) }, filled = SlideshowPrefs.order == o) }
            }
            Text(
                when (SlideshowPrefs.order) {
                    SlideOrder.ION ->
                        if (region != null) "지금 가는 곳($region) 근처 사진 → 이맘때 사진 → 나머지 순서로 틀고, 사진마다 이유를 달아요"
                        else "이맘때 사진 → 나머지(오늘 날짜로 섞어요) 순서로 틀어요. 가는 일정이 있으면 그 근처 사진을 먼저 보여 줘요"
                    SlideOrder.SEQUENCE -> "앨범에 든 순서대로 틀어요"
                    SlideOrder.SHUFFLE -> "틀 때마다 새로 섞어요"
                },
                color = p.textSecondary, style = Ivi.bodyMedium, modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("넘기는 시간", color = p.textTertiary, style = Ivi.labelMedium)
                Slideshow.SECONDS.forEach { s -> PillButton("${s}초", { SlideshowPrefs.setSeconds(ctx, s) }, filled = seconds == s) }
            }
            Spacer(Modifier.height(16.dp))
            BigButton("슬라이드쇼 보기", true, { startShow() }, Modifier.fillMaxWidth())
        }
        show?.let { s -> PhotoLightbox(s.photos, s.start, notes = s.notes, autoplay = s.autoplay, driving = ui.mode == Mode.DRIVING) { show = null } }
        SectionTitle("골라 보기")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("모든 지역", { region_ = null }, filled = region_ == null)
            app.roadmemory.car.brain.AlbumShelf.regions(cat.album).forEach { (r, n) -> PillButton("$r ${n}장", { region_ = if (region_ == r) null else r }, filled = region_ == r) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("모든 해", { year_ = null }, filled = year_ == null)
            app.roadmemory.car.brain.AlbumShelf.years(cat.album).forEach { (y, n) -> PillButton("${y}년 ${n}장", { year_ = if (year_ == y) null else y }, filled = year_ == y) }
        }
        val shown = app.roadmemory.car.brain.AlbumShelf.filter(cat.album, region_, year_)
        if (region_ != null || year_ != null) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(if (shown.isEmpty()) "고른 조건에 맞는 사진이 없어요" else "${listOfNotNull(region_, year_?.let { "${it}년" }).joinToString(" · ")} · ${shown.size}장", color = p.textPrimary, style = Ivi.bodyLargeStrong, modifier = Modifier.weight(1f).onBackdrop(p.backgroundSecondary))
                if (shown.size > 1 && ui.mode != Mode.DRIVING) PillButton("이 사진만 슬라이드쇼", {
                    show = AlbumShow(shown.map { it.image to it.title }, shown.map { a -> if (a.region.isNotBlank()) "${a.month} · ${a.region}" else a.month }, 0, true)
                }, filled = true)
            }
        }
        shown.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { ph ->
                    val idx = shown.indexOf(ph)
                    Column(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "${ph.title} 크게 보기") {
                        show = AlbumShow(shown.map { it.image to it.title }, shown.map { a -> if (a.region.isNotBlank()) "${a.month} · ${a.region}" else a.month }, idx, false)
                    }) {
                        StudioImage(ph.thumb, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)), ph.title)
                        Text(ph.title, color = p.textPrimary, style = Ivi.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp).onBackdrop(p.backgroundSecondary))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        NoteLine("내가 마이현대에 올린 사진 · 동영상이에요. ION이 가는 곳 근처 사진과 이맘때 사진을 먼저 골라서 보여 줘요.")
    }
}

/** 크게 보기 · 슬라이드쇼에 넘기는 값: 사진(그림 경로 · 제목), 사진 아래 한 줄, 시작 자리, 저절로 넘길지. */
private data class AlbumShow(val photos: List<Pair<String, String>>, val notes: List<String?>, val start: Int, val autoplay: Boolean)
