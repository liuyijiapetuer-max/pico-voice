package com.spatialcoach.sonosphere.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.spatialcoach.sonosphere.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}