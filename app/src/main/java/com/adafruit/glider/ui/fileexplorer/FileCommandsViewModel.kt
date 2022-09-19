package com.adafruit.glider.ui.fileexplorer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adafruit.glider.utils.LogUtils
import io.openroad.filetransfer.DirectoryEntry
import io.openroad.filetransfer.FileTransferClient
import io.openroad.filetransfer.TransmissionLog
import io.openroad.filetransfer.TransmissionProgress
import io.openroad.utils.isRootDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.*

open class FileCommandsViewModel : ViewModel() {
    // Params
    var showOnlyDirectories = false

    // Data
    private val log by LogUtils()
    private val _isRootDirectory = MutableStateFlow(true)
    private val _entries = MutableStateFlow<List<DirectoryEntry>>(
        emptyList()
    )
    internal val _path = MutableStateFlow("")

    private val _isTransmitting = MutableStateFlow(false)
    private val _transmissionProgress = MutableStateFlow<TransmissionProgress?>(null)
    private val _lastTransmit = MutableStateFlow<TransmissionLog?>(null)

    // State
    val isRootDirectory = _isRootDirectory.asStateFlow()

    val entries = _entries.asStateFlow()
    val path = _path.asStateFlow()

    val isTransmitting = _isTransmitting.asStateFlow()
    val transmissionProgress = _transmissionProgress.asStateFlow()
    val lastTransmit = _lastTransmit.asStateFlow()

    // region Actions
    fun listDirectory(directory: String, fileTransferClient: FileTransferClient) {
        startCommand(description = "List directory")

        _isRootDirectory.update { isRootDirectory(directory) }
        _entries.update { emptyList() }
        _isTransmitting.update { true }

        fileTransferClient.listDirectory(
            externalScope = viewModelScope,
            path = directory,
        ) { result ->
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
                                exception.localizedMessage ?: "List directory unknown error"
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

        fileTransferClient.makeDirectory(
            externalScope = viewModelScope,
            path = path
        ) { result ->
            _isTransmitting.update { false }

            result.fold(
                onSuccess = { _ ->      // date
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
                                exception.localizedMessage ?: "Make directory unknown error"
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
            if (result.isSuccess) {
                // On success, force list again directory
                listDirectory(directory = this.path.value, fileTransferClient = fileTransferClient)
            }
        }
    }

    fun renameFile(fromPath: String, toPath: String, fileTransferClient: FileTransferClient) {
        moveFile(fromPath, toPath, fileTransferClient) { result ->
            if (result.isSuccess) {
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

        fileTransferClient.readFile(path = filePath, progress = { transmittedBytes, totalBytes ->
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
                                exception.localizedMessage ?: "Read file unknown error"
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
            externalScope = viewModelScope,
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
                                    exception.localizedMessage ?: "Write file unknown error"
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
            externalScope = viewModelScope,
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
                                exception.localizedMessage ?: "Move unknown error"
                            )
                        )
                    }
                    completion?.let { it(Result.failure(exception)) }
                }
            )

            endCommand()
        }
    }

    private fun setEntries(entries: List<DirectoryEntry>) {
        // Filter if needed
        val filteredEntries =
            if (showOnlyDirectories) entries.filter { it.isDirectory } else entries

        // Sort by directory and as a second criteria order by name
        val sortedEntries = filteredEntries.sortedWith(CompareDirectoryEntry)

        // Update
        _entries.update { sortedEntries }
    }

    private class CompareDirectoryEntry {
        companion object : Comparator<DirectoryEntry> {
            override fun compare(
                a: DirectoryEntry,
                b: DirectoryEntry
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

    fun delete(
        entry: DirectoryEntry,
        fileTransferClient: FileTransferClient,
        completion: ((Result<Unit>) -> Unit)? = null
    ) {
        val filename = path.value + entry.name + (if (entry.isDirectory) '/' else "")

        startCommand(description = "Deleting $filename")
        _isTransmitting.update { true }

        log.info("Deleting $filename")
        fileTransferClient.deleteFile(
            path = filename
        ) { result ->
            log.info("Delete result for $filename: ${if (result.isSuccess) "success" else "fail"}")
            _isTransmitting.update { false }

            result.fold(
                onSuccess = {
                    listDirectory(this.path.value, fileTransferClient)

                    _lastTransmit.update {
                        TransmissionLog(
                            type = TransmissionLog.TransmissionType.Delete
                        )
                    }
                    completion?.let { it(Result.success(Unit)) }
                },

                onFailure = { exception ->
                    log.warning("deleteFile $filename error: $exception")
                    _lastTransmit.update {
                        TransmissionLog(
                            type = TransmissionLog.TransmissionType.Error(
                                exception.localizedMessage ?: "Delete unknown error"
                            )
                        )
                    }
                    completion?.let { it(Result.failure(exception)) }
                }
            )

            endCommand()
        }
    }

    fun disconnect(fileTransferClient: FileTransferClient) {
        fileTransferClient.peripheral.disconnect()
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