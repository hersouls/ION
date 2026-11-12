package app.roadmemory.car.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.roadmemory.car.data.Tracks
import app.roadmemory.car.data.TrackPlayer

/**
 * 디버그 전용: 화면 없이 번들 곡 재생 경로(MediaPlayer · 오디오 포커스)만 시험해요.
 *   adb shell am broadcast -a app.roadmemory.car.DEBUG_PLAY -p app.roadmemory.car            # 아이가 고른 곡 순서대로
 *   adb shell am broadcast -a app.roadmemory.car.DEBUG_PLAY -p app.roadmemory.car --ez stop true
 *   adb shell am broadcast -a app.roadmemory.car.DEBUG_PLAY -p app.roadmemory.car --es other duck|transient|loss|release
 *     (다른 앱이 포커스를 가져가는 일을 흉내 내요: 소리 줄임 · 잠시 멈춤 · 영영 가져감, release = 돌려줌)
 * 곡이 바뀔 때마다 `adb logcat -s RoadMemory.Debug`에 한 줄 나와요.
 */
class PlayDebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.getBooleanExtra("stop", false)) { player?.stop(); Log.d(TAG, "stopped"); return }
        intent.getStringExtra("other")?.let { simulateOtherApp(app, it); return }
        val p = player ?: TrackPlayer(app) { t -> Log.d(TAG, "now playing: ${t?.title ?: "(none)"}") }.also { player = it }
        p.play(Tracks.all)
        Log.d(TAG, "play queue=${Tracks.all.size}")
    }

    private fun simulateOtherApp(context: Context, kind: String) {
        val audio = context.getSystemService(AudioManager::class.java)
        if (kind == "release") {
            other?.let { audio.abandonAudioFocusRequest(it) }; other = null
            Handler(Looper.getMainLooper()).postDelayed({ Log.d(TAG, "other released; sounding=${player?.isSounding}") }, 300)
            return
        }
        val gain = when (kind) {
            "duck" -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            "transient" -> AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            else -> AudioManager.AUDIOFOCUS_GAIN
        }
        val req = AudioFocusRequest.Builder(gain)
            // 차(AAOS)에서는 음악끼리는 멈춤이고, 길 안내 음성처럼 다른 종류의 소리가 올 때만 줄여요
            .setAudioAttributes(AudioAttributes.Builder().setUsage(if (kind == "duck") AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE else AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { }
            .build()
        other?.let { audio.abandonAudioFocusRequest(it) }
        other = req
        val r = audio.requestAudioFocus(req)
        Handler(Looper.getMainLooper()).postDelayed({ Log.d(TAG, "other=$kind granted=${r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED} playing=${player?.isPlaying} sounding=${player?.isSounding}") }, 300)
    }

    private companion object {
        const val TAG = "RoadMemory.Debug"
        var player: TrackPlayer? = null
        var other: AudioFocusRequest? = null
    }
}
