package app.roadmemory.car.pack

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * 시그니처 송의 목소리 한 토막을 틀어요. 한 번에 하나만 나오고(새 토막이 오면 앞 것을 멈춰요), 오디오 포커스는 "잠깐 줄여도 돼요"로 받아서
 * 듣던 음악은 줄었다가 돌아와요(TrackPlayer와 같은 가이드의 포커스 정책. 차마다 음악을 줄이는 방식이 달라서 실차 확인이 필요해요).
 * 포커스를 영영 빼앗기면 멈춰요. 주행 중에는 [maxMs] 안에서 끊어요. 메인 스레드에서만 써요.
 * 단계마다 `adb logcat -s RoadMemory`에 한 줄 남겨요(파일 이름 · 길이만, 개인 정보 없음).
 */
class PackPlayer(context: Context) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { change -> if (change == AudioManager.AUDIOFOCUS_LOSS) stop() }
        .build()
    private var player: MediaPlayer? = null

    val isPlaying: Boolean get() = player != null

    /** 지금 소리가 나는 중인지(준비 중이면 false). 시험용이에요. */
    val isSounding: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /** 토막을 틀어요. 포커스를 못 받거나 열지 못하면 false(그때 화면의 글은 그대로 보여요). maxMs가 있으면 그 시간 안에서 끊어요. */
    fun play(file: File, maxMs: Long?, onDone: () -> Unit = {}): Boolean {
        stop()
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { Log.w(TAG, "목소리: 오디오 포커스를 받지 못했어요"); return false }
        return runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(file.path)
                setOnPreparedListener {
                    it.start()
                    Log.i(TAG, "목소리: 재생 시작 ${file.name} ${it.duration}ms")
                    // 이 토막이 아직 소리 나는 중일 때만 끊어요(끝난 뒤에 늦게 도는 일이 없게 이 핸들러의 예약은 stop에서 모두 지워요)
                    if (maxMs != null) { val mine = it; handler.postDelayed({ if (player === mine) { Log.i(TAG, "목소리 토막을 주행 시간 제한에서 끊어요"); stop() } }, maxMs) }
                }
                setOnCompletionListener { Log.i(TAG, "목소리: 재생 끝 ${file.name}"); stop(); onDone() }
                setOnErrorListener { _, what, extra -> Log.w(TAG, "목소리: 재생 오류 $what/$extra"); stop(); true }
                prepareAsync()
            }
            true
        }.getOrElse { Log.w(TAG, "목소리: 열지 못했어요 ${it.javaClass.simpleName}"); stop(); false }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.let { p -> runCatching { p.setOnCompletionListener(null); p.stop() }; runCatching { p.release() } }
        player = null
        runCatching { audio.abandonAudioFocusRequest(focus) }
    }

    private companion object { const val TAG = "RoadMemory" }
}
