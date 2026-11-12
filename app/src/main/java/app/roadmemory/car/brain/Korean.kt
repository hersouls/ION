package app.roadmemory.car.brain

/** 조사 붙이기: 받침이 있으면 "이에요 · 으로 · 을 · 은", 없으면 "예요 · 로 · 를 · 는". 한글이 아니면 괄호로 둘 다 쓰고, "예요"만은 짧은 쪽으로 붙여요(영문 곡 · 테마 이름). */
object Korean {
    private fun last(word: String): Char? = word.trimEnd().lastOrNull { it != ')' && it != '"' && it != '\'' }
    private fun batchim(c: Char): Int? = if (c in '가'..'힣') (c - '가') % 28 else null

    fun ieyo(word: String): String = when (last(word)?.let(::batchim)) { null -> "${word}예요"; 0 -> "${word}예요"; else -> "${word}이에요" }
    fun euro(word: String): String = when (val b = last(word)?.let(::batchim)) { null -> "$word(으)로"; 0, 8 -> "${word}로"; else -> "${word}으로" }
    fun eul(word: String): String = when (last(word)?.let(::batchim)) { null -> "$word(을)를"; 0 -> "${word}를"; else -> "${word}을" }
    fun iga(word: String): String = when (last(word)?.let(::batchim)) { null -> "$word(이)가"; 0 -> "${word}가"; else -> "${word}이" }
    /** "…과 · …와": 받침이 있으면 "과", 없으면 "와"(한글이 아니면 "(과)와"). */
    fun gwa(word: String): String = when (last(word)?.let(::batchim)) { null -> "$word(과)와"; 0 -> "${word}와"; else -> "${word}과" }
    fun eun(word: String): String = when (last(word)?.let(::batchim)) { null -> "$word(은)는"; 0 -> "${word}는"; else -> "${word}은" }
}
