package com.spatialcoach.soundarchitecture.stage

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.spatialcoach.soundarchitecture.audio.OverlayRole
import com.spatialcoach.soundarchitecture.audio.OverlayScene
import com.spatialcoach.soundarchitecture.audio.OverlayStyle
import com.spatialcoach.soundarchitecture.audio.SpatialOverlay

/**
 * 参考白线框与实际青色实体的空间叠合。偏差用香槟金提示。
 * 摆放是默认桌面尺度，不声称已锚定到真实琴键。
 */
class OverlayTrack(parent: Entity) {

    val root: Entity = Entity().apply { setName(SpatialOverlay.ROOT_NAME) }

    private val slots: List<Slot>
    private val grid: List<ModelEntity>

    init {
        parent.addChild(root)
        val boxModel = SoundMeshes.unitBox()
        val rodModel = SoundMeshes.unitRod()
        val boxRes = MeshResource.createWithMeshModel(
            model = boxModel,
            bounds = SoundMeshes.boundsOf(boxModel.positions),
            name = "SaOverlayBox",
        )
        val rodRes = MeshResource.createWithMeshModel(
            model = rodModel,
            bounds = SoundMeshes.boundsOf(rodModel.positions),
            name = "SaOverlayRod",
        )
        slots = (0 until POOL).map { index ->
            Slot(
                solid = model(boxRes, CYAN_GLASS, "SaOverlaySlotSolid_$index"),
                wire = model(boxRes, WIRE_WHITE, "SaOverlaySlotWire_$index"),
                dashA = model(boxRes, DASH_CYAN, "SaOverlaySlotDashA_$index"),
                dashB = model(boxRes, DASH_CYAN, "SaOverlaySlotDashB_$index"),
                dashC = model(boxRes, DASH_CYAN, "SaOverlaySlotDashC_$index"),
                gap = model(boxRes, CHAMPAGNE, "SaOverlaySlotGap_$index"),
                stem = model(rodRes, CHAMPAGNE, "SaOverlaySlotStem_$index"),
            )
        }
        val gridMatRod = rodRes
        grid = (0..4).map { i ->
            model(gridMatRod, GRID, "SaOverlayGrid_$i").also { entity ->
                val x = -SpatialOverlay.DEFAULT_WIDTH_M / 2f + i * SpatialOverlay.DEFAULT_WIDTH_M / 4f
                entity.components[TransformComponent::class.java]?.apply {
                    setPosition(Vector3(x, StageY, SpatialOverlay.OVERLAY_Z))
                    setScaleVector(Vector3(0.008f, 0.9f, 0.008f))
                }
                // Grid lines are visual only; names do not use SaOverlay_ prefix so they are not selectable.
            }
        }
        hideAll()
    }

    fun apply(scene: OverlayScene) {
        val rotation = SpatialOverlay.viewRotationDeg(scene.view)
        root.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(0f, 0f, 0f))
            setEulerAngles(EulerAngles(pitch = rotation.x, yaw = rotation.y, roll = rotation.z))
        }
        hideAll()
        scene.members.forEachIndexed { index, member ->
            if (index >= slots.size) return@forEachIndexed
            val slot = slots[index]
            when (member.style) {
                OverlayStyle.WIREFRAME -> showBox(slot.wire, member.id, member.center.x, member.center.y, member.center.z, member.size.x, member.size.y * 1.12f, member.size.z * 1.12f)
                OverlayStyle.SOLID -> {
                    val opacityScale = if (member.role == OverlayRole.PREVIOUS) 0.7f else 1f
                    showBox(
                        slot.solid,
                        member.id,
                        member.center.x,
                        member.center.y,
                        member.center.z,
                        member.size.x,
                        member.size.y * opacityScale,
                        member.size.z * opacityScale,
                    )
                }
                OverlayStyle.DASHED -> {
                    val third = member.size.x / 5f
                    val start = member.center.x - member.size.x / 2f
                    showBox(slot.dashA, member.id, start + third, member.center.y, member.center.z, third, member.size.y * 0.8f, member.size.z * 0.8f)
                    showBox(slot.dashB, member.id, start + 3f * third, member.center.y, member.center.z, third, member.size.y * 0.8f, member.size.z * 0.8f)
                    showBox(slot.dashC, member.id, start + 5f * third - third, member.center.y, member.center.z, third, member.size.y * 0.8f, member.size.z * 0.8f)
                }
                OverlayStyle.GAP -> showBox(slot.gap, member.id, member.center.x, member.center.y, member.center.z, member.size.x, member.size.y, member.size.z)
                OverlayStyle.DEVIATION -> {
                    val t = slot.stem
                    t.setName(member.id)
                    t.components[TransformComponent::class.java]?.apply {
                        setPosition(Vector3(member.center.x, member.center.y - member.size.y / 2f, member.center.z))
                        setScaleVector(Vector3(0.018f, member.size.y, 0.018f))
                    }
                }
            }
        }
    }

    private fun showBox(
        entity: ModelEntity,
        name: String,
        x: Float,
        y: Float,
        z: Float,
        sx: Float,
        sy: Float,
        sz: Float,
    ) {
        entity.setName(name)
        entity.components[TransformComponent::class.java]?.apply {
            setPosition(Vector3(x, y, z))
            setScaleVector(Vector3(sx, sy, sz))
        }
    }

    private fun hideAll() {
        for (slot in slots) {
            for (entity in slot.all) {
                entity.components[TransformComponent::class.java]?.setScaleVector(Vector3(0.001f, 0.001f, 0.001f))
                if (entity.getName()?.startsWith(SpatialOverlay.ENTITY_PREFIX) == true) {
                    entity.setName("SaOverlayHidden")
                }
            }
        }
    }

    private fun model(mesh: MeshResource, color: Long, name: String): ModelEntity {
        val material = com.pico.spatial.core.ecs.resource.UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColorTexture(SolidTextures.color(color.toInt()))
            setCullingMode(MaterialCullingMode.NONE)
            setOpacity(opacityOf(color))
        }
        return ModelEntity(mesh, material).apply {
            setName(name)
            components[InteractableComponent::class.java] = InteractableComponent()
            components[HoverEffectComponent::class.java] = HoverEffectComponent()
            runCatching { createCollisionShapes(true) }
            root.addChild(this)
        }
    }

    private fun opacityOf(color: Long): Float = ((color shr 24) and 0xFF) / 255f

    private data class Slot(
        val solid: ModelEntity,
        val wire: ModelEntity,
        val dashA: ModelEntity,
        val dashB: ModelEntity,
        val dashC: ModelEntity,
        val gap: ModelEntity,
        val stem: ModelEntity,
    ) {
        val all: List<ModelEntity> get() = listOf(solid, wire, dashA, dashB, dashC, gap, stem)
    }

    companion object {
        private const val POOL = 28
        private val StageY = 0.9f
        // 青灰玻璃、白线框、香槟金偏差；深石墨背景由舞台地板提供。
        private const val CYAN_GLASS = 0x9968C5D0L
        private const val WIRE_WHITE = 0x66E8EEF4L
        private const val DASH_CYAN = 0x5568C5D0L
        private const val CHAMPAGNE = 0xE6D4B483L
        private const val GRID = 0x332A3344L
    }
}
