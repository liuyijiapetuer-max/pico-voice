package com.spatialcoach.soundarchitecture.audio

/**
 * 用户导入的请求代次/会话门。纯 JVM 状态机，不含协程。
 * App 发布路径必须调用本门的 [begin] / [publish] / [invalidateInFlight] / [setReferenceConfirmed]，
 * 不得在令牌失效后把旧结果写回槽位。
 */
enum class ImportSlot(val id: String, val label: String) {
    FIRST("first", "第一遍"),
    RETAKE("retake", "重奏"),
    ;

    companion object {
        fun fromId(id: String): ImportSlot? = entries.find { it.id == id }
    }
}

data class ImportToken(
    val slot: ImportSlot,
    val slotGeneration: Long,
    val sessionGeneration: Long,
    val requestId: Long,
)

enum class ImportPublishResult {
    ACCEPTED,
    REJECTED_STALE,
    REJECTED_UNCONFIRMED,
    REJECTED_INVALID,
}

data class UserImportState(
    val referenceConfirmed: Boolean = false,
    val first: PerformanceTake? = null,
    val retake: PerformanceTake? = null,
    val lastAlignment: TakeAlignment? = null,
    val comparison: TakeComparison? = null,
    val pendingFirst: Boolean = false,
    val pendingRetake: Boolean = false,
    val status: String = "尚未导入用户演奏",
) {
    fun take(slot: ImportSlot): PerformanceTake? = when (slot) {
        ImportSlot.FIRST -> first
        ImportSlot.RETAKE -> retake
    }

    fun pending(slot: ImportSlot): Boolean = when (slot) {
        ImportSlot.FIRST -> pendingFirst
        ImportSlot.RETAKE -> pendingRetake
    }
}

class UserImportCoordinator {
    private var sessionGeneration = 0L
    private val slotGeneration = mutableMapOf(
        ImportSlot.FIRST to 0L,
        ImportSlot.RETAKE to 0L,
    )
    private var requestSeq = 0L

    var state: UserImportState = UserImportState()
        private set

    fun isCurrent(token: ImportToken): Boolean =
        token.sessionGeneration == sessionGeneration &&
            token.slotGeneration == slotGeneration.getValue(token.slot)

    fun currentTokenOrNull(slot: ImportSlot): ImportToken? {
        val gen = slotGeneration.getValue(slot)
        if (gen <= 0L || !state.pending(slot)) return null
        return ImportToken(slot, gen, sessionGeneration, requestSeq)
    }

    /**
     * 开始该槽一次新导入：提升该槽代次（使旧令牌失效），清空该槽已有结果和比较，
     * 失败后也不会把旧成功写回。不提升会话代次，故另一槽在途请求不被误杀。
     */
    fun begin(slot: ImportSlot): ImportToken {
        slotGeneration[slot] = slotGeneration.getValue(slot) + 1
        requestSeq += 1
        val token = ImportToken(
            slot = slot,
            slotGeneration = slotGeneration.getValue(slot),
            sessionGeneration = sessionGeneration,
            requestId = requestSeq,
        )
        state = when (slot) {
            ImportSlot.FIRST -> state.copy(
                first = null,
                pendingFirst = true,
                comparison = null,
                lastAlignment = if (state.pendingRetake || state.retake != null) state.lastAlignment else null,
            )
            ImportSlot.RETAKE -> state.copy(
                retake = null,
                pendingRetake = true,
                comparison = null,
                lastAlignment = if (state.pendingFirst || state.first != null) state.lastAlignment else null,
            )
        }.copy(status = "正在导入${slot.label}。该槽先前结果已停用，在途的旧请求即使迟到也不会写回。")
        return token
    }

    fun publish(token: ImportToken, result: UserTakeLoadResult): ImportPublishResult {
        if (!isCurrent(token)) return ImportPublishResult.REJECTED_STALE
        if (!state.referenceConfirmed) {
            clearPending(token.slot, "参考未确认，已拒绝写入，没有恢复旧的成功结果。")
            return ImportPublishResult.REJECTED_UNCONFIRMED
        }
        return when (result) {
            is UserTakeLoadResult.Ok -> acceptTake(token.slot, result.take)
            is UserTakeLoadResult.Failed -> {
                clearPending(token.slot, "导入失败：${result.reason} 未恢复该槽旧的成功结果，也没有回退到合成示范。")
                ImportPublishResult.ACCEPTED
            }
        }
    }

    /**
     * 恢复示范、播放参考等：使所有在途令牌失效，不改写已成功写入的用户槽（已被 begin 清空的除外）。
     */
    fun invalidateInFlight(reason: String) {
        sessionGeneration += 1
        state = state.copy(
            pendingFirst = false,
            pendingRetake = false,
            status = reason,
        )
    }

    fun setReferenceConfirmed(confirmed: Boolean) {
        if (confirmed == state.referenceConfirmed) return
        if (!confirmed) {
            sessionGeneration += 1
            state = state.copy(
                referenceConfirmed = false,
                pendingFirst = false,
                pendingRetake = false,
                comparison = null,
                status = "已取消确认参考。在途导入已失效，不能比较。已导入文件不会被改写成示范。",
            )
        } else {
            state = state.copy(
                referenceConfirmed = true,
                status = "已确认这是固定练习谱 ${PracticeFragments.ID_V1} 的演奏，可以导入。",
            )
        }
    }

    fun compare(): TakeComparison {
        val a = state.first
        val b = state.retake
        if (!state.referenceConfirmed) {
            val rejected = TakeComparison(comparable = false, rejectedReason = "未确认固定练习谱，不能比较。")
            state = state.copy(comparison = rejected, status = rejected.rejectedReason!!)
            return rejected
        }
        if (a == null || b == null) {
            val rejected = TakeComparison(
                comparable = false,
                rejectedReason = "需要先导入用户的第一遍和重奏（录音文件），才能比较。内置示范不能代替。",
            )
            state = state.copy(comparison = rejected, status = rejected.rejectedReason!!)
            return rejected
        }
        val result = TakeComparator.compare(a, b)
        state = state.copy(
            comparison = result,
            status = if (result.comparable) {
                "已按确认的固定练习谱 ${a.fragmentId} 比较用户第一遍与重奏。缺/多音仅在可判定时显示确定计数。"
            } else {
                result.rejectedReason ?: "不能比较。"
            },
        )
        return result
    }

    fun ignoreStale(token: ImportToken, message: String? = null): ImportPublishResult {
        if (isCurrent(token)) {
            clearPending(token.slot, message ?: "导入已取消。该槽没有恢复旧的成功结果。")
            return ImportPublishResult.ACCEPTED
        }
        return ImportPublishResult.REJECTED_STALE
    }

    private fun acceptTake(slot: ImportSlot, take: PerformanceTake): ImportPublishResult {
        if (take.source != InputSource.USER_WAV_FILE || !take.referenceConfirmed) {
            clearPending(slot, "拒绝写入：不是已确认参考的用户录音文件。没有恢复旧的成功结果。")
            return ImportPublishResult.REJECTED_INVALID
        }
        val aligned = TakeComparator.alignWithProcessedReference(take)
        state = when (slot) {
            ImportSlot.FIRST -> state.copy(first = take, pendingFirst = false, lastAlignment = aligned, comparison = null)
            ImportSlot.RETAKE -> state.copy(retake = take, pendingRetake = false, lastAlignment = aligned, comparison = null)
        }.copy(status = "已导入${slot.label}：${ReviewText.takeMetaLine(take)}。稳定音 ${take.events.count { it.stable }} 个。")
        return ImportPublishResult.ACCEPTED
    }

    private fun clearPending(slot: ImportSlot, status: String) {
        state = when (slot) {
            ImportSlot.FIRST -> state.copy(first = null, pendingFirst = false, comparison = null)
            ImportSlot.RETAKE -> state.copy(retake = null, pendingRetake = false, comparison = null)
        }.copy(status = status)
    }
}
