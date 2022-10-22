package com.adafruit.glider.ui.fileexplorer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import io.openroad.filetransfer.filetransfer.FileTransferClient
import io.openroad.filetransfer.utils.pathRemovingFilename
import io.openroad.filetransfer.utils.upPath
import kotlinx.coroutines.flow.update

class FileSystemViewModel :
    FileCommandsViewModel() {

    fun setup(directory: String, fileTransferClient: FileTransferClient) {
        // Clean directory name
        val path = pathRemovingFilename(directory)
        _path.update { path }

        // List directory
        listDirectory(directory = path, fileTransferClient = fileTransferClient)
    }

    /*
        Returns the path for the parent directory or null if is a root directory and there is no parent to list
    */
    fun listParentDirectory(fileTransferClient: FileTransferClient): String? {
        if (isRootDirectory.value) return null

        val newPath = upPath(from = _path.value)
        listDirectory(newPath, fileTransferClient)
        return newPath
    }

}