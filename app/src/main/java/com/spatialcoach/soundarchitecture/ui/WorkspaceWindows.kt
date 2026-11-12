package com.spatialcoach.soundarchitecture.ui

/**
 * 四个空间窗口的容器 id。同一个字符串必须同时出现在
 * AndroidManifest 的 `pico.spatial.windowcontainer.id` 和 `mainApp` 的 `WindowContainer(id=...)`。
 */
object WorkspaceWindows {
    const val STAGE = "SOUNDARCHITECTURE"
    const val SPECTRUM = "SA_SpectrumWindow"
    const val DYNAMICS = "SA_DynamicsWindow"
    const val COACH = "SA_CoachWindow"

    val AUX: List<String> = listOf(SPECTRUM, DYNAMICS, COACH)
}
