package app.roadmemory.car.pack

import org.json.JSONObject

/** 팩 안의 한 순간: 그 순간에 틀 목소리 파일과, 목소리가 하는 말(자막으로 그대로 보여요). */
data class PackMoment(val moment: String, val line: String, val file: String, val ms: Int)

/**
 * 시그니처 송 팩 한 개(`pack.json`, 형식 `ion-pack/1`). 스토어에서 산 목소리 묶음이에요: 제작자(선수 · 연예인)의 한마디가 순간마다 하나씩 들어 있어요.
 * 약속은 ION Studio의 docs/PACK_FORMAT.md예요. 앱은 `moments`에 없는 순간에는 아무것도 하지 않아요.
 */
data class PackFile(
    val id: String,
    val title: String,
    /** 목소리 이름(자막 앞에 붙어요). 비어 있으면 팩 제목을 써요. */
    val speaker: String,
    /** 바탕이 된 바이럴 영상 이름(없으면 빈 글). */
    val viral: String,
    /** `synthetic` = 합성 음성(시연) · `recorded` = 본인 녹음. */
    val voice: String,
    val cover: String?,
    val moments: List<PackMoment>,
) {
    val synthetic: Boolean get() = voice == SYNTHETIC
    val speakerName: String get() = speaker.ifBlank { title }
    fun momentOf(id: String): PackMoment? = moments.firstOrNull { it.moment == id }

    companion object {
        const val FORMAT = "ion-pack/1"
        const val SYNTHETIC = "synthetic"
        const val RECORDED = "recorded"

        /** `pack.json` 글에서 읽어요. 형식이 다르거나 id · 순간이 없으면 null(깨진 팩은 목록에 올리지 않아요). 파일이 빠진 순간은 건너뛰어요. */
        fun parse(json: String): PackFile? = runCatching {
            val o = JSONObject(json)
            if (o.optString("format") != FORMAT) return null
            val id = o.optString("id").takeIf { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' } } ?: return null
            val arr = o.optJSONArray("moments") ?: return null
            val list = (0 until arr.length()).mapNotNull { i ->
                val m = arr.optJSONObject(i) ?: return@mapNotNull null
                val moment = m.optString("moment"); val file = m.optString("file")
                // 파일 이름에 경로가 들어 있으면 폴더 밖을 가리킬 수 있어서 받지 않아요
                if (moment.isBlank() || file.isBlank() || '/' in file || '\\' in file || ".." in file) return@mapNotNull null
                PackMoment(moment, m.optString("line").trim(), file, m.optInt("ms", 0).coerceAtLeast(0))
            }.distinctBy { it.moment }
            if (list.isEmpty()) return null
            PackFile(
                id, o.optString("title").ifBlank { id }, o.optString("speaker").trim(), o.optString("viral").trim(),
                o.optString("voice", SYNTHETIC).ifBlank { SYNTHETIC }, o.optString("cover").takeIf { it.isNotBlank() && '/' !in it && ".." !in it }, list,
            )
        }.getOrNull()
    }
}
