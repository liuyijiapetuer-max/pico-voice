package com.spatialcoach.soundarchitecture.audio

/**
 * 专业修音对照与练习演奏对照的可见性门。
 * 切换到练习模式时，修复对照必须隐藏，且不得把修复变化写成演奏改善。
 * 不可判定的演奏早退规则仍由 SpatialReviewLayout / TakeComparator 负责，这里不改、不复用。
 *
 * ViewMode 必须使用 SPACE 现树枚举，本目录不另定义，以免落地时接错或重名。
 */
object CorrectionModeGate {
    fun correctionVisible(mode: ViewMode): Boolean = mode == ViewMode.PROFESSIONAL

    fun practiceReviewVisible(mode: ViewMode): Boolean = mode == ViewMode.PRACTICE

    fun professionalReadout(mode: ViewMode, session: CorrectionSession?): String? {
        if (!correctionVisible(mode) || session == null) return null
        val previewLine = session.preview?.let { CorrectionPreview.claimLine(it) }
        val retainLine = CorrectionText.beforeAfterLine(session)
        return listOfNotNull(
            CorrectionText.SOURCE_NOTE,
            CorrectionText.NOT_PERFORMANCE,
            CorrectionText.DRAG_IS_VIEW_ONLY,
            previewLine,
            retainLine,
        ).joinToString(" ")
    }

    fun practiceReadout(
        mode: ViewMode,
        earlyExitMessage: String?,
        practiceLines: List<String>,
    ): String? {
        if (!practiceReviewVisible(mode)) return null
        val body = if (earlyExitMessage != null) {
            earlyExitMessage
        } else {
            practiceLines.joinToString(" ")
        }
        return listOf(body, CorrectionText.PRACTICE_HIDES_CORRECTION).joinToString(" ")
    }

    fun leaksCorrectionIntoPractice(mode: ViewMode, session: CorrectionSession?): Boolean {
        if (mode != ViewMode.PRACTICE) return false
        val text = professionalReadout(ViewMode.PRACTICE, session)
        if (text != null) return true
        return false
    }

    fun leaksPracticeImprovementIntoCorrection(text: String): Boolean {
        val needles = listOf("缺音", "多音", "重奏已对准", "演奏改善", "第一遍缺音", "重奏缺音")
        return needles.any { text.contains(it) }
    }
}
