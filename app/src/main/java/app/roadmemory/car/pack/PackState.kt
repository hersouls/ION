package app.roadmemory.car.pack

/** 화면에 잠깐 보이는 한 순간: ION의 문장(차가 준 값으로 채운 글)과 목소리 자막. */
data class MomentNote(
    val moment: String,
    val label: String,
    val mode: MomentMode,
    /** ION이 쓰는 문장. 값을 몰라 만들 수 없으면 null(그때는 자막만 보여요). */
    val info: String?,
    val speaker: String,
    val line: String,
    val packTitle: String,
    val synthetic: Boolean,
    val at: Long,
)

/** 설정 · 데이터 화면이 보여 주는 팩 한 개. */
data class PackInfo(
    val id: String,
    val title: String,
    val speaker: String,
    val viral: String,
    val synthetic: Boolean,
    val source: PackSource,
    val moments: List<String>,
) {
    companion object {
        fun of(p: InstalledPack) = PackInfo(p.id, p.file.title, p.file.speakerName, p.file.viral, p.file.synthetic, p.source, p.file.moments.map { it.moment })
    }
}
