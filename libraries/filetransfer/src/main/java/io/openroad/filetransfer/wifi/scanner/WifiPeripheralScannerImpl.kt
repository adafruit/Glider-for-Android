package io.openroad.wifi.scanner

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.content.Context
import io.openroad.filetransfer.wifi.peripheral.WifiPeripheral
import io.openroad.filetransfer.wifi.scanner.NsdException
import io.openroad.filetransfer.wifi.scanner.NsdServiceInfoScanner
import io.openroad.filetransfer.wifi.scanner.WifiPeripheralScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*

/*
    Wifi peripherals scanner
    @param serviceType: string containing the protocol and transport layer for this service.
 */
class WifiPeripheralScannerImpl(
    context: Context,
    serviceType: String,
) : WifiPeripheralScanner {

    // Data - Private
    private val nsdServiceInfoScanner = NsdServiceInfoScanner(context, serviceType)
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

    override fun clearWifiLastException() {
        _wifiLastException.update { null }
    }

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