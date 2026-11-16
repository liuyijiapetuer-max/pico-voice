package com.spatialcoach.soundarchitecture.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.spatialcoach.soundarchitecture.audio.AnalysisBus
import com.spatialcoach.soundarchitecture.audio.FileDemoPlayer
import com.spatialcoach.soundarchitecture.audio.StageInteraction
import com.spatialcoach.soundarchitecture.audio.StageMapping
import com.spatialcoach.soundarchitecture.audio.ViewMode
import com.spatialcoach.soundarchitecture.ui.ModeToggle
import com.spatialcoach.soundarchitecture.ui.WorkspaceWindows
import kotlinx.coroutines.delay

@Composable
fun StageContent() {
    val context = LocalContext.current
    val navigator = LocalSpatialNavigator.current
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val player = remember {
        FileDemoPlayer(context) { frame ->
            AnalysisBus.publish(frame)
        }
    }
    val field = remember { SoundField() }

    LaunchedEffect(Unit) {
        runCatching { player.ensureDemoFile() }
            .onSuccess {
                ready = true
                player.start()
                AnalysisBus.markPlaying(true)
            }
            .onFailure {
                error = it.message ?: "演示音轨装载失败"
                AnalysisBus.errorMessage = error
            }
    }

    // 副窗必须错开打开，后一扇才能锚到前一扇，否则会叠在一起。
    LaunchedEffect(Unit) {
        delay(1500)
        for (id in WorkspaceWindows.AUX) {
            runCatching { navigator.openWindowContainer(id) }
            delay(900)
        }
    }

    DisposableEffect(field) {
        val runtime = SoundFieldRuntime(field)
        SoundFieldRuntime.install(runtime)
        registerSystem<SoundFieldSystem>()
        onDispose {
            unregisterSystem<SoundFieldSystem>()
            SoundFieldRuntime.uninstall()
            player.stop()
            AnalysisBus.markPlaying(false)
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0E14))) {
        if (!ready) {
            Text(
                error ?: "正在准备文件回放演示…",
                color = Color(0xFF8A97AD),
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        } else {
            SpatialView(
                Modifier
                    .fillMaxSize()
                    .pointerInput(field) {
                        detectSpatialPointerEvent(
                            context,
                            TargetEntity.any { entity ->
                                val name = entity.getName() ?: return@any false
                                StageMapping.isFanEntity(name) || StageMapping.isRulerEntity(name)
                            },
                        ) { infos ->
                            val hit = infos.lastOrNull { it.targetedEntity != null } ?: infos.lastOrNull()
                            if (hit == null) {
                                if (infos.any { it.isUpEvent() }) AnalysisBus.clearFollow()
                                return@detectSpatialPointerEvent false
                            }
                            val name = hit.targetedEntity?.getName()
                            if (name.isNullOrEmpty()) {
                                if (hit.isUpEvent() || !hit.pressed) AnalysisBus.clearFollow()
                                return@detectSpatialPointerEvent false
                            }
                            val pressed = hit.pressed && !hit.isUpEvent()
                            val result = StageInteraction.onHit(
                                entityName = name,
                                x = hit.x,
                                y = hit.y,
                                pressed = pressed,
                                frame = AnalysisBus.frame,
                            )
                            if (result == null) {
                                if (!pressed) AnalysisBus.clearFollow()
                                return@detectSpatialPointerEvent false
                            }
                            AnalysisBus.updateProbe(result.first, result.second)
                            true
                        }
                    },
            ) { content, _ ->
                content.addEntity(field.root)
                content.addEntity(field.floorEntity)
            }
            StageHud(Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun StageHud(modifier: Modifier = Modifier) {
    val mode = AnalysisBus.mode
    val frame = AnalysisBus.frame
    val playing = AnalysisBus.playing
    Column(
        modifier
            .padding(20.dp)
            .background(Color(0xCC0B0E14))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("声之建筑", color = Color(0xFFE8C48A), fontSize = 16.sp)
        val cents = frame.cents
        val pitchHz = frame.pitchHz
        val probe = AnalysisBus.probe
        val liveReadout = if (mode == ViewMode.PROFESSIONAL && frame.noteName != null && pitchHz != null && cents != null) {
            "${frame.noteName}  ${"%.1f".format(pitchHz)} Hz  ${"%.0f".format(cents)} 音分"
        } else {
            AnalysisBus.practiceCue.sentence
        }
        Text(liveReadout, color = Color(0xFFB8935C), fontSize = 14.sp)
        if (probe != null) {
            Text(
                probe.readout(mode),
                color = Color(0xFFE8C48A),
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (AnalysisBus.followingHand) {
                Text("标记正在跟手，松开后回到当前音高", color = Color(0xFF8A97AD), fontSize = 11.sp)
            }
        }
        ModeToggle(Modifier.padding(top = 8.dp), color = Color(0xFFE8C48A))
        Text(
            "${if (playing) "正在回放" else "已暂停"}  ·  ${AnalysisBus.INPUT_LABEL}  ·  ${AnalysisBus.DEVICE_NOTE}",
            color = Color(0xFF8A97AD),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
