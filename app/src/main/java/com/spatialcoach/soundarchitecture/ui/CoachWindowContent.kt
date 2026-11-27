package com.spatialcoach.soundarchitecture.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.spatialcoach.soundarchitecture.audio.AnalysisBus
import com.spatialcoach.soundarchitecture.audio.CorrectionListenSide
import com.spatialcoach.soundarchitecture.audio.CorrectionModeGate
import com.spatialcoach.soundarchitecture.audio.CorrectionOperatorKind
import com.spatialcoach.soundarchitecture.audio.CorrectionText
import com.spatialcoach.soundarchitecture.audio.PracticeFragments
import com.spatialcoach.soundarchitecture.audio.ReviewText
import com.spatialcoach.soundarchitecture.audio.UserTakeLoadResult
import com.spatialcoach.soundarchitecture.audio.UserWavImporter
import com.spatialcoach.soundarchitecture.audio.ViewMode
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CoachWindowContent() {
    val frame = AnalysisBus.frame
    val cue = AnalysisBus.practiceCue
    val practice = AnalysisBus.mode == ViewMode.PRACTICE
    val alignment = AnalysisBus.lastAlignment
    val demoAlignment = AnalysisBus.lastDemoAlignment
    val comparison = AnalysisBus.comparison
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var referenceConfirmed by remember { mutableStateOf(false) }
    var pendingSlot by remember { mutableStateOf<String?>(null) }

    val openWav = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val slot = pendingSlot
        pendingSlot = null
        if (uri == null) {
            AnalysisBus.onImportCancelled()
            return@rememberLauncherForActivityResult
        }
        if (!referenceConfirmed || slot == null) {
            AnalysisBus.failImport("未确认固定练习谱，或导入槽位无效。")
            return@rememberLauncherForActivityResult
        }
        val token = AnalysisBus.beginUserImport(slot)
        if (token == null) {
            AnalysisBus.failImport("导入槽位无效。")
            return@rememberLauncherForActivityResult
        }
        val job = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    UserWavImporter.load(
                        context,
                        uri,
                        takeId = slot,
                        referenceConfirmed = AnalysisBus.userImports.state.referenceConfirmed,
                        cancelled = { !isActive || !AnalysisBus.isImportCurrent(token) },
                    )
                }
                ensureActive()
                if (!AnalysisBus.isImportCurrent(token)) return@launch
                AnalysisBus.publishUserImport(token, result)
            } catch (e: CancellationException) {
                AnalysisBus.onImportJobCancelled(token)
                throw e
            }
        }
        AnalysisBus.replaceImportJob(slot, job)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(WindowTheme.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(if (practice) "练习提示" else "练习提示（专业模式也可对照）", color = WindowTheme.Amber, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text("当前帧 · 最近律音  ${cue.pitchLabel}", color = WindowTheme.Amber, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text("起音间隔  ${cue.rhythmLabel}", color = WindowTheme.AmberDim, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text("强弱  ${cue.loudnessLabel}", color = WindowTheme.AmberDim, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        Text(cue.sentence, color = WindowTheme.Good, fontSize = 18.sp)
        val cents = frame.cents
        val pitchHz = frame.pitchHz
        if (!practice && cents != null && pitchHz != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "最近律音 ${frame.noteName ?: "—"}    ${"%.1f".format(pitchHz)} Hz    ${signedCents(cents)}（不是谱面目标）",
                color = WindowTheme.Ink,
                fontSize = 13.sp,
            )
        }
        val probe = AnalysisBus.probe
        if (probe != null) {
            Spacer(Modifier.height(16.dp))
            Text(probe.readout(AnalysisBus.mode), color = WindowTheme.AmberDim, fontSize = 14.sp)
            if (AnalysisBus.followingHand) {
                Text("标记正在跟手，松开后回到当前音高。这是查看，不是修音。", color = WindowTheme.Ink, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        ModeToggle()
        Spacer(Modifier.height(16.dp))
        Text("${AnalysisBus.currentInputLabel}。${AnalysisBus.currentDeviceNote}。", color = WindowTheme.Ink, fontSize = 12.sp)

        Spacer(Modifier.height(16.dp))
        OverlayCoachSection()

        if (CorrectionModeGate.correctionVisible(AnalysisBus.mode)) {
            ProfessionalCorrectionPanel()
        }

        Spacer(Modifier.height(24.dp))
        Text("演奏复验 · 用户导入", color = WindowTheme.Amber, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(AnalysisBus.REVIEW_NOTE, color = WindowTheme.Ink, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "固定练习谱 ${PracticeFragments.ID_V1} · 120 BPM · 原点 0 ms · 四拍谱面目标 A4 C5 G4 E4。不支持其他曲目。${ReviewText.SOURCE_DISCLAIMER}",
            color = WindowTheme.Ink,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(ReviewText.NEAREST_VS_TARGET, color = WindowTheme.Ink, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(ReviewText.WINDOW_BIAS_NOTE, color = WindowTheme.Ink, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text("${ReviewText.LIMITS} ${ReviewText.REPEAT_LIMIT} 开头静音不会自动裁掉，也不会平移到与参考对齐。只接受 PCM16 单声道 44.1 kHz WAV。", color = WindowTheme.Ink, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Text(AnalysisBus.practiceStatus, color = WindowTheme.Good, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        ReviewAction(
            if (referenceConfirmed)
                "已确认：这是 practice-melody-v1 的演奏（再点可取消确认）"
            else
                "确认这是 practice-melody-v1（四拍 A4–C5–G4–E4）的演奏，然后才能导入",
        ) {
            referenceConfirmed = !referenceConfirmed
            AnalysisBus.setPracticeReferenceConfirmed(referenceConfirmed)
        }
        ReviewAction("导入第一遍（系统选文件）") {
            if (!referenceConfirmed) {
                AnalysisBus.failImport("请先确认固定练习谱。")
            } else {
                pendingSlot = "first"
                openWav.launch(arrayOf("audio/wav", "audio/x-wav", "audio/wave", "audio/*"))
            }
        }
        ReviewAction("导入重奏（系统选文件）") {
            if (!referenceConfirmed) {
                AnalysisBus.failImport("请先确认固定练习谱。")
            } else {
                pendingSlot = "retake"
                openWav.launch(arrayOf("audio/wav", "audio/x-wav", "audio/wave", "audio/*"))
            }
        }
        ReviewAction("比较第一遍与重奏") { AnalysisBus.compareTakes() }
        ReviewAction("恢复舞台演示回放") { AnalysisBus.requestResumeDemo() }

        AnalysisBus.takeFirst?.let { take ->
            Spacer(Modifier.height(12.dp))
            Text("已导入第一遍", color = WindowTheme.AmberDim, fontSize = 14.sp)
            Text(ReviewText.takeMetaLine(take), color = WindowTheme.Ink, fontSize = 12.sp)
        }
        AnalysisBus.takeRetake?.let { take ->
            Spacer(Modifier.height(8.dp))
            Text("已导入重奏", color = WindowTheme.AmberDim, fontSize = 14.sp)
            Text(ReviewText.takeMetaLine(take), color = WindowTheme.Ink, fontSize = 12.sp)
        }

        if (alignment != null) {
            Spacer(Modifier.height(16.dp))
            Text("最近一次用户导入（相对谱面目标 / 相对同处理参考）", color = WindowTheme.Amber, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(ReviewText.coverageLine("本遍", alignment.coverage), color = WindowTheme.AmberDim, fontSize = 13.sp)
            Text(ReviewText.subscoreLine("对准音音分（不含错音）", alignment.pitch), color = WindowTheme.AmberDim, fontSize = 13.sp)
            Text(ReviewText.subscoreLine("节奏相对谱面（含检测窗偏差）", alignment.rhythm), color = WindowTheme.AmberDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            for (item in alignment.items) {
                if (!alignment.coverage.countsDeterminate &&
                    (item.kind == com.spatialcoach.soundarchitecture.audio.AlignmentKind.MISSING ||
                        item.kind == com.spatialcoach.soundarchitecture.audio.AlignmentKind.EXTRA)
                ) {
                    continue
                }
                Text(ReviewText.alignmentLine(item), color = WindowTheme.Ink, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
            }
        }

        if (comparison != null) {
            Spacer(Modifier.height(16.dp))
            Text("用户第一遍与重奏比较", color = WindowTheme.Amber, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            if (!comparison.comparable) {
                Text(comparison.rejectedReason ?: "不能比较", color = WindowTheme.Good, fontSize = 14.sp)
            } else {
                val a = comparison.takeA
                val b = comparison.takeB
                if (a != null && b != null) {
                    Text(ReviewText.coverageLine("第一遍", a.coverage), color = WindowTheme.AmberDim, fontSize = 13.sp)
                    Text(ReviewText.coverageLine("重奏", b.coverage), color = WindowTheme.AmberDim, fontSize = 13.sp)
                    Text("对准音音分（不含错音）  第一遍 ${scoreText(a.pitch)}  ·  重奏 ${scoreText(b.pitch)}", color = WindowTheme.AmberDim, fontSize = 13.sp)
                    Text("不能用排除错音后的平均音分宣称整体改善。错音/缺音/多音须单列。", color = WindowTheme.Ink, fontSize = 12.sp)
                    Text(ReviewText.WINDOW_BIAS_NOTE, color = WindowTheme.Ink, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("建议（有对照证据，不是 DSP 修音）", color = WindowTheme.Amber, fontSize = 14.sp)
                for (line in comparison.suggestions) {
                    Spacer(Modifier.height(4.dp))
                    Text(line, color = WindowTheme.Good, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("内置合成示范（不是用户成果）", color = WindowTheme.Amber, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(ReviewText.DEMO_DISCLAIMER, color = WindowTheme.Ink, fontSize = 12.sp)
        ReviewAction("播放内置参考示范") { AnalysisBus.requestDemoPlay(PracticeFragments.Rendition.REFERENCE) }
        ReviewAction("播放内置偏差示范") { AnalysisBus.requestDemoPlay(PracticeFragments.Rendition.FIRST_TAKE) }
        ReviewAction("播放内置改善示范") { AnalysisBus.requestDemoPlay(PracticeFragments.Rendition.RETAKE) }
        if (demoAlignment != null) {
            Spacer(Modifier.height(8.dp))
            Text("最近一次内置示范（仅预览）", color = WindowTheme.AmberDim, fontSize = 14.sp)
            Text(ReviewText.coverageLine("示范", demoAlignment.coverage), color = WindowTheme.Ink, fontSize = 12.sp)
            for (item in demoAlignment.items) {
                Text(ReviewText.alignmentLine(item), color = WindowTheme.Ink, fontSize = 12.sp)
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}

@Composable
private fun ReviewAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = WindowTheme.Amber,
        fontSize = 15.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}

private fun scoreText(score: com.spatialcoach.soundarchitecture.audio.Subscore?): String =
    if (score == null) "无样本"
    else "平均偏差 ${score.meanAbs.roundToInt()} ${score.unit}（n=${score.count}）"

private fun signedCents(cents: Double): String {
    val v = cents.roundToInt()
    return if (v > 0) "+$v 音分" else "$v 音分"
}


@Composable
private fun ProfessionalCorrectionPanel() {
    val session = AnalysisBus.correctionSession
    val preview = session?.preview
    Spacer(Modifier.height(24.dp))
    Text("修音预览 · 实验室夹具（专业模式）", color = WindowTheme.Amber, fontSize = 18.sp)
    Spacer(Modifier.height(6.dp))
    Text(CorrectionText.SOURCE_NOTE, color = WindowTheme.Ink, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Text(CorrectionText.NOT_PERFORMANCE, color = WindowTheme.Ink, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Text(CorrectionText.DRAG_IS_VIEW_ONLY, color = WindowTheme.Ink, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Text(CorrectionText.ACCEPTANCE, color = WindowTheme.Ink, fontSize = 12.sp)
    Spacer(Modifier.height(10.dp))
    Text(AnalysisBus.correctionStatus, color = WindowTheme.Good, fontSize = 14.sp)
    Spacer(Modifier.height(8.dp))
    Text(
        "当前句段 ${AnalysisBus.correctionStartMs}–${AnalysisBus.correctionEndMs} ms · 操作 " +
            when (AnalysisBus.correctionOperator) {
                CorrectionOperatorKind.BAND_ATTENUATE -> "指定频段衰减（40–80 Hz，12 dB）"
                CorrectionOperatorKind.DE_CLICK -> "保守去咕嗒"
            },
        color = WindowTheme.AmberDim,
        fontSize = 13.sp,
    )
    Spacer(Modifier.height(8.dp))
    ReviewAction("加载实验室夹具：A4 + 50 Hz 干扰") { AnalysisBus.loadLabCorrectionHum() }
    ReviewAction("加载实验室夹具：A4 + 已知短脉冲") { AnalysisBus.loadLabCorrectionClicks() }
    ReviewAction("标记句段：整段") {
        val duration = session?.dry?.durationMs
        if (duration != null) {
            AnalysisBus.setCorrectionRange(0L, duration)
        }
    }
    ReviewAction("标记句段：200–800 ms") { AnalysisBus.setCorrectionRange(200L, 800L) }
    ReviewAction("选择指定频段衰减") { AnalysisBus.selectCorrectionOperator(CorrectionOperatorKind.BAND_ATTENUATE) }
    ReviewAction("选择保守去咕嗒") { AnalysisBus.selectCorrectionOperator(CorrectionOperatorKind.DE_CLICK) }
    ReviewAction("生成预览") { AnalysisBus.previewCorrection() }
    ReviewAction("听干声") { AnalysisBus.requestCorrectionListen(CorrectionListenSide.DRY) }
    ReviewAction("听湿声") { AnalysisBus.requestCorrectionListen(CorrectionListenSide.WET) }
    ReviewAction("保留修前/修后对照") { AnalysisBus.retainCorrection() }
    ReviewAction("撤销预览和对照") { AnalysisBus.discardCorrection() }
    val readout = CorrectionModeGate.professionalReadout(AnalysisBus.mode, session)
    if (readout != null) {
        Spacer(Modifier.height(10.dp))
        Text(readout, color = WindowTheme.Ink, fontSize = 12.sp)
    }
    if (preview != null) {
        Spacer(Modifier.height(8.dp))
        Text("预览证据（不是已修好，也不使用演奏复验文案）", color = WindowTheme.AmberDim, fontSize = 13.sp)
        for (line in CorrectionText.evidencePanelLines(preview)) {
            Spacer(Modifier.height(3.dp))
            Text(line, color = WindowTheme.Ink, fontSize = 12.sp)
        }
    }
    session?.retained?.let {
        Spacer(Modifier.height(8.dp))
        Text(CorrectionText.beforeAfterLine(session), color = WindowTheme.AmberDim, fontSize = 12.sp)
    }
}

