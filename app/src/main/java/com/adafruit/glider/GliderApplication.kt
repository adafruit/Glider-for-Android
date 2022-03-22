package com.adafruit.glider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.app.Application

class GliderApplication : Application() {

    val appContainer = AppContainer(this)      // Manual injection: https://developer.android.com/training/dependency-injection/manual

    init {
        // Debug coroutines in Debug mode
        /*
        if (BuildConfig.DEBUG) {
            System.setProperty(
                kotlinx.coroutines.DEBUG_PROPERTY_NAME,
                kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
            )
        }*/
    }
}