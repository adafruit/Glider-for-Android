package io.openroad.ble

import io.openroad.ble.filetransfer.BleFileTransferPeripheral
import io.openroad.ble.peripheral.BlePeripheral
import java.util.*

/**
 * Created by Antonio García (antonio@openroad.es)
 */

typealias ProgressHandler = (transmittedBytes: Int, totalBytes: Int) -> Unit

class FileTransferClient(blePeripheral: BlePeripheral) {

    private val fileTransferPeripheral: BleFileTransferPeripheral =
        BleFileTransferPeripheral(blePeripheral)
    val fileTransferState = fileTransferPeripheral.fileTransferState

    val peripheralName = fileTransferPeripheral.nameOrAddress

    fun connectAndSetup() {
        fileTransferPeripheral.connect()
    }

    // region File Transfer Commands

    /*
         Given a full path, returns the full contents of the file
     */
    fun readFile(
        path: String,
        progress: ProgressHandler? = null,
        completion: ((Result<ByteArray>) -> Unit)? = null
    ) {
        fileTransferPeripheral.readFile(path, progress, completion)
    }

    /*
       Writes the content to the given full path. If the file exists, it will be overwritten
   */
    fun writeFile(
        path: String,
        data: ByteArray,
        progress: ProgressHandler? = null,
        completion: ((Result<Date?>) -> Unit)? = null
    ) {
        fileTransferPeripheral.writeFile(path, data, progress, completion)
    }

    /*
        Lists all of the contents in a directory given a full path. Returned paths are relative to the given path to reduce duplication
     */
    fun listDirectory(
        path: String,
        completion: ((Result<List<BleFileTransferPeripheral.DirectoryEntry>?>) -> Unit)?
    ) {
        fileTransferPeripheral.listDirectory(path, completion)
    }


    // endregion
}