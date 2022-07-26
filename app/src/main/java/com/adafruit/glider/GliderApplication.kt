package com.adafruit.glider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.app.Application

class GliderApplication : Application() {
    // AppContainer instance used by the rest of classes to obtain dependencies
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainerImpl(this)
    }

}