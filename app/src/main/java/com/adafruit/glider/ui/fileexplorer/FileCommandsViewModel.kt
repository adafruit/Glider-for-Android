package com.adafruit.glider.ui.fileexplorer

import androidx.lifecycle.ViewModel
import io.openroad.ble.FileTransferClient
import io.openroad.ble.filetransfer.BleFileTransferPeripheral
import io.openroad.ble.utils.isRootDirectory
import io.openroad.utils.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Created by Antonio García (antonio@openroad.es)
 */
open class FileCommandsViewModel() : ViewModel() {

    // Params
    var showOnlyDirectories = false

    // State
    private val _isRootDirectory = MutableStateFlow(true)
    val isRootDirectory = _isRootDirectory.asStateFlow()

    private val _entries = MutableStateFlow<List<BleFileTransferPeripheral.DirectoryEntry>>(
        emptyList()
        /*
        listOf(
            BleFileTransferPeripheral.DirectoryEntry(name = "cosa", type = BleFileTransferPeripheral.DirectoryEntry.EntryType.File(344)),
        )*/
    )
    val entries = _entries.asStateFlow()
    internal val _path = MutableStateFlow("")
    val path = _path.asStateFlow()

    private val _isTransmitting = MutableStateFlow(false)
    private val _transmissionProgress = MutableStateFlow<TransmissionProgress?>(null)
    private val _lastTransmit = MutableStateFlow<TransmissionLog?>(null)
    //MutableStateFlow(TransmissionLog(TransmissionLog.TransmissionType.Write(334, null)))

    // Data - Private
    private val log by LogUtils()

    // region Actions

    fun listDirectory(directory: String, fileTransferClient: FileTransferClient) {
        startCommand(description = "List directory")

        _isRootDirectory.update { isRootDirectory(directory) }
        _entries.update { emptyList() }
        _isTransmitting.update { true }

        fileTransferClient.listDirectory(directory) { result ->
            _isTransmitting.update { false }

            result.fold(
                onSuccess = { entries ->
                    if (entries != null) {
                        setEntries(entries)
                    } else {
                        log.info("listDirectory: nonexistent directory")
                    }
                    _path.update { directory }
                    _lastTransmit.update {
                        TransmissionLog(
                            TransmissionLog.TransmissionType.ListDirectory(
                                numItems = entries?.size
                            )
                        )
                    }
                },
                onFailure = { exception ->
                    log.warning("listDirectory $directory: error: $exception")
                    _lastTransmit.update {
                        TransmissionLog(
                            TransmissionLog.TransmissionType.Error(
                                exception.localizedMessage ?: "Unknown Error"
                            )
                        )
                    }
                }
            )

            endCommand()
        }
    }

    private fun setEntries(entries: List<BleFileTransferPeripheral.DirectoryEntry>) {
        // Filter if needed
        val filteredEntries =
            if (showOnlyDirectories) entries.filter { it.isDirectory } else entries

        // Sort by directory and as a second criteria order by name
        val sortedEntries = filteredEntries.sortedWith(CompareDirectoryEntry)

        // Update
        _entries.update { sortedEntries }
    }

    private class CompareDirectoryEntry {
        companion object : Comparator<BleFileTransferPeripheral.DirectoryEntry> {
            override fun compare(
                a: BleFileTransferPeripheral.DirectoryEntry,
                b: BleFileTransferPeripheral.DirectoryEntry
            ): Int = when {
                // Both directories: order alphabetically
                a.isDirectory && b.isDirectory -> a.name.compareTo(b.name)

                // Both files: order alphabetically
                !a.isDirectory && !b.isDirectory -> a.name.compareTo(b.name)

                // Compare directory and file
                else -> if (a.isDirectory) -1 else 1
            }
        }
    }

    fun readFile(
        filePath: String,
        fileTransferClient: FileTransferClient,
        completion: ((Result<ByteArray>) -> Unit)? = null
    ) {
        startCommand(description = "Reading $filePath")
        _isTransmitting.update { true }

        fileTransferClient.readFile(filePath, progress = { transmittedBytes, totalBytes ->
            _transmissionProgress.value?.transmittedBytes = transmittedBytes
            _transmissionProgress.value?.totalBytes = totalBytes
        }, completion = { result ->
            _isTransmitting.update { false }

            result.fold(
                onSuccess = { data ->
                    _lastTransmit.update {
                        TransmissionLog(
                            type = TransmissionLog.TransmissionType.Read(
                                data.size
                            )
                        )
                    }
                    if (completion != null) {
                        completion(Result.success(data))
                    }
                },

                onFailure = { exception ->
                    log.warning("readFile $filePath error: $exception")
                    _lastTransmit.update {
                        TransmissionLog(
                            type = TransmissionLog.TransmissionType.Error(
                                exception.localizedMessage ?: "Unknown Error"
                            )
                        )
                    }
                    if (completion != null) {
                        completion(Result.failure(exception))
                    }
                }
            )

            endCommand()
        })
    }

    // endregion

    // region Transmission Status

    private fun startCommand(description: String) {
        _transmissionProgress.update { TransmissionProgress(description) }
        _lastTransmit.update { null }
    }

    private fun endCommand() {
        _transmissionProgress.update { null }
    }

    // endregion
}