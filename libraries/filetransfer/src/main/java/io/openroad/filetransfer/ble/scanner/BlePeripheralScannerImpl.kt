package io.openroad.filetransfer.ble.scanner

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.Context
import io.openroad.filetransfer.ble.peripheral.BlePeripheral
import io.openroad.filetransfer.ble.utils.BleException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private const val kScanForgetDevicesEnabled = true
private const val kScanForgetDevicesInterval: Long = 2000       // in milliseconds
private const val kScanIntervalToForgetDevice = 4500L            // in milliseconds

/*
    Starts bluetooth scanning and creates a list of known peripherals (created from the advertising record that is received when scanning)
 */
class BlePeripheralScannerImpl(
    context: Context,
    scanFilters: List<ScanFilter>?,
    private val externalScope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BlePeripheralScanner {

    // Data - Private
    private val bleAdvertisementScanner = BleAdvertisementScanner(context, scanFilters)
    //private val log by LogUtils()
    private var scanJob: Job? = null
    private var forgetDevicesJob: Job? = null

    private val _bleLastException = MutableStateFlow<BleException?>(null)
    private val knownBlePeripherals =
        mutableListOf<BlePeripheral>()     // Cached list of peripherals
    private val _blePeripherals = MutableStateFlow<List<BlePeripheral>>(emptyList())

    // Data - Public
    override val isRunning: Boolean; get() = scanJob != null
    override val bleLastException = _bleLastException.asStateFlow()
    override val blePeripherals = _blePeripherals.asStateFlow()

    // Cold flow independent from start() stop()
    override val blePeripheralsFlow: Flow<List<BlePeripheral>> =
        bleAdvertisementScanner.scanResultFlow
            .onStart {
                // Remove devices that didn't update advertising after some time
                if (kScanForgetDevicesEnabled) {
                    forgetDevicesJob = externalScope.launch(defaultDispatcher) {
                        while (isActive) {
                            forgetOldDevices()
                            delay(kScanForgetDevicesInterval)
                        }
                    }
                }
            }
            .catch { exception ->
                val cause = exception.cause as? BleException
                if (cause != null) {
                    _bleLastException.update { cause }
                }
            }
            .transform { scanResultList ->
                // Update peripherals
                scanResultList.forEach { onDeviceAdvertisingFound(it) }
                emit(knownBlePeripherals.toList())
            }
            .onCompletion {
                if (kScanForgetDevicesEnabled) {
                    forgetDevicesJob?.cancel()
                }
            }


    override fun clearBleLastException() {
        _bleLastException.update { null }
    }


    /*
    // region Actions
    override fun start() {
        if (isRunning) {
            return
        }
        log.info("Start BlePeripheralScanner")

        // Collect each advertising found and update a list of known blePeripheral and a blePeripheralsState StateFlow
        _bleLastException.update { null }
        scanJob?.cancel()

        scanJob = bleAdvertisementScanner.scanResultFlow
            .onEach { scanResultList ->
                // Update peripherals
                scanResultList.forEach { onDeviceAdvertisingFound(it) }

                // Update state
                _blePeripherals.update { knownBlePeripherals.toList() }
            }
            .onStart {
                // Remove devices that didn't update advertising after some time
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
                if (cause is BleScanException) {
                    log.severe("scanResultFlow finished: failed")
                    _bleLastException.update { cause }
                } else {
                    log.info("scanResultFlow finished: done")
                }
            }
            .flowOn(defaultDispatcher)
            .launchIn(externalScope)
    }

    override fun stop() {
        if (!isRunning) {
            return
        }
        log.info("Stop BlePeripheralScanner")

        scanJob?.cancel()
        scanJob = null
    }
    // endregion
*/
    // region Utils
    @Synchronized
    private fun onDeviceAdvertisingFound(scanResult: ScanResult) {
        val address = scanResult.device.address
        val existingPeripheral = knownBlePeripherals.firstOrNull { it.address == address }

        if (existingPeripheral != null) {
            existingPeripheral.updateScanResult(scanResult)
        } else {
            val blePeripheral = BlePeripheral(scanResult)

            //log.info("Found: ${blePeripheral.nameOrAddress}\nServices: ${blePeripheral.scanRecord()?.serviceUuids?.joinToString { it.uuid.toString() } ?: "none"}")
            knownBlePeripherals.add(blePeripheral)
        }
    }

    @Synchronized
    private fun forgetOldDevices() {
        //log.info("forgetOldDevices");
        val currentTime = System.currentTimeMillis()
        knownBlePeripherals.removeIf { currentTime - it.lastUpdateMillis > kScanIntervalToForgetDevice }
    }
    // endregion
}