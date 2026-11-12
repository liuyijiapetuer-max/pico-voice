package com.spatialcoach.soundarchitecture.ui

import androidx.compose.ui.unit.Dp
import com.pico.spatial.ui.foundation.dsl.Placement
import com.pico.spatial.ui.foundation.dsl.PlacementContext

/**
 * 把副窗锚到已打开的窗口上。Stage 作为默认容器时，常常不会出现在
 * placement 的 containers 列表里，所以要准备一串候选锚点。
 * 找不到锚点时退回默认摆放，避免窗口根本打不开。
 */
fun PlacementContext.placeBeside(
    anchorIds: List<String>,
    orientation: Placement.Orientation,
    offset: Dp = Dp.Unspecified,
): Placement {
    val anchor = anchorIds.firstNotNullOfOrNull { id -> containers.firstOrNull { it.id == id } }
    return if (anchor != null) Placement.placement(anchor, orientation, offset) else Placement.none()
}
