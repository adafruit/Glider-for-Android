package io.openroad.filetransfer.ble.scanner

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import io.openroad.filetransfer.ble.peripheral.BlePeripheral
import io.openroad.filetransfer.ble.utils.BleException
import io.openroad.filetransfer.ble.scanner.BlePeripheralScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*

class BlePeripheralScannerFake(
    lastException: BleException? = null,
    externalScope: CoroutineScope = MainScope()
) : BlePeripheralScanner {
    override val isRunning: Boolean = true
    override val bleLastException: StateFlow<BleException?> = flow { emit(lastException) }
        .stateIn(
            scope = externalScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    override val blePeripherals: StateFlow<List<BlePeripheral>> =
        flow<List<BlePeripheral>> { emit(emptyList()) }
            .stateIn(
                scope = externalScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = emptyList()
            )


    override val blePeripheralsFlow: Flow<List<BlePeripheral>> =
        emptyFlow()

    override fun clearBleLastException() {
    }
    /*
    override fun start() {}

    override fun stop() {}

     */
}