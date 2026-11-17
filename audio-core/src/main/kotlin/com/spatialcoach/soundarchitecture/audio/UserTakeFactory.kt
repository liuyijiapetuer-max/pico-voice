package com.spatialcoach.soundarchitecture.audio

import java.io.InputStream

sealed class UserTakeLoadResult {
    data class Ok(val take: PerformanceTake) : UserTakeLoadResult()
    data class Failed(val reason: String) : UserTakeLoadResult()
}

/**
 * 把用户选择的 WAV 变成一次可比较的演奏。
 * 必须由调用方先得到用户对固定练习谱 practice-melody-v1 的确认；未确认不得把任意文件标成该片段。
 * 失败只返回原因，不回退到内置合成示范。
 */
object UserTakeFactory {
    fun fromWavBytes(
        bytes: ByteArray,
        takeId: String,
        sourceName: String,
        referenceConfirmed: Boolean,
        reference: PracticeReference = PracticeFragments.REFERENCE_V1,
        limits: WavLimits = StrictUserWav.DEFAULT_LIMITS,
        cancelled: () -> Boolean = { false },
    ): UserTakeLoadResult {
        val rejected = bindRejected(referenceConfirmed, reference)
        if (rejected != null) return UserTakeLoadResult.Failed(rejected)
        return try {
            if (cancelled()) return UserTakeLoadResult.Failed("导入已取消或已失效，已停止分析。")
            val wav = StrictUserWav.decode(bytes, limits, cancelled)
            if (cancelled()) return UserTakeLoadResult.Failed("导入已取消或已失效，已停止分析。")
            UserTakeLoadResult.Ok(toTake(takeId, sourceName, wav, reference))
        } catch (e: UserWavException) {
            UserTakeLoadResult.Failed(e.message ?: "WAV 无法解码。")
        }
    }

    fun fromWavStream(
        stream: InputStream,
        knownLength: Long?,
        takeId: String,
        sourceName: String,
        referenceConfirmed: Boolean,
        reference: PracticeReference = PracticeFragments.REFERENCE_V1,
        limits: WavLimits = StrictUserWav.DEFAULT_LIMITS,
        cancelled: () -> Boolean = { false },
    ): UserTakeLoadResult {
        val rejected = bindRejected(referenceConfirmed, reference)
        if (rejected != null) return UserTakeLoadResult.Failed(rejected)
        return try {
            if (cancelled()) return UserTakeLoadResult.Failed("导入已取消或已失效，已停止分析。")
            val wav = StrictUserWav.decodeStream(stream, knownLength, limits, cancelled)
            if (cancelled()) return UserTakeLoadResult.Failed("导入已取消或已失效，已停止分析。")
            UserTakeLoadResult.Ok(toTake(takeId, sourceName, wav, reference))
        } catch (e: UserWavException) {
            UserTakeLoadResult.Failed(e.message ?: "WAV 无法解码。")
        }
    }

    fun bindRejected(referenceConfirmed: Boolean, reference: PracticeReference): String? {
        if (!referenceConfirmed) {
            return "未确认这是固定练习谱 ${PracticeFragments.ID_V1}（四拍 A4–C5–G4–E4）的演奏，已拒绝绑定该参考，也没有改写已有结果。"
        }
        if (reference.fragmentId != PracticeFragments.ID_V1 || reference.targets != PracticeFragments.TARGETS_V1) {
            return "只支持固定练习谱 ${PracticeFragments.ID_V1} 的既定四拍目标，不支持其他曲目或改谱。"
        }
        return null
    }

    private fun toTake(
        takeId: String,
        sourceName: String,
        wav: DecodedUserWav,
        reference: PracticeReference,
    ): PerformanceTake {
        val report = PassageAnalyzer.analyze(wav.samples, wav.sampleRate)
        return PracticeFragments.buildTake(
            takeId = takeId,
            events = report.events,
            reference = reference,
            rendition = null,
            source = InputSource.USER_WAV_FILE,
            referenceConfirmed = true,
            sourceName = sourceName,
            audioDurationMs = wav.durationMs,
            timeWindowStartMs = 0L,
            timeWindowEndMs = wav.durationMs,
            segmentation = report.diagnostics,
        )
    }
}
