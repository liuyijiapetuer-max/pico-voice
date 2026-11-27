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
import com.spatialcoach.soundarchitecture.audio.CorrectionClipPlayer
import com.spatialcoach.soundarchitecture.audio.CorrectionModeGate
import com.spatialcoach.soundarchitecture.audio.CorrectionText
import com.spatialcoach.soundarchitecture.audio.EvaluationPolicy
import com.spatialcoach.soundarchitecture.audio.FileDemoPlayer
import com.spatialcoach.soundarchitecture.audio.LiveCaptureRequest
import com.spatialcoach.soundarchitecture.audio.LiveCaptureResult
import com.spatialcoach.soundarchitecture.audio.LiveInputKind
import com.spatialcoach.soundarchitecture.audio.MidiDeviceSource
import com.spatialcoach.soundarchitecture.audio.MidiTakeFactory
import com.spatialcoach.soundarchitecture.audio.MicrophoneTakeFactory
import com.spatialcoach.soundarchitecture.audio.MonoMicrophoneCapture
import com.spatialcoach.soundarchitecture.audio.OverlayAuditionSide
import com.spatialcoach.soundarchitecture.audio.PracticeSessionPlayer
import com.spatialcoach.soundarchitecture.audio.PracticeSessionRequest
import com.spatialcoach.soundarchitecture.audio.SpatialOverlay
import com.spatialcoach.soundarchitecture.audio.StageInteraction
import com.spatialcoach.soundarchitecture.audio.StageMapping
import com.spatialcoach.soundarchitecture.audio.ViewMode
import com.spatialcoach.soundarchitecture.ui.ModeToggle
import com.spatialcoach.soundarchitecture.ui.WindowTheme
import com.spatialcoach.soundarchitecture.ui.WorkspaceWindows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val practicePlayer = remember {
        PracticeSessionPlayer(context) { frame ->
            AnalysisBus.publish(frame)
        }
    }
    val correctionPlayer = remember {
        CorrectionClipPlayer(context) { frame ->
            AnalysisBus.publish(frame)
        }
    }
    val overlayPlayer = remember {
        CorrectionClipPlayer(context) { frame ->
            AnalysisBus.publish(frame)
        }
    }
    val micCapture = remember {
        MonoMicrophoneCapture(
            onFrame = { frame -> AnalysisBus.publish(frame) },
            onAutoStop = { AnalysisBus.requestStopLiveCapture() },
        )
    }
    val midiCapture = remember {
        MidiDeviceSource(
            context = context,
            onAutoStop = { AnalysisBus.requestStopLiveCapture() },
        )
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

    val practiceRequest = AnalysisBus.practiceRequest
    LaunchedEffect(practiceRequest?.id) {
        val request = practiceRequest ?: return@LaunchedEffect
        when (request.action) {
            PracticeSessionRequest.Action.PLAY -> {
                val rendition = request.rendition ?: return@LaunchedEffect
                overlayPlayer.stop()
                correctionPlayer.stop()
                player.stop()
                AnalysisBus.markPlaying(true)
                val frames = withContext(Dispatchers.IO) {
                    practicePlayer.play(rendition)
                }
                if (frames == null) {
                    AnalysisBus.onPracticeCancelled()
                } else {
                    AnalysisBus.completeDemo(frames, rendition)
                }
            }
            PracticeSessionRequest.Action.RESUME_DEMO -> {
                overlayPlayer.stop()
                correctionPlayer.stop()
                practicePlayer.stop()
                player.start()
                AnalysisBus.markPlaying(true)
            }
        }
    }

    val correctionListen = AnalysisBus.correctionListenRequest
    LaunchedEffect(correctionListen?.id) {
        val request = correctionListen ?: return@LaunchedEffect
        overlayPlayer.stop()
        practicePlayer.stop()
        player.stop()
        AnalysisBus.markPlaying(true)
        val completed = withContext(Dispatchers.IO) {
            correctionPlayer.play(request.clip)
        }
        if (completed) {
            AnalysisBus.onCorrectionListenFinished()
        } else {
            AnalysisBus.onCorrectionListenCancelled()
        }
    }

    val overlayListen = AnalysisBus.overlayAuditionRequest
    LaunchedEffect(overlayListen?.id) {
        val request = overlayListen ?: return@LaunchedEffect
        correctionPlayer.stop()
        practicePlayer.stop()
        player.stop()
        AnalysisBus.markPlaying(true)
        val completed = withContext(Dispatchers.IO) {
            overlayPlayer.play(request.clip)
        }
        if (completed) {
            AnalysisBus.onOverlayAuditionFinished()
        } else {
            AnalysisBus.onOverlayAuditionCancelled()
        }
    }

    val liveRequest = AnalysisBus.liveCaptureRequest
    LaunchedEffect(liveRequest?.id) {
        val request = liveRequest ?: return@LaunchedEffect
        when (request.action) {
            LiveCaptureRequest.Action.START -> {
                overlayPlayer.stop()
                correctionPlayer.stop()
                practicePlayer.stop()
                player.stop()
                AnalysisBus.markPlaying(false)
                val error = withContext(Dispatchers.IO) {
                    when (request.kind) {
                        LiveInputKind.MICROPHONE_MONO -> micCapture.start()
                        LiveInputKind.MIDI_EVENTS -> midiCapture.start()
                        null -> EvaluationPolicy.DEVICE_MISSING
                    }
                }
                if (error != null) {
                    AnalysisBus.onLiveCaptureFailed(error)
                } else {
                    AnalysisBus.onLiveCaptureStarted()
                }
            }
            LiveCaptureRequest.Action.STOP -> {
                val kind = request.kind
                val confirmed = AnalysisBus.userImports.state.referenceConfirmed
                val result = withContext(Dispatchers.IO) {
                    when (kind) {
                        LiveInputKind.MICROPHONE_MONO -> {
                            val recording = micCapture.stop()
                            if (recording == null) {
                                LiveCaptureResult.Failed(EvaluationPolicy.MIC_NO_AUDIO)
                            } else {
                                MicrophoneTakeFactory.fromPcm(
                                    samples = recording.samples,
                                    sampleRate = recording.sampleRate,
                                    takeId = AnalysisBus.nextLiveTakeId("mic"),
                                    sourceName = "android-audiorecord",
                                    referenceConfirmed = confirmed,
                                )
                            }
                        }
                        LiveInputKind.MIDI_EVENTS -> {
                            val (messages, endMs) = midiCapture.stop()
                            MidiTakeFactory.fromMessages(
                                messages = messages,
                                takeId = AnalysisBus.nextLiveTakeId("midi"),
                                sourceName = "android-midi",
                                referenceConfirmed = confirmed,
                                sessionEndMs = endMs,
                            )
                        }
                        null -> LiveCaptureResult.Failed(EvaluationPolicy.CAPTURE_CANCELLED)
                    }
                }
                AnalysisBus.completeLiveCapture(result)
            }
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
            overlayPlayer.stop()
            correctionPlayer.stop()
            practicePlayer.stop()
            player.stop()
            micCapture.stopAndDiscard()
            midiCapture.stopAndDiscard()
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
                                StageMapping.isFanEntity(name) ||
                                    StageMapping.isRulerEntity(name) ||
                                    StageMapping.isOverlayEntity(name)
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
                                overlay = AnalysisBus.overlayScene,
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
    val selected = AnalysisBus.overlayScene.selected
    Column(
        modifier
            .padding(20.dp)
            .background(Color(0xCC0B0E14))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("声之建筑", color = WindowTheme.Champagne, fontSize = 16.sp)
        Text(
            AnalysisBus.inputBanner,
            color = WindowTheme.Champagne,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (selected != null) {
            val readout = SpatialOverlay.selectionReadout(AnalysisBus.overlayScene)
            if (readout != null) {
                Text(readout, color = WindowTheme.CyanGlass, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            }
        } else {
            val cents = frame.cents
            val pitchHz = frame.pitchHz
            val liveReadout = if (mode == ViewMode.PROFESSIONAL && frame.noteName != null && pitchHz != null && cents != null) {
                "${frame.noteName}  ${"%.1f".format(pitchHz)} Hz  ${"%.0f".format(cents)} 音分"
            } else {
                AnalysisBus.practiceCue.sentence
            }
            Text(liveReadout, color = WindowTheme.AmberDim, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        }
        val probe = AnalysisBus.probe
        if (probe != null && probe.kind != com.spatialcoach.soundarchitecture.audio.InteractionProbe.Kind.OVERLAY) {
            Text(
                probe.readout(mode),
                color = WindowTheme.Champagne,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (AnalysisBus.followingHand) {
                Text("标记正在跟手，松开后回到当前音高。这是查看，不是修音。手势尚未通过稳定性测试。", color = WindowTheme.Ink, fontSize = 11.sp)
            }
        }
        Row(Modifier.padding(top = 8.dp)) {
            ModeToggle(color = WindowTheme.Champagne)
        }
        Text(
            "一键正交视图 · ${SpatialOverlay.viewLabel(AnalysisBus.overlayScene.view)}",
            color = WindowTheme.CyanGlass,
            fontSize = 14.sp,
            modifier = Modifier
                .padding(top = 8.dp)
                .clickable { AnalysisBus.cycleOverlayView() }
                .padding(vertical = 4.dp),
        )
        if (selected != null) {
            Text(
                "试听参考（A）",
                color = WindowTheme.Champagne,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { AnalysisBus.requestOverlayAudition(OverlayAuditionSide.REFERENCE) }
                    .padding(vertical = 4.dp),
            )
            Text(
                "试听实际（B）",
                color = WindowTheme.Champagne,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable { AnalysisBus.requestOverlayAudition(OverlayAuditionSide.PERFORMANCE) }
                    .padding(vertical = 4.dp),
            )
        }
        Text(
            "${if (AnalysisBus.liveCapture.isRecording) "正在采集" else if (playing) "正在回放" else "已暂停"}  ·  ${AnalysisBus.currentInputLabel}  ·  ${AnalysisBus.currentDeviceNote}",
            color = WindowTheme.Ink,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(EvaluationPolicy.UNANCHORED_PLACEMENT, color = WindowTheme.Ink, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        Text(EvaluationPolicy.RAY_DEFAULT, color = WindowTheme.Ink, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        if (CorrectionModeGate.correctionVisible(mode) && AnalysisBus.correctionSession != null) {
            Text(
                AnalysisBus.correctionStatus,
                color = WindowTheme.Champagne,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                CorrectionText.DRAG_IS_VIEW_ONLY + " " + CorrectionText.NOT_PERFORMANCE,
                color = WindowTheme.Ink,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (AnalysisBus.demoPausedForPractice) {
            Text(
                "演示回放已暂停，可用第三窗「恢复舞台演示回放」。${AnalysisBus.REVIEW_NOTE}",
                color = WindowTheme.Ink,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

