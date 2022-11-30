package io.openroad.filetransfer.filetransfer

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
import androidx.annotation.MainThread
import androidx.annotation.RequiresPermission
import com.adafruit.glider.utils.LogUtils
import io.openroad.filetransfer.BuildConfig
import io.openroad.filetransfer.PeripheralConnectCompletionHandler
import io.openroad.filetransfer.ble.bond.BleBondState
import io.openroad.filetransfer.ble.peripheral.BlePeripheral
import io.openroad.filetransfer.ble.peripheral.CompletionHandler
import io.openroad.filetransfer.ble.peripheral.NotifyHandler
import io.openroad.filetransfer.ble.peripheral.kClientCharacteristicConfigUUID
import io.openroad.filetransfer.ble.utils.*
import io.openroad.filetransfer.utils.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
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

const val kPreferredMtuSize = 512

const val kReadFileResponseHeaderSize = 16              // (1+1+2+4+4+4+variable)
const val kWriteChunkHeaderSize = 12                     // 4 + 4 + 4
const val kWriteFileResponseHeaderSize = 20             // (1+1+2+4+8+4)
const val kListDirectoryResponseHeaderSize = 28         // (1+1+2+4+4+4+8+4+variable)
const val kMakeDirectoryResponseHeaderSize = 16         // (1+1+6+8)
const val kDeleteFileResponseHeaderSize = 2             // (1+1)
const val kMoveFileResponseHeaderSize = 2               // (1+1)

const val kForceEnableNotifyEvenIfAlreadyEnabled = true     // Force enable notify. Solves problem with notify not set for bonded devices on CircuitPython

class BleFileTransferPeripheral(
    override val peripheral: BlePeripheral,
    private val onBonded: ((name: String?, address: String) -> Unit)?,
) : FileTransferPeripheral {

    // Data - Private
    companion object {
        private val log by LogUtils()

        init {
            log.addHandler(LogHandler())
        }
    }

    private var fileTransferVersion: Int? = null
    private var dataProcessingQueue = DataProcessingQueue()

    private var readStatus: ReadStatus? = null
    private var writeStatus: WriteStatus? = null
    private var deleteStatus: DeleteStatus? = null
    private var listDirectoryStatus: ListDirectoryStatus? = null
    private var makeDirectoryStatus: MakeDirectoryStatus? = null
    private var moveStatus: MoveStatus? = null

    private var setupCompletionHandler: PeripheralConnectCompletionHandler? = null

    // Data
    val address = peripheral.address
    val nameOrAddress = peripheral.nameOrAddress
    //val connectionState = peripheral.connectionState

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
        object Bonding : FileTransferState()
        object Enabled : FileTransferState()
        data class GattError(val gattErrorCode: Int) : FileTransferState()
        data class Error(val cause: Throwable? = null) : FileTransferState()
    }

    private val _fileTransferState =
        MutableStateFlow<FileTransferState>(FileTransferState.Start)
    val fileTransferState = _fileTransferState.asStateFlow()

    // Data - Internal
    private var fileTransferCharacteristic: BluetoothGattCharacteristic? = null

    // region Lifecycle
    init {
        // Warning: make sure that there is only ONE BlePeripheralTransferPeripheral for each peripheral or this "state change listener" will be executed multiple times
        peripheral.externalScope.launch {
            // Listen to state changes
            peripheral.connectionState
                /*
            .onStart {
                log.info("blePeripheral.connectionState onStart")
            }*/
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
                            try {
                                discoverServicesAndEnableFileTransfer()
                            } catch (e: SecurityException) {
                                disconnect(BleDiscoveryException())
                            }
                        }
                        is BlePeripheral.ConnectionState.Disconnecting -> _fileTransferState.update {
                            FileTransferState.Disconnecting(connectionState.cause)
                        }
                        is BlePeripheral.ConnectionState.Disconnected -> _fileTransferState.update {
                            setupCompletionHandler?.let {
                                it(
                                    Result.failure(
                                        connectionState.cause
                                            ?: BleConnectionInvalidStateException()
                                    )
                                )
                            }
                            disable()
                            FileTransferState.Disconnected(connectionState.cause)
                        }
                    }
                }

            peripheral.bondingState.collect { bondState ->
                when (bondState) {
                    BleBondState.Bonding -> _fileTransferState.update { FileTransferState.Bonding }
                    else -> {}
                }
            }
        }
    }
    // endregion

    // region Actions

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [BLUETOOTH_SCAN, BLUETOOTH_CONNECT])
    @MainThread
    override fun connectAndSetup(
        externalScope: CoroutineScope,
        connectionTimeout: Int?,
        completion: PeripheralConnectCompletionHandler
    ) {
        disable()

        _fileTransferState.update { FileTransferState.Start }
        // Connects using the standard connect function but the state changes will be collected to trigger the next steps in the FileTransfer setup
        peripheral.connect(
            connectionTimeout = connectionTimeout,
            onBonded = onBonded
        ) { result ->
            // if connected, wait to call completion until setup has finished
            result.fold(
                onSuccess = { setupCompletionHandler = completion },
                onFailure = { completion(Result.failure(it)) }
            )
        }
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(value = BLUETOOTH_CONNECT)
    fun disconnect(cause: Throwable? = null) = peripheral.disconnect(cause)

    private fun disable() {
        //log.info("disable $nameOrAddress")

        // Clear all internal data
        fileTransferVersion = null
        setupCompletionHandler = null

        dataProcessingQueue.reset()

        readStatus?.completion?.let {
            it(
                Result.failure(
                    BleDisconnectedWhileWaitingForCommandException()
                )
            )
        }
        readStatus = null
        writeStatus?.completion?.let {
            it(
                Result.failure(
                    BleDisconnectedWhileWaitingForCommandException()
                )
            )
        }
        writeStatus = null
        deleteStatus?.completion?.let {
            it(
                Result.failure(
                    BleDisconnectedWhileWaitingForCommandException()
                )
            )
        }
        deleteStatus = null
        listDirectoryStatus?.completion?.let {
            it(
                Result.failure(
                    BleDisconnectedWhileWaitingForCommandException()
                )
            )
        }
        listDirectoryStatus = null
        makeDirectoryStatus?.completion?.let {
            it(
                Result.failure(
                    BleDisconnectedWhileWaitingForCommandException()
                )
            )
        }
        makeDirectoryStatus = null
        moveStatus?.completion?.let {
            it(
                Result.failure(
                    BleDisconnectedWhileWaitingForCommandException()
                )
            )
        }
        moveStatus = null
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(value = BLUETOOTH_CONNECT)
    private fun discoverServicesAndEnableFileTransfer() {
        log.info("Discovering services...")
        peripheral.discoverServices { discoverStatus ->
            if (discoverStatus == BluetoothGatt.GATT_SUCCESS) {
                // Discovery finished. Setup services
                fileTransferEnable { fileTransferStatus ->        // BluetoothGatt.GATT_SUCCESS

                    log.info("File Transfer Enable result: $fileTransferStatus")

                    _fileTransferState.update {
                        if (fileTransferStatus != BluetoothGatt.GATT_SUCCESS)
                            FileTransferState.GattError(fileTransferStatus)
                        else FileTransferState.Enabled
                    }

                    val completionHandler = setupCompletionHandler
                    this.setupCompletionHandler = null
                    completionHandler?.let { it(Result.success(Unit)) }
                }
            } else {
                peripheral.disconnect(BleDiscoveryException())
            }
        }
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(value = BLUETOOTH_CONNECT)
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
                peripheral.getCharacteristic(
                    kFileTransferDataCharacteristicUUID,
                    kFileTransferServiceUUID
                )

            fileTransferCharacteristic?.let { fileTransferCharacteristic ->

                // Requests a bigger MTU size
                peripheral.requestMtu(kPreferredMtuSize) {

                    // Set notify
                    setNotifyResponse(
                        fileTransferCharacteristic,
                        ::receiveFileTransferData
                    ) { notifyState ->
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

    override fun readFile(
        externalScope: CoroutineScope,
        path: String,
        progress: FileTransferProgressHandler?,
        completion: ((Result<ByteArray>) -> Unit)?
    ) {
        log.info("Read file $path")

        if (readStatus != null) {
            log.warning("Warning: concurrent readFile")
        }

        readStatus = ReadStatus(progress = progress, completion = completion)

        val mtu = peripheral.mtuSize

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

    override fun writeFile(
        externalScope: CoroutineScope,
        path: String,
        data: ByteArray,
        progress: FileTransferProgressHandler?,
        completion: ((Result<Date?>) -> Unit)?
    ) {
        log.info("Write file $path")

        if (writeStatus != null) {
            log.warning("Warning: concurrent writeFile")
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
                totalSize.toByteArray32bit() +
                path.toByteArray()

        sendCommand(commandData) { result ->
            if (completion != null) {
                result.exceptionOrNull()?.let { exception ->
                    completion(Result.failure(exception))
                }
            }
        }
    }

    private fun writeFileChunk(
        offset: Int,
        chunkSize: Int,
        completion: ((Result<Unit>) -> Unit)? = null
    ) {
        writeStatus?.let { writeStatus ->
            val chunkData = writeStatus.data.copyOfRange(offset, offset + chunkSize)

            val data = byteArrayOf(0x22, 0x01, 0x00, 0x00) +
                    offset.toByteArray32bit() +
                    chunkSize.toByteArray32bit() +
                    chunkData

            if (kFileTransferDebugMessagesEnabled) {
                log.info("write chunk at offset $offset chunkSize: $chunkSize. message size: ${data.size}. mtu: ${peripheral.mtuSize}")
            }

            sendCommand(data, completion)
        } ?: run {
            completion?.let { it(Result.failure(FileTransferInvalidInternalState())) }
        }
    }

    override fun deleteFile(
        externalScope: CoroutineScope,
        path: String,
        completion: ((Result<Unit>) -> Unit)?
    ) {
        log.info("Delete file from $path")

        if (deleteStatus != null) {
            log.warning("Warning: concurrent deleteFile")
        }

        deleteStatus = DeleteStatus(completion = completion)

        val pathSize = path.codePoints().count().toInt()
        val data = byteArrayOf(0x30, 0x00) +
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

    override fun listDirectory(
        externalScope: CoroutineScope,
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

    override fun makeDirectory(
        externalScope: CoroutineScope,
        path: String,
        completion: ((Result<Date?>) -> Unit)?
    ) {
        log.info("Make directory $path")

        if (makeDirectoryStatus != null) {
            log.warning("Warning: concurrent makeDirectory")
        }

        makeDirectoryStatus = MakeDirectoryStatus(completion = completion)

        val pathSize = path.codePoints().count().toInt()
        val timestamp = System.currentTimeMillis() * 1000 * 1000

        val data = byteArrayOf(0x40, 0x00) +
                pathSize.toByteArray16bit() +
                byteArrayOf(0x00, 0x00, 0x00, 0x00) +      // 4 bytes padding
                timestamp.toByteArray64bit() +
                path.toByteArray()

        sendCommand(data) { result ->
            if (completion != null) {
                result.exceptionOrNull()?.let { exception ->
                    completion(Result.failure(exception))
                }
            }
        }
    }

    override fun moveFile(
        externalScope: CoroutineScope,
        fromPath: String,
        toPath: String,
        completion: ((Result<Unit>) -> Unit)?
    ) {
        log.info("Move file from $fromPath to $toPath")

        if (moveStatus != null) {
            log.warning("Warning: concurrent moveDirectory")
        }

        moveStatus = MoveStatus(completion = completion)

        val fromPathSize = fromPath.codePoints().count().toInt()
        val toPathSize = toPath.codePoints().count().toInt()

        val data = byteArrayOf(0x60, 0x00) +
                fromPathSize.toByteArray16bit() +
                toPathSize.toByteArray16bit() +
                fromPath.toByteArray() +
                byteArrayOf(0x00) +      // Padding byte
                toPath.toByteArray()

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

        //log.info("received command: ${command.toHexString()}")

        bytesProcessed = when (command) {
            0x11.toByte() -> decodeReadFile(data)
            0x21.toByte() -> decodeWriteFile(data)
            0x31.toByte() -> decodeDeleteFile(data)
            0x41.toByte() -> decodeMakeDirectory(data)
            0x51.toByte() -> decodeListDirectory(data)
            0x61.toByte() -> decodeMoveFile(data)

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
                completion?.let { it(Result.failure(FileTransferStatusFailedException(status.toInt()))) }
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
                val mtu = peripheral.mtuSize
                val maxChunkLength = mtu - kReadFileResponseHeaderSize
                readFileChunk(offset + chunkSize, maxChunkLength) { result ->
                    result.exceptionOrNull()?.let { exception ->
                        this.readStatus = null
                        completion?.let { it(Result.failure(exception)) }
                    }
                }
            } else {
                val fileData = readStatus.data.toByteArray()
                this.readStatus = null
                completion?.let { it(Result.success(fileData)) }
            }

            return packetSize

        } ?: run {
            log.warning("Error: read invalid internal status. Invalidating all received data...")
            return Int.MAX_VALUE
        }
    }

    private fun decodeWriteFile(data: ByteArray): Int {
        writeStatus?.let { writeStatus ->
            val completion = writeStatus.completion

            if (data.size < kWriteFileResponseHeaderSize) {
                // Header has not been fully received yet
                return 0
            }

            var decodingOffset = 1
            val status = data[decodingOffset]
            val isStatusOk = status == 0x01.toByte()

            decodingOffset = 4      // Skip padding
            val offset = data.readInt32(4)
            decodingOffset += 4
            val truncatedTime = data.readLong64(decodingOffset)
            val writeDate = Date(Timestamp(truncatedTime).time)
            decodingOffset += 8
            val freeSpace = data.readInt32(decodingOffset)
            if (kFileTransferDebugMessagesEnabled) {
                log.info("write ${if (isStatusOk) "ok" else "error"} at offset $offset freeSpace: $freeSpace")
            }

            if (!isStatusOk) {
                this.writeStatus = null
                completion?.let { it(Result.failure(FileTransferStatusFailedException(status.toInt()))) }
                return Int.MAX_VALUE
            }

            writeStatus.progress?.let {
                it(offset, writeStatus.data.size)
            }

            if (offset >= writeStatus.data.size) {
                this.writeStatus = null
                completion?.let { it(Result.success(writeDate)) }

            } else {
                //val chunkSize = freeSpace.coerceIn(0, 25)
                val chunkSize = freeSpace.coerceIn(0, peripheral.mtuSize - kWriteChunkHeaderSize - 4)           // - 4 because it fails if closer to the MTU size (maybe CircuitPython is not handling all the mtusize message)
                writeFileChunk(offset = offset, chunkSize = chunkSize) { result ->
                    val exception = result.exceptionOrNull()
                    if (exception != null) {
                        this.writeStatus = null
                        completion?.let { it(Result.failure(exception)) }
                    }
                }
            }

            return kWriteFileResponseHeaderSize

        } ?: run {
            log.warning("Error: read invalid internal status. Invalidating all received data...")
            return Int.MAX_VALUE
        }
    }

    private fun decodeDeleteFile(data: ByteArray): Int {
        deleteStatus?.let { deleteStatus ->
            val completion = deleteStatus.completion

            if (data.size < kDeleteFileResponseHeaderSize) {
                // Header has not been fully received yet
                return 0
            }

            val status = data[1]
            val isDeleted = status == 0x01.toByte()

            this.deleteStatus = null
            completion?.let {
                if (isDeleted) {
                    it(Result.success(Unit))
                } else {
                    it(Result.failure(FileTransferStatusFailedException(status.toInt())))
                }
            }

            return kDeleteFileResponseHeaderSize

        } ?: run {
            log.warning("Error: read invalid internal status. Invalidating all received data...")
            return Int.MAX_VALUE
        }
    }

    private fun decodeMakeDirectory(data: ByteArray): Int {
        makeDirectoryStatus?.let { makeDirectoryStatus ->
            val completion = makeDirectoryStatus.completion

            if (data.size < kMakeDirectoryResponseHeaderSize) {
                // Header has not been fully received yet
                return 0
            }

            val status = data[1]
            val isCreated = status == 0x01.toByte()

            this.makeDirectoryStatus = null
            completion?.let {
                if (isCreated) {
                    val truncatedTime = data.readLong64(8)
                    val modificationDate = Date(Timestamp(truncatedTime).time)
                    it(Result.success(modificationDate))
                } else {
                    it(Result.failure(FileTransferStatusFailedException(status.toInt())))
                }
            }

            return kMakeDirectoryResponseHeaderSize

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
                    completion?.let { it(Result.success(emptyList())) }
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
                        completion?.let { it(Result.success(entries)) }
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
                            completion?.let { it(Result.failure(FileTransferInvalidData())) }
                        }

                    }
                }
            } else {
                this.listDirectoryStatus = null
                completion?.let { it(Result.success(null)) }        // null means directory does not exist
            }

            return packetSize

        } ?: run {
            log.warning("Error: listDirectory invalid internal status. Invalidating all received data...")
            return Int.MAX_VALUE
        }
    }

    private fun decodeMoveFile(data: ByteArray): Int {
        moveStatus?.let { moveStatus ->
            val completion = moveStatus.completion

            if (data.size < kMoveFileResponseHeaderSize) {
                // Header has not been fully received yet
                return 0
            }

            val status = data[1]
            val isMoved = status == 0x01.toByte()

            this.moveStatus = null
            completion?.let {
                if (isMoved) {
                    it(Result.success(Unit))
                } else {
                    it(Result.failure(FileTransferStatusFailedException(status.toInt())))
                }
            }

            return kMoveFileResponseHeaderSize

        } ?: run {
            log.warning("Error: read invalid internal status. Invalidating all received data...")
            return Int.MAX_VALUE
        }
    }

    private fun sendCommand(
        data: ByteArray,
        completion: ((Result<Unit>) -> Unit)?
    ) {
        if (!peripheral.confirmConnectionState(BlePeripheral.ConnectionState.Connected)) {
            completion?.let { it(Result.failure(FileTransferDisconnected())) }
        }

        fileTransferCharacteristic?.let { fileTransferCharacteristic ->

            peripheral.writeCharacteristic(
                fileTransferCharacteristic,
                WRITE_TYPE_NO_RESPONSE,
                data
            ) { status ->
                completion?.let {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        it(Result.success(Unit))
                    } else {
                        it(Result.failure(BleStatusResultException(status)))
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

    @SuppressLint("InlinedApi")
    @RequiresPermission(value = BLUETOOTH_CONNECT)
    private fun setNotifyResponse(
        characteristic: BluetoothGattCharacteristic,
        updateHandler: FileTransferDataHandler,
        completion: CompletionHandler
    ) {
        // Prepare notification handler
        /* TODO: check if weak reference causes problems
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
        peripheral.readDescriptor(
            characteristic,
            kClientCharacteristicConfigUUID
        ) { status ->
            if (status != BluetoothGatt.GATT_SUCCESS) {
                completion(status)
                return@readDescriptor
            }

            // Enable (or update) notifications. Note: check if a notify handler is registered because even if the notify flag is set we need to be sure than our notification handler is registered or we will miss notifications
            if (kForceEnableNotifyEvenIfAlreadyEnabled || (!peripheral.isCharacteristicNotifyingForCachedClientConfigDescriptor(characteristic) || peripheral.isNotifyHandlerRegistered(characteristic))) {
                peripheral.characteristicEnableNotify(characteristic, notifyHandler, completion)
            } else {
                peripheral.characteristicUpdateNotify(characteristic, notifyHandler)
                completion(BluetoothGatt.GATT_SUCCESS)
            }
        }
    }

    private fun getVersion(completion: (version: Int?) -> Unit) {
        val characteristic = peripheral.getCharacteristic(
            kFileTransferVersionCharacteristicUUID,
            kFileTransferServiceUUID
        )

        if (characteristic == null) {
            completion(null)
        } else {
            // Read version
            peripheral.readCharacteristic(characteristic) { status: Int, _: ByteArray? ->
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
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as WriteStatus

            if (!data.contentEquals(other.data)) return false
            if (progress != other.progress) return false
            if (completion != other.completion) return false

            return true
        }

        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + (progress?.hashCode() ?: 0)
            result = 31 * result + (completion?.hashCode() ?: 0)
            return result
        }
    }

    private data class DeleteStatus(
        val completion: ((Result<Unit>) -> Unit)? = null
    )

    private data class ListDirectoryStatus(
        var entries: MutableList<DirectoryEntry> = mutableListOf(),
        val completion: ((Result<List<DirectoryEntry>?>) -> Unit)? = null
    )

    private data class MakeDirectoryStatus(
        val completion: ((Result<Date?>) -> Unit)? = null
    )

    private data class MoveStatus(
        val completion: ((Result<Unit>) -> Unit)? = null
    )

    // endregion

    // region Exceptions
    data class FileTransferStatusFailedException internal constructor(
        val code: Int,
    ) : BleException(if (code == 5) "status error $code. Filesystem in read-only mode" else "status error: $code")

    class FileTransferCharacteristicNotFound internal constructor() : BleException()
    class FileTransferDisconnected internal constructor() : BleException()
    class FileTransferInvalidData internal constructor() : BleException()
    class FileTransferInvalidInternalState internal constructor() : BleException()
    // endregion
}