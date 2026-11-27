package com.spatialcoach.soundarchitecture.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * 麦克风单音采集：使用 Android [AudioRecord]，不绑未验证的 SpatialML 麦 API。
 * 默认启动仍走 [FileDemoPlayer]。本类只在用户明确开始采集时调用，
 * 不能把「能打开录音接口」写成「已在头显上验证实时拾音」。
 * 权限失败必须明确提示，且不得改用未标注的预录冒充实时。
 */
object ReservedMicSource {
    const val STATUS = "麦克风单音采集已接到 Android AudioRecord。默认启动仍走文件回放。软件接线不是头显验收。"
    const val PERMISSION_DENIED = EvaluationPolicy.PERMISSION_DENIED
    const val DEVICE_MISSING = EvaluationPolicy.DEVICE_MISSING
    const val SAMPLE_RATE = 44_100
    const val MAX_DURATION_MS = 20_000L
}

class MonoMicrophoneCapture(
    private val onFrame: (AnalysisFrame) -> Unit,
    private val onAutoStop: () -> Unit = {},
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null
    private val collected = ArrayList<Float>()
    private var sampleRateUsed: Int = ReservedMicSource.SAMPLE_RATE

    val isRunning: Boolean get() = running.get()

    data class Recording(
        val samples: FloatArray,
        val sampleRate: Int,
    )

    /**
     * 打开 [AudioRecord] 并开始读入。失败返回原因，成功返回 null。
     * 调用方必须已经获得 [android.Manifest.permission.RECORD_AUDIO]。
     */
    @SuppressLint("MissingPermission")
    fun start(): String? {
        stopAndDiscard()
        val sampleRate = ReservedMicSource.SAMPLE_RATE
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE || minBuf <= 0) {
            return EvaluationPolicy.DEVICE_MISSING
        }
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf.coerceAtLeast(sampleRate / 5) * 2,
            )
        } catch (e: SecurityException) {
            return EvaluationPolicy.PERMISSION_DENIED
        } catch (e: Exception) {
            return EvaluationPolicy.DEVICE_MISSING
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { record.release() }
            return EvaluationPolicy.DEVICE_MISSING
        }
        if (!running.compareAndSet(false, true)) {
            runCatching { record.release() }
            return EvaluationPolicy.CAPTURE_BUSY
        }
        recorder = record
        sampleRateUsed = sampleRate
        collected.clear()
        val engine = AnalysisEngine(sampleRate = sampleRate, fftSize = PassageAnalyzer.FFT_SIZE, hopSize = PassageAnalyzer.HOP_SIZE)
        val hop = PassageAnalyzer.HOP_SIZE
        val shortBuf = ShortArray(hop)
        val maxSamples = ((sampleRate * ReservedMicSource.MAX_DURATION_MS) / 1000L).toInt()
        try {
            record.startRecording()
        } catch (e: SecurityException) {
            running.set(false)
            runCatching { record.release() }
            recorder = null
            return EvaluationPolicy.PERMISSION_DENIED
        } catch (e: Exception) {
            running.set(false)
            runCatching { record.release() }
            recorder = null
            return EvaluationPolicy.DEVICE_MISSING
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            running.set(false)
            runCatching { record.stop() }
            runCatching { record.release() }
            recorder = null
            return EvaluationPolicy.DEVICE_MISSING
        }
        worker = thread(name = "mono-mic-capture", isDaemon = true) {
            try {
                while (running.get() && collected.size < maxSamples) {
                    val n = record.read(shortBuf, 0, hop)
                    if (n <= 0) {
                        if (n == AudioRecord.ERROR_INVALID_OPERATION || n == AudioRecord.ERROR_BAD_VALUE) break
                        continue
                    }
                    val slice = FloatArray(n)
                    for (i in 0 until n) {
                        val s = shortBuf[i] / 32768f
                        slice[i] = s
                    }
                    synchronized(collected) {
                        for (i in 0 until n) {
                            if (collected.size < maxSamples) collected += slice[i]
                        }
                    }
                    val frames = engine.push(slice)
                    if (frames.isNotEmpty()) {
                        val latest = frames.last()
                        mainHandler.post { onFrame(latest) }
                    }
                }
                val hitMax = collected.size >= maxSamples
                if (hitMax && running.get()) {
                    mainHandler.post { onAutoStop() }
                }
            } finally {
                runCatching { record.stop() }
                runCatching { record.release() }
                if (recorder === record) recorder = null
                running.set(false)
            }
        }
        return null
    }

    fun stop(): Recording? {
        running.set(false)
        worker?.join(1000)
        worker = null
        val existing = recorder
        if (existing != null) {
            runCatching { existing.stop() }
            runCatching { existing.release() }
            recorder = null
        }
        val samples = synchronized(collected) { collected.toFloatArray() }
        collected.clear()
        if (samples.isEmpty()) return null
        return Recording(samples, sampleRateUsed)
    }

    fun stopAndDiscard() {
        stop()
    }
}
