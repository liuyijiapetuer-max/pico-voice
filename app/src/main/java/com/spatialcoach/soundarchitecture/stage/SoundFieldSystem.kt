package com.spatialcoach.soundarchitecture.stage

import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.spatialcoach.soundarchitecture.audio.AnalysisBus

/** 框架要求 System 有公开无参构造，所以运行时从 [SoundFieldRuntime] 取当前实例。 */
class SoundFieldSystem : System() {
    override fun update(context: SceneUpdateContext) {
        val runtime = SoundFieldRuntime.current ?: return
        runtime.field.apply(AnalysisBus.frame, AnalysisBus.mode)
    }
}

class SoundFieldRuntime(val field: SoundField) {
    companion object {
        @Volatile
        var current: SoundFieldRuntime? = null
            private set

        fun install(runtime: SoundFieldRuntime) {
            current = runtime
        }

        fun uninstall() {
            current = null
        }
    }
}
