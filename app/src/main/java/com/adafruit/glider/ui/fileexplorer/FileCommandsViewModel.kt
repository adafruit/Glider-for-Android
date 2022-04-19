package com.adafruit.glider.ui.fileexplorer

import androidx.lifecycle.ViewModel
import io.openroad.ble.FileTransferClient
import io.openroad.ble.filetransfer.BleFileTransferPeripheral
import io.openroad.ble.utils.isRootDirectory
import io.openroad.utils.LogUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.*

/**
 * Created by Antonio García (antonio@openroad.es)
 */
open class FileCommandsViewModel : ViewModel() {

    // Params
    var showOnlyDirectories = false

    // Data
    private val log by LogUtils()

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
    val isTransmitting = _isTransmitting.asStateFlow()
    private val _transmissionProgress = MutableStateFlow<TransmissionProgress?>(null)
    val transmissionProgress = _transmissionProgress.asStateFlow()
    private val _lastTransmit = MutableStateFlow<TransmissionLog?>(null)
    val lastTransmit = _lastTransmit.asStateFlow()
    //MutableStateFlow(TransmissionLog(TransmissionLog.TransmissionType.Write(334, null)))


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

    fun makeDirectory(path: String, fileTransferClient: FileTransferClient) {
        startCommand(description = "Creating $path")
        _isTransmitting.update { true }

        fileTransferClient.makeDirectory(path) { result ->
            _isTransmitting.update { false }

            result.fold(
                onSuccess = { date ->

                    listDirectory(this.path.value, fileTransferClient)

                    _lastTransmit.update {
                        TransmissionLog(
                            type = TransmissionLog.TransmissionType.MakeDirectory
                        )
                    }
                },

                onFailure = { exception ->
                    log.warning("makeDirectory $path error: $exception")
                    _lastTransmit.update {
                        TransmissionLog(
                            type = TransmissionLog.TransmissionType.Error(
                                exception.localizedMessage ?: "Unknown Error"
                            )
                        )
                    }
                }
            )

            endCommand()
        }
    }

    fun makeFile(filename: String, fileTransferClient: FileTransferClient) {
        writeFile(
            filename = filename,
            data = byteArrayOf(),
            fileTransferClient = fileTransferClient
        ) { result ->
            result.getOrNull()?.let {
                // On success, force list again directory
                listDirectory(directory = this.path.value, fileTransferClient = fileTransferClient)
            }
        }
    }

    fun renameFile(fromPath: String, toPath: String, fileTransferClient: FileTransferClient) {
        moveFile(fromPath, toPath, fileTransferClient) { result ->
            result.getOrNull()?.let {
                // On success, force list again directory
                listDirectory(directory = this.path.value, fileTransferClient = fileTransferClient)
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
            val newProgress = transmissionProgress.value?.copy(
                transmittedBytes = transmittedBytes,
                totalBytes = totalBytes
            )
            _transmissionProgress.update { newProgress }
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
                    completion?.let { it(Result.success(data)) }

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
                    completion?.let { it(Result.failure(exception)) }
                }
            )

            endCommand()
        })
    }

    fun writeFile(
        filename: String,
        data: ByteArray,
        fileTransferClient: FileTransferClient,
        completion: ((Result<Date?>) -> Unit)? = null
    ) {
        startCommand(description = "Writing $filename")
        _isTransmitting.update { true }

        fileTransferClient.writeFile(
            path = filename,
            data = data,
            progress = { transmittedBytes, totalBytes ->
                val newProgress = transmissionProgress.value?.copy(
                    transmittedBytes = transmittedBytes,
                    totalBytes = totalBytes
                )
                _transmissionProgress.update { newProgress }
            },
            completion = { result ->
                _isTransmitting.update { false }

                result.fold(
                    onSuccess = { date ->
                        _lastTransmit.update {
                            TransmissionLog(
                                type = TransmissionLog.TransmissionType.Write(
                                    data.size, date
                                )
                            )
                        }
                        completion?.let { it(Result.success(date)) }
                    },
                    onFailure = { exception ->
                        log.warning("writeFile $filename error: $exception")
                        _lastTransmit.update {
                            TransmissionLog(
                                type = TransmissionLog.TransmissionType.Error(
                                    exception.localizedMessage ?: "Unknown Error"
                                )
                            )
                        }
                        completion?.let { it(Result.failure(exception)) }
                    }
                )

                endCommand()
            })
    }

    fun moveFile(
        fromPath: String,
        toPath: String,
        fileTransferClient: FileTransferClient,
        completion: ((Result<Unit>) -> Unit)? = null
    ) {
        startCommand(description = "Moving from $fromPath to $toPath")
        _isTransmitting.update { true }

        fileTransferClient.moveFile(
            fromPath = fromPath,
            toPath = toPath
        ) { result ->
            _isTransmitting.update { false }

            result.fold(
                onSuccess = {
                    _lastTransmit.update {
                        TransmissionLog(
                            type = TransmissionLog.TransmissionType.Move
                        )
                    }
                    completion?.let { it(Result.success(Unit)) }
                },

                onFailure = { exception ->
                    log.warning("moveFile from $fromPath to $path error: $exception")
                    _lastTransmit.update {
                        TransmissionLog(
                            type = TransmissionLog.TransmissionType.Error(
                                exception.localizedMessage ?: "Unknown Error"
                            )
                        )
                    }
                    completion?.let { it(Result.failure(exception)) }
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