package com.spatialcoach.soundarchitecture

import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.Form
import com.pico.spatial.ui.foundation.dsl.Placement
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.WindowContainer
import com.pico.spatial.ui.foundation.dsl.WindowContainerSize
import com.pico.spatial.ui.foundation.dsl.WorldScale
import com.pico.spatial.ui.platform.resize.CaptionBarType
import com.pico.spatial.ui.platform.resize.ContainerResizeRestriction
import com.pico.spatial.ui.platform.resize.ContainerResizeType
import com.spatialcoach.soundarchitecture.platform.CoachWindowActivity
import com.spatialcoach.soundarchitecture.platform.DynamicsWindowActivity
import com.spatialcoach.soundarchitecture.platform.SpectrumWindowActivity
import com.spatialcoach.soundarchitecture.stage.StageContent
import com.spatialcoach.soundarchitecture.ui.CoachWindowContent
import com.spatialcoach.soundarchitecture.ui.DynamicsWindowContent
import com.spatialcoach.soundarchitecture.ui.SpectrumWindowContent
import com.spatialcoach.soundarchitecture.ui.WorkspaceWindows
import com.spatialcoach.soundarchitecture.ui.placeBeside

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme {
                StageContent()
            }
        }

        WindowContainer(
            id = WorkspaceWindows.SPECTRUM,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 720.dp, height = 520.dp),
            resizeType = ContainerResizeType.ContentMinSize,
            defaultResizeRestriction = ContainerResizeRestriction.NonUniformResizable,
            enableMaterialBackground = false,
            defaultCaptionBarType = CaptionBarType.AutomaticHide,
            worldScale = WorldScale.Fixed,
            targetActivity = SpectrumWindowActivity::class.java,
            placement = { placeBeside(listOf(WorkspaceWindows.STAGE), Placement.Orientation.Right, 0.dp) },
        ) {
            PicoTheme { SpectrumWindowContent() }
        }

        WindowContainer(
            id = WorkspaceWindows.DYNAMICS,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 720.dp, height = 520.dp),
            resizeType = ContainerResizeType.ContentMinSize,
            defaultResizeRestriction = ContainerResizeRestriction.NonUniformResizable,
            enableMaterialBackground = false,
            defaultCaptionBarType = CaptionBarType.AutomaticHide,
            worldScale = WorldScale.Fixed,
            targetActivity = DynamicsWindowActivity::class.java,
            placement = {
                placeBeside(
                    listOf(WorkspaceWindows.STAGE, WorkspaceWindows.SPECTRUM),
                    Placement.Orientation.Left,
                    0.dp,
                )
            },
        ) {
            PicoTheme { DynamicsWindowContent() }
        }

        WindowContainer(
            id = WorkspaceWindows.COACH,
            form = Form.Planar,
            defaultSize = WindowContainerSize(width = 720.dp, height = 520.dp),
            resizeType = ContainerResizeType.ContentMinSize,
            defaultResizeRestriction = ContainerResizeRestriction.NonUniformResizable,
            enableMaterialBackground = false,
            defaultCaptionBarType = CaptionBarType.AutomaticHide,
            worldScale = WorldScale.Fixed,
            targetActivity = CoachWindowActivity::class.java,
            placement = {
                placeBeside(
                    listOf(WorkspaceWindows.STAGE, WorkspaceWindows.SPECTRUM, WorkspaceWindows.DYNAMICS),
                    Placement.Orientation.Bottom,
                    0.dp,
                )
            },
        ) {
            PicoTheme { CoachWindowContent() }
        }
    }
