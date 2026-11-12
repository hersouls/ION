package app.roadmemory.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import app.roadmemory.car.Pane
import app.roadmemory.car.studio.Look
import app.roadmemory.car.studio.LookColors
import androidx.compose.ui.graphics.Brush
import app.roadmemory.car.studio.StudioLibrary
import app.roadmemory.car.studio.StudioStore
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.roadmemory.car.R

/**
 * Pleos Connect UI Design Guide의 토큰(접두사 ivi, snake_case)을 res/values(-night)/colors.xml에 두고 여기서 읽어요.
 * 낮 · 밤은 시스템 테마(uiMode)를 따라가요.
 */
class Palette(
    val backgroundPrimary: Color,   // ivi_background_primary — 카드 바탕
    val backgroundSecondary: Color, // ivi_background_secondary — 화면 바탕
    val surfaceHigh: Color,         // ivi_surface_high
    val surfaceAccent: Color,       // ivi_surface_accent
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textOnFilled: Color,
    val buttonFilled: Color,        // ivi_button_filled_enabled
    val buttonBasic: Color,         // ivi_button_basic_enabled
    val buttonFilledPressed: Color, // ivi_button_filled_pressed — 누르는 동안(가이드 Interaction › Button)
    val buttonBasicPressed: Color,  // ivi_button_basic_pressed
    val informativeActive: Color,   // ivi_informative_active — "지금 · 켜짐"
    val informativeNegative: Color,
)

/**
 * 색 값은 res의 ivi_* 토큰이 한 곳의 진실이에요. 날씨 톤(ui/Tone.kt)은 "자리마다 어느 토큰을 쓸지"만 바꾸고, 새 색을 만들지 않아요.
 * 톤의 색이 가이드의 대비 규칙(4.5:1)을 어기면 기본 톤을 써요: colors.xml을 고쳐도 이 조건은 지켜져요(ToneTest가 같은 검사를 해요).
 */
private fun tokenRes(token: String): Int = when (token) {
    "ivi_background_primary" -> R.color.ivi_background_primary
    "ivi_background_secondary" -> R.color.ivi_background_secondary
    "ivi_background_popup" -> R.color.ivi_background_popup
    "ivi_surface_high" -> R.color.ivi_surface_high
    "ivi_surface_accent" -> R.color.ivi_surface_accent
    "ivi_text_primary" -> R.color.ivi_text_primary
    "ivi_text_secondary" -> R.color.ivi_text_secondary
    "ivi_text_tertiary" -> R.color.ivi_text_tertiary
    "ivi_text_on_filled" -> R.color.ivi_text_on_filled
    "ivi_button_filled_enabled" -> R.color.ivi_button_filled_enabled
    "ivi_button_filled_pressed" -> R.color.ivi_button_filled_pressed
    "ivi_button_basic_enabled" -> R.color.ivi_button_basic_enabled
    else -> error("톤이 모르는 토큰이에요: $token")
}

@Composable
private fun toneColors(dark: Boolean, tone: Tone): Map<ToneRole, Color> =
    Tones.tokens(dark, tone).mapValues { (_, token) -> colorResource(tokenRes(token)) }

private fun Map<ToneRole, Color>.argb(): Map<ToneRole, Long> = mapValues { (_, c) -> c.toArgb().toLong() and 0xFFFFFFFFL }

@Composable
private fun iviPalette(tone: Tone): Palette {
    val dark = isSystemInDarkTheme()
    val standard = toneColors(dark, Tone.STANDARD)
    val wanted = if (tone == Tone.STANDARD) standard else toneColors(dark, tone)
    val use = if (tone == Tone.STANDARD || Tones.check(wanted.argb(), standard.argb()).isEmpty()) wanted else standard
    return Palette(
        backgroundPrimary = use.getValue(ToneRole.BACKGROUND_PRIMARY),
        backgroundSecondary = use.getValue(ToneRole.BACKGROUND_SECONDARY),
        surfaceHigh = use.getValue(ToneRole.SURFACE_HIGH),
        surfaceAccent = use.getValue(ToneRole.SURFACE_ACCENT),
        textPrimary = use.getValue(ToneRole.TEXT_PRIMARY),
        textSecondary = use.getValue(ToneRole.TEXT_SECONDARY),
        textTertiary = use.getValue(ToneRole.TEXT_TERTIARY),
        textOnFilled = use.getValue(ToneRole.TEXT_ON_FILLED),
        buttonFilled = use.getValue(ToneRole.BUTTON_FILLED),
        buttonBasic = use.getValue(ToneRole.BUTTON_BASIC),
        buttonFilledPressed = colorResource(R.color.ivi_button_filled_pressed),
        buttonBasicPressed = colorResource(R.color.ivi_button_basic_pressed),
        informativeActive = colorResource(R.color.ivi_informative_active),
        informativeNegative = colorResource(R.color.ivi_informative_negative),
    )
}

val LocalPalette = staticCompositionLocalOf<Palette> { error("CarFrame 안에서만 써요") }

/** 적용한 디스플레이 테마(없으면 null). 배터리 막대 · ION 구 · 엠블럼처럼 색 이름만으로 못 받는 부품이 읽어요. */
val LocalLook = staticCompositionLocalOf<Look?> { null }
/** 적용한 테마의 지금(낮 · 밤) 색. */
val LocalLookColors = staticCompositionLocalOf<LookColors?> { null }

/** 앱 창의 모양. 가이드: 앱은 가로(landscape, 두 창을 넓게 씀)와 세로(portrait, 한 창) 두 배치에 맞아야 해요. */
val LocalWide = staticCompositionLocalOf { true }

/**
 * Pleos Connect 글자 체계(Typography). 크기는 dp/sp 고정값이고 화면 크기에 따라 늘이지 않아요.
 * Headline 60/56/48 · Title 40/36/32 · Body 30/28/26(Strong/Normal) · Label 24/20.
 * 폰트는 시스템 폰트(기본값)를 써요. 캐릭터가 강한 폰트는 쓰지 않아요.
 */
object Ivi {
    val headlineLarge = TextStyle(fontSize = 60.sp, lineHeight = 72.sp, fontWeight = FontWeight.ExtraBold)
    val headlineMedium = TextStyle(fontSize = 56.sp, lineHeight = 68.sp, fontWeight = FontWeight.ExtraBold)
    val headlineSmall = TextStyle(fontSize = 48.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold)
    val titleLarge = TextStyle(fontSize = 40.sp, lineHeight = 50.sp, fontWeight = FontWeight.Bold)
    val titleMedium = TextStyle(fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold)
    val titleSmall = TextStyle(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
    val bodyLargeStrong = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
    val bodyLarge = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Normal)
    val bodyMediumStrong = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold)
    val bodyMedium = TextStyle(fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal)
    val bodySmallStrong = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold)
    val bodySmall = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal)
    val labelMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Normal)
    val labelSmall = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal)
}

/** 버튼 높이(가이드 Basic button: Large 100 · Medium 80 · Small 64 dp). 주행 중 카드의 버튼은 Large. */
object IviSize {
    const val buttonLarge = 100
    const val buttonMedium = 80
    const val buttonSmall = 64
}

/** 적용한 디스플레이 테마의 낮 · 밤 색으로 Palette를 바꿔요. 색 이름이 같아서 칸마다 알맞은 자리에 들어가요(글자 대비는 테마가 지켜요). */
internal fun Palette.themedBy(l: LookColors): Palette {
    fun c(v: Long) = Color(v.toInt())
    return Palette(
        backgroundPrimary = c(l.card), backgroundSecondary = c(l.bubble), surfaceHigh = c(l.cardLine), surfaceAccent = c(l.pill),
        textPrimary = c(l.textPrimary), textSecondary = c(l.textSecondary), textTertiary = c(l.textTertiary), textOnFilled = c(l.textOnFilled),
        buttonFilled = c(l.buttonFilled), buttonBasic = c(l.pill),
        buttonFilledPressed = lerp(c(l.buttonFilled), Color.Black, 0.2f), buttonBasicPressed = c(l.cardSolid),
        informativeActive = c(l.informativeActive), informativeNegative = c(l.informativeNegative),
    )
}

/**
 * 낮 · 밤은 차 시스템이, 톤은 날씨가 정해요(주행 중에는 DriveController가 톤을 붙들어 둬요).
 * 디스플레이 테마를 적용했으면 창 바탕 그림과 색이 그 테마 것으로 바뀌어요(톤은 테마가 색을 정해서 따로 적용하지 않아요).
 */
@Composable
fun CarFrame(tone: Tone = Tone.STANDARD, rain: Boolean = false, content: @Composable () -> Unit) {
    val base = iviPalette(tone)
    val ctx = LocalContext.current
    StudioLibrary.ensure(ctx)
    val dark = isSystemInDarkTheme()
    val lib = StudioLibrary.state
    // 비가 와서 서 있으면(rain) 내려받아 둔 비 테마를 덮어요. 사람이 적용한 테마는 그대로 두고, 비가 그치면 그 테마로 돌아와요([RainTheme]).
    val appliedId = RainTheme.resolve(rain, RainTheme.pick(StudioStore.catalog(ctx).themes, lib.downloaded), lib.appliedId(Pane.SVC_THEME))
    val look = remember(appliedId, app.roadmemory.car.studio.StudioLink.rev) { StudioStore.catalog(ctx).theme(appliedId)?.look }
    val colors = look?.let { if (dark) it.night else it.day }
    val p = if (colors != null) base.themedBy(colors) else base
    Box(Modifier.fillMaxSize().background(p.backgroundSecondary)) {
        // 테마가 바뀔 때(비가 오거나 그칠 때, 사람이 적용할 때) 바탕 사진이 천천히 겹쳐 바뀌어요
        androidx.compose.animation.Crossfade(targetState = look, animationSpec = androidx.compose.animation.core.tween(900), label = "skin") { lk ->
            val cs = lk?.let { if (dark) it.night else it.day }
            if (lk != null && cs != null) {
                // 창 바탕은 테마의 선명한 사진이에요(위쪽은 그대로 보이고, 카드가 놓이는 아래쪽으로 갈수록 테마의 가림색이 짙어져 글이 읽혀요).
                // Studio가 만든 흐린 바탕(bgDay · bgNight)은 쓰지 않아요: 흐리면 무슨 테마인지 알아볼 수 없어요.
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalPalette provides base.themedBy(cs)) {
                        StudioImage(lk.photo ?: (if (dark) lk.bgNight else lk.bgDay), Modifier.fillMaxSize(), alignment = androidx.compose.ui.Alignment.TopCenter)
                    }
                    val veil = Color(cs.veil.toInt())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to veil.copy(alpha = veil.alpha * 0.25f), 0.45f to veil, 1f to veil.copy(alpha = (veil.alpha + 0.25f).coerceAtMost(1f)))))
                }
            }
        }
        // 시스템 UI(상태 막대 · GNB · 키보드)는 앱보다 위 레이어예요. 앱 내용은 그 영역을 피해서 그려요.
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            // 폭이 높이보다 충분히 넓으면 가로(두 칸), 아니면 세로(한 칸)
            val wide = maxWidth.value > maxHeight.value * 1.2f
            CompositionLocalProvider(LocalPalette provides p, LocalWide provides wide, LocalLook provides look, LocalLookColors provides colors) { content() }
        }
    }
}
