package io.openroad.filetransfer.filetransfer

import io.openroad.filetransfer.Peripheral
import io.openroad.filetransfer.PeripheralConnectCompletionHandler
import kotlinx.coroutines.CoroutineScope
import java.util.*

// TypeAlias
typealias FileTransferProgressHandler = (transmittedBytes: Int, totalBytes: Int) -> Unit

interface FileTransferPeripheral {
    val peripheral: Peripheral

    fun connectAndSetup(
        externalScope: CoroutineScope,
        connectionTimeout: Int? = null,
        completion: PeripheralConnectCompletionHandler
    )

    fun listDirectory(
        externalScope: CoroutineScope,
        path: String,
        completion: ((Result<List<DirectoryEntry>?>) -> Unit)?
    )

    fun makeDirectory(
        externalScope: CoroutineScope,
        path: String,
        completion: ((Result<Date?>) -> Unit)? = null
    )

    fun readFile(
        externalScope: CoroutineScope,
        path: String,
        progress: FileTransferProgressHandler? = null,
        completion: ((Result<ByteArray>) -> Unit)? = null
    )

    fun writeFile(
        externalScope: CoroutineScope,
        path: String,
        data: ByteArray,
        progress: FileTransferProgressHandler? = null,
        completion: ((Result<Date?>) -> Unit)? = null
    )

    fun deleteFile(
        externalScope: CoroutineScope,
        path: String,
        completion: ((Result<Unit>) -> Unit)? = null
    )

    fun moveFile(
        externalScope: CoroutineScope,
        fromPath: String,
        toPath: String,
        completion: ((Result<Unit>) -> Unit)? = null
    )
}