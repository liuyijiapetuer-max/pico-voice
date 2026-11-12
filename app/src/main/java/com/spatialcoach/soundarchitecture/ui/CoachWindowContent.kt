package com.spatialcoach.soundarchitecture.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.spatialcoach.soundarchitecture.audio.AnalysisBus
import com.spatialcoach.soundarchitecture.audio.ViewMode
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CoachWindowContent() {
    val frame = AnalysisBus.frame
    val cue = AnalysisBus.practiceCue
    val practice = AnalysisBus.mode == ViewMode.PRACTICE
    Column(
        Modifier
            .fillMaxSize()
            .background(WindowTheme.Background)
            .padding(20.dp),
    ) {
        Text(if (practice) "练习提示" else "练习提示（专业模式也可对照）", color = WindowTheme.Amber, fontSize = 20.sp)
        Spacer(Modifier.height(12.dp))
        Text("音准  ${cue.pitchLabel}", color = WindowTheme.Amber, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text("节奏  ${cue.rhythmLabel}", color = WindowTheme.AmberDim, fontSize = 16.sp)
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
        Spacer(Modifier.height(24.dp))
        Row(Modifier.clickable { AnalysisBus.toggleMode() }.padding(8.dp)) {
            Text(
                if (practice) "切换到专业模式" else "切换到练习模式",
                color = WindowTheme.Amber,
                fontSize = 15.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("${AnalysisBus.INPUT_LABEL}。${AnalysisBus.DEVICE_NOTE}。", color = WindowTheme.Ink, fontSize = 12.sp)
    }
}

private fun signedCents(cents: Double): String {
    val v = cents.roundToInt()
    return if (v > 0) "+$v 音分" else "$v 音分"
}
