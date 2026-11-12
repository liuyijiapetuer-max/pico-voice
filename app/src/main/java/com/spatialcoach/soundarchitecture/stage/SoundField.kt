package com.spatialcoach.soundarchitecture.stage

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.spatialcoach.soundarchitecture.audio.AnalysisFrame
import com.spatialcoach.soundarchitecture.audio.Tuning
import com.spatialcoach.soundarchitecture.audio.ViewMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * 主舞台上的空间可视化：前方一张随频谱起伏的扇面，中央一条音高尺，
 * 目标音和当前音是两个可量的空间位置。不是 24 柱回放，也不是灯光秀。
 */
class SoundField {

    val root: Entity = Entity().apply { setName("SoundFieldRoot") }

    private val wedges: List<ModelEntity>
    private val targetMarker: ModelEntity
    private val currentMarker: ModelEntity
    private val ruler: ModelEntity
    private val ticks: List<ModelEntity>
    private val errorStem: ModelEntity
    private val floor: ModelEntity

    init {
        val wedgeModel = SoundMeshes.unitWedge(WEDGE_SPAN)
        val wedgeRes = MeshResource.createWithMeshModel(
            model = wedgeModel,
            bounds = SoundMeshes.boundsOf(wedgeModel.positions),
            name = "SaSpectrumWedge",
        )
        val wedgeMat = UnlitMaterial.create().apply {
            setBaseColorTexture(SolidTextures.color(0xE6C48A7A.toInt()))
        }
        wedges = (0 until BANDS).map { i ->
            ModelEntity(wedgeRes, wedgeMat).apply {
                setName("SaBand_$i")
                val angleDeg = startDeg() + (i + 0.5f) * stepDeg()
                components[TransformComponent::class.java]?.apply {
                    setEulerAngles(EulerAngles(pitch = 0f, yaw = angleDeg, roll = 0f))
                    setScaleVector(Vector3(RADIUS_SCALE, 0.05f, RADIUS_SCALE))
                    setPosition(Vector3(0f, FLOOR_Y, 0f))
                }
                root.addChild(this)
            }
        }

        val rodModel = SoundMeshes.unitRod()
        val rodRes = MeshResource.createWithMeshModel(
            model = rodModel,
            bounds = SoundMeshes.boundsOf(rodModel.positions),
            name = "SaRod",
        )
        val rodMat = UnlitMaterial.create().apply {
            setBaseColorTexture(SolidTextures.color(0xFF8A97AD.toInt()))
        }
        ruler = ModelEntity(rodRes, rodMat).apply {
            setName("SaPitchRuler")
            components[TransformComponent::class.java]?.apply {
                setPosition(Vector3(0f, FLOOR_Y, RULER_Z))
                setScaleVector(Vector3(0.03f, RULER_HEIGHT, 0.03f))
            }
            root.addChild(this)
        }

        ticks = listOf(48, 60, 69, 72, 84).map { midi ->
            ModelEntity(rodRes, rodMat).apply {
                setName("SaTick_$midi")
                components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(0f, pitchY(midi.toDouble()), RULER_Z))
                    setScaleVector(Vector3(0.16f, 0.02f, 0.02f))
                }
                root.addChild(this)
            }
        }

        val targetMat = UnlitMaterial.create().apply {
            setBaseColorTexture(SolidTextures.color(0xFF7DCEA0.toInt()))
        }
        val currentMat = UnlitMaterial.create().apply {
            setBaseColorTexture(SolidTextures.color(0xFFE8C48A.toInt()))
        }
        val sphere = MeshResource.createSphere(0.07f)
        targetMarker = ModelEntity(sphere, targetMat).apply {
            setName("SaTargetPitch")
            components[TransformComponent::class.java]?.setPosition(Vector3(0f, pitchY(69.0), RULER_Z))
            root.addChild(this)
        }
        currentMarker = ModelEntity(sphere, currentMat).apply {
            setName("SaCurrentPitch")
            components[TransformComponent::class.java]?.setPosition(Vector3(0f, pitchY(69.0), RULER_Z))
            root.addChild(this)
        }

        val stemMat = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColorTexture(SolidTextures.color(0xCCE07A5F.toInt()))
            setCullingMode(MaterialCullingMode.NONE)
            setOpacity(0.85f)
        }
        errorStem = ModelEntity(rodRes, stemMat).apply {
            setName("SaPitchError")
            components[TransformComponent::class.java]?.setScaleVector(Vector3(0.02f, 0.01f, 0.02f))
            root.addChild(this)
        }

        val disc = SoundMeshes.disc(2.4f)
        val floorMat = UnlitMaterial.create().apply {
            setBaseColorTexture(SolidTextures.color(0xFF0B0E14.toInt()))
        }
        floor = ModelEntity(
            MeshResource.createWithMeshModel(
                model = disc,
                bounds = SoundMeshes.boundsOf(disc.positions),
                name = "SaFloor",
            ),
            floorMat,
        ).apply {
            setName("SaFloor")
            components[TransformComponent::class.java]?.setPosition(Vector3(0f, FLOOR_Y, 0f))
        }
    }

    val floorEntity: Entity get() = floor

    fun apply(frame: AnalysisFrame, mode: ViewMode) {
        val grow = dynamicsScale(frame.dbfs)
        updateWedges(frame, mode, grow)
        updatePitch(frame)
    }

    private fun updateWedges(frame: AnalysisFrame, mode: ViewMode, grow: Float) {
        val spectrum = frame.spectrum
        val showSpectrum = mode == ViewMode.PROFESSIONAL
        for (i in wedges.indices) {
            val energy = if (spectrum.isEmpty()) 0f else bandEnergy(spectrum, frame.spectrumHzPerBin, i)
            val height = if (showSpectrum) {
                (0.06f + energy * 1.15f) * grow
            } else {
                0.08f * grow
            }
            wedges[i].components[TransformComponent::class.java]?.setScaleVector(
                Vector3(RADIUS_SCALE, height.coerceIn(0.04f, 1.6f), RADIUS_SCALE),
            )
        }
    }

    private fun updatePitch(frame: AnalysisFrame) {
        val targetMidi = frame.midi?.toDouble() ?: 69.0
        val currentMidi = if (frame.pitchHz != null) Tuning.midiFromHz(frame.pitchHz!!) else null
        val targetY = pitchY(targetMidi)
        val currentY = pitchY(currentMidi ?: targetMidi)
        // 音分误差同时做成左右偏移：右偏高、左偏低，垂直方向仍是音高位置。
        val xOffset = ((frame.cents ?: 0.0) / 50.0).toFloat().coerceIn(-0.35f, 0.35f)
        val visible = frame.pitchHz != null
        targetMarker.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(0f, targetY, RULER_Z))
            setScaleVector(if (visible) Vector3(1f, 1f, 1f) else Vector3(0.4f, 0.4f, 0.4f))
        }
        currentMarker.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(xOffset, currentY, RULER_Z))
            setScaleVector(if (visible) Vector3(1.15f, 1.15f, 1.15f) else Vector3(0.01f, 0.01f, 0.01f))
        }
        val midY = (targetY + currentY) / 2f
        val stemH = abs(currentY - targetY).coerceAtLeast(0.02f)
        errorStem.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(xOffset / 2f, midY, RULER_Z))
            setScaleVector(
                if (visible) Vector3(0.018f, stemH, 0.018f)
                else Vector3(0.001f, 0.001f, 0.001f),
            )
        }
    }

    private fun bandEnergy(spectrum: FloatArray, hzPerBin: Double, index: Int): Float {
        val minHz = 80.0
        val maxHz = 8000.0
        val t0 = index / BANDS.toDouble()
        val t1 = (index + 1) / BANDS.toDouble()
        val f0 = minHz * (maxHz / minHz).pow(t0)
        val f1 = minHz * (maxHz / minHz).pow(t1)
        val i0 = (f0 / hzPerBin).toInt().coerceIn(0, spectrum.lastIndex)
        val i1 = (f1 / hzPerBin).toInt().coerceIn(i0 + 1, spectrum.size)
        var peak = 0f
        for (i in i0 until i1) peak = maxOf(peak, spectrum[i])
        // 轻量压缩，避免某一带顶满
        return (peak * 8f).coerceIn(0f, 1f)
    }

    private fun dynamicsScale(dbfs: Double): Float {
        val t = ((dbfs + 48.0) / 48.0).coerceIn(0.0, 1.0)
        return (0.45 + 0.7 * t).toFloat()
    }

    private fun pitchY(midi: Double): Float {
        val t = ((midi - 48.0) / 36.0).toFloat()
        return FLOOR_Y + 0.55f + t.coerceIn(0f, 1f) * 1.25f
    }

    private fun startDeg(): Float = -FAN_SPAN_DEG / 2f
    private fun stepDeg(): Float = FAN_SPAN_DEG / BANDS

    companion object {
        const val BANDS = 36
        const val FAN_SPAN_DEG = 120f
        val WEDGE_SPAN: Float = (FAN_SPAN_DEG / BANDS) * (PI.toFloat() / 180f)
        const val RADIUS_SCALE = 1.05f
        const val FLOOR_Y = 0f
        const val RULER_Z = -1.25f
        const val RULER_HEIGHT = 1.9f
    }
}
