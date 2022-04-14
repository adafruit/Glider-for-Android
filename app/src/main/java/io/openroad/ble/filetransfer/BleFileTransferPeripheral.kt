package io.openroad.ble.filetransfer

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
import androidx.annotation.MainThread
import com.adafruit.glider.BuildConfig
import io.openroad.ble.BleDiscoveryException
import io.openroad.ble.BleException
import io.openroad.ble.BleStatusResultException
import io.openroad.ble.peripheral.BlePeripheral
import io.openroad.ble.peripheral.CompletionHandler
import io.openroad.ble.peripheral.NotifyHandler
import io.openroad.ble.peripheral.kClientCharacteristicConfigUUID
import io.openroad.ble.utils.*
import io.openroad.utils.LogUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.sql.Timestamp
import java.util.*

/**
 * Created by Antonio García (antonio@openroad.es)
 */

val kFileTransferServiceUUID: UUID = UUID.fromString("0000FEBB-0000-1000-8000-00805F9B34FB")
val kFileTransferVersionCharacteristicUUID: UUID =
    UUID.fromString("ADAF0100-4669-6C65-5472-616E73666572")
val kFileTransferDataCharacteristicUUID: UUID =
    UUID.fromString("ADAF0200-4669-6C65-5472-616E73666572")
val kFileTransferDebugMessagesEnabled = BuildConfig.DEBUG && true

typealias FileTransferDataHandler = (data: ByteArray) -> Unit
typealias FileTransferProgressHandler = (transmittedBytes: Int, totalBytes: Int) -> Unit

const val kPreferredMtuSize = 517

const val kReadFileResponseHeaderSize = 16      // (1+1+2+4+4+4+variable)
const val kListDirectoryResponseHeaderSize = 28      // (1+1+2+4+4+4+8+4+variable)
const val kDeleteFileResponseHeaderSize = 2     // (1+1)
const val kMoveFileResponseHeaderSize = 2     // (1+1)


class BleFileTransferPeripheral(
    private val blePeripheral: BlePeripheral,
) {

    // Data - Private
    private val log by LogUtils()
    val nameOrAddress = blePeripheral.nameOrAddress

    private var fileTransferVersion: Int? = null
    private var dataProcessingQueue = DataProcessingQueue()

    private var readStatus: ReadStatus? = null
    private var writeStatus: WriteStatus? = null
    private var listDirectoryStatus: ListDirectoryStatus? = null

    // States
    sealed class FileTransferState {
        object Start :
            FileTransferState()      // Note: don't use disconnected as initial state to differentiate between a real disconnect and the initialization

        object Connecting : FileTransferState()
        data class Disconnecting(val cause: Throwable? = null) : FileTransferState()
        data class Disconnected(val cause: Throwable? = null) : FileTransferState()

        object Discovering : FileTransferState()
        object CheckingFileTransferVersion : FileTransferState()
        object EnablingNotifications : FileTransferState()
        object Enabled : FileTransferState()
        data class GattError(val gattErrorCode: Int) : FileTransferState()
        data class Error(val cause: Throwable? = null) : FileTransferState()
    }

    private val _fileTransferState =
        MutableStateFlow<FileTransferState>(FileTransferState.Start)
    val fileTransferState = _fileTransferState.asStateFlow()

    // Data - Structures
    data class DirectoryEntry(
        val name: String,
        val type: EntryType,
        val modificationDate: Date? = null
    ) {
        sealed class EntryType {
            data class File(val size: Int) : EntryType()
            object Directory : EntryType()
        }

        val isDirectory = type == EntryType.Directory
        val isHidden = name.startsWith(".")
    }

    // Data - Internal
    private var fileTransferCharacteristic: BluetoothGattCharacteristic? = null

    // region Lifecycle
    init {
        blePeripheral.externalScope.launch {
            // Listen to state changes
            blePeripheral.connectionState
                .onStart {
                    log.info("blePeripheral.connectionState onStart")
                }
                .onCompletion { exception ->
                    log.info("blePeripheral.connectionState onCompletion: $exception")
                }
                .collect { connectionState ->
                    when (connectionState) {
                        BlePeripheral.ConnectionState.Start -> {}
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
        blePeripheral.discoverServices { discoverStatus ->
            if (discoverStatus == BluetoothGatt.GATT_SUCCESS) {
                // Discovery finished. Setup services
                fileTransferEnable { fileTransferStatus ->        // BluetoothGatt.GATT_SUCCESS

                    log.info("File Transfer Enable result: $fileTransferStatus")

                    _fileTransferState.update {
                        if (fileTransferStatus != BluetoothGatt.GATT_SUCCESS)
                            FileTransferState.GattError(fileTransferStatus)
                        else FileTransferState.Enabled
                    }
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
            if (version != null) {
                log.info("$nameOrAddress: FileTransfer Protocol v$version detected")
            } else {
                log.warning("$nameOrAddress: FileTransfer Protocol no characteristic version detected")
            }

            fileTransferVersion = version
            _fileTransferState.update { FileTransferState.EnablingNotifications }

            // Enable notifications
            fileTransferCharacteristic =
                blePeripheral.getCharacteristic(
                    kFileTransferDataCharacteristicUUID,
                    kFileTransferServiceUUID
                )

            fileTransferCharacteristic?.let { fileTransferCharacteristic ->

                // Requests a bigger MTU size
                blePeripheral.requestMtu(kPreferredMtuSize) {

                    // Set notify
                    setNotifyResponse(fileTransferCharacteristic, ::receiveFileTransferData) { notifyState ->
                        completion(notifyState)
                    }
                }
            } ?: run {
                log.warning("Cannot find fileTransferCharacteristic")
                _fileTransferState.update {
                    FileTransferState.Error(
                        FileTransferCharacteristicNotFound()
                    )
                }
            }
        }
    }

    // endregion

    // region Commands

    fun readFile(
        path: String,
        progress: FileTransferProgressHandler? = null,
        completion: ((Result<ByteArray>) -> Unit)? = null
    ) {
        log.info("Read file $path")

        if (readStatus != null) {
            log.warning("Warning: concurrent readFile")
        }

        readStatus = ReadStatus(progress = progress, completion = completion)

        val mtu = blePeripheral.mtuSize

        val offset = 0
        val chunkSize = mtu - kReadFileResponseHeaderSize
        val pathSize = path.codePoints().count().toInt()

        val data = byteArrayOf(0x10, 0x00) +
                pathSize.toByteArray16bit() +
                offset.toByteArray32bit() +
                chunkSize.toByteArray32bit() +
                path.toByteArray()

        sendCommand(data) { result ->
            if (completion != null) {
                result.exceptionOrNull()?.let { exception ->
                    completion(Result.failure(exception))
                }
            }
        }
    }

    private fun readFileChunk(
        offset: Int,
        chunkSize: Int,
        completion: ((Result<Unit>) -> Unit)?
    ) {
        val data = byteArrayOf(0x12, 0x01, 0x00, 0x00) +
                offset.toByteArray32bit() +
                chunkSize.toByteArray32bit()

        sendCommand(data) { result ->
            if (completion != null) {
                result.exceptionOrNull()?.let { exception ->
                    completion(Result.failure(exception))
                }
            }
        }
    }

    fun writeFile(
        path: String,
        data: ByteArray,
        progress: FileTransferProgressHandler? = null,
        completion: ((Result<Date?>) -> Unit)? = null
    ) {
        log.info("Write file $path")

        if (writeStatus != null) {
            log.warning("Warning: concurrent readFile")
        }

        writeStatus = WriteStatus(data = data, progress = progress, completion = completion)

        val offset = 0
        val totalSize = data.size
        val pathSize = path.codePoints().count().toInt()
        val timestamp = System.currentTimeMillis() * 1000 * 1000

        val commandData = byteArrayOf(0x20, 0x00) +
                pathSize.toByteArray16bit() +
                offset.toByteArray32bit() +
                timestamp.toByteArray64bit() +
                totalSize.toByteArray32bit()
                path.toByteArray()

        sendCommand(commandData) { result ->
            if (completion != null) {
                result.exceptionOrNull()?.let { exception ->
                    completion(Result.failure(exception))
                }
            }
        }
    }

    fun listDirectory(
        path: String,
        completion: ((Result<List<DirectoryEntry>?>) -> Unit)?
    ) {
        log.info("List directory $path")

        if (listDirectoryStatus != null) {
            log.warning("Warning: concurrent listDirectory")
        }

        listDirectoryStatus = ListDirectoryStatus(completion = completion)

        val pathSize = path.codePoints().count().toInt()
        val data = byteArrayOf(0x50, 0x00) +
                pathSize.toByteArray16bit() +
                path.toByteArray()

        sendCommand(data) { result ->
            if (completion != null) {
                result.exceptionOrNull()?.let { exception ->
                    completion(Result.failure(exception))
                }
            }
        }
    }
    // endregion

    // region Receive Data
    private fun receiveFileTransferData(data: ByteArray) {
        //log.info("received data: ${data.size}")
        // Add received data
        dataProcessingQueue.processQueue(data) { remainingData ->
            decodeResponseChunk(remainingData)
        }
    }

    // Returns number of bytes processed (they will need to be discarded from the queue)
    private fun decodeResponseChunk(data: ByteArray): Int {
        var bytesProcessed = 0

        if (data.isEmpty()) {
            log.info("Error: response invalid data")
            return bytesProcessed
        }

        val command = data[0]

        log.info("received command: ${command.toHexString()}")

        bytesProcessed = when (command) {
            0x11.toByte() -> decodeReadFile(data)
            0x51.toByte() -> decodeListDirectory(data)
            else -> {
                log.warning("Error: unknown command: ${command.toHexString()}. Invalidating all received data...")
                return Int.MAX_VALUE        // Invalidate all received data
            }
        }

        return bytesProcessed
    }

    private fun decodeReadFile(data: ByteArray): Int {
        readStatus?.let { readStatus ->
            val completion = readStatus.completion

            if (data.size < kReadFileResponseHeaderSize) {
                // Header has not been fully received yet
                return 0
            }

            val status = data[1]
            val isStatusOk = status == 0x01.toByte()

            val offset = data.readInt32(4)
            val totalLength = data.readInt32(8)
            val chunkSize = data.readInt32(12)

            if (kFileTransferDebugMessagesEnabled) {
                log.info("read ${if (isStatusOk) "ok" else "error"} at offset $offset chunkSize: $chunkSize totalLength: $totalLength")
            }

            // Report completion if status is not ok
            if (!isStatusOk) {
                this.readStatus = null

                if (completion != null) {
                    completion(Result.failure(FileTransferStatusFailedException(status.toInt())))
                }
            }

            val packetSize = kReadFileResponseHeaderSize + chunkSize
            if (data.size < packetSize) {
                // The first chunk is still no available wait for it
                return 0
            }

            // Append received chunk to buffer and update progress
            readStatus.data.write(data, kReadFileResponseHeaderSize, chunkSize)
            readStatus.progress?.let {
                it(offset + chunkSize, totalLength)
            }

            if (offset + chunkSize < totalLength) {
                val mtu = blePeripheral.mtuSize
                val maxChunkLength = mtu - kReadFileResponseHeaderSize
                readFileChunk(offset + chunkSize, maxChunkLength) { result ->
                    result.exceptionOrNull()?.let { exception ->
                        this.readStatus = null
                        if (completion != null) {
                            completion(Result.failure(exception))
                        }
                    }
                }
            } else {
                val fileData = readStatus.data.toByteArray()
                this.readStatus = null
                if (completion != null) {
                    completion(Result.success(fileData))
                }
            }

            return packetSize

        } ?: run {
            log.warning("Error: read invalid internal status. Invalidating all received data...")
            return Int.MAX_VALUE
        }
    }



    private fun decodeListDirectory(data: ByteArray): Int {
        listDirectoryStatus?.let { listDirectoryStatus ->
            val completion = listDirectoryStatus.completion

            val headerSize = kListDirectoryResponseHeaderSize
            if (data.size < headerSize) {
                // Header has not been fully received yet
                return 0
            }

            // Chunk size processed (can be less that data.count if several chunks are included in the data)
            var packetSize = headerSize

            val directoryExists = data[1] == 0x01.toByte()
            if (directoryExists && data.size >= headerSize) {
                val entryCount = data.readInt32(8)
                if (entryCount == 0) {
                    this.listDirectoryStatus = null
                    if (completion != null) {
                        completion(Result.success(emptyList()))
                    }
                } else {
                    val pathLength = data.readInt16(2)
                    val entryIndex = data.readInt32(4)

                    if (entryIndex >= entryCount) {
                        // Finished. Return entries
                        val entries = listDirectoryStatus.entries
                        this.listDirectoryStatus = null
                        if (kFileTransferDebugMessagesEnabled) {
                            log.info("list: finished")
                        }
                        if (completion != null) {
                            completion(Result.success(entries))
                        }
                    } else {
                        val flags = data.readInt32(12)
                        val isDirectory = flags and 0x1 == 1

                        var decodingOffset = 16
                        val truncatedTime = data.readLong64(decodingOffset)
                        val modificationDate = Date(Timestamp(truncatedTime).time)
                        decodingOffset += 8

                        val fileSize = data.readInt32(decodingOffset)    // Ignore for directories

                        if (data.size < headerSize + pathLength) {
                            // Path is still no available wait for it
                            return 0
                        }

                        if (pathLength > 0) {
                            val path = data.decodeToString(headerSize, headerSize + pathLength)

                            // chunk includes the variable length path, so add it
                            packetSize += pathLength

                            if (kFileTransferDebugMessagesEnabled) {
                                log.info("list: ${entryIndex + 1}/$entryCount ${if (isDirectory) "directory" else "file size: $fileSize bytes"}, path: $path")
                            }

                            val entry = DirectoryEntry(
                                name = path,
                                type = if (isDirectory) DirectoryEntry.EntryType.Directory else DirectoryEntry.EntryType.File(
                                    fileSize
                                ),
                                modificationDate = modificationDate
                            )

                            // Add entry
                            this.listDirectoryStatus?.entries?.add(entry)

                        } else {
                            this.listDirectoryStatus = null
                            if (completion != null) {
                                completion(Result.failure(FileTransferInvalidData()))
                            }
                        }

                    }
                }
            } else {
                this.listDirectoryStatus = null
                if (completion != null) {
                    completion(Result.success(null))        // null means directory does not exist
                }
            }

            return packetSize

        } ?: run {
            log.warning("Error: listDirectory invalid internal status. Invalidating all received data...")
            return Int.MAX_VALUE
        }
    }

    private fun sendCommand(
        data: ByteArray,
        completion: ((Result<Unit>) -> Unit)?
    ) {
        if (!blePeripheral.confirmConnectionState(BlePeripheral.ConnectionState.Connected)) {
            if (completion != null) {
                completion(Result.failure(FileTransferDisconnected()))
            }
        }

        fileTransferCharacteristic?.let { fileTransferCharacteristic ->

            blePeripheral.writeCharacteristic(
                fileTransferCharacteristic,
                WRITE_TYPE_NO_RESPONSE,
                data
            ) { status ->
                if (completion != null) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        completion(Result.success(Unit))
                    } else {
                        completion(Result.failure(BleStatusResultException(status)))
                    }
                }
            }

        } ?: run {
            _fileTransferState.update {
                FileTransferState.Error(
                    FileTransferCharacteristicNotFound()
                )
            }
        }
    }

    private fun setNotifyResponse(
        characteristic: BluetoothGattCharacteristic,
        updateHandler: FileTransferDataHandler,
        completion: CompletionHandler
    ) {
        // Prepare notification handler
        /* TODO: check if weak referece causes problems
        val weakUpdateHandler = WeakReference(updateHandler)
        val notifyHandler: NotifyHandler = {
            val handler = weakUpdateHandler.get()
            if (handler != null) {
                handler(characteristic.value)       // Directly read characteristic.value instead of using the getXXX functions because we are looking for the raw byte data
            }
        }*/
        val notifyHandler: NotifyHandler = {
            updateHandler(characteristic.value)
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
                    log.warning("Error reading FileTransfer service version")
                    completion(null)

                } else {
                    // Return version
                    val version = getVersion(characteristic)
                    completion(version)
                }
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

    // region Data Structures
    private data class ReadStatus(
        var data: ByteArrayOutputStream = ByteArrayOutputStream(),
        val progress: FileTransferProgressHandler? = null,
        val completion: ((Result<ByteArray>) -> Unit)? = null
    )

    private data class WriteStatus(
        val data: ByteArray,
        val progress: FileTransferProgressHandler? = null,
        val completion: ((Result<Date?>) -> Unit)? = null
    )

    private data class ListDirectoryStatus(
        var entries: MutableList<DirectoryEntry> = mutableListOf(),
        val completion: ((Result<List<DirectoryEntry>?>) -> Unit)? = null
    )

    // endregion

    // region Exceptions
    data class FileTransferStatusFailedException internal constructor(
        val code: Int,
    ) : BleException("Status Failed: $code")

    class FileTransferCharacteristicNotFound internal constructor() : BleException()
    class FileTransferDisconnected internal constructor() : BleException()
    class FileTransferInvalidData internal constructor() : BleException()
    // endregion
}