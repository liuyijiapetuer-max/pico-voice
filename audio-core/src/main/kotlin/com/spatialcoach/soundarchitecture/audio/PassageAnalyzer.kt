package com.spatialcoach.soundarchitecture.audio

/**
 * 用真实 [AnalysisEngine] 分析一段 PCM，再交给 [NoteSegmenter] 出事件。
 * 测试、练习会话和用户 WAV 导入共用这一条路径。每次调用新建引擎，等价于跨段 reset。
 */
object PassageAnalyzer {
    const val FFT_SIZE = 2048
    const val HOP_SIZE = 512

    fun frames(
        samples: FloatArray,
        sampleRate: Int = PracticeFragments.SAMPLE_RATE,
        fftSize: Int = FFT_SIZE,
        hopSize: Int = HOP_SIZE,
        engine: AnalysisEngine? = null,
    ): List<AnalysisFrame> {
        val used = engine ?: AnalysisEngine(sampleRate = sampleRate, fftSize = fftSize, hopSize = hopSize)
        return used.push(samples)
    }

    fun events(
        samples: FloatArray,
        sampleRate: Int = PracticeFragments.SAMPLE_RATE,
        segmenter: NoteSegmenter = NoteSegmenter(),
        engine: AnalysisEngine? = null,
    ): List<NoteEvent> = segmenter.segment(frames(samples, sampleRate, engine = engine))

    fun take(
        takeId: String,
        samples: FloatArray,
        sampleRate: Int = PracticeFragments.SAMPLE_RATE,
        reference: PracticeReference = PracticeFragments.REFERENCE_V1,
        rendition: PracticeFragments.Rendition? = null,
        source: InputSource = InputSource.FILE_SYNTHETIC,
        segmenter: NoteSegmenter = NoteSegmenter(),
        referenceConfirmed: Boolean? = null,
        sourceName: String? = null,
        audioDurationMs: Long? = StrictUserWav.durationMs(samples.size, sampleRate),
        timeWindowStartMs: Long? = 0L,
        timeWindowEndMs: Long? = StrictUserWav.durationMs(samples.size, sampleRate),
    ): PerformanceTake = PracticeFragments.buildTake(
        takeId = takeId,
        events = events(samples, sampleRate, segmenter),
        reference = reference,
        rendition = rendition,
        source = source,
        referenceConfirmed = referenceConfirmed,
        sourceName = sourceName,
        audioDurationMs = audioDurationMs,
        timeWindowStartMs = timeWindowStartMs,
        timeWindowEndMs = timeWindowEndMs,
    )

    fun takeFromRendition(
        rendition: PracticeFragments.Rendition,
        takeId: String = rendition.id,
    ): PerformanceTake = take(
        takeId = takeId,
        samples = PracticeFragments.render(rendition),
        rendition = rendition,
        source = InputSource.FILE_SYNTHETIC,
        sourceName = rendition.label,
    )
}
