package com.spatialcoach.soundarchitecture.audio

/** 修音对照专用文案。禁止复用演奏复验的缺多音或改善结论。 */
object CorrectionText {
    const val SOURCE_NOTE =
        "这是实验室夹具或内置合成文件上的修音预览，不是用户演奏，不是实时采集，也不是专业修音验收。"
    const val SCORE_SCOPE = "本切片不对照练习谱，也不产生演奏对错结论。"
    const val WET_LABEL = "修后预览（保守处理，不是已修好）"
    const val DRY_LABEL = "修前（干声）"
    const val REJECTED = "未做修复，音频未被改写。"
    const val NO_FIXED_WITHOUT_EVIDENCE =
        "没有对应的频段、短脉冲或动态证据，不得显示「已修好」，本预览也不宣称已修好。"
    const val NEVER_CLAIM_FIXED =
        "本切片只报告对应技术量是否下降，不宣称已修好，也不表示已达专业修音质量。"
    const val NOT_PERFORMANCE =
        "这是专业模式的修前/修后对照，不是练习模式的第一遍或重奏。"
    const val PRACTICE_HIDES_CORRECTION =
        "练习模式不显示修复对照，也不把修复变化写成演奏改善。"
    const val DRAG_IS_VIEW_ONLY = "舞台拖动和音高尺跟手只是查看，不会改音频。"
    const val ACCEPTANCE =
        "正式验收须同时具备：真实素材、可听的处理前后、可复核的技术证据。实验室夹具只能做草稿自测，不能代替这三条。"

    fun rejectUnsupported(kind: UnsupportedCorrectionKind): String {
        val name = when (kind) {
            UnsupportedCorrectionKind.DE_ESS -> "去齿音"
            UnsupportedCorrectionKind.DENOISE_ML -> "机器学习降噪"
            UnsupportedCorrectionKind.PITCH_AUTOTUNE -> "自动修音高"
            UnsupportedCorrectionKind.LIVE_MIC -> "实时麦克风修复"
            UnsupportedCorrectionKind.UNSPECIFIED -> "未指明的修复类型"
        }
        return "不支持「$name」。未做修复，未改音频，也未编造已修好。"
    }

    fun bandDescription(band: FrequencyBand, attenuationDb: Double): String {
        val range = "${band.lowHz.toInt()}–${band.highHz.toInt()} Hz，衰减 ${attenuationDb.toInt()} dB"
        return "本预览对该句段做了指定频段衰减（$range）。这不是一键专业修音。"
    }

    fun deClickDescription(): String =
        "本预览对该句段做了保守去咕嗒（按短脉冲检出后邻域插值）。这不是专业去咔嗒或整轨修复。"

    fun measuredDrop(evidence: CorrectionEvidence, operatorDescription: String): String {
        if (evidence.hitRatioFloor) return ratioFloorLine(evidence, operatorDescription)
        val drop = evidence.displayDropDb() ?: return NO_FIXED_WITHOUT_EVIDENCE
        val dropText = String.format("%.1f", drop)
        return "$operatorDescription ${evidence.metricLabel}约下降 $dropText dB。${NEVER_CLAIM_FIXED}"
    }

    fun ratioFloorLine(evidence: CorrectionEvidence, operatorDescription: String): String {
        val detail = when (evidence.kind) {
            CorrectionEvidenceKind.CLICK_RESIDUAL ->
                "干侧检出 ${evidence.dryClickCount} 个短脉冲，湿侧残差已到检测下限。"
            CorrectionEvidenceKind.BAND_ENERGY ->
                "目标频段湿侧能量已到检测下限。"
        }
        return "$operatorDescription $detail 所列上限来自比值地板 1e-4（对应 80 dB 上限），不是听感上测到了 80 dB。$NEVER_CLAIM_FIXED"
    }

    fun evidencePanelLines(result: CorrectionPreviewResult): List<String> {
        if (result.status == CorrectionStatus.REJECTED) {
            return listOf(result.rejectReason ?: REJECTED)
        }
        val claim = CorrectionPreview.claimLine(result)
        val ev = result.evidence
        if (ev == null || !ev.isSufficient) return listOf(claim)
        val lines = mutableListOf(
            claim,
            "句段 ${result.rangeStartMs}–${result.rangeEndMs} ms。干侧整体 RMS ${"%.4f".format(ev.dryRms)}，湿侧整体 RMS ${"%.4f".format(ev.wetRms)}。",
        )
        when (ev.kind) {
            CorrectionEvidenceKind.BAND_ENERGY ->
                lines += "目标频段干侧能量 ${"%.4f".format(ev.dryBandRms)}，湿侧能量 ${"%.4f".format(ev.wetBandRms)}。"
            CorrectionEvidenceKind.CLICK_RESIDUAL ->
                lines += "干侧检出 ${ev.dryClickCount} 个短脉冲，湿侧 ${ev.wetClickCount} 个。"
        }
        lines += "isSufficient 只表示够报告这些测量值，不是已修好。"
        return lines
    }

    fun beforeAfterLine(session: CorrectionSession): String {
        val retained = session.retained
        return if (retained != null) {
            "已保留修前/修后对照（同一文件 ${session.fileId}，${retained.rangeStartMs}–${retained.rangeEndMs} ms）。$NOT_PERFORMANCE $SOURCE_NOTE"
        } else {
            "尚未保留修前/修后对照。$SOURCE_NOTE"
        }
    }
}
