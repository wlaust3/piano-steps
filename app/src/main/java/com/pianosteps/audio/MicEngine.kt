package com.pianosteps.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.max

class MicUnavailableException(message: String) : Exception(message)

/**
 * Cold flow of [AnalysisFrame]s from the device microphone (AudioRecord, 44.1 kHz mono, a new
 * frame every 1024 samples ~ 23 ms over a 2048-sample sliding window). The recorder is created when
 * collection starts and released when it is cancelled. Frames produced while [muteGate] is closed
 * (our own demo tone) are emitted as silence.
 */
class MicEngine(private val muteGate: MuteGate) {
    private val sampleRate = 44100
    private val hop = 1024

    @SuppressLint("MissingPermission") // permission is requested by the UI before collecting
    fun frames(calibration: () -> Calibration): Flow<AnalysisFrame> = flow {
        val analyzer = FrameAnalyzer(sampleRate, calibration)
        val window = max(analyzer.requiredSamples, 2048)
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) throw MicUnavailableException("No microphone input available")
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, max(minBuf, hop * 8 * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw MicUnavailableException("Could not open the microphone")
        }
        try {
            record.startRecording()
            val shorts = ShortArray(hop)
            val ring = FloatArray(window)
            var filled = 0
            while (currentCoroutineContext().isActive) {
                val n = record.read(shorts, 0, hop)
                if (n <= 0) {
                    if (n < 0) throw MicUnavailableException("Microphone read failed ($n)")
                    continue
                }
                // slide window
                System.arraycopy(ring, n, ring, 0, window - n)
                for (i in 0 until n) ring[window - n + i] = shorts[i] / 32768f
                filled = minOf(window, filled + n)
                if (filled < window) continue
                val now = SystemClock.elapsedRealtime()
                emit(if (muteGate.isMuted(now)) AnalysisFrame.silent(now) else analyzer.analyze(ring, now))
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.Default)
}
