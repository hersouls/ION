package app.roadmemory.car.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.roadmemory.car.gleo.GleoBridge
import app.roadmemory.car.voice.PleosVoice
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 디버그 전용: 에뮬레이터는 마이크 없이(-no-audio) 떠 있어서, 미리 만든 음성 파일을 음성 인식 엔진에 직접 넣어 봐요.
 *   adb shell run-as app.roadmemory.car cp /data/local/tmp/say.wav files/say.wav
 *   adb shell am broadcast -a app.roadmemory.car.DEBUG_SPEECH --es file say.wav
 * 말하기 버튼을 누른 것처럼 듣기를 시작하고, 1.5초 뒤부터 파일(16kHz 16bit 모노 WAV)을 실시간 속도로 흘려 넣어요.
 */
class SpeechFeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "DEBUG_SPEECH received file=${intent.getStringExtra("file")} controller=${GleoBridge.controller != null}")
        val file = File(context.filesDir, intent.getStringExtra("file") ?: run { Log.w(TAG, "file 값이 없어요"); return })
        val ion = GleoBridge.controller?.ion ?: run { Log.w(TAG, "앱 화면이 아직 없어요(controller 없음)"); return }
        val voice = ion.voiceInput as? PleosVoice ?: run { Log.w(TAG, "차의 음성 인식이 아니에요: ${ion.voiceInput?.kind}"); return }
        CoroutineScope(Dispatchers.Default).launch {
            val wav = file.readBytes()
            val pcm = if (wav.size > 44 && String(wav, 0, 4) == "RIFF") wav.copyOfRange(44, wav.size) else wav
            Log.d(TAG, "feed ${pcm.size} bytes (${pcm.size / 32000.0}s)")
            withContext(Dispatchers.Main) { ion.onMic() }
            delay(1500)
            pcm.asList().chunked(3200).forEach { chunk -> voice.feed(chunk.toByteArray()); delay(100) }   // 100ms씩
            voice.completeFeed()
            Log.d(TAG, "feed done")
        }
    }

    private companion object { const val TAG = "RoadMemory.Debug" }
}
