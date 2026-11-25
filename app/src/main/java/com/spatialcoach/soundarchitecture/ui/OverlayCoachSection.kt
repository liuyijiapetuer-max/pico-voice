package com.spatialcoach.soundarchitecture.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.spatialcoach.soundarchitecture.audio.AnalysisBus
import com.spatialcoach.soundarchitecture.audio.EvaluationPolicy
import com.spatialcoach.soundarchitecture.audio.OverlayAuditionSide
import com.spatialcoach.soundarchitecture.audio.PracticeFragments
import com.spatialcoach.soundarchitecture.audio.SpatialOverlay

/** 练习窗里的叠合/试听/正交控制。数值只在选中构件后展开，不另开悬浮面板。 */
@Composable
fun OverlayCoachSection() {
    val scene = AnalysisBus.overlayScene
    Text("空间叠合", color = WindowTheme.CyanGlass, fontSize = 18.sp)
    Spacer(Modifier.height(6.dp))
    Text(scene.sourceBanner, color = WindowTheme.Champagne, fontSize = 13.sp)
    Spacer(Modifier.height(4.dp))
    Text(scene.scopeLine, color = WindowTheme.Ink, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Text(EvaluationPolicy.UNANCHORED_PLACEMENT, color = WindowTheme.Ink, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Text(EvaluationPolicy.RAY_DEFAULT, color = WindowTheme.Ink, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Text("当前视图  ${SpatialOverlay.viewLabel(scene.view)}", color = WindowTheme.AmberDim, fontSize = 13.sp)
    Spacer(Modifier.height(4.dp))
    Text(AnalysisBus.overlayStatus, color = WindowTheme.Good, fontSize = 13.sp)
    val selected = scene.selected
    if (selected != null) {
        Spacer(Modifier.height(6.dp))
        Text("已选中构件，数值仅在此时展开。", color = WindowTheme.Champagne, fontSize = 12.sp)
        SpatialOverlay.selectionReadout(scene)?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = WindowTheme.Amber, fontSize = 14.sp)
        }
    }
    OverlayAction("一键正交视图") { AnalysisBus.cycleOverlayView() }
    OverlayAction("试听参考（A）") { AnalysisBus.requestOverlayAudition(OverlayAuditionSide.REFERENCE) }
    OverlayAction("试听实际（B）") { AnalysisBus.requestOverlayAudition(OverlayAuditionSide.PERFORMANCE) }
    Spacer(Modifier.height(10.dp))
    Text(EvaluationPolicy.DEMO_LOOP_NOTE, color = WindowTheme.Ink, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Text(
        "提纲：0–15 秒用真实输入或已标注预录生成结构；15–35 秒叠合；35–55 秒选中错音并试听；55–75 秒重奏并显示变化；75–90 秒展示测试证据与项目名称。本轮未做用户对照或真机录屏。",
        color = WindowTheme.Ink,
        fontSize = 12.sp,
    )
    Spacer(Modifier.height(8.dp))
    OverlayAction("播放内置漏音示范") { AnalysisBus.requestDemoPlay(PracticeFragments.Rendition.MISSING_TAKE) }
}

@Composable
private fun OverlayAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = WindowTheme.Amber,
        fontSize = 15.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}
