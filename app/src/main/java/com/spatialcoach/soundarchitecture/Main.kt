package com.spatialcoach.soundarchitecture

import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.spatialcoach.soundarchitecture.stage.PlaceholderStageContent

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultStage {
            PicoTheme {
                PlaceholderStageContent()
            }
        }
    }