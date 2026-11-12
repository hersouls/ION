package app.roadmemory.car.pack

import android.content.Context
import android.util.Log
import java.io.File

enum class PackSource(val label: String) { BUNDLED("앱에 들어 있어요"), DEVICE("이 기기에 넣었어요") }

/** 이 기기에서 찾은 팩 하나. dir는 번들이면 assets 안 폴더(`packs/<id>`), 기기에 넣은 것이면 실제 폴더 경로예요. */
data class InstalledPack(val file: PackFile, val source: PackSource, val dir: String) {
    val id: String get() = file.id
}

/**
 * 이 기기에 있는 시그니처 송 팩을 찾아요.
 *  - 앱에 번들로 든 시연용 팩: `assets/packs/<id>/pack.json`
 *  - 기기에 넣은 팩: `<앱 외부 files>/packs/<id>/pack.json`(adb push) 또는 `<앱 안 files>/packs/<id>/pack.json`(디버그 빌드의 run-as). 같은 id면 이쪽이 번들을 이겨요
 * 네트워크는 쓰지 않아요. 적용한 팩의 id는 이 기기에만 남아요([app.roadmemory.car.data.AppPrefsStore]).
 */
class PackStore(context: Context) {
    private val context = context.applicationContext

    fun scan(): List<InstalledPack> {
        val found = LinkedHashMap<String, InstalledPack>()
        runCatching {
            context.assets.list(ASSET_DIR).orEmpty().sorted().forEach { id ->
                val text = runCatching { context.assets.open("$ASSET_DIR/$id/pack.json").bufferedReader(Charsets.UTF_8).use { it.readText() } }.getOrNull() ?: return@forEach
                PackFile.parse(text)?.takeIf { it.id == id }?.let { found[id] = InstalledPack(it, PackSource.BUNDLED, "$ASSET_DIR/$id") }
            }
        }.onFailure { Log.w(TAG, "번들 팩을 찾지 못했어요", it) }
        // 기기에 넣은 팩: 앱 외부 files(adb push)와 앱 안 files(디버그 빌드의 run-as로 넣기 쉬워요). 같은 id면 나중에 찾은 쪽이 이겨요
        listOfNotNull(context.getExternalFilesDir(null), context.filesDir).forEach { base ->
            runCatching {
                File(base, ASSET_DIR).listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { dir ->
                    val text = runCatching { File(dir, "pack.json").readText(Charsets.UTF_8) }.getOrNull() ?: return@forEach
                    PackFile.parse(text)?.takeIf { it.id == dir.name }?.let { found[it.id] = InstalledPack(it, PackSource.DEVICE, dir.path) }
                }
            }.onFailure { Log.w(TAG, "기기에 넣은 팩을 찾지 못했어요", it) }
        }
        return found.values.toList()
    }

    /** 순간의 목소리 파일을 재생할 수 있는 파일로 돌려줘요(번들은 처음 한 번 캐시 폴더로 풀어요). 없거나 읽지 못하면 null. */
    fun clip(pack: InstalledPack, moment: PackMoment): File? = runCatching {
        when (pack.source) {
            PackSource.DEVICE -> File(pack.dir, moment.file).takeIf { it.isFile && it.length() > 0 }
            PackSource.BUNDLED -> {
                val out = File(File(context.cacheDir, "packclips/${pack.id}").also { it.mkdirs() }, moment.file)
                if (!out.isFile || out.length() == 0L) {
                    context.assets.open("${pack.dir}/${moment.file}").use { input -> out.outputStream().use { input.copyTo(it) } }
                }
                out
            }
        }
    }.onFailure { Log.w(TAG, "목소리 파일을 열지 못했어요: ${moment.file}") }.getOrNull()

    private companion object {
        const val ASSET_DIR = "packs"
        const val TAG = "RoadMemory"
    }
}
