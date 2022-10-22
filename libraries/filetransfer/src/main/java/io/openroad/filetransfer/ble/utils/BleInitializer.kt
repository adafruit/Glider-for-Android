package io.openroad.filetransfer.ble.utils

import android.content.Context
import androidx.startup.Initializer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

lateinit var applicationContext: Context; private set

object Ble

// Initializes WorkManager.
class BleInitializer : Initializer<Ble> {
    override fun create(context: Context): Ble {
        applicationContext = context.applicationContext
        return Ble
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        // No dependencies on other libraries.
        return emptyList()
    }
}