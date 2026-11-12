package com.spatialcoach.soundarchitecture.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
/**
 * 主舞台和三扇副窗共享的一份分析状态。
 * 默认输入是文件回放，不能写成「已头显验证的实时拾音」。
 */
object AnalysisBus {
    const val INPUT_LABEL = "文件回放（合成音轨）"
    const val DEVICE_NOTE = "模拟器无真麦；实时拾音需另开头显验收"

    var mode by mutableStateOf(ViewMode.PROFESSIONAL)
        private set

    var frame by mutableStateOf(AnalysisFrame.silent())
        private set

    var playing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)

    val practiceCue: PracticeCue
        get() = PracticeCoach.cue(frame)

    fun toggleMode() {
        mode = if (mode == ViewMode.PROFESSIONAL) ViewMode.PRACTICE else ViewMode.PROFESSIONAL
    }

    fun publish(next: AnalysisFrame) {
        frame = next
    }

    fun markPlaying(value: Boolean) {
        playing = value
    }
}
