package io.openroad.wifi.scanner

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import io.openroad.wifi.peripheral.WifiPeripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed

class WifiPeripheralScannerFake(
    lastException: NsdException? = null,
    externalScope: CoroutineScope = MainScope()
) : WifiPeripheralScanner {

    override val isRunning: Boolean = true
    override val wifiLastException: StateFlow<NsdException?> = flow { emit(lastException) }
        .stateIn(
            scope = externalScope,
            started = WhileSubscribed(5000L),
            initialValue = null
        )
    override val wifiPeripherals: StateFlow<List<WifiPeripheral>> =
        flow { emit(emptyList<WifiPeripheral>()) }
            .stateIn(
                scope = externalScope,
                started = WhileSubscribed(5000L),
                initialValue = emptyList()
            )


    override val wifiPeripheralsFlow: Flow<List<WifiPeripheral>> =
        emptyFlow()


    override fun start() {}

    override fun stop() {}
}