package com.spatialcoach.soundarchitecture.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.spatialcoach.soundarchitecture.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}