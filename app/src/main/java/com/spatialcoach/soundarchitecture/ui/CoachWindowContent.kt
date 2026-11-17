package com.spatialcoach.soundarchitecture.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.spatialcoach.soundarchitecture.audio.AnalysisBus
import com.spatialcoach.soundarchitecture.audio.PracticeFragments
import com.spatialcoach.soundarchitecture.audio.ReviewText
import com.spatialcoach.soundarchitecture.audio.ViewMode
import kotlin.math.roundToInt

@Composable
fun CoachWindowContent() {
    val frame = AnalysisBus.frame
    val cue = AnalysisBus.practiceCue
    val practice = AnalysisBus.mode == ViewMode.PRACTICE
    val alignment = AnalysisBus.lastAlignment
    val comparison = AnalysisBus.comparison
    Column(
        Modifier
            .fillMaxSize()
            .background(WindowTheme.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(if (practice) "练习提示" else "练习提示（专业模式也可对照）", color = WindowTheme.Amber, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text("音准  ${cue.pitchLabel}", color = WindowTheme.Amber, fontSize = 18.sp)
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
                "${frame.noteName ?: "—"}    ${"%.1f".format(pitchHz)} Hz    ${signedCents(cents)}",
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
        Text("${AnalysisBus.INPUT_LABEL}。${AnalysisBus.DEVICE_NOTE}。", color = WindowTheme.Ink, fontSize = 12.sp)

        Spacer(Modifier.height(24.dp))
        Text("演奏复验", color = WindowTheme.Amber, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text(AnalysisBus.REVIEW_NOTE, color = WindowTheme.Ink, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "片段 ${PracticeFragments.ID_V1} · 120 BPM · 原点 0 ms · 四拍 A4 C5 G4 E4。${ReviewText.SOURCE_DISCLAIMER}",
            color = WindowTheme.Ink,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(ReviewText.LIMITS, color = WindowTheme.Ink, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Text(AnalysisBus.practiceStatus, color = WindowTheme.Good, fontSize = 14.sp)
        Spacer(Modifier.height(12.dp))
        ReviewAction("播放参考（对准、准点）") {
            AnalysisBus.requestPracticePlay(PracticeFragments.Rendition.REFERENCE, null)
        }
        ReviewAction("记为第一遍（内置含偏差示范）") {
            AnalysisBus.requestPracticePlay(PracticeFragments.Rendition.FIRST_TAKE, "first")
        }
        ReviewAction("记为重奏（内置已改善示范）") {
            AnalysisBus.requestPracticePlay(PracticeFragments.Rendition.RETAKE, "retake")
        }
        ReviewAction("比较第一遍与重奏") { AnalysisBus.compareTakes() }
        ReviewAction("恢复舞台演示回放") { AnalysisBus.requestResumeDemo() }

        if (alignment != null) {
            Spacer(Modifier.height(16.dp))
            Text("本遍事件（相对参考）", color = WindowTheme.Amber, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text(ReviewText.subscoreLine("音准", alignment.pitch), color = WindowTheme.AmberDim, fontSize = 13.sp)
            Text(ReviewText.subscoreLine("节奏", alignment.rhythm), color = WindowTheme.AmberDim, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            for (item in alignment.items) {
                Text(ReviewText.alignmentLine(item), color = WindowTheme.Ink, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
            }
        }

        if (comparison != null) {
            Spacer(Modifier.height(16.dp))
            Text("第一遍与重奏比较", color = WindowTheme.Amber, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            if (!comparison.comparable) {
                Text(comparison.rejectedReason ?: "不能比较", color = WindowTheme.Good, fontSize = 14.sp)
            } else {
                val a = comparison.takeA
                val b = comparison.takeB
                if (a != null && b != null) {
                    Text("音准  第一遍 ${scoreText(a.pitch)}  ·  重奏 ${scoreText(b.pitch)}", color = WindowTheme.AmberDim, fontSize = 13.sp)
                    Text("节奏  第一遍 ${scoreText(a.rhythm)}  ·  重奏 ${scoreText(b.rhythm)}", color = WindowTheme.AmberDim, fontSize = 13.sp)
                    Text("没有用总分代替分项。时差未扣除整段统一延迟。", color = WindowTheme.Ink, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("第一遍", color = WindowTheme.AmberDim, fontSize = 13.sp)
                    for (item in a.items) {
                        Text(ReviewText.alignmentLine(item), color = WindowTheme.Ink, fontSize = 12.sp)
                        Spacer(Modifier.height(3.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("重奏", color = WindowTheme.AmberDim, fontSize = 13.sp)
                    for (item in b.items) {
                        Text(ReviewText.alignmentLine(item), color = WindowTheme.Ink, fontSize = 12.sp)
                        Spacer(Modifier.height(3.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("建议（有对照证据，不是 DSP 修音）", color = WindowTheme.Amber, fontSize = 14.sp)
                for (line in comparison.suggestions) {
                    Spacer(Modifier.height(4.dp))
                    Text(line, color = WindowTheme.Good, fontSize = 13.sp)
                }
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
