package io.openroad.ble.scanner

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.Context
import io.openroad.ble.device.BlePeripheral
import io.openroad.utils.LogUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*


private const val kScanForgetDevicesEnabled = true
private const val kScanForgetDevicesInterval: Long = 2000       // in milliseconds
private const val kScanIntervalToForgetDevice = 4500L            // in milliseconds

/*
    Starts bluetooth scanning and creates a list of known peripherals (created from the advertising record that is received when scanning)
    Should be manually started / stopped (allows more freedom to the app to pause it when not in foreground)
 */
class BlePeripheralScanner(
    context: Context,
    scanFilters: List<ScanFilter>?,
    private val externalScope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    // Data - Private
    private val bleAdvertisementScanner = BleAdvertisementScanner(context, scanFilters)
    private val log by LogUtils()
    private var scanJob: Job? = null
    private var forgetDevicesJob: Job? = null

    private val blePeripherals = mutableListOf<BlePeripheral>()     // Cached list of devices
    private val _blePeripheralsState = MutableStateFlow<List<BlePeripheral>>(emptyList())
    private val _bleErrorState = MutableStateFlow<Int?>(null)

    // Data
    val blePeripheralsState = _blePeripheralsState.asStateFlow()
    val bleErrorState = _bleErrorState.asStateFlow()
    val isRunning: Boolean; get() = scanJob != null

    // region Actions
    fun start() {
        if (isRunning) {
            return
        }
        log.info("Start BlePeripheralScanner")

        // Collect each advertising found and update a list of known blePeripheral and a blePeripheralsState StateFlow
        _bleErrorState.update { null }
        scanJob?.cancel()
        scanJob = bleAdvertisementScanner.scanResultFlow
            .onEach { scanResultList ->
                // Update peripherals
                scanResultList.forEach { onDeviceAdvertisingFound(it) }

                // Update state
                _blePeripheralsState.update { blePeripherals.toList() }
            }
            .onStart {
                // Remove devices without updated advertising after some time
                if (kScanForgetDevicesEnabled) {
                    forgetDevicesJob = externalScope.launch(defaultDispatcher) {
                        while (isActive) {
                            forgetOldDevices()
                            delay(kScanForgetDevicesInterval)
                        }
                    }
                }
            }
            .onCompletion { exception ->
                if (kScanForgetDevicesEnabled) {
                    forgetDevicesJob?.cancel()
                }

                val cause = exception?.cause
                if (cause is BleAdvertisementScanner.ScanException) {
                    log.severe("scanResultFlow finished: failed")
                    _bleErrorState.update { cause.errorCode }
                } else {
                    log.info("scanResultFlow finished: done")
                }
            }
            .flowOn(defaultDispatcher)
            .launchIn(externalScope)
    }

    fun stop() {
        if (!isRunning) {
            return
        }
        log.info("Stop BlePeripheralScanner")

        scanJob?.cancel()
        scanJob = null
    }
    // endregion

    // region Utils
    @Synchronized
    private fun onDeviceAdvertisingFound(scanResult: ScanResult) {
        val address = scanResult.device.address
        val existingPeripheral = blePeripherals.firstOrNull { it.address == address }

        if (existingPeripheral != null) {
            existingPeripheral.updateScanResult(scanResult)
        } else {
            val device = BlePeripheral(scanResult)

            //log.info("Found: ${device.nameOrAddress}\nServices: ${device.scanRecord?.serviceUuids?.joinToString { it.uuid.toString() } ?: "none"}")
            blePeripherals.add(device)
        }
    }

    @Synchronized
    private fun forgetOldDevices() {
        //log.info("forgetOldDevices");
        val currentTime = System.currentTimeMillis()
        blePeripherals.removeIf { currentTime - it.lastUpdateMillis > kScanIntervalToForgetDevice }

        /*
        val numPeripherals: Int = blePeripherals.size
        for (i in numPeripherals - 1 downTo 0) {
            if (currentTime - blePeripherals[i].lastUpdateMillis > kScanIntervalToForgetDevice) {
                val blePeripheral = blePeripherals.removeAt(i)
                log.info("Forget peripheral: ${blePeripheral.nameOrAddress}");
            }
        }*/
    }
    // endregion
}