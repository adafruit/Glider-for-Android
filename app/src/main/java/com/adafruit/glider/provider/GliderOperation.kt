package com.adafruit.glider.provider

/**
 * Created by Antonio García (antonio@openroad.es)
 */


internal abstract class GliderOperation {

    // Data
    var isCancelled = false; private set

    // region Actions
    fun cancel() {
        isCancelled = true
    }

    // @return true if finished
    abstract fun execute()
}
