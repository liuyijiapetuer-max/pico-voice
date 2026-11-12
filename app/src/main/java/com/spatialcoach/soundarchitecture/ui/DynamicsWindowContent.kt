package com.spatialcoach.soundarchitecture.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.spatialcoach.soundarchitecture.audio.AnalysisBus
import com.spatialcoach.soundarchitecture.audio.ViewMode
import kotlin.math.abs

@Composable
fun DynamicsWindowContent() {
    val frame = AnalysisBus.frame
    val professional = AnalysisBus.mode == ViewMode.PROFESSIONAL
    val cue = AnalysisBus.practiceCue
    val level = ((frame.dbfs + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat()
    Column(
        Modifier
            .fillMaxSize()
            .background(WindowTheme.Background)
            .padding(20.dp),
    ) {
        Text(if (professional) "动态" else "强弱", color = WindowTheme.Amber, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        if (professional) {
            Text(
                "RMS ${"%.1f".format(frame.dbfs)} dBFS    峰值 ${"%.3f".format(frame.peak)}",
                color = WindowTheme.Ink,
                fontSize = 13.sp,
            )
        } else {
            Text(cue.loudnessLabel, color = WindowTheme.AmberDim, fontSize = 16.sp)
        }
        Spacer(Modifier.height(16.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(WindowTheme.Panel),
        ) {
            drawRect(
                color = WindowTheme.Amber,
                topLeft = Offset(0f, 8f),
                size = Size(size.width * level, size.height - 16f),
            )
            if (professional) {
                val ticks = listOf(-48f, -24f, -12f, -6f, 0f)
                for (db in ticks) {
                    val x = ((db + 60f) / 60f).coerceIn(0f, 1f) * size.width
                    drawLine(WindowTheme.Line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5f)
                }
            }
        }
        if (professional) {
            Spacer(Modifier.height(8.dp))
            Text("刻度：-48  -24  -12  -6  0 dBFS", color = WindowTheme.Ink, fontSize = 12.sp)
            if (frame.onset) {
                Spacer(Modifier.height(12.dp))
                Text("起音", color = WindowTheme.Good, fontSize = 14.sp)
            }
            frame.onsetIntervalMs?.let {
                Text("距上一拍 ${it} ms", color = WindowTheme.Ink, fontSize = 13.sp)
            }
        }
    }
}
