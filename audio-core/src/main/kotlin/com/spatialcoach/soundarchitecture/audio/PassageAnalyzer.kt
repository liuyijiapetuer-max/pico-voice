package com.spatialcoach.soundarchitecture.audio

/**
 * 用真实 [AnalysisEngine] 分析一段 PCM，再交给 [NoteSegmenter] 出事件。
 * 测试和练习会话共用这一条路径，避免只对手造事件做比较。
 */
object PassageAnalyzer {
    const val FFT_SIZE = 2048
    const val HOP_SIZE = 512

    fun frames(
        samples: FloatArray,
        sampleRate: Int = PracticeFragments.SAMPLE_RATE,
        fftSize: Int = FFT_SIZE,
        hopSize: Int = HOP_SIZE,
    ): List<AnalysisFrame> {
        val engine = AnalysisEngine(sampleRate = sampleRate, fftSize = fftSize, hopSize = hopSize)
        return engine.push(samples)
    }

    fun events(
        samples: FloatArray,
        sampleRate: Int = PracticeFragments.SAMPLE_RATE,
        segmenter: NoteSegmenter = NoteSegmenter(),
    ): List<NoteEvent> = segmenter.segment(frames(samples, sampleRate))

    fun take(
        takeId: String,
        samples: FloatArray,
        sampleRate: Int = PracticeFragments.SAMPLE_RATE,
        reference: PracticeReference = PracticeFragments.REFERENCE_V1,
        rendition: PracticeFragments.Rendition? = null,
        source: InputSource = InputSource.FILE_SYNTHETIC,
        segmenter: NoteSegmenter = NoteSegmenter(),
    ): PerformanceTake = PracticeFragments.buildTake(
        takeId = takeId,
        events = events(samples, sampleRate, segmenter),
        reference = reference,
        rendition = rendition,
        source = source,
    )

    fun takeFromRendition(
        rendition: PracticeFragments.Rendition,
        takeId: String = rendition.id,
    ): PerformanceTake = take(
        takeId = takeId,
        samples = PracticeFragments.render(rendition),
        rendition = rendition,
    )
}
