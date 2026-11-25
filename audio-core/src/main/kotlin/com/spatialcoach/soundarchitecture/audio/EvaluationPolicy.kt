package com.spatialcoach.soundarchitecture.audio

/**
 * 首期评价口径。MIDI 只能核对准键音符及时间；麦克风单音模式才评价声学音准。
 * 文件导入与内置合成回放可以分析录音音准，但必须标明不是实时采集。
 * 本对象不声称已接上经头显验收的 MIDI 设备或真麦。
 */
object EvaluationPolicy {
    const val LOW_CONFIDENCE_THRESHOLD = 0.55
    const val UNKNOWN_LABEL = "未知"

    const val MIDI_SCOPE =
        "MIDI 只核对准键音符及时间，不评价声学音准，也不发布音分作为音准结论。"
    const val MIC_SCOPE =
        "麦克风单音模式才评价声学音准。当前默认启动链路未接入真麦，不能写成已在头显上验证的实时拾音。"
    const val FILE_SCOPE =
        "录音或合成文件可以分析音准与节奏，但必须标明这是预录/文件回放，不是实时采集。"
    const val PERMISSION_DENIED =
        "录音权限未授予，无法开始麦克风单音采集。未改写已有导入，也没有改用未标明的预录冒充实时。"
    const val DEVICE_MISSING =
        "未检测到可用的 MIDI 设备或麦克风。请检查连接后重试，或改用已确认的文件导入。"
    const val IMPORT_FAILED_PREFIX = "导入失败："
    const val LOW_CONFIDENCE_HINT =
        "该音置信度过低，构件改为虚线，并标记为「未知」，不发布确定音分。"
    const val PRERECORDED_BANNER = "预录音 / 文件回放（不是实时采集）"
    const val LIVE_MIC_BANNER = "麦克风单音（仅在实际采集时使用此标注）"
    const val MIDI_BANNER = "MIDI 事件（只核对准键与时间）"
    const val UNANCHORED_PLACEMENT =
        "默认桌面尺度约 1.2 米宽，位置是未锚定的默认摆放，禁止遮挡真实琴键。尚未完成对实体乐器的真机锚定。"
    const val RAY_DEFAULT =
        "交互默认使用控制器射线。手势跟随仅作加分项，尚未通过稳定性测试，不能写成已验收。"
    const val DEMO_LOOP_NOTE =
        "90 秒体验是竞赛演示提纲，不是已完成的用户对照实验。若使用预录音，画面必须明确标注，不得冒充实时。"

    fun canEvaluateAcousticIntonation(source: InputSource): Boolean = when (source) {
        InputSource.MICROPHONE_MONO, InputSource.USER_WAV_FILE, InputSource.FILE_SYNTHETIC -> true
        InputSource.MIDI_EVENTS -> false
    }

    fun canEvaluateNoteIdentityAndTiming(source: InputSource): Boolean = true

    fun isPrerecorded(source: InputSource): Boolean = when (source) {
        InputSource.FILE_SYNTHETIC, InputSource.USER_WAV_FILE -> true
        InputSource.MIDI_EVENTS, InputSource.MICROPHONE_MONO -> false
    }

    fun sourceBanner(source: InputSource): String = when (source) {
        InputSource.FILE_SYNTHETIC, InputSource.USER_WAV_FILE -> PRERECORDED_BANNER
        InputSource.MIDI_EVENTS -> MIDI_BANNER
        InputSource.MICROPHONE_MONO -> LIVE_MIC_BANNER
    }

    fun scopeLine(source: InputSource): String = when (source) {
        InputSource.MIDI_EVENTS -> MIDI_SCOPE
        InputSource.MICROPHONE_MONO -> MIC_SCOPE
        InputSource.FILE_SYNTHETIC, InputSource.USER_WAV_FILE -> FILE_SCOPE
    }

    fun isLowConfidence(event: NoteEvent?): Boolean {
        if (event == null) return false
        val confidence = event.pitchConfidence
        return !event.stable || (confidence != null && confidence < LOW_CONFIDENCE_THRESHOLD)
    }

    /**
     * 可发布的相对谱面音分。MIDI 不评价声学音准；低置信度不发布确定音分。
     */
    fun publishedCentsVsTarget(source: InputSource, event: NoteEvent?, centsVsTarget: Double?): Double? {
        if (!canEvaluateAcousticIntonation(source)) return null
        if (isLowConfidence(event)) return null
        return centsVsTarget
    }

    fun publishedTimingMs(source: InputSource, event: NoteEvent?, timingErrorMs: Long?): Long? {
        if (!canEvaluateNoteIdentityAndTiming(source)) return null
        if (isLowConfidence(event)) return null
        return timingErrorMs
    }

    fun importFailureMessage(reason: String): String =
        "$IMPORT_FAILED_PREFIX$reason 未改写已有的第一遍或重奏，也没有回退到合成示范，更不会把预录标成实时。"
}
