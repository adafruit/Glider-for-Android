package io.openroad.ble.filetransfer

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.ParcelUuid
import androidx.annotation.MainThread
import io.openroad.ble.BleDiscoveryException
import io.openroad.ble.peripheral.BlePeripheral
import io.openroad.ble.peripheral.CompletionHandler
import io.openroad.ble.peripheral.NotifyHandler
import io.openroad.ble.peripheral.kClientCharacteristicConfigUUID
import io.openroad.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.*

/**
 * Created by Antonio García (antonio@openroad.es)
 */

val kFileTransferServiceUUID: UUID = UUID.fromString("0000FEBB-0000-1000-8000-00805F9B34FB")
val kFileTransferVersionCharacteristicUUID: UUID =
    UUID.fromString("ADAF0100-4669-6C65-5472-616E73666572")
val kFileTransferDataCharacteristicUUID: UUID =
    UUID.fromString("ADAF0200-4669-6C65-5472-616E73666572")

typealias FileTransferDataHandler = (data: ByteArray) -> Unit

class BleFileTransferPeripheral(
    private val blePeripheral: BlePeripheral,
    externalScope: CoroutineScope = MainScope()
) {

    // Data - Private
    private val log by LogUtils()
    val nameOrAddress = blePeripheral.nameOrAddress

    private var fileTransferVersion: Int? = null

    // States
    sealed class FileTransferState {
        object Connecting : FileTransferState()
        object Discovering : FileTransferState()
        object CheckingFileTransferVersion : FileTransferState()
        object EnablingNotifications : FileTransferState()
        data class Disconnecting(val cause: Throwable? = null) : FileTransferState()
        data class Disconnected(val cause: Throwable? = null) : FileTransferState()
    }

    private val _fileTransferState =
        MutableStateFlow<FileTransferState>(FileTransferState.Disconnected())
    val fileTransferState = _fileTransferState.asStateFlow()

    // region Lifecycle
    init {
        // Listen to state changes
        externalScope.launch {
            blePeripheral.connectionState.collect { connectionState ->
                when (connectionState) {
                    BlePeripheral.ConnectionState.Connecting -> _fileTransferState.update { FileTransferState.Connecting }
                    BlePeripheral.ConnectionState.Connected -> {
                        _fileTransferState.update { FileTransferState.Discovering }

                        //  Discover
                        discoverServices()
                    }
                    is BlePeripheral.ConnectionState.Disconnecting -> _fileTransferState.update {
                        FileTransferState.Disconnecting(connectionState.cause)
                    }
                    is BlePeripheral.ConnectionState.Disconnected -> _fileTransferState.update {
                        cleanCachedState()
                        FileTransferState.Disconnected(connectionState.cause)
                    }
                }

            }
        }
    }
    // endregion

    // region Actions

    @MainThread
    fun connect() {
        cleanCachedState()

        // Connects using the standard connect function but the state changes will be collected to trigger the next steps in the FileTransfer setup
        blePeripheral.connect()
    }

    private fun cleanCachedState() {
        fileTransferVersion = null
    }

    private fun discoverServices() {
        log.info("Discovering services...")
        blePeripheral.discoverServices { status ->
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Discovery finished. Setup services
                fileTransferEnable() {
                    // TODO
                }
            } else {
                blePeripheral.disconnect(BleDiscoveryException())
            }
        }
    }

    private fun fileTransferEnable(
        completion: CompletionHandler
    ) {
        _fileTransferState.update { FileTransferState.CheckingFileTransferVersion }

        // Get version
        getVersion { version ->
            log.info("$nameOrAddress: FileTransfer Protocol v$version detected")


            _fileTransferState.update { FileTransferState.EnablingNotifications }

            val fileTransferCharacteristic =
                blePeripheral.getCharacteristic(
                    kFileTransferDataCharacteristicUUID,
                    kFileTransferServiceUUID
                )

            fileTransferCharacteristic?.let {

                // Set notify
                setNotifyResponse(it,{ data ->
                    // TODO
                    // Add data to processing queue

                } , completion)
            }
        }
    }

    private fun setNotifyResponse(
        characteristic: BluetoothGattCharacteristic,
        updateHandler: FileTransferDataHandler,
        completion: CompletionHandler
    ) {
        // Prepare notification handler
        val weakUpdateHandler = WeakReference(updateHandler)
        val notifyHandler: NotifyHandler = {
            val handler = weakUpdateHandler.get()
            if (handler != null) {
                handler(characteristic.value)       // Directly read the characteristic.value instead of using the getXXX functions because we are looking for the ByteArray
            }
        }

        // Check if already notifying (read client characteristic config descriptor to check it)
        blePeripheral.readDescriptor(
            characteristic,
            kClientCharacteristicConfigUUID
        ) { status ->
            if (status != BluetoothGatt.GATT_SUCCESS) {
                completion(status)
                return@readDescriptor
            }

            // Enable (or update) notifications
            if (!blePeripheral.isCharacteristicNotifyingForCachedClientConfigDescriptor(
                    characteristic
                )
            ) {
                blePeripheral.characteristicEnableNotify(characteristic, notifyHandler, completion)
            } else {
                blePeripheral.characteristicUpdateNotify(characteristic, notifyHandler)
                completion(BluetoothGatt.GATT_SUCCESS)
            }
        }
    }

    private fun getVersion(completion: (version: Int?) -> Unit) {
        val characteristic = blePeripheral.getCharacteristic(
            kFileTransferVersionCharacteristicUUID,
            kFileTransferServiceUUID
        )

        if (characteristic == null) {
            completion(null)
        } else {
            // Read version
            blePeripheral.readCharacteristic(characteristic) { status: Int, _: ByteArray? ->
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    log.info("Error reading FileTransfer service version")
                    completion(null)
                    return@readCharacteristic
                }

                // Return version
                val version = getVersion(characteristic)
                completion(version)
            }
        }
    }

    private fun getVersion(versionCharacteristic: BluetoothGattCharacteristic): Int? {
        return try {
            versionCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)
        } catch (e: NullPointerException) {
            null
        }
    }


    // endregion
}