package io.openroad.wifi.scanner

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.content.Context
import io.openroad.wifi.peripheral.WifiPeripheral
import com.adafruit.glider.utils.LogUtils
import io.openroad.ble.utils.BleException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*

/*
    Wifi peripherals scanner
    @param serviceType: string containing the protocol and transport layer for this service.
 */
class WifiPeripheralScannerImpl(
    context: Context,
    serviceType: String,
    //private val externalScope: CoroutineScope,
    //private val defaultDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WifiPeripheralScanner {

    // Data - Private
    private val nsdServiceInfoScanner = NsdServiceInfoScanner(context, serviceType)
    private val log by LogUtils()
    private var scanJob: Job? = null

    private val _wifiLastException = MutableStateFlow<NsdException?>(null)
    private val knownWifiPeripherals =
        mutableListOf<WifiPeripheral>()     // Cached list of peripherals
    private val _wifiPeripherals = MutableStateFlow<List<WifiPeripheral>>(emptyList())

    // Data - Public
    override val isRunning: Boolean; get() = scanJob != null
    override val wifiLastException = _wifiLastException.asStateFlow()
    override val wifiPeripherals = _wifiPeripherals.asStateFlow()


    // Cold flow independent from start() stop()
    override val wifiPeripheralsFlow: Flow<List<WifiPeripheral>> =
        nsdServiceInfoScanner.nsdServiceInfoFlow
            .catch { exception ->
                val cause = exception.cause as? NsdException
                if (cause != null) {
                    _wifiLastException.update { cause }
                }
            }
            .transform { scanResult ->
                // Update peripherals
                updateWifiPeripheralsWithScanResult(scanResult)

                emit(knownWifiPeripherals.toList())
            }

/*
    // region Actions
    override fun start() {
        if (isRunning) {
            return
        }
        log.info("Start WifiPeripheralScanner")

        // Collect each found NsdServiceInfo and update a list of known wifiPeripheral and a wifiPeripheralsState StateFlow
        _wifiLastException.update { null }
        scanJob?.cancel()

        scanJob = nsdServiceInfoScanner.nsdServiceInfoFlow
            .onEach { scanResult ->
                // Update peripherals
                updateWifiPeripheralsWithScanResult(scanResult)

                // Update state
                _wifiPeripherals.update { knownWifiPeripherals.toList() }
            }
            /*.onStart{}*/
            .onCompletion { exception ->
                val cause = exception?.cause
                if (cause is NsdScanException) {
                    log.severe("wifi scanner finished: failed")
                    _wifiLastException.update { cause }
                } else {
                    log.info("wifi scanner finished: done")
                }
            }
            .flowOn(defaultDispatcher)
            .launchIn(externalScope)
    }

    override fun stop() {
        if (!isRunning) {
            return
        }
        log.info("Stop WifiPeripheralScanner")

        scanJob?.cancel()
        scanJob = null
    }
    // endregion
*/
    // region Utils
    @Synchronized
    private fun updateWifiPeripheralsWithScanResult(scanResult: NsdServiceInfoScanner.NsdScanResult) {
        val serviceInfo = scanResult.info

        // Only consider it if hostAddress is not null
        val hostAddress = serviceInfo.host?.hostAddress ?: return

        val existingPeripheral =
            knownWifiPeripherals.firstOrNull { it.address == hostAddress }

        if (existingPeripheral != null && scanResult.isLost) {
            // Remove if lost
            knownWifiPeripherals.remove(existingPeripheral)
        } else if (existingPeripheral == null && !scanResult.isLost) {
            // Only add it if not already existing
            val wifiPeripheral = WifiPeripheral(
                name = serviceInfo.serviceName,
                address = hostAddress,
                port = serviceInfo.port
            )
            knownWifiPeripherals.add(wifiPeripheral)
        }
    }
    // endregion
}