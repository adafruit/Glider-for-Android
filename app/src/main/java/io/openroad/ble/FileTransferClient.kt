package io.openroad.ble

import io.openroad.ble.filetransfer.BleFileTransferPeripheral
import io.openroad.ble.peripheral.BlePeripheral
import io.openroad.ble.peripheral.BlePeripheralConnectCompletionHandler
import java.util.*

/**
 * Created by Antonio García (antonio@openroad.es)
 */

typealias ProgressHandler = (transmittedBytes: Int, totalBytes: Int) -> Unit

class FileTransferClient(val bleFileTransferPeripheral: BleFileTransferPeripheral) {

    constructor(blePeripheral: BlePeripheral) : this(BleFileTransferPeripheral(blePeripheral))

    val address = bleFileTransferPeripheral.address

    val fileTransferState = bleFileTransferPeripheral.fileTransferState

    val peripheralName = bleFileTransferPeripheral.nameOrAddress

    fun connectAndSetup(connectionTimeout: Int? = null, completion: BlePeripheralConnectCompletionHandler) {
        bleFileTransferPeripheral.connectAndSetup(connectionTimeout, completion)
    }

    // region File Transfer Commands

    /**
    Given a full path, returns the full contents of the file
     */
    fun readFile(
        path: String,
        progress: ProgressHandler? = null,
        completion: ((Result<ByteArray>) -> Unit)? = null
    ) {
        bleFileTransferPeripheral.readFile(path, progress, completion)
    }

    /**
    Writes the content to the given full path. If the file exists, it will be overwritten
     */
    fun writeFile(
        path: String,
        data: ByteArray,
        progress: ProgressHandler? = null,
        completion: ((Result<Date?>) -> Unit)? = null
    ) {
        bleFileTransferPeripheral.writeFile(path, data, progress, completion)
    }

    /**
    Deletes the file or directory at the given full path. Directories must be empty to be deleted
     */
    fun deleteFile(
        path: String,
        completion: ((Result<Unit>) -> Unit)? = null
    ) {
        bleFileTransferPeripheral.deleteFile(path, completion)
    }

    /**
    Creates a new directory at the given full path. If a parent directory does not exist, then it will also be created. If any name conflicts with an existing file, an error will be returned
    @param path: Full path
     */
    fun makeDirectory(
        path: String,
        completion: ((Result<Date?>) -> Unit)? = null
    ) {
        bleFileTransferPeripheral.makeDirectory(path, completion)
    }

    /**
    Lists all of the contents in a directory given a full path. Returned paths are relative to the given path to reduce duplication
     */
    fun listDirectory(
        path: String,
        completion: ((Result<List<BleFileTransferPeripheral.DirectoryEntry>?>) -> Unit)?
    ) {
        bleFileTransferPeripheral.listDirectory(path, completion)
    }

    /**
    Moves a single file from fromPath to toPath
     */
    fun moveFile(
        fromPath: String,
        toPath: String,
        completion: ((Result<Unit>) -> Unit)? = null
    ) {
        bleFileTransferPeripheral.moveFile(fromPath, toPath, completion)
    }
    // endregion
}