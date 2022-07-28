package com.adafruit.glider.ui.fileexplorer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import io.openroad.filetransfer.FileTransferClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FileEditViewModel : FileCommandsViewModel() {

    private val _text = MutableStateFlow<String?>(null)
    val text = _text.asStateFlow()

    fun setup(filePath: String, fileTransferClient: FileTransferClient) {
        _path.update { filePath }

        // Initial read
        readFile(filePath, fileTransferClient) { result ->
            val data = result.getOrNull()
            if (data != null) {
                setData(data)
            }
        }
    }

    fun setText(text: String) {
        _text.update { text }
    }

    private fun setData(data: ByteArray) {
        _text.update { data.decodeToString() }
    }
}