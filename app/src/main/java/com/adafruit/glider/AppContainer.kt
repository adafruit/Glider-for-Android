package com.adafruit.glider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.content.Context
import io.openroad.ble.state.BleStateDataSource
import io.openroad.ble.state.BleStateRepository
import kotlinx.coroutines.MainScope

// Container of objects shared across the whole app
class AppContainer(context: Context) {

    var bleStateRepository: BleStateRepository

    init {
        val bleStateDataSource = BleStateDataSource(context)
        bleStateRepository = BleStateRepository(bleStateDataSource, MainScope())
    }
}

