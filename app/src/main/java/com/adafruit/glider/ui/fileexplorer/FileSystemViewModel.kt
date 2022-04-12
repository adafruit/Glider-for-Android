package com.adafruit.glider.ui.fileexplorer

import io.openroad.ble.FileTransferClient
import io.openroad.ble.utils.pathRemovingFilename
import kotlinx.coroutines.flow.update

/**
 * Created by Antonio García (antonio@openroad.es)
 */

class FileSystemViewModel :
    FileCommandsViewModel() {

    fun setup(directory: String, fileTransferClient: FileTransferClient) {
        // Clean directory name
        val path = pathRemovingFilename(directory)
        _path.update { path }

        // List directory
        listDirectory(directory = path, fileTransferClient = fileTransferClient)
    }
}