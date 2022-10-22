package io.openroad.filetransfer.ble.scanner

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import androidx.annotation.RequiresPermission
import com.adafruit.glider.utils.LogUtils
import io.openroad.filetransfer.ble.utils.BleManager
import io.openroad.filetransfer.ble.utils.BleScanException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn

// Config
// -    Batch scan results and deliver them after kScanPortDelay milliseconds. Warning: setting a delay bigger than 0 will not be taken into account on some devices and an internal delay of several seconds is used
private const val kScanReportDelay = 0L//500L

/*
       Bluetooth scanner
       Provides a flow with the scanRecord results
 */
class BleAdvertisementScanner(
    context: Context,
    scanFilters: List<ScanFilter>?,
    scanMode: Int = ScanSettings.SCAN_MODE_LOW_LATENCY,
    matchMode: Int = ScanSettings.MATCH_MODE_STICKY
) {

    // Data - Private
    private val scanner = BleManager.getBluetoothAdapter(context)?.bluetoothLeScanner
    private val log by LogUtils()

    // Data
    var isScanning = false; private set

    // region Flow
    @SuppressLint("InlinedApi")
    @RequiresPermission(value = BLUETOOTH_SCAN)
    val scanResultFlow: Flow<List<ScanResult>> = callbackFlow {

        if (scanner == null || !BleManager.isBleStateAndPermissionsReady(context)) {
            log.warning("scanResultFlow cannot start")
            cancel("scanResultFlow cannot start")
        } else {
            val callback = object : ScanCallback() {
                override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                    super.onBatchScanResults(results)

                    if (results == null) return
                    /*
                    results.forEach {
                        log.info("result: ${it.scanRecord?.deviceName}")
                    }*/

                    trySend(results)
                            /*
                        .onFailure {
                            log.warning("scanResultFlow failure for batch result with ${results.size} items")
                        }*/
                }

                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    super.onScanResult(callbackType, result)

                    if (result == null) return
                    trySend(arrayListOf(result))/*
                        .onFailure {
                            log.warning("scanResultFlow failure for result: ${result.scanRecord?.deviceName ?: result.device.address}")
                        }*/
                }

                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    log.severe("scanResultFlow failed: $errorCode")
                    cancel("scanResultFlow failed", BleScanException(errorCode))
                }
            }

            // Start scanning automatically
            //val isOffloadedScanBatchingSupported = getBluetoothAdapter(context)?.isOffloadedScanBatchingSupported ?: false

            val scanSettings: ScanSettings = ScanSettings.Builder()
                .setLegacy(false)
                .setScanMode(scanMode)
                .setMatchMode(matchMode)
                .setReportDelay(kScanReportDelay) //if (isOffloadedScanBatchingSupported) kScanReportDelay else 0)
                .build()
            scanner.startScan(scanFilters, scanSettings, callback)
            isScanning = true

            awaitClose {
                scanner.stopScan(callback)
                isScanning = false
            }
        }
    }.flowOn(Dispatchers.Main.immediate)        // Call startScan on MainThread

    // endregion
}