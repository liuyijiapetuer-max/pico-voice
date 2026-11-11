package com.spatialcoach.soundarchitecture.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text

/**
 * M1 占位舞台：独立工程能启动进入 DefaultStage，并看到可见内容。
 * 音频内核与双模式可视化属于后续里程碑，本表面先确认空壳可安装、可进入。
 */
@Composable
fun PlaceholderStageContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04060B)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "声之建筑",
            color = Color(0xFFE8C48A),
            fontSize = 22.sp,
            modifier = Modifier.padding(24.dp),
        )
    }
}