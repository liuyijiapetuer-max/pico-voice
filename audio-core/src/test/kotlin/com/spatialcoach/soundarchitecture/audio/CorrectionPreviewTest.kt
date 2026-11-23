package com.spatialcoach.soundarchitecture.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CorrectionPreviewTest {

    @Test
    fun bandAttenuateOnLabHumDropsTargetBandAndDoesNotClaimFixed() {
        val dry = LabCorrectionFixtures.humUnderTone()
        val request = CorrectionRequest(
            startMs = 0,
            endMs = dry.durationMs,
            operator = CorrectionOperatorKind.BAND_ATTENUATE,
            band = LabCorrectionFixtures.INTERFERER_BAND,
            attenuationDb = 12.0,
        )
        val result = CorrectionPreview.preview(request, dry)
        assertEquals(CorrectionStatus.PREVIEW, result.status)
        assertNotNull(result.wet)
        assertNotNull(result.evidence)
        assertEquals(CorrectionEvidenceKind.BAND_ENERGY, result.evidence!!.kind)
        assertTrue(result.evidence!!.isSufficient)
        assertTrue(result.evidence!!.dropDb > 3.0)
        assertFalse(result.claimFixed)
        val line = CorrectionPreview.claimLine(result)
        assertTrue(line.contains("指定频段衰减"))
        assertTrue(line.contains("不宣称已修好"))
        assertFalse(line.startsWith("已修好"))
        assertFalse(line.contains("咕啰"))
    }

    @Test
    fun attenuatingEmptyBandIsNotSufficientEvidence() {
        val dry = LabCorrectionFixtures.cleanTone()
        val request = CorrectionRequest(
            startMs = 0,
            endMs = dry.durationMs,
            operator = CorrectionOperatorKind.BAND_ATTENUATE,
            band = FrequencyBand(40.0, 80.0),
            attenuationDb = 12.0,
        )
        val result = CorrectionPreview.preview(request, dry)
        assertEquals(CorrectionStatus.PREVIEW, result.status)
        assertNotNull(result.evidence)
        assertFalse(result.evidence!!.isSufficient)
        assertFalse(result.claimFixed)
        // 空频段上 dropDb 可能很大或为负；对用户只显示不足文案，不展示该 dB。
        assertNull(result.evidence!!.displayDropDb())
        val line = CorrectionPreview.claimLine(result)
        assertEquals(CorrectionText.NO_FIXED_WITHOUT_EVIDENCE, line)
        assertFalse(line.contains("dB"))
        assertFalse(line.contains("下降"))
    }

    @Test
    fun deClickOnLabClicksDropsResidualAndDoesNotClaimFixed() {
        val dry = LabCorrectionFixtures.clicksOnTone()
        val result = CorrectionPreview.preview(
            CorrectionRequest(
                startMs = 0,
                endMs = dry.durationMs,
                operator = CorrectionOperatorKind.DE_CLICK,
            ),
            dry,
        )
        assertEquals(CorrectionStatus.PREVIEW, result.status)
        assertNotNull(result.wet)
        assertNotNull(result.evidence)
        assertEquals(CorrectionEvidenceKind.CLICK_RESIDUAL, result.evidence!!.kind)
        assertTrue(result.evidence!!.dryClickCount >= 1)
        assertTrue(result.evidence!!.isSufficient)
        assertTrue(result.evidence!!.dropDb > 3.0)
        assertFalse(result.claimFixed)
        val line = CorrectionPreview.claimLine(result)
        assertTrue(line.contains("去咕嗒"))
        assertTrue(line.contains("不宣称已修好"))
        assertFalse(line.startsWith("已修好"))
        assertFalse(line.contains("约下降"))
    }

    @Test
    fun deClickFloorMustNotPresent80AsMeasuredDrop() {
        val dry = LabCorrectionFixtures.clicksOnTone()
        val result = CorrectionPreview.preview(
            CorrectionRequest(
                startMs = 0,
                endMs = dry.durationMs,
                operator = CorrectionOperatorKind.DE_CLICK,
            ),
            dry,
        )
        val evidence = result.evidence!!
        assertTrue(evidence.isSufficient)
        assertTrue(evidence.hitRatioFloor)
        assertEquals(0, evidence.wetClickCount)
        assertEquals(0.0, evidence.wetClickEnergy, 1e-18)
        assertEquals(80.0, evidence.dropDb, 0.01) // 内部仍保留地板上限，但不得当作测得下降写给用户
        assertFalse(result.claimFixed)
        assertNull(evidence.displayDropDb())
        val line = CorrectionPreview.claimLine(result)
        val viaMeasured = CorrectionText.measuredDrop(evidence, result.operatorDescription)
        for (visible in listOf(line, viaMeasured)) {
            assertTrue(visible.contains("干侧检出"))
            assertTrue(visible.contains("${evidence.dryClickCount}"))
            assertTrue(visible.contains("检测下限"))
            assertTrue(visible.contains("比值地板 1e-4"))
            assertTrue(visible.contains("80 dB 上限"))
            assertTrue(visible.contains("不是听感上测到了 80 dB"))
            assertTrue(visible.contains("不宣称已修好"))
            assertFalse(visible.contains("约下降 80"))
            assertFalse(visible.contains("下降 80.0 dB"))
            assertFalse(visible.contains("下降 80 dB"))
            assertFalse(visible.contains("短脉冲残差约下降"))
        }
    }

    @Test
    fun deClickOnCleanToneIsNotSufficientEvidence() {
        val dry = LabCorrectionFixtures.cleanTone()
        val result = CorrectionPreview.preview(
            CorrectionRequest(
                startMs = 0,
                endMs = dry.durationMs,
                operator = CorrectionOperatorKind.DE_CLICK,
            ),
            dry,
        )
        assertEquals(CorrectionStatus.PREVIEW, result.status)
        assertNotNull(result.evidence)
        assertEquals(0, result.evidence!!.dryClickCount)
        assertFalse(result.evidence!!.isSufficient)
        assertFalse(result.claimFixed)
        // 空频段上 dropDb 可能很大或为负；对用户只显示不足文案，不展示该 dB。
        assertNull(result.evidence!!.displayDropDb())
        val line = CorrectionPreview.claimLine(result)
        assertEquals(CorrectionText.NO_FIXED_WITHOUT_EVIDENCE, line)
        assertFalse(line.contains("dB"))
        assertFalse(line.contains("下降"))
    }

    @Test
    fun unsupportedDeEssIsRejectedWithoutWetAudio() {
        val dry = LabCorrectionFixtures.humUnderTone()
        val result = CorrectionPreview.preview(
            CorrectionRequest(
                startMs = 0,
                endMs = dry.durationMs,
                operator = null,
                unsupportedKind = UnsupportedCorrectionKind.DE_ESS,
            ),
            dry,
        )
        assertEquals(CorrectionStatus.REJECTED, result.status)
        assertNull(result.wet)
        assertNull(result.evidence)
        assertFalse(result.claimFixed)
        assertTrue(result.rejectReason!!.contains("去齿音"))
        assertTrue(result.rejectReason!!.contains("未改音频"))
    }

    @Test
    fun retainCreatesBeforeAfterOnSameRangeAndDiscardClearsIt() {
        val dry = LabCorrectionFixtures.humUnderTone()
        val started = CorrectionWorkflow.start(LabCorrectionFixtures.HUM_FILE_ID, dry)
        val previewed = CorrectionWorkflow.preview(
            started,
            CorrectionRequest(
                startMs = 200,
                endMs = 800,
                operator = CorrectionOperatorKind.BAND_ATTENUATE,
                band = LabCorrectionFixtures.INTERFERER_BAND,
                attenuationDb = 12.0,
            ),
        )
        val retained = CorrectionWorkflow.retain(previewed)
        assertNotNull(retained.retained)
        assertEquals(200L, retained.retained!!.rangeStartMs)
        assertEquals(800L, retained.retained!!.rangeEndMs)
        assertFalse(retained.retained!!.claimFixed)
        val pair = retained.beforeAfter()
        assertNotNull(pair)
        val discarded = CorrectionWorkflow.discard(retained)
        assertNull(discarded.retained)
        assertNull(discarded.preview)
        assertNull(discarded.beforeAfter())
    }

    @Test
    fun practiceModeHidesCorrectionAndDoesNotRelabelItAsPerformance() {
        val dry = LabCorrectionFixtures.clicksOnTone()
        val session = CorrectionWorkflow.retain(
            CorrectionWorkflow.preview(
                CorrectionWorkflow.start(LabCorrectionFixtures.CLICK_FILE_ID, dry),
                CorrectionRequest(
                    startMs = 0,
                    endMs = dry.durationMs,
                    operator = CorrectionOperatorKind.DE_CLICK,
                ),
            ),
        )
        assertNull(CorrectionModeGate.professionalReadout(ViewMode.PRACTICE, session))
        assertFalse(CorrectionModeGate.leaksCorrectionIntoPractice(ViewMode.PRACTICE, session))
        val practice = CorrectionModeGate.practiceReadout(
            ViewMode.PRACTICE,
            earlyExitMessage = ReviewText.INDETERMINATE_COUNTS_ADVICE,
            practiceLines = listOf("不得使用的改善结论"),
        )
        assertNotNull(practice)
        assertTrue(practice!!.contains(ReviewText.INDETERMINATE_COUNTS_ADVICE))
        assertTrue(practice.contains(CorrectionText.PRACTICE_HIDES_CORRECTION))
        assertFalse(practice.contains("修后"))
        val pro = CorrectionModeGate.professionalReadout(ViewMode.PROFESSIONAL, session)
        assertNotNull(pro)
        assertTrue(pro!!.contains(CorrectionText.NOT_PERFORMANCE))
        assertTrue(pro.contains(CorrectionText.SOURCE_NOTE))
        assertFalse(CorrectionModeGate.leaksPracticeImprovementIntoCorrection(pro))
        assertFalse(pro.contains("缺音"))
        // NOT_PERFORMANCE 会写「不是练习模式的第一遍或重奏」以分轨，不能用光秃的「重奏」二字误杀。
        assertTrue(pro.contains(CorrectionText.NOT_PERFORMANCE))
        assertFalse(pro.contains("重奏已对准"))
        assertFalse(pro.contains("重奏改善"))
        assertFalse(pro.contains("第一遍缺音"))
    }
}
