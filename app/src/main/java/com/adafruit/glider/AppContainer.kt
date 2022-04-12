package com.adafruit.glider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.content.Context
import io.openroad.ble.FileTransferClient
import io.openroad.ble.state.BleStateDataSource
import io.openroad.ble.state.BleStateRepository
import io.openroad.utils.LogUtils
import kotlinx.coroutines.MainScope

// Container of objects shared across the whole app
class AppContainer(context: Context) {

    // Data
    private val log by LogUtils()
    var bleStateRepository: BleStateRepository

    var fileTransferClient: FileTransferClient? = null      // TODO: move this from here

    init {
        val bleStateDataSource = BleStateDataSource(context)
        bleStateRepository = BleStateRepository(bleStateDataSource, MainScope())


    }

}

