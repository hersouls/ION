package app.roadmemory.car.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.roadmemory.car.studio.LinkRules
import app.roadmemory.car.studio.StudioLink
import java.io.File

/** 시연 상품의 그림. 내려받은 것(files/studio/)이 있으면 그것을, 없으면 앱 안(assets/studio/)의 것을 읽어요. */
internal object StudioImages {
    private val cache = LruCache<String, ImageBitmap>(40)

    fun get(context: Context, path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        val app = context.applicationContext
        val file = File(app.filesDir, "studio/$path")
        val bmp = runCatching {
            when {
                // ION Studio에서 받은 목록의 그림: 이 기기에 없으면 지금 받아요(화면 스레드가 아닌 곳에서 불려요). 큰 그림은 줄여서 읽어요.
                LinkRules.isLinked(path) -> StudioLink.fetch(app, path)?.let { decodeFitted(it) }
                file.isFile -> BitmapFactory.decodeFile(file.path)
                else -> app.assets.open("studio/$path").use { BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()?.asImageBitmap()
        if (bmp != null) cache.put(path, bmp)
        return bmp
    }
}

/** 긴 변이 [edge]를 넘는 그림은 절반씩 줄여서 읽어요(Studio의 원본은 앱 안 그림보다 클 수 있어요). */
private fun decodeFitted(file: File, edge: Int = 1600): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > edge) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
}

@Composable
internal fun StudioImage(path: String?, modifier: Modifier = Modifier, description: String? = null, scale: ContentScale = ContentScale.Crop, plain: Boolean = false, alignment: androidx.compose.ui.Alignment = androidx.compose.ui.Alignment.Center) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = if (path == null) null else withContext(Dispatchers.IO) { StudioImages.get(ctx, path) }
    }
    Box(if (plain) modifier else modifier.background(p.buttonBasic)) {
        bmp?.let { Image(it, description, Modifier.fillMaxSize(), contentScale = scale, alignment = alignment) }
    }
}
