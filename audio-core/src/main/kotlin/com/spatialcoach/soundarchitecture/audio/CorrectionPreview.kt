package com.spatialcoach.soundarchitecture.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 文件驱动的保守修音预览（S1）。
 * 当前实现：指定频段衰减，以及短脉冲去咕嗒（邻域插值）。
 * 这不是 DSP 一键专业修音，也不改 YIN / 频谱 / 动力学分析口径。
 */
object CorrectionPreview {
    const val DEFAULT_SAMPLE_RATE = 44_100
    const val DEFAULT_ATTENUATION_DB = 12.0
    const val FADE_MS = 8L
    const val MIN_EVIDENCE_RATIO = 0.75
    const val MIN_DRY_BAND_RMS = 1.0e-4
    const val MIN_DRY_CLICK_ENERGY = 1.0e-4
    const val CLICK_STEP_MULT = 8.0
    const val CLICK_MIN_ABS = 0.08
    const val CLICK_DILATE = 1
    const val DROP_RATIO_FLOOR = 1.0e-4

    fun preview(request: CorrectionRequest, dry: PcmClip): CorrectionPreviewResult {
        val rejected = rejectReason(request, dry) ?: request.unsupportedKind?.let {
            CorrectionText.rejectUnsupported(it)
        }
        if (rejected != null || request.unsupportedKind != null || request.operator == null) {
            val reason = rejected ?: CorrectionText.rejectUnsupported(
                request.unsupportedKind ?: UnsupportedCorrectionKind.UNSPECIFIED,
            )
            return rejectResult(dry, reason, request.startMs, request.endMs)
        }
        return when (request.operator) {
            CorrectionOperatorKind.BAND_ATTENUATE -> previewBand(request, dry)
            CorrectionOperatorKind.DE_CLICK -> previewDeClick(request, dry)
        }
    }

    fun claimLine(result: CorrectionPreviewResult): String {
        if (result.status == CorrectionStatus.REJECTED) {
            return result.rejectReason ?: CorrectionText.REJECTED
        }
        val evidence = result.evidence
        // isSufficient 只表示够报告测量值，不得升成「已修好」。
        // 证据不足或下降为负/零时，只显示 NO_FIXED_WITHOUT_EVIDENCE。
        // 湿侧触到比值地板时，不得把 80 dB 上限写成测得的下降。
        return when {
            evidence == null -> CorrectionText.NO_FIXED_WITHOUT_EVIDENCE
            evidence.hitRatioFloor -> CorrectionText.ratioFloorLine(evidence, result.operatorDescription)
            evidence.displayDropDb() == null -> CorrectionText.NO_FIXED_WITHOUT_EVIDENCE
            else -> CorrectionText.measuredDrop(evidence, result.operatorDescription)
        }
    }

    private fun previewBand(request: CorrectionRequest, dry: PcmClip): CorrectionPreviewResult {
        val band = request.band
            ?: return rejectResult(dry, "指定频段衰减必须给出频段范围，未改音频。", request.startMs, request.endMs)
        val attenuationDb = request.attenuationDb ?: DEFAULT_ATTENUATION_DB
        if (attenuationDb <= 0.0) {
            return rejectResult(dry, "衰减必须大于 0 dB，未改音频。", request.startMs, request.endMs)
        }
        val wetSamples = bandAttenuate(
            samples = dry.samples,
            sampleRate = dry.sampleRate,
            startMs = request.startMs,
            endMs = request.endMs,
            centerHz = band.centerHz(),
            q = band.q(),
            attenuationDb = attenuationDb,
        )
        val wet = dry.copy(samples = wetSamples, label = CorrectionText.WET_LABEL)
        val evidence = measureBand(dry, wet, request.startMs, request.endMs, band)
        return previewResult(
            dry = dry,
            wet = wet,
            evidence = evidence,
            description = CorrectionText.bandDescription(band, attenuationDb),
            startMs = request.startMs,
            endMs = request.endMs,
        )
    }

    private fun previewDeClick(request: CorrectionRequest, dry: PcmClip): CorrectionPreviewResult {
        val processed = deClick(dry.samples, dry.sampleRate, request.startMs, request.endMs)
        val wet = dry.copy(samples = processed.samples, label = CorrectionText.WET_LABEL)
        val evidence = measureClick(
            dry = dry,
            wet = wet,
            startMs = request.startMs,
            endMs = request.endMs,
            typicalStep = processed.typicalStep,
        )
        return previewResult(
            dry = dry,
            wet = wet,
            evidence = evidence,
            description = CorrectionText.deClickDescription(),
            startMs = request.startMs,
            endMs = request.endMs,
        )
    }

    private fun rejectReason(request: CorrectionRequest, dry: PcmClip): String? {
        if (request.endMs <= request.startMs) return "句段时间范围无效，未改音频。"
        if (request.startMs < 0 || request.endMs > dry.durationMs + 1) {
            return "句段超出文件范围，未改音频。"
        }
        if (dry.sampleRate <= 0 || dry.samples.isEmpty()) return "音频无效，未改音频。"
        return null
    }

    private fun rejectResult(
        dry: PcmClip,
        reason: String,
        startMs: Long,
        endMs: Long,
    ) = CorrectionPreviewResult(
        status = CorrectionStatus.REJECTED,
        rejectReason = reason,
        dry = dry,
        wet = null,
        evidence = null,
        claimFixed = false,
        operatorDescription = reason,
        rangeStartMs = startMs,
        rangeEndMs = endMs,
    )

    private fun previewResult(
        dry: PcmClip,
        wet: PcmClip,
        evidence: CorrectionEvidence,
        description: String,
        startMs: Long,
        endMs: Long,
    ) = CorrectionPreviewResult(
        status = CorrectionStatus.PREVIEW,
        rejectReason = null,
        dry = dry,
        wet = wet,
        evidence = evidence,
        claimFixed = false,
        operatorDescription = description,
        rangeStartMs = startMs,
        rangeEndMs = endMs,
    )

    /**
     * 仅在 [startMs, endMs] 内应用 RBJ 峰值衰减，两端做短淡化，避免整段被改写。
     */
    fun bandAttenuate(
        samples: FloatArray,
        sampleRate: Int,
        startMs: Long,
        endMs: Long,
        centerHz: Double,
        q: Double,
        attenuationDb: Double,
    ): FloatArray {
        val out = samples.copyOf()
        val start = indexOf(sampleRate, startMs).coerceIn(0, out.size)
        val end = indexOf(sampleRate, endMs).coerceIn(start, out.size)
        if (start >= end) return out
        val fade = ((sampleRate * FADE_MS) / 1000L).toInt().coerceAtLeast(4)
        val filter = PeakingCut(sampleRate, centerHz, q, attenuationDb)
        for (i in start until end) {
            val wet = filter.process(out[i])
            val w = fadeWeight(i - start, end - start, fade)
            out[i] = (out[i] * (1f - w) + wet * w)
        }
        return out
    }

    /**
     * 保守去咕嗒：用句段内一阶差分的中位数作典型步长，
     * 把相对邻域插值偏差过大的短脉冲标出，再在原干声邻域之间线性插值。
     * 只改句段内被标记的样点，不是宽带降噪。
     */
    fun deClick(
        samples: FloatArray,
        sampleRate: Int,
        startMs: Long,
        endMs: Long,
    ): DeClickProcess {
        val start = indexOf(sampleRate, startMs).coerceIn(0, samples.size)
        val end = indexOf(sampleRate, endMs).coerceIn(start, samples.size)
        val typicalStep = typicalStep(samples, start, end)
        val marked = BooleanArray(samples.size)
        for (i in (start + 1) until (end - 1)) {
            if (isClick(samples, i, typicalStep)) marked[i] = true
        }
        val dilated = dilate(marked, start, end, CLICK_DILATE)
        val out = samples.copyOf()
        for (i in start until end) {
            if (!dilated[i]) continue
            var a = i - 1
            while (a >= start && dilated[a]) a--
            var b = i + 1
            while (b < end && dilated[b]) b++
            out[i] = interpolate(samples, a, b, i, start, end)
        }
        return DeClickProcess(samples = out, typicalStep = typicalStep)
    }

    fun measureBand(
        dry: PcmClip,
        wet: PcmClip,
        startMs: Long,
        endMs: Long,
        band: FrequencyBand,
    ): CorrectionEvidence {
        val drySeg = segment(dry, startMs, endMs)
        val wetSeg = segment(wet, startMs, endMs)
        val dryBand = goertzelBandRms(drySeg, dry.sampleRate, band)
        val wetBand = goertzelBandRms(wetSeg, wet.sampleRate, band)
        val ratio = if (dryBand < 1.0e-12) 1.0 else wetBand / dryBand
        val dropDb = dropDb(ratio)
        return CorrectionEvidence(
            kind = CorrectionEvidenceKind.BAND_ENERGY,
            metricLabel = "目标频段能量",
            dropDb = dropDb,
            dryRms = rms(drySeg),
            wetRms = rms(wetSeg),
            isSufficient = dryBand >= MIN_DRY_BAND_RMS && ratio <= MIN_EVIDENCE_RATIO,
            band = band,
            dryBandRms = dryBand,
            wetBandRms = wetBand,
            bandEnergyRatio = ratio,
            bandDropDb = dropDb,
        )
    }

    fun measureClick(
        dry: PcmClip,
        wet: PcmClip,
        startMs: Long,
        endMs: Long,
        typicalStep: Double,
    ): CorrectionEvidence {
        val start = indexOf(dry.sampleRate, startMs).coerceIn(0, dry.samples.size)
        val end = indexOf(dry.sampleRate, endMs).coerceIn(start, dry.samples.size)
        val dryStats = clickStats(dry.samples, start, end, typicalStep)
        val wetStats = clickStats(wet.samples, start, end, typicalStep)
        val ratio = if (dryStats.energy < 1.0e-12) 1.0 else wetStats.energy / dryStats.energy
        val dropDb = dropDb(ratio)
        val drySegRms = rms(segment(dry, startMs, endMs))
        val wetSegRms = rms(segment(wet, startMs, endMs))
        return CorrectionEvidence(
            kind = CorrectionEvidenceKind.CLICK_RESIDUAL,
            metricLabel = "短脉冲残差",
            dropDb = dropDb,
            dryRms = drySegRms,
            wetRms = wetSegRms,
            isSufficient = dryStats.count >= 1 &&
                dryStats.energy >= MIN_DRY_CLICK_ENERGY &&
                ratio <= MIN_EVIDENCE_RATIO,
            dryClickEnergy = dryStats.energy,
            wetClickEnergy = wetStats.energy,
            clickResidualRatio = ratio,
            dryClickCount = dryStats.count,
            wetClickCount = wetStats.count,
            typicalStep = typicalStep,
        )
    }

    private data class ClickStats(val energy: Double, val count: Int)

    private fun clickStats(
        samples: FloatArray,
        start: Int,
        end: Int,
        typicalStep: Double,
    ): ClickStats {
        val lo = (start + 1).coerceAtMost(end)
        val hi = (end - 1).coerceAtLeast(lo)
        var clickAcc = 0.0
        var count = 0
        var n = 0
        for (i in lo until hi) {
            val dev = interpolationDev(samples, i)
            n++
            if (isClickDev(dev, typicalStep)) {
                clickAcc += dev * dev
                count++
            }
        }
        // 只把超阈偏差计入残差能量，便于复核；无检出时能量为 0。分母用句段长度，使干/湿可比。
        val clickEnergy = if (n == 0) 0.0 else sqrt(clickAcc / n)
        return ClickStats(energy = clickEnergy, count = count)
    }

    fun typicalStep(samples: FloatArray, start: Int, end: Int): Double {
        val n = (end - start - 1).coerceAtLeast(0)
        if (n <= 0) return 1.0e-6
        val steps = FloatArray(n) { i -> abs(samples[start + i + 1] - samples[start + i]) }
        return median(steps).coerceAtLeast(1.0e-6)
    }

    private fun isClick(samples: FloatArray, i: Int, typicalStep: Double): Boolean =
        isClickDev(interpolationDev(samples, i), typicalStep)

    private fun isClickDev(dev: Double, typicalStep: Double): Boolean =
        dev > CLICK_STEP_MULT * typicalStep && dev > CLICK_MIN_ABS

    private fun interpolationDev(samples: FloatArray, i: Int): Double {
        if (i <= 0 || i >= samples.size - 1) return 0.0
        val interp = 0.5 * (samples[i - 1].toDouble() + samples[i + 1].toDouble())
        return abs(samples[i].toDouble() - interp)
    }

    private fun dilate(marked: BooleanArray, start: Int, end: Int, width: Int): BooleanArray {
        val out = marked.copyOf()
        for (i in start until end) {
            if (!marked[i]) continue
            val lo = (i - width).coerceAtLeast(start)
            val hi = (i + width).coerceAtMost(end - 1)
            for (j in lo..hi) out[j] = true
        }
        return out
    }

    private fun interpolate(
        samples: FloatArray,
        a: Int,
        b: Int,
        i: Int,
        start: Int,
        end: Int,
    ): Float {
        return when {
            a < start && b >= end -> 0f
            a < start -> samples[b]
            b >= end -> samples[a]
            else -> {
                val t = (i - a).toDouble() / (b - a).toDouble()
                (samples[a].toDouble() * (1.0 - t) + samples[b].toDouble() * t).toFloat()
            }
        }
    }

    private fun dropDb(ratio: Double): Double {
        val safe = if (ratio <= 0.0) DROP_RATIO_FLOOR else ratio.coerceAtLeast(DROP_RATIO_FLOOR)
        return -20.0 * ln(safe) / ln(10.0)
    }

    private fun segment(clip: PcmClip, startMs: Long, endMs: Long): FloatArray {
        val a = indexOf(clip.sampleRate, startMs).coerceIn(0, clip.samples.size)
        val b = indexOf(clip.sampleRate, endMs).coerceIn(a, clip.samples.size)
        return clip.samples.copyOfRange(a, b)
    }

    fun indexOf(sampleRate: Int, ms: Long): Int =
        ((sampleRate.toLong() * ms) / 1000L).toInt()

    private fun fadeWeight(offset: Int, length: Int, fade: Int): Float {
        val f = fade.coerceAtMost(length / 2)
        if (f <= 0) return 1f
        return when {
            offset < f -> offset / f.toFloat()
            offset >= length - f -> (length - 1 - offset) / f.toFloat()
            else -> 1f
        }
    }

    private fun rms(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s.toDouble()
        return sqrt(sum / samples.size)
    }

    private fun median(values: FloatArray): Double {
        if (values.isEmpty()) return 0.0
        val copy = values.copyOf()
        copy.sort()
        val n = copy.size
        return if (n % 2 == 1) {
            copy[n / 2].toDouble()
        } else {
            0.5 * (copy[n / 2 - 1].toDouble() + copy[n / 2].toDouble())
        }
    }

    /**
     * 用若干个 Goertzel 点估计频段能量，避免为证据另写一套 FFT 口径。
     * 这只是修音预览证据，不是要替换分析引擎的频谱。
     */
    fun goertzelBandRms(samples: FloatArray, sampleRate: Int, band: FrequencyBand, points: Int = 9): Double {
        if (samples.isEmpty()) return 0.0
        val n = points.coerceAtLeast(3)
        var sum = 0.0
        for (i in 0 until n) {
            val t = i / (n - 1).toDouble()
            val hz = band.lowHz + t * (band.highHz - band.lowHz)
            val mag = goertzel(samples, sampleRate, hz)
            sum += mag * mag
        }
        return sqrt(sum / n)
    }

    fun goertzel(samples: FloatArray, sampleRate: Int, hz: Double): Double {
        if (samples.isEmpty() || sampleRate <= 0) return 0.0
        val k = (0.5 + samples.size * hz / sampleRate).toInt()
        val omega = 2.0 * PI * k / samples.size
        val coeff = 2.0 * cos(omega)
        var s0 = 0.0
        var s1 = 0.0
        var s2 = 0.0
        for (x in samples) {
            s0 = x.toDouble() + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        val real = s1 - s2 * cos(omega)
        val imag = s2 * sin(omega)
        return sqrt(real * real + imag * imag) / samples.size
    }
}

data class DeClickProcess(
    val samples: FloatArray,
    val typicalStep: Double,
)

enum class CorrectionOperatorKind {
    BAND_ATTENUATE,
    DE_CLICK,
}

enum class UnsupportedCorrectionKind {
    DE_ESS,
    DENOISE_ML,
    PITCH_AUTOTUNE,
    LIVE_MIC,
    UNSPECIFIED,
}

enum class CorrectionStatus {
    PREVIEW,
    REJECTED,
}

enum class CorrectionEvidenceKind {
    BAND_ENERGY,
    CLICK_RESIDUAL,
}

data class FrequencyBand(val lowHz: Double, val highHz: Double) {
    init {
        require(lowHz > 0.0 && highHz > lowHz) { "频段必须为正，且上限大于下限" }
    }
    fun centerHz(): Double = (lowHz + highHz) / 2.0
    fun q(): Double {
        val width = highHz - lowHz
        return (centerHz() / width).coerceIn(0.4, 8.0)
    }
}

data class CorrectionRequest(
    val startMs: Long,
    val endMs: Long,
    val operator: CorrectionOperatorKind? = CorrectionOperatorKind.BAND_ATTENUATE,
    val unsupportedKind: UnsupportedCorrectionKind? = null,
    val band: FrequencyBand? = null,
    val attenuationDb: Double? = null,
)

data class PcmClip(
    val samples: FloatArray,
    val sampleRate: Int,
    val label: String,
    val sourceName: String,
) {
    val durationMs: Long
        get() = if (sampleRate <= 0) 0L else (samples.size * 1000L) / sampleRate
}

data class CorrectionEvidence(
    val kind: CorrectionEvidenceKind,
    val metricLabel: String,
    val dropDb: Double,
    val dryRms: Double,
    val wetRms: Double,
    val isSufficient: Boolean,
    val band: FrequencyBand? = null,
    val dryBandRms: Double = 0.0,
    val wetBandRms: Double = 0.0,
    val bandEnergyRatio: Double = 1.0,
    val bandDropDb: Double = 0.0,
    val dryClickEnergy: Double = 0.0,
    val wetClickEnergy: Double = 0.0,
    val clickResidualRatio: Double = 1.0,
    val dryClickCount: Int = 0,
    val wetClickCount: Int = 0,
    val typicalStep: Double = 0.0,
) {
    /**
     * 比值是否触到 DROP_RATIO_FLOOR。触及时 dropDb 只是上限，不是听感测量值。
     */
    val hitRatioFloor: Boolean
        get() {
            if (!isSufficient) return false
            val ratio = when (kind) {
                CorrectionEvidenceKind.CLICK_RESIDUAL ->
                    if (wetClickEnergy <= 0.0) 0.0 else clickResidualRatio
                CorrectionEvidenceKind.BAND_ENERGY -> bandEnergyRatio
            }
            return ratio <= CorrectionPreview.DROP_RATIO_FLOOR
        }

    /**
     * 仅在证据充足、下降为正、且未被比值地板封顶时，才可把 dB 当作测量值报告。
     * isSufficient 不得被解释成已修好；claimFixed 在结果里写死为 false。
     */
    fun displayDropDb(): Double? =
        if (isSufficient && !hitRatioFloor && dropDb > 0.0) dropDb else null
}

data class CorrectionPreviewResult(
    val status: CorrectionStatus,
    val rejectReason: String?,
    val dry: PcmClip,
    val wet: PcmClip?,
    val evidence: CorrectionEvidence?,
    val claimFixed: Boolean,
    val operatorDescription: String,
    val rangeStartMs: Long,
    val rangeEndMs: Long,
)

/** RBJ peaking EQ，增益为负，用于指定频段衰减。 */
internal class PeakingCut(
    sampleRate: Int,
    centerHz: Double,
    q: Double,
    attenuationDb: Double,
) {
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    init {
        val a = Math.pow(10.0, -attenuationDb / 40.0)
        val w0 = 2.0 * PI * centerHz / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val cosw = cos(w0)
        val b0n = 1.0 + alpha * a
        val b1n = -2.0 * cosw
        val b2n = 1.0 - alpha * a
        val a0 = 1.0 + alpha / a
        val a1n = -2.0 * cosw
        val a2n = 1.0 - alpha / a
        b0 = b0n / a0
        b1 = b1n / a0
        b2 = b2n / a0
        a1 = a1n / a0
        a2 = a2n / a0
    }

    fun process(input: Float): Float {
        val x0 = input.toDouble()
        val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x0
        y2 = y1
        y1 = y0
        return y0.toFloat()
    }
}
