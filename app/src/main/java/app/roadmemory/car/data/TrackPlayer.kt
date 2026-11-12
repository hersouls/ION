package app.roadmemory.car.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.util.Log

/**
 * 번들 연주곡을 차례로 재생해요. 오디오 포커스를 받고(USAGE_MEDIA) 가이드의 포커스 정책을 따라요:
 *  GAIN → 재생(멈춰 있었으면 이어서, 줄였으면 원래 크기로) · LOSS → 멈추고 포커스 반환 ·
 *  LOSS_TRANSIENT → 잠시 멈췄다가 GAIN이 오면 이어서 · LOSS_TRANSIENT_CAN_DUCK → 소리만 줄임.
 * 앱이 화면에서 사라져도(백그라운드) 재생은 포커스 정책을 따라요. 영상 · 게임이 아니라 음악이라 주행 중에도 재생할 수 있어요.
 * 곡이 바뀔 때마다 [onChange]로 알려요(null = 멈춤). 메인 스레드에서만 써요.
 *
 * 차의 미디어 위젯 · 하드 키가 ION이 트는 곡을 알아보도록 MediaSession에 곡 이름과 상태를 올려요(▶ · ◀◀ · ▶▶| 가 이 플레이어로 와요).
 * 시스템의 "일시정지"는 멈춤으로 받아요: 다시 ▶를 누르면 멈춘 곡부터 처음부터 틀어요.
 */
/** 재생 화면이 읽는 창구: 지금 곡의 지난 시간 · 길이와 이어질 곡. 플레이어가 없으면(시험 · 미리 보기) 모두 비어 있어요. */
object Playback {
    @Volatile var probe: () -> Pair<Int, Int>? = { null }
    @Volatile var next: () -> List<String> = { emptyList() }
}

class TrackPlayer(private val context: Context, private val onChange: (Tracks.Track?) -> Unit) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { change -> onFocusChange(change) }
        .build()

    private var player: MediaPlayer? = null
    private var queue: List<Tracks.Track> = emptyList()
    private var index = 0

    /** 멈춘 뒤 시스템의 ▶가 다시 틀 자리(마지막으로 튼 줄과 곡). */
    private var lastQueue: List<Tracks.Track> = emptyList()
    private var lastIndex = 0

    private val session = MediaSession(context.applicationContext, "ION 디지털 뮤직").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() { if (lastQueue.isNotEmpty()) { queue = lastQueue; index = lastIndex.coerceIn(0, lastQueue.size - 1); startPlaying() } }
            override fun onPause() = stop()
            override fun onStop() = stop()
            override fun onSkipToNext() = next()
            override fun onSkipToPrevious() = previous()
        })
    }

    /** 다른 소리가 잠시 포커스를 가져가서 멈춘 상태. GAIN이 오면 이어서 재생해요. */
    private var resumeOnGain = false

    val isPlaying: Boolean get() = player != null

    /** 지금 곡의 지난 시간 · 전체 길이(ms). 틀지 않거나 준비 중이면 null. */
    val progress: Pair<Int, Int>? get() = runCatching { player?.takeIf { it.duration > 0 }?.let { it.currentPosition to it.duration } }.getOrNull()

    /** 지금 곡 뒤에 이어질 곡의 이름(없으면 빈 목록). */
    val upNext: List<String> get() = queue.drop(index + 1).map { it.title }

    init { Playback.probe = { progress }; Playback.next = { upNext } }

    /** 지금 소리가 나는지(잠시 멈춤 · 준비 중이면 false). 시험용이에요. */
    val isSounding: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    private fun onFocusChange(change: Int) {
        Log.d(TAG, "focus change=$change")
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                player?.setVolume(1f, 1f)
                if (resumeOnGain) { resumeOnGain = false; runCatching { player?.start() } }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(DUCK_VOLUME, DUCK_VOLUME)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnGain = true
                runCatching { if (player?.isPlaying == true) player?.pause() }
            }
            AudioManager.AUDIOFOCUS_LOSS -> stop()
        }
    }

    fun play(tracks: List<Tracks.Track>) {
        stop()
        if (tracks.isEmpty()) return
        queue = tracks; index = 0
        startPlaying()
    }

    /** 포커스를 받고 queue의 index번째 곡부터 틀어요(받지 못하면 틀지 않아요). */
    private fun startPlaying() {
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "audio focus not granted"); queue = emptyList(); return
        }
        startCurrent()
    }

    /** 다음 곡(줄의 끝이면 멈춰요). */
    fun next() {
        if (queue.isEmpty()) return
        index++
        startCurrent()
    }

    /** 앞 곡. 곡을 튼 지 3초가 넘었거나 첫 곡이면 이 곡을 처음부터 다시 틀어요. */
    fun previous() {
        if (queue.isEmpty()) return
        val played = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
        if (played <= RESTART_AFTER_MS && index > 0) index--
        startCurrent()
    }

    fun stop() {
        release()
        resumeOnGain = false
        queue = emptyList(); index = 0
        runCatching { audio.abandonAudioFocusRequest(focus) }
        publish(null)
        onChange(null)
    }

    /** 앱이 끝날 때: 멈추고 세션도 놓아요. */
    fun shutdown() {
        stop()
        runCatching { session.release() }
    }

    /** 차의 미디어 위젯에 곡 이름 · 상태를 알려요(null이면 세션을 쉬게 해요). */
    private fun publish(track: Tracks.Track?, durationMs: Long = 0L) {
        runCatching {
            if (track == null) {
                session.setPlaybackState(PlaybackState.Builder().setActions(0L).setState(PlaybackState.STATE_STOPPED, 0L, 0f).build())
                session.isActive = false
                return
            }
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "ION · 디지털 뮤직")
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, "ION")
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                    .build(),
            )
            session.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                    .build(),
            )
            session.isActive = true
        }.onFailure { Log.w(TAG, "media session: ${it.javaClass.simpleName}") }
    }

    private fun startCurrent() {
        val track = queue.getOrNull(index) ?: return stop()
        lastQueue = queue; lastIndex = index
        release()
        runCatching {
            context.resources.openRawResourceFd(track.res).use { afd ->
                player = MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    // 준비되는 사이에 포커스를 잠시 잃었다면 GAIN이 올 때까지 기다려요
                    setOnPreparedListener { if (!resumeOnGain) it.start(); publish(track, it.duration.toLong()); onChange(track) }
                    setOnCompletionListener { index++; startCurrent() }
                    setOnErrorListener { _, what, extra -> Log.w(TAG, "player error $what/$extra"); stop(); true }
                    prepareAsync()
                }
            }
        }.onFailure { Log.w(TAG, "start failed: ${it.javaClass.simpleName}"); stop() }
    }

    private fun release() {
        player?.let { p -> runCatching { p.setOnCompletionListener(null); p.stop() }; runCatching { p.release() } }
        player = null
    }

    private companion object {
        const val TAG = "RoadMemory"
        const val DUCK_VOLUME = 0.3f
        const val RESTART_AFTER_MS = 3_000
    }
}
