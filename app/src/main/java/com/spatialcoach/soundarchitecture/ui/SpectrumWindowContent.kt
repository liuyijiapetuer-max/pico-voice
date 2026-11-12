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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.spatialcoach.soundarchitecture.audio.AnalysisBus
import com.spatialcoach.soundarchitecture.audio.Fft
import com.spatialcoach.soundarchitecture.audio.ViewMode
import kotlin.math.log10
import kotlin.math.max

@Composable
fun SpectrumWindowContent() {
    val frame = AnalysisBus.frame
    val professional = AnalysisBus.mode == ViewMode.PROFESSIONAL
    val peakHz = if (frame.spectrum.isNotEmpty()) Fft.peakFrequency(frame.spectrum, frame.sampleRate) else 0.0
    Column(
        Modifier
            .fillMaxSize()
            .background(WindowTheme.Background)
            .padding(20.dp),
    ) {
        Text("频谱", color = WindowTheme.Amber, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        if (professional) {
            val peakText = if (peakHz > 0) "${peakHz.toInt()} Hz" else "—"
            Text(
                "峰值约 $peakText    分辨 ${"%.1f".format(frame.spectrumHzPerBin)} Hz/格",
                color = WindowTheme.Ink,
                fontSize = 13.sp,
            )
        } else {
            Text("练习模式：这里只提示声音主要在哪一带。", color = WindowTheme.Ink, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(WindowTheme.Panel),
        ) {
            val bins = frame.spectrum
            if (bins.isEmpty()) return@Canvas
            val n = bins.size
            val maxMag = bins.max().toDouble().coerceAtLeast(1e-6)
            val usable = (n * 0.85f).toInt().coerceAtLeast(8)
            val barW = size.width / usable
            for (i in 0 until usable) {
                val mag = bins[i].toDouble()
                val h = (mag / maxMag).toFloat().coerceIn(0f, 1f) * size.height * 0.92f
                drawRect(
                    color = WindowTheme.Amber.copy(alpha = 0.85f),
                    topLeft = Offset(i * barW, size.height - h),
                    size = Size(max(1f, barW - 1f), h),
                )
            }
            if (professional) {
                val marks = listOf(125, 250, 500, 1000, 2000, 4000)
                for (hz in marks) {
                    val x = (hz / frame.spectrumHzPerBin / usable.toDouble() * usable * barW).toFloat()
                    if (x in 0f..size.width) {
                        drawLine(WindowTheme.Line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    }
                }
            }
        }
        if (professional) {
            Spacer(Modifier.height(8.dp))
            Text("刻度：125 · 250 · 500 · 1k · 2k · 4k Hz", color = WindowTheme.Ink, fontSize = 12.sp)
        }
    }
}
