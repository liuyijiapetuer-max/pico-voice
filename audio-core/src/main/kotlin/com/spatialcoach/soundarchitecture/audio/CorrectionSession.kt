package com.spatialcoach.soundarchitecture.audio

/**
 * 同一文件、同一时间范围的修音预览与保留（S3）。
 * 与练习模式的 PerformanceTake / TakeComparison 分轨，不得互换结论。
 */
data class CorrectionRetention(
    val fileId: String,
    val rangeStartMs: Long,
    val rangeEndMs: Long,
    val operatorDescription: String,
    val dry: PcmClip,
    val wet: PcmClip,
    val evidence: CorrectionEvidence,
    val claimFixed: Boolean = false,
)

data class CorrectionSession(
    val fileId: String,
    val sourceName: String,
    val dry: PcmClip,
    val preview: CorrectionPreviewResult? = null,
    val retained: CorrectionRetention? = null,
) {
    fun beforeAfter(): Pair<PcmClip, PcmClip>? {
        retained?.let { return it.dry to it.wet }
        val wet = preview?.wet ?: return null
        return preview.dry to wet
    }
}

object CorrectionWorkflow {
    fun start(fileId: String, dry: PcmClip): CorrectionSession =
        CorrectionSession(fileId = fileId, sourceName = dry.sourceName, dry = dry)

    fun preview(session: CorrectionSession, request: CorrectionRequest): CorrectionSession =
        session.copy(preview = CorrectionPreview.preview(request, session.dry))

    fun retain(session: CorrectionSession): CorrectionSession {
        val result = session.preview ?: return session
        val wet = result.wet ?: return session
        val evidence = result.evidence ?: return session
        if (result.status != CorrectionStatus.PREVIEW) return session
        return session.copy(
            retained = CorrectionRetention(
                fileId = session.fileId,
                rangeStartMs = result.rangeStartMs,
                rangeEndMs = result.rangeEndMs,
                operatorDescription = result.operatorDescription,
                dry = result.dry,
                wet = wet,
                evidence = evidence,
                claimFixed = false,
            ),
        )
    }

    fun discard(session: CorrectionSession): CorrectionSession =
        session.copy(retained = null, preview = null)
}
