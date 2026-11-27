package com.spatialcoach.soundarchitecture.audio

enum class LiveInputKind {
    MIDI_EVENTS,
    MICROPHONE_MONO,
    ;

    val source: InputSource
        get() = when (this) {
            MIDI_EVENTS -> InputSource.MIDI_EVENTS
            MICROPHONE_MONO -> InputSource.MICROPHONE_MONO
        }
}

enum class LiveCapturePhase {
    IDLE,
    RECORDING,
    FAILED,
    COMPLETED,
}

data class LiveCaptureState(
    val phase: LiveCapturePhase = LiveCapturePhase.IDLE,
    val kind: LiveInputKind? = null,
    val status: String = "尚未开始实时采集。默认启动仍是文件回放。",
    val take: PerformanceTake? = null,
    val lastFailure: String? = null,
)

sealed class LiveCaptureResult {
    data class Ok(val take: PerformanceTake) : LiveCaptureResult()
    data class Failed(val reason: String) : LiveCaptureResult()
}

/**
 * 实时采集的纯 JVM 状态门：权限失败、设备缺失必须明确，且不得悄悄改用未标明的预录。
 * 成功结果也不写入用户导入槽。
 */
class LiveCaptureCoordinator {
    var state: LiveCaptureState = LiveCaptureState()
        private set

    val isRecording: Boolean get() = state.phase == LiveCapturePhase.RECORDING

    fun begin(
        kind: LiveInputKind,
        permissionGranted: Boolean,
        deviceAvailable: Boolean,
    ): Boolean {
        if (isRecording) {
            state = state.copy(status = EvaluationPolicy.CAPTURE_BUSY)
            return false
        }
        if (kind == LiveInputKind.MICROPHONE_MONO && !permissionGranted) {
            fail(kind, EvaluationPolicy.PERMISSION_DENIED)
            return false
        }
        if (!deviceAvailable) {
            fail(kind, EvaluationPolicy.DEVICE_MISSING)
            return false
        }
        val starting = when (kind) {
            LiveInputKind.MICROPHONE_MONO ->
                "正在进行麦克风单音采集。采集从按下开始计时，原点为 0 ms。${EvaluationPolicy.SOFTWARE_WIRING_NOTE} ${EvaluationPolicy.LIVE_CAPTURE_NOT_USER_SLOT}"
            LiveInputKind.MIDI_EVENTS ->
                "正在进行 MIDI 采集（USB 或系统已枚举的蓝牙 MIDI）。采集从按下开始计时，原点为 0 ms，只核对准键与时间。${EvaluationPolicy.SOFTWARE_WIRING_NOTE} ${EvaluationPolicy.LIVE_CAPTURE_NOT_USER_SLOT}"
        }
        state = LiveCaptureState(
            phase = LiveCapturePhase.RECORDING,
            kind = kind,
            status = starting,
            take = null,
            lastFailure = null,
        )
        return true
    }

    fun complete(result: LiveCaptureResult): Boolean {
        if (!isRecording) return false
        val kind = state.kind
        return when (result) {
            is LiveCaptureResult.Ok -> {
                if (kind != null && result.take.source != kind.source) {
                    fail(kind, "采集结果来源与当前路线不一致，已拒绝写入。未改用未标明的预录冒充实时。")
                    return false
                }
                state = LiveCaptureState(
                    phase = LiveCapturePhase.COMPLETED,
                    kind = kind,
                    status = "已完成${kindLabel(kind)}：${result.take.source.label}。稳定音 ${result.take.events.count { it.stable }} 个。${EvaluationPolicy.SOFTWARE_WIRING_NOTE} ${EvaluationPolicy.LIVE_CAPTURE_NOT_USER_SLOT}",
                    take = result.take,
                    lastFailure = null,
                )
                true
            }
            is LiveCaptureResult.Failed -> {
                fail(kind, result.reason)
                true
            }
        }
    }

    fun cancel(reason: String = EvaluationPolicy.CAPTURE_CANCELLED) {
        if (!isRecording) return
        state = LiveCaptureState(
            phase = LiveCapturePhase.FAILED,
            kind = state.kind,
            status = reason,
            take = null,
            lastFailure = reason,
        )
    }

    fun fail(kind: LiveInputKind?, reason: String) {
        state = LiveCaptureState(
            phase = LiveCapturePhase.FAILED,
            kind = kind,
            status = reason,
            take = null,
            lastFailure = reason,
        )
    }

    private fun kindLabel(kind: LiveInputKind?): String = when (kind) {
        LiveInputKind.MICROPHONE_MONO -> "麦克风单音采集"
        LiveInputKind.MIDI_EVENTS -> "MIDI 采集"
        null -> "实时采集"
    }
}
