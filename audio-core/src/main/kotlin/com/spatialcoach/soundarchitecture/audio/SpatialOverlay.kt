package com.spatialcoach.soundarchitecture.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class OverlayViewKind {
    PERSPECTIVE,
    ORTHO_PITCH,
    ORTHO_RHYTHM,
}

enum class OverlayStyle {
    WIREFRAME,
    SOLID,
    DASHED,
    GAP,
    DEVIATION,
}

enum class OverlayRole {
    REFERENCE,
    PERFORMANCE,
    PREVIOUS,
    DEVIATION,
    MISSING,
    UNKNOWN,
}

enum class OverlayAuditionSide {
    REFERENCE,
    PERFORMANCE,
}

data class OverlayVec3(
    val x: Float,
    val y: Float,
    val z: Float,
)

data class OverlayMember(
    val id: String,
    val role: OverlayRole,
    val style: OverlayStyle,
    val referenceIndex: Int? = null,
    val performanceIndex: Int? = null,
    val target: TargetNote? = null,
    val event: NoteEvent? = null,
    val center: OverlayVec3,
    val size: OverlayVec3,
    val centsVsTarget: Double? = null,
    val timingErrorMs: Long? = null,
    val label: String? = null,
    val selectable: Boolean = true,
    val auditionStartMs: Long? = null,
    val auditionEndMs: Long? = null,
) {
    val entityName: String get() = id
}

data class OverlayScene(
    val reference: PracticeReference,
    val source: InputSource,
    val view: OverlayViewKind,
    val selectedId: String?,
    val members: List<OverlayMember>,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val widthMeters: Float = SpatialOverlay.DEFAULT_WIDTH_M,
    val placementNote: String = EvaluationPolicy.UNANCHORED_PLACEMENT,
    val sourceBanner: String,
    val scopeLine: String,
) {
    val selected: OverlayMember? get() = selectedId?.let { id -> members.firstOrNull { it.id == id } }
}

data class OverlayAuditionPlan(
    val available: Boolean,
    val side: OverlayAuditionSide,
    val clip: PcmClip? = null,
    val reason: String? = null,
    val prerecorded: Boolean,
    val banner: String,
)

/**
 * 把参考与演奏对齐结果映射为时间（水平）× 音高（垂直）的空间叠合。
 * 构件长度等于时值，不把演奏时间自由拉伸到参考长度以掩盖节奏错误。
 * 几何是默认桌面摆放，不声称已锚定到真实琴键。
 */
object SpatialOverlay {
    const val DEFAULT_WIDTH_M = 1.2f
    const val OVERLAY_Z = -0.72f
    const val PREVIOUS_Z = -0.78f
    const val DEVIATION_Z = -0.68f
    const val SECTION_MIN = 0.035f
    const val SECTION_SPAN = 0.05f
    const val DEFAULT_VELOCITY = 0.55f
    const val MIN_LENGTH_M = 0.04f
    const val ENTITY_PREFIX = "SaOverlay_"
    const val ROOT_NAME = "SaOverlayRoot"

    fun isOverlayEntity(name: String): Boolean =
        name.startsWith(ENTITY_PREFIX)

    fun parseOverlayId(name: String): String? =
        name.takeIf { isOverlayEntity(it) }

    fun layout(
        reference: PracticeReference = PracticeFragments.REFERENCE_V1,
        current: TakeAlignment? = null,
        previous: TakeAlignment? = null,
        source: InputSource? = current?.take?.source ?: previous?.take?.source,
        view: OverlayViewKind = OverlayViewKind.PERSPECTIVE,
        selectedId: String? = null,
        widthMeters: Float = DEFAULT_WIDTH_M,
    ): OverlayScene {
        val resolvedSource = source ?: InputSource.FILE_SYNTHETIC
        val windowStart = 0L
        val windowEnd = windowEndMs(reference, current, previous)
        val members = ArrayList<OverlayMember>()

        reference.targets.forEachIndexed { index, target ->
            val aligned = current?.items?.firstOrNull { it.referenceIndex == index }
            members += referenceMember(index, target, windowStart, windowEnd, widthMeters)
            when {
                aligned == null && current == null -> Unit
                aligned == null || aligned.kind == AlignmentKind.MISSING ->
                    members += missingMember(index, target, windowStart, windowEnd, widthMeters)
                EvaluationPolicy.isLowConfidence(aligned.event) ->
                    members += unknownMember(index, aligned, windowStart, windowEnd, widthMeters, resolvedSource)
                else -> {
                    members += performanceMember(
                        role = OverlayRole.PERFORMANCE,
                        id = "${ENTITY_PREFIX}Perf_$index",
                        index = index,
                        item = aligned,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        width = widthMeters,
                        z = OVERLAY_Z,
                        source = resolvedSource,
                    )
                    deviationMember(index, target, aligned, windowStart, windowEnd, widthMeters, resolvedSource)
                        ?.let { members += it }
                }
            }
        }

        current?.items?.filter { it.kind == AlignmentKind.EXTRA || it.kind == AlignmentKind.UNALIGNED }?.forEachIndexed { extraIndex, item ->
            val ev = item.event ?: return@forEachIndexed
            val idIndex = 100 + extraIndex
            if (EvaluationPolicy.isLowConfidence(ev)) {
                members += unknownMember(idIndex, item, windowStart, windowEnd, widthMeters, resolvedSource)
            } else {
                members += performanceMember(
                    role = OverlayRole.PERFORMANCE,
                    id = "${ENTITY_PREFIX}Extra_$extraIndex",
                    index = item.referenceIndex ?: idIndex,
                    item = item,
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    width = widthMeters,
                    z = OVERLAY_Z,
                    source = resolvedSource,
                )
            }
        }

        previous?.items?.forEachIndexed { prevIndex, item ->
            val ev = item.event ?: return@forEachIndexed
            if (item.kind == AlignmentKind.MISSING) return@forEachIndexed
            members += performanceMember(
                role = OverlayRole.PREVIOUS,
                id = "${ENTITY_PREFIX}Prev_$prevIndex",
                index = item.referenceIndex ?: prevIndex,
                item = item,
                windowStart = windowStart,
                windowEnd = windowEnd,
                width = widthMeters,
                z = PREVIOUS_Z,
                source = previous.take.source,
            ).copy(selectable = false, label = "前次轮廓")
        }

        val selectedStillValid = selectedId != null && members.any { it.id == selectedId && it.selectable }
        return OverlayScene(
            reference = reference,
            source = resolvedSource,
            view = view,
            selectedId = if (selectedStillValid) selectedId else null,
            members = members,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            widthMeters = widthMeters,
            sourceBanner = EvaluationPolicy.sourceBanner(resolvedSource),
            scopeLine = EvaluationPolicy.scopeLine(resolvedSource),
        )
    }

    fun cycleView(current: OverlayViewKind): OverlayViewKind = when (current) {
        OverlayViewKind.PERSPECTIVE -> OverlayViewKind.ORTHO_PITCH
        OverlayViewKind.ORTHO_PITCH -> OverlayViewKind.ORTHO_RHYTHM
        OverlayViewKind.ORTHO_RHYTHM -> OverlayViewKind.PERSPECTIVE
    }

    fun viewLabel(view: OverlayViewKind): String = when (view) {
        OverlayViewKind.PERSPECTIVE -> "透视（默认桌面摆放）"
        OverlayViewKind.ORTHO_PITCH -> "音高正交（侧视时间×音高）"
        OverlayViewKind.ORTHO_RHYTHM -> "节奏正交（俯视时间轴）"
    }

    /**
     * 把默认摆放绕轨道中心变换到正交视图。纯计算，舞台根节点按此旋转/平移。
     * 俯视把音高从竖直改到进深，便于从上方看节奏，不声称已改相机锚定。
     */
    fun viewRotationDeg(view: OverlayViewKind): OverlayVec3 = when (view) {
        OverlayViewKind.PERSPECTIVE, OverlayViewKind.ORTHO_PITCH -> OverlayVec3(0f, 0f, 0f)
        OverlayViewKind.ORTHO_RHYTHM -> OverlayVec3(-90f, 0f, 0f)
    }

    fun selectionReadout(scene: OverlayScene, member: OverlayMember? = scene.selected): String? {
        if (member == null || scene.selectedId != member.id) return null
        val beat = member.target?.let { target ->
            val idx = scene.reference.targets.indexOf(target)
            if (idx >= 0) "第${idx + 1}拍" else null
        } ?: member.event?.beatIndex?.let { "第${it}拍" }
        val name = member.target?.noteName ?: member.event?.noteName ?: "—"
        return when (member.role) {
            OverlayRole.MISSING -> listOfNotNull(beat, "漏音", "谱面目标$name", "此处为空缺").joinToString("  ")
            OverlayRole.UNKNOWN -> listOfNotNull(beat, EvaluationPolicy.UNKNOWN_LABEL, EvaluationPolicy.LOW_CONFIDENCE_HINT).joinToString("  ")
            OverlayRole.REFERENCE -> {
                val dur = member.target?.durationMs?.let { "时值 ${it} ms" }
                listOfNotNull("参考线框", beat, name, dur).joinToString("  ")
            }
            OverlayRole.PREVIOUS -> "前次轮廓（保留对照，不是当前遍）。"
            OverlayRole.DEVIATION, OverlayRole.PERFORMANCE -> {
                val cents = member.centsVsTarget?.let { "音分 ${signedCents(it)}" }
                val timing = member.timingErrorMs?.let { "时差 ${signedMs(it)}" }
                val midiNote = if (!EvaluationPolicy.canEvaluateAcousticIntonation(scene.source)) {
                    "按键 $name"
                } else {
                    "演奏 $name"
                }
                val parts = mutableListOf(midiNote)
                beat?.let { parts += it }
                if (cents != null) parts += cents else if (!EvaluationPolicy.canEvaluateAcousticIntonation(scene.source)) {
                    parts += "不发布音分"
                }
                timing?.let { parts += it }
                parts += scene.scopeLine
                parts.joinToString("  ")
            }
        }
    }

    fun auditionPlan(
        scene: OverlayScene,
        side: OverlayAuditionSide,
        member: OverlayMember? = scene.selected,
        sampleRate: Int = PracticeFragments.SAMPLE_RATE,
    ): OverlayAuditionPlan {
        val banner = EvaluationPolicy.sourceBanner(scene.source)
        val prerecorded = EvaluationPolicy.isPrerecorded(scene.source) || side == OverlayAuditionSide.REFERENCE
        if (member == null || scene.selectedId != member.id) {
            return OverlayAuditionPlan(
                available = false,
                side = side,
                reason = "请先用控制器射线选中一个构件，再试听参考或实际。",
                prerecorded = prerecorded,
                banner = banner,
            )
        }
        if (member.role == OverlayRole.MISSING && side == OverlayAuditionSide.PERFORMANCE) {
            return OverlayAuditionPlan(
                available = false,
                side = side,
                reason = "该处是漏音空缺，没有可试听的实际演奏片段。",
                prerecorded = prerecorded,
                banner = banner,
            )
        }
        if (member.role == OverlayRole.UNKNOWN && side == OverlayAuditionSide.PERFORMANCE) {
            return OverlayAuditionPlan(
                available = false,
                side = side,
                reason = "该音标记为「未知」，不提供确定的实际片段试听。",
                prerecorded = prerecorded,
                banner = banner,
            )
        }
        return when (side) {
            OverlayAuditionSide.REFERENCE -> {
                val target = member.target ?: scene.reference.targets.getOrNull(member.referenceIndex ?: -1)
                if (target == null) {
                    OverlayAuditionPlan(false, side, reason = "没有对应的参考音符。", prerecorded = true, banner = EvaluationPolicy.PRERECORDED_BANNER)
                } else {
                    val notes = listOf(
                        PracticeFragments.SynthNote(
                            startMs = 0L,
                            durationMs = target.durationMs.coerceAtLeast(80L),
                            frequencyHz = target.frequencyHz ?: Tuning.A4_HZ,
                        ),
                    )
                    val samples = PracticeFragments.renderNotes(notes, sampleRate, tailMs = 80L)
                    OverlayAuditionPlan(
                        available = true,
                        side = side,
                        clip = PcmClip(
                            samples = samples,
                            sampleRate = sampleRate,
                            label = "参考试听 ${target.noteName ?: "—"}（预录合成，不是实时）",
                            sourceName = "practice-melody-v1 reference",
                        ),
                        prerecorded = true,
                        banner = EvaluationPolicy.PRERECORDED_BANNER,
                    )
                }
            }
            OverlayAuditionSide.PERFORMANCE ->
                performanceClip(scene, member, sampleRate, banner, prerecorded)
        }
    }

    fun performanceClip(
        scene: OverlayScene,
        member: OverlayMember,
        sampleRate: Int = PracticeFragments.SAMPLE_RATE,
        banner: String = scene.sourceBanner,
        prerecorded: Boolean = EvaluationPolicy.isPrerecorded(scene.source),
        renditionId: String? = null,
    ): OverlayAuditionPlan {
        val ev = member.event
        val hz = ev?.pitchHz
        val duration = ev?.durationMs?.coerceAtLeast(80L)
        if (hz == null || duration == null) {
            return OverlayAuditionPlan(
                available = false,
                side = OverlayAuditionSide.PERFORMANCE,
                reason = "没有保留该音的实际音频切片。用户导入的 WAV 当前只保存分析结果，只能试听参考；内置示范可按分析音高合成对照片段，但仍是预录，不是实时。",
                prerecorded = prerecorded,
                banner = banner,
            )
        }
        val knownRendition = renditionId
        if (scene.source == InputSource.USER_WAV_FILE && knownRendition == null) {
            return OverlayAuditionPlan(
                available = false,
                side = OverlayAuditionSide.PERFORMANCE,
                reason = "用户导入未保留原音频切片，无法试听实际演奏，仅可试听参考。",
                prerecorded = true,
                banner = EvaluationPolicy.PRERECORDED_BANNER,
            )
        }
        if (scene.source == InputSource.MIDI_EVENTS) {
            return OverlayAuditionPlan(
                available = false,
                side = OverlayAuditionSide.PERFORMANCE,
                reason = "MIDI 事件没有声学录音，不能用合成音色冒充实际演奏。可试听参考音符。",
                prerecorded = false,
                banner = EvaluationPolicy.MIDI_BANNER,
            )
        }
        val notes = listOf(
            PracticeFragments.SynthNote(startMs = 0L, durationMs = duration, frequencyHz = hz),
        )
        val samples = PracticeFragments.renderNotes(notes, sampleRate, tailMs = 80L)
        return OverlayAuditionPlan(
            available = true,
            side = OverlayAuditionSide.PERFORMANCE,
            clip = PcmClip(
                samples = samples,
                sampleRate = sampleRate,
                label = "实际试听 ${ev.noteName ?: "—"}（按分析音高合成对照，${banner}）",
                sourceName = ev.noteName ?: "performance",
            ),
            prerecorded = prerecorded,
            banner = banner,
        )
    }

    private fun windowEndMs(reference: PracticeReference, current: TakeAlignment?, previous: TakeAlignment?): Long {
        val refEnd = reference.targets.maxOfOrNull { it.endMs } ?: 1L
        val curEnd = current?.items?.mapNotNull { it.event?.endMs }?.maxOrNull() ?: 0L
        val prevEnd = previous?.items?.mapNotNull { it.event?.endMs }?.maxOrNull() ?: 0L
        return max(refEnd, max(curEnd, prevEnd)).coerceAtLeast(1L)
    }

    private fun xStart(ms: Long, windowStart: Long, windowEnd: Long, width: Float): Float {
        val t = ((ms - windowStart).toFloat() / (windowEnd - windowStart).toFloat()).coerceIn(0f, 1.5f)
        return -width / 2f + t * width
    }

    private fun xLength(durationMs: Long, windowStart: Long, windowEnd: Long, width: Float): Float {
        val raw = (durationMs.toFloat() / (windowEnd - windowStart).toFloat()) * width
        return raw.coerceAtLeast(MIN_LENGTH_M)
    }

    private fun section(velocity: Double = DEFAULT_VELOCITY.toDouble()): Float =
        SECTION_MIN + SECTION_SPAN * velocity.toFloat().coerceIn(0.15f, 1f)

    private fun referenceMember(
        index: Int,
        target: TargetNote,
        windowStart: Long,
        windowEnd: Long,
        width: Float,
    ): OverlayMember {
        val length = xLength(target.durationMs, windowStart, windowEnd, width)
        val start = xStart(target.startMs, windowStart, windowEnd, width)
        val y = StageMapping.yFromMidi((target.midi ?: 69).toDouble())
        val sec = section()
        return OverlayMember(
            id = "${ENTITY_PREFIX}Ref_$index",
            role = OverlayRole.REFERENCE,
            style = OverlayStyle.WIREFRAME,
            referenceIndex = index,
            target = target,
            center = OverlayVec3(start + length / 2f, y, OVERLAY_Z),
            size = OverlayVec3(length, sec, sec),
            label = target.noteName,
            auditionStartMs = target.startMs,
            auditionEndMs = target.endMs,
        )
    }

    private fun missingMember(
        index: Int,
        target: TargetNote,
        windowStart: Long,
        windowEnd: Long,
        width: Float,
    ): OverlayMember {
        val ref = referenceMember(index, target, windowStart, windowEnd, width)
        return ref.copy(
            id = "${ENTITY_PREFIX}Miss_$index",
            role = OverlayRole.MISSING,
            style = OverlayStyle.GAP,
            size = OverlayVec3(ref.size.x, 0.012f, ref.size.z),
            label = "漏音",
            selectable = true,
        )
    }

    private fun unknownMember(
        index: Int,
        item: AlignmentItem,
        windowStart: Long,
        windowEnd: Long,
        width: Float,
        source: InputSource,
    ): OverlayMember {
        val ev = item.event
        val startMs = ev?.startMs ?: item.target?.startMs ?: 0L
        val duration = ev?.durationMs ?: item.target?.durationMs ?: 200L
        val midi = ev?.midi?.toDouble() ?: item.target?.midi?.toDouble() ?: 69.0
        val length = xLength(duration, windowStart, windowEnd, width)
        val start = xStart(startMs, windowStart, windowEnd, width)
        return OverlayMember(
            id = "${ENTITY_PREFIX}Unk_$index",
            role = OverlayRole.UNKNOWN,
            style = OverlayStyle.DASHED,
            referenceIndex = item.referenceIndex ?: index,
            performanceIndex = item.performanceIndex,
            target = item.target,
            event = ev,
            center = OverlayVec3(start + length / 2f, StageMapping.yFromMidi(midi), OVERLAY_Z),
            size = OverlayVec3(length, section(0.35), section(0.35)),
            centsVsTarget = null,
            timingErrorMs = null,
            label = EvaluationPolicy.UNKNOWN_LABEL,
            auditionStartMs = ev?.startMs,
            auditionEndMs = ev?.endMs,
        )
    }

    private fun performanceMember(
        role: OverlayRole,
        id: String,
        index: Int,
        item: AlignmentItem,
        windowStart: Long,
        windowEnd: Long,
        width: Float,
        z: Float,
        source: InputSource,
    ): OverlayMember {
        val ev = item.event
        val startMs = ev?.startMs ?: item.target?.startMs ?: 0L
        val duration = ev?.durationMs ?: item.target?.durationMs ?: 200L
        val midi = when {
            ev?.pitchHz != null -> Tuning.midiFromHz(ev.pitchHz)
            ev?.midi != null -> ev.midi.toDouble()
            item.target?.midi != null -> item.target.midi.toDouble()
            else -> 69.0
        }
        val length = xLength(duration, windowStart, windowEnd, width)
        val start = xStart(startMs, windowStart, windowEnd, width)
        return OverlayMember(
            id = id,
            role = role,
            style = OverlayStyle.SOLID,
            referenceIndex = item.referenceIndex ?: index,
            performanceIndex = item.performanceIndex,
            target = item.target,
            event = ev,
            center = OverlayVec3(start + length / 2f, StageMapping.yFromMidi(midi), z),
            size = OverlayVec3(length, section(), section()),
            centsVsTarget = EvaluationPolicy.publishedCentsVsTarget(source, ev, item.centsVsTarget),
            timingErrorMs = EvaluationPolicy.publishedTimingMs(source, ev, item.timingVsProcessedReferenceMs ?: item.timingErrorMs),
            label = ev?.noteName,
            auditionStartMs = ev?.startMs,
            auditionEndMs = ev?.endMs,
        )
    }

    private fun deviationMember(
        index: Int,
        target: TargetNote,
        item: AlignmentItem,
        windowStart: Long,
        windowEnd: Long,
        width: Float,
        source: InputSource,
    ): OverlayMember? {
        val ev = item.event ?: return null
        val cents = EvaluationPolicy.publishedCentsVsTarget(source, ev, item.centsVsTarget)
        val timing = EvaluationPolicy.publishedTimingMs(source, ev, item.timingVsProcessedReferenceMs ?: item.timingErrorMs)
        val pitchOff = cents != null && abs(cents) >= 8.0
        val timeOff = timing != null && abs(timing) >= 40L
        val wrong = item.kind == AlignmentKind.WRONG_NOTE
        if (!pitchOff && !timeOff && !wrong) return null
        val actualMidi = ev.pitchHz?.let { Tuning.midiFromHz(it) } ?: ev.midi?.toDouble() ?: return null
        val refY = StageMapping.yFromMidi((target.midi ?: 69).toDouble())
        val actY = StageMapping.yFromMidi(actualMidi)
        val refStart = xStart(target.startMs, windowStart, windowEnd, width)
        val refLen = xLength(target.durationMs, windowStart, windowEnd, width)
        val actStart = xStart(ev.startMs, windowStart, windowEnd, width)
        val actLen = xLength(ev.durationMs, windowStart, windowEnd, width)
        val x = (refStart + refLen / 2f + actStart + actLen / 2f) / 2f
        val y = (refY + actY) / 2f
        val height = abs(actY - refY).coerceAtLeast(0.03f)
        val dx = abs((actStart + actLen / 2f) - (refStart + refLen / 2f)).coerceAtLeast(0.01f)
        return OverlayMember(
            id = "${ENTITY_PREFIX}Dev_$index",
            role = OverlayRole.DEVIATION,
            style = OverlayStyle.DEVIATION,
            referenceIndex = index,
            performanceIndex = item.performanceIndex,
            target = target,
            event = ev,
            center = OverlayVec3(x, y, DEVIATION_Z),
            size = OverlayVec3(dx.coerceAtMost(0.08f) + 0.02f, height, 0.018f),
            centsVsTarget = cents,
            timingErrorMs = timing,
            label = "偏差",
            selectable = true,
            auditionStartMs = ev.startMs,
            auditionEndMs = ev.endMs,
        )
    }

    private fun signedCents(cents: Double): String {
        val v = cents.roundToInt()
        return if (v > 0) "+$v" else "$v"
    }

    private fun signedMs(ms: Long): String = if (ms > 0) "+${ms}ms" else "${ms}ms"
}
