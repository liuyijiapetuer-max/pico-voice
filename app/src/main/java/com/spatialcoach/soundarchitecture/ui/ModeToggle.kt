package com.spatialcoach.soundarchitecture.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.spatialcoach.soundarchitecture.audio.AnalysisBus
import com.spatialcoach.soundarchitecture.audio.ViewMode

/** 一键切换专业/练习。同一帧的读数和舞台可视化都读 [AnalysisBus.mode]。 */
@Composable
fun ModeToggle(modifier: Modifier = Modifier, color: Color = WindowTheme.Amber) {
    val practice = AnalysisBus.mode == ViewMode.PRACTICE
    Row(
        modifier
            .clickable { AnalysisBus.toggleMode() }
            .background(Color(0x33E8C48A))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            if (practice) "切换到专业模式" else "切换到练习模式",
            color = color,
            fontSize = 15.sp,
        )
    }
}
