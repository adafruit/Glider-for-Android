package io.openroad.filetransfer.filetransfer
/**
 * Created by Antonio García (antonio@openroad.es)
 */

import io.openroad.filetransfer.Peripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import java.util.*

class FileTransferClient(
    val fileTransferPeripheral: FileTransferPeripheral,
) {
    val peripheral: Peripheral = fileTransferPeripheral.peripheral

    // region File Transfer Commands
    /**
    Given a full path, returns the full contents of the file
     */
    fun readFile(
        externalScope: CoroutineScope = MainScope(),
        path: String,
        progress: FileTransferProgressHandler? = null,
        completion: ((Result<ByteArray>) -> Unit)? = null
    ) {
        fileTransferPeripheral.readFile(externalScope, path, progress, completion)
    }

    /**
    Writes the content to the given full path. If the file exists, it will be overwritten
     */
    fun writeFile(
        externalScope: CoroutineScope = MainScope(),
        path: String,
        data: ByteArray,
        progress: FileTransferProgressHandler? = null,
        completion: ((Result<Date?>) -> Unit)? = null
    ) {
        fileTransferPeripheral.writeFile(externalScope,path, data, progress, completion)
    }

    /**
    Deletes the file or directory at the given full path. Directories must be empty to be deleted
     */
    fun deleteFile(
        externalScope: CoroutineScope = MainScope(),
        path: String,
        completion: ((Result<Unit>) -> Unit)? = null
    ) {
        fileTransferPeripheral.deleteFile(externalScope,path, completion)
    }

    /**
    Creates a new directory at the given full path. If a parent directory does not exist, then it will also be created. If any name conflicts with an existing file, an error will be returned
    @param path: Full path
     */
    fun makeDirectory(
        externalScope: CoroutineScope = MainScope(),
        path: String,
        completion: ((Result<Date?>) -> Unit)? = null
    ) {
        fileTransferPeripheral.makeDirectory(externalScope,path, completion)
    }

    /**
    Lists all of the contents in a directory given a full path. Returned paths are relative to the given path to reduce duplication
     */
    fun listDirectory(
        externalScope: CoroutineScope = MainScope(),
        path: String,
        completion: ((Result<List<DirectoryEntry>?>) -> Unit)?
    ) {
        fileTransferPeripheral.listDirectory(externalScope, path, completion)
    }

    /**
    Moves a single file from fromPath to toPath
     */
    fun moveFile(
        externalScope: CoroutineScope = MainScope(),
        fromPath: String,
        toPath: String,
        completion: ((Result<Unit>) -> Unit)? = null
    ) {
        fileTransferPeripheral.moveFile(externalScope,fromPath, toPath, completion)
    }
    // endregion
}
