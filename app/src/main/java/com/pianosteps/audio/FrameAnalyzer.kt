package com.pianosteps.audio

/**
 * Turns raw audio into [AnalysisFrame]s: RMS level, pitch (YIN), octave folding into the lesson
 * range, tuning-offset correction and loudness gating, all using the stored [Calibration].
 */
class FrameAnalyzer(
    sampleRate: Int = 44100,
    private val calibration: () -> Calibration = { Calibration() },
    /** Candidate pitches are folded into this MIDI range (default B2..C6, i.e. the lesson octaves). */
    private val preferredLo: Double = Notes.C3 - 0.5,
    private val preferredHi: Double = Notes.C6 + 0.5,
) {
    val detector = PitchDetector(sampleRate)
    val requiredSamples get() = detector.requiredSamples

    /** Analyse the most recent [requiredSamples] samples in [buf] (oldest first). */
    fun analyze(buf: FloatArray, timeMs: Long): AnalysisFrame {
        val cal = calibration()
        val n = detector.windowSize
        val rms = PitchDetector.rms(buf, buf.size - n, buf.size)
        if (rms < cal.gateRms) return AnalysisFrame(timeMs, rms, null, null, 1.0, false)
        val p = detector.detect(buf, buf.size - detector.requiredSamples, cal.maxCmndf)
            ?: return AnalysisFrame(timeMs, rms, null, null, 1.0, false)
        val raw = Notes.foldIntoRange(Notes.freqToMidi(p.frequency), preferredLo, preferredHi)
        val corrected = raw - cal.centsOffset / 100.0
        val voiced = rms >= cal.thresholdFor(corrected)
        return AnalysisFrame(timeMs, rms, if (voiced) corrected else null, raw, p.cmndf, voiced)
    }
}
