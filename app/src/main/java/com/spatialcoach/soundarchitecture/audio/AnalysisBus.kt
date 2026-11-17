package com.spatialcoach.soundarchitecture.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job

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

    /**
     * 用户导入发布的唯一状态门。所有写槽必须经过 [beginUserImport] / [publishUserImport]，
     * 与 [UserImportCoordinatorTest] 覆盖的同一套令牌规则。
     */
    val userImports = UserImportCoordinator()

    private var requestSeq = 0L
    private val importJobs = HashMap<ImportSlot, Job>()

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
        invalidateInFlightImports("正在播放${rendition.label}。在途导入已失效，已成功的用户文件未改写成示范。")
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
        invalidateInFlightImports("已恢复舞台演示回放。在途导入已失效，已成功的用户文件未被改写。")
        requestSeq += 1
        demoPausedForPractice = false
        practiceStatus = "已恢复舞台演示回放（合成音轨，非真麦）。在途导入已失效。"
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

    fun setPracticeReferenceConfirmed(confirmed: Boolean) {
        userImports.setReferenceConfirmed(confirmed)
        syncImportState()
    }

    fun beginUserImport(slotId: String): ImportToken? {
        val slot = ImportSlot.fromId(slotId) ?: return null
        importJobs.remove(slot)?.cancel()
        val token = userImports.begin(slot)
        syncImportState()
        return token
    }

    fun replaceImportJob(slotId: String, job: Job) {
        val slot = ImportSlot.fromId(slotId) ?: return
        importJobs.remove(slot)?.cancel()
        importJobs[slot] = job
        job.invokeOnCompletion { importJobs.remove(slot, job) }
    }

    fun isImportCurrent(token: ImportToken): Boolean = userImports.isCurrent(token)

    fun publishUserImport(token: ImportToken, result: UserTakeLoadResult): ImportPublishResult {
        val outcome = userImports.publish(token, result)
        syncImportState()
        return outcome
    }

    fun onImportJobCancelled(token: ImportToken) {
        userImports.ignoreStale(token, "导入已取消。该槽没有恢复旧的成功结果，也没有回退到合成示范。")
        syncImportState()
    }

    fun invalidateInFlightImports(reason: String) {
        userImports.invalidateInFlight(reason)
        importJobs.values.forEach { it.cancel() }
        importJobs.clear()
        syncImportState()
    }

    fun failImport(reason: String) {
        // 尚未拿到令牌的预检失败（未确认、未选文件等）不改写已有槽位。
        practiceStatus = "导入失败：$reason 未改写已有的第一遍或重奏，也没有回退到合成示范。"
    }

    fun onImportCancelled() {
        practiceStatus = "已取消选择文件，没有改写第一遍或重奏。"
    }

    fun compareTakes() {
        userImports.compare()
        syncImportState()
    }

    private fun syncImportState() {
        val s = userImports.state
        takeFirst = s.first
        takeRetake = s.retake
        lastAlignment = s.lastAlignment
        comparison = s.comparison
        practiceStatus = s.status
    }
}

data class PracticeSessionRequest(
    val id: Long,
    val action: Action,
    val rendition: PracticeFragments.Rendition? = null,
) {
    enum class Action { PLAY, RESUME_DEMO }
}
