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
    const val REVIEW_NOTE =
        "用户重奏比较只接受已确认的 practice-melody-v1 录音 WAV。内置合成示范是独立演示，不是用户成果。舞台拖动只是查看，不是修音。"

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

    var practiceStatus by mutableStateOf("尚未导入用户演奏")
        private set

    var takeFirst by mutableStateOf<PerformanceTake?>(null)
        private set

    var takeRetake by mutableStateOf<PerformanceTake?>(null)
        private set

    var lastAlignment by mutableStateOf<TakeAlignment?>(null)
        private set

    var lastDemoAlignment by mutableStateOf<TakeAlignment?>(null)
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

    fun requestDemoPlay(rendition: PracticeFragments.Rendition) {
        requestSeq += 1
        practiceStatus = "正在播放${rendition.label}。这是内置合成示范，不会记为用户第一遍或重奏。"
        demoPausedForPractice = true
        practiceRequest = PracticeSessionRequest(
            id = requestSeq,
            action = PracticeSessionRequest.Action.PLAY,
            rendition = rendition,
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
        practiceStatus = "示范播放被打断。用户已导入的第一遍/重奏未被改写。"
        playing = false
    }

    fun completeDemo(frames: List<AnalysisFrame>, rendition: PracticeFragments.Rendition) {
        val events = NoteSegmenter().segment(frames)
        val take = PracticeFragments.buildTake(
            takeId = rendition.id,
            events = events,
            rendition = rendition,
            source = InputSource.FILE_SYNTHETIC,
            sourceName = rendition.label,
        )
        lastDemoAlignment = TakeComparator.alignWithProcessedReference(take)
        practiceStatus =
            "已播放${rendition.label}（${InputSource.FILE_SYNTHETIC.label}）。未写入用户第一遍或重奏。稳定音 ${events.count { it.stable }} 个。"
        playing = false
    }

    fun storeUserTake(slot: String, take: PerformanceTake) {
        if (take.source != InputSource.USER_WAV_FILE || !take.referenceConfirmed) {
            practiceStatus = "拒绝写入：不是已确认参考的用户录音文件。"
            return
        }
        val aligned = TakeComparator.alignWithProcessedReference(take)
        lastAlignment = aligned
        comparison = null
        when (slot) {
            "first" -> takeFirst = take
            "retake" -> takeRetake = take
            else -> {
                practiceStatus = "未知导入槽位，未改写已有结果。"
                return
            }
        }
        val label = if (slot == "first") "第一遍" else "重奏"
        practiceStatus = "已导入$label：${ReviewText.takeMetaLine(take)}。稳定音 ${take.events.count { it.stable }} 个。"
    }

    fun failImport(reason: String) {
        practiceStatus = "导入失败：$reason 未改写已有的第一遍或重奏，也没有回退到合成示范。"
    }

    fun onImportCancelled() {
        practiceStatus = "已取消选择文件，没有改写第一遍或重奏。"
    }

    fun compareTakes() {
        val a = takeFirst
        val b = takeRetake
        if (a == null || b == null) {
            comparison = null
            practiceStatus = "需要先导入用户的第一遍和重奏（录音文件），才能比较。内置示范不能代替。"
            return
        }
        val result = TakeComparator.compare(a, b)
        comparison = result
        practiceStatus = if (result.comparable) {
            "已按确认的固定练习谱 ${a.fragmentId} 比较用户第一遍与重奏。错音/缺音/多音与覆盖率如下；对准音音分不含错音，不能当作整体改善。"
        } else {
            result.rejectedReason ?: "不能比较。"
        }
    }
}

data class PracticeSessionRequest(
    val id: Long,
    val action: Action,
    val rendition: PracticeFragments.Rendition? = null,
) {
    enum class Action { PLAY, RESUME_DEMO }
}
