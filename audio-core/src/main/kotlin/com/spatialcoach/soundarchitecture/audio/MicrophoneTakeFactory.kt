package com.spatialcoach.soundarchitecture.audio

/**
 * 把麦克风单音采集到的 PCM 收成一次可对齐的演奏，并保留原波形供「实际」试听。
 * 分析与用户 WAV 导入共用 [PassageAnalyzer]，来源标记为麦克风单音，不得标成预录文件。
 */
object MicrophoneTakeFactory {
    fun fromPcm(
        samples: FloatArray,
        sampleRate: Int,
        takeId: String,
        sourceName: String,
        referenceConfirmed: Boolean,
        reference: PracticeReference = PracticeFragments.REFERENCE_V1,
    ): LiveCaptureResult {
        val rejected = UserTakeFactory.bindRejected(referenceConfirmed, reference)
        if (rejected != null) return LiveCaptureResult.Failed(rejected)
        if (samples.isEmpty() || sampleRate <= 0) {
            return LiveCaptureResult.Failed(EvaluationPolicy.MIC_NO_AUDIO)
        }
        val durationMs = StrictUserWav.durationMs(samples.size, sampleRate)
        if (durationMs <= 0L) {
            return LiveCaptureResult.Failed(EvaluationPolicy.MIC_NO_AUDIO)
        }
        val report = PassageAnalyzer.analyze(samples, sampleRate)
        val audio = PcmClip(
            samples = samples,
            sampleRate = sampleRate,
            label = "麦克风单音原音频（$sourceName）",
            sourceName = sourceName,
        )
        val take = PracticeFragments.buildTake(
            takeId = takeId,
            events = report.events,
            reference = reference,
            rendition = null,
            source = InputSource.MICROPHONE_MONO,
            referenceConfirmed = true,
            sourceName = sourceName,
            audioDurationMs = durationMs,
            timeWindowStartMs = 0L,
            timeWindowEndMs = durationMs,
            segmentation = report.diagnostics,
            audio = audio,
        )
        return LiveCaptureResult.Ok(take)
    }
}
