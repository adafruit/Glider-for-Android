package io.openroad.filetransfer

import io.openroad.filetransfer.filetransfer.DirectoryEntry

/**
 * Created by Antonio García (antonio@openroad.es)
 */

// TypeAlias
typealias PeripheralConnectCompletionHandler = (Result<Unit>) -> Unit// (isConnected: Boolean) -> Unit

interface Peripheral {
    val name: String?
    val address: String

    /*
    sealed class Type {
        object Bluetooth: Type()
        object Wifi: Type()
    }
    val type: Type*/

    val nameOrAddress: String

    val createdMillis: Long      // Time when it was created (usually the time when it was discovered)

    fun disconnect(cause: Throwable? = null)
}

