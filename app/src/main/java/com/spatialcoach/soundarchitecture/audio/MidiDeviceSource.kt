package com.spatialcoach.soundarchitecture.audio

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 标准 MIDI 输入：经 [MidiManager] 打开 USB 或系统已枚举的蓝牙 MIDI 设备。
 * 不做自行扫描或未验证的空间音频 API。设备缺失必须明确提示，不得用合成音色冒充实际演奏。
 */
class MidiDeviceSource(
    context: Context,
    private val onAutoStop: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable {
        if (running.get()) onAutoStop()
    }
    private val running = AtomicBoolean(false)
    private var manager: MidiManager? = null
    private var device: MidiDevice? = null
    private var port: MidiOutputPort? = null
    private var sessionStartMs = 0L
    private val messages = CopyOnWriteArrayList<TimedMidiMessage>()

    val isRunning: Boolean get() = running.get()

    fun availableDevices(): List<MidiDeviceInfo> {
        val midi = appContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return emptyList()
        return midi.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM)
            .filter { it.outputPortCount > 0 }
    }

    /**
     * 打开第一台带输出端口的 MIDI 设备并开始收事件。失败返回原因，成功返回 null。
     * 时间原点是开始采集的时刻，开头静音不会被裁掉。
     */
    fun start(): String? {
        stopAndDiscard()
        val midi = appContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager
            ?: return EvaluationPolicy.DEVICE_MISSING
        manager = midi
        val info = availableDevices().firstOrNull() ?: return EvaluationPolicy.DEVICE_MISSING
        if (!running.compareAndSet(false, true)) return EvaluationPolicy.CAPTURE_BUSY
        sessionStartMs = SystemClock.elapsedRealtime()
        messages.clear()
        val opened = CountDownLatch(1)
        var openError: String? = EvaluationPolicy.DEVICE_MISSING
        try {
            midi.openDevice(
                info,
                { openedDevice ->
                    if (openedDevice == null) {
                        openError = EvaluationPolicy.DEVICE_MISSING
                        running.set(false)
                        opened.countDown()
                        return@openDevice
                    }
                    val outPort = openedDevice.openOutputPort(0)
                    if (outPort == null) {
                        runCatching { openedDevice.close() }
                        openError = EvaluationPolicy.DEVICE_MISSING
                        running.set(false)
                        opened.countDown()
                        return@openDevice
                    }
                    device = openedDevice
                    port = outPort
                    outPort.connect(object : MidiReceiver() {
                        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                            if (!running.get() || count <= 0) return
                            val timeMs = (SystemClock.elapsedRealtime() - sessionStartMs).coerceAtLeast(0L)
                            val copy = msg.copyOfRange(offset, offset + count)
                            messages += TimedMidiMessage(timeMs, copy)
                        }
                    })
                    openError = null
                    opened.countDown()
                },
                mainHandler,
            )
        } catch (e: Exception) {
            running.set(false)
            return EvaluationPolicy.DEVICE_MISSING
        }
        val ok = opened.await(2, TimeUnit.SECONDS)
        if (!ok || openError != null || !running.get()) {
            stopAndDiscard()
            return openError ?: EvaluationPolicy.DEVICE_MISSING
        }
        mainHandler.postDelayed(autoStop, ReservedMicSource.MAX_DURATION_MS)
        return null
    }

    fun snapshot(): List<TimedMidiMessage> = messages.toList()

    fun stop(): Pair<List<TimedMidiMessage>, Long> {
        val endMs = (SystemClock.elapsedRealtime() - sessionStartMs).coerceAtLeast(0L)
        val captured = messages.toList()
        closeHardware()
        messages.clear()
        return captured to endMs
    }

    fun stopAndDiscard() {
        closeHardware()
        messages.clear()
    }

    private fun closeHardware() {
        mainHandler.removeCallbacks(autoStop)
        running.set(false)
        val outPort = port
        port = null
        if (outPort != null) {
            runCatching { outPort.close() }
        }
        val opened = device
        device = null
        if (opened != null) runCatching { opened.close() }
    }
}
