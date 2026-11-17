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
    const val REVIEW_NOTE = "演奏复验只支持内置合成练习片段 practice-melody-v1，不是用户文件，也不是真麦。舞台拖动只是查看，不是修音。"

    var mode by mutableStateOf(ViewMode.PROFESSIONAL)
        private set

    var frame by mutableStateOf(AnalysisFrame.silent())
        private set

    var playing by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)

    var probe by mutableStateOf<InteractionProbe?>(null)
        private set

    var followingHand by mutableStateOf(false)
        private set

    var practiceRequest by mutableStateOf<PracticeSessionRequest?>(null)
        private set

    var demoPausedForPractice by mutableStateOf(false)
        private set

    var practiceStatus by mutableStateOf("尚未分析练习片段")
        private set

    var takeFirst by mutableStateOf<PerformanceTake?>(null)
        private set

    var takeRetake by mutableStateOf<PerformanceTake?>(null)
        private set

    var lastAlignment by mutableStateOf<TakeAlignment?>(null)
        private set

    var comparison by mutableStateOf<TakeComparison?>(null)
        private set

    private var requestSeq = 0L

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

    fun updateProbe(next: InteractionProbe?, following: Boolean) {
        probe = next
        followingHand = following
    }

    fun clearFollow() {
        followingHand = false
    }

    fun requestPracticePlay(rendition: PracticeFragments.Rendition, storeAs: String?) {
        requestSeq += 1
        practiceStatus = "正在回放：${rendition.label}"
        demoPausedForPractice = true
        practiceRequest = PracticeSessionRequest(
            id = requestSeq,
            action = PracticeSessionRequest.Action.PLAY,
            rendition = rendition,
            storeAs = storeAs,
        )
    }

    fun requestResumeDemo() {
        requestSeq += 1
        demoPausedForPractice = false
        practiceStatus = "已恢复舞台演示回放（合成音轨，非真麦）"
        practiceRequest = PracticeSessionRequest(
            id = requestSeq,
            action = PracticeSessionRequest.Action.RESUME_DEMO,
        )
    }

    fun onPracticeCancelled() {
        practiceStatus = "练习片段播放被打断，未记为完整的一遍。"
        playing = false
    }

    fun completePractice(
        frames: List<AnalysisFrame>,
        rendition: PracticeFragments.Rendition,
        storeAs: String?,
    ) {
        val events = NoteSegmenter().segment(frames)
        val takeId = storeAs ?: rendition.id
        val take = PracticeFragments.buildTake(
            takeId = takeId,
            events = events,
            rendition = rendition,
        )
        val aligned = TakeComparator.align(take)
        lastAlignment = aligned
        when (storeAs) {
            "first" -> {
                takeFirst = take
                comparison = null
                practiceStatus = "已记下第一遍（${rendition.label}，${take.source.label}）。稳定音 ${events.count { it.stable }} 个。"
            }
            "retake" -> {
                takeRetake = take
                comparison = null
                practiceStatus = "已记下重奏（${rendition.label}，${take.source.label}）。稳定音 ${events.count { it.stable }} 个。"
            }
            else -> {
                practiceStatus = "已播放${rendition.label}（未记入比较）。稳定音 ${events.count { it.stable }} 个。"
            }
        }
        playing = false
    }

    fun compareTakes() {
        val a = takeFirst
        val b = takeRetake
        if (a == null || b == null) {
            comparison = null
            practiceStatus = "需要先记下第一遍和重奏，才能比较。"
            return
        }
        val result = TakeComparator.compare(a, b)
        comparison = result
        practiceStatus = if (result.comparable) {
            "已按同一片段 ${a.fragmentId} 比较第一遍与重奏。音准与节奏分项如下，不用总分代替。"
        } else {
            result.rejectedReason ?: "不能比较。"
        }
    }
}

data class PracticeSessionRequest(
    val id: Long,
    val action: Action,
    val rendition: PracticeFragments.Rendition? = null,
    val storeAs: String? = null,
) {
    enum class Action { PLAY, RESUME_DEMO }
}
