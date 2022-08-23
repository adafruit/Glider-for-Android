package io.openroad.filetransfer

import io.openroad.Peripheral
import io.openroad.PeripheralConnectCompletionHandler
import kotlinx.coroutines.CoroutineScope
import java.util.*

// TypeAlias
typealias FileTransferProgressHandler = (transmittedBytes: Int, totalBytes: Int) -> Unit

interface FileTransferPeripheral {
    val peripheral: Peripheral

    // onSetupDataReceived: will be called when a Bluetooth peripheral is bonded or when a Wifi peripheral hostname is resolved
    fun connectAndSetup(
        externalScope: CoroutineScope,
        connectionTimeout: Int? = null,
        //onSetupDataReceived: (name: String?, address: String) -> Unit,
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