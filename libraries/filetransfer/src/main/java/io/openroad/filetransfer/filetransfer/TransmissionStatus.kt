package io.openroad.filetransfer.filetransfer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import java.util.*

data class TransmissionProgress(
    var description: String,
    var transmittedBytes: Int = 0,
    var totalBytes: Int? = null,
)

data class TransmissionLog(
    val type: TransmissionType
) {
    sealed class TransmissionType {
        data class Read(val size: Int) : TransmissionType()
        data class Write(val size: Int, val date: Date?) : TransmissionType()
        object Delete : TransmissionType()
        object Move : TransmissionType()
        data class ListDirectory(val numItems: Int?) : TransmissionType()
        object MakeDirectory : TransmissionType()
        data class Error(val message: String) : TransmissionType()
    }

    val isError = type is TransmissionType.Error

    val description: String = when (type) {
        is TransmissionType.Read -> {
            "Received ${type.size} bytes"
        }
        is TransmissionType.Write -> {
            if (type.size == 0) "Created empty file" else "Sent ${type.size} bytes"
        }
        is TransmissionType.Delete -> {
            "Deleted file"
        }
        is TransmissionType.Move -> {
            "Moved file"
        }
        is TransmissionType.ListDirectory -> {
            if (type.numItems != null) {
                "Listed directory: ${type.numItems} items"
            } else {
                "Listed nonexistent directory"
            }
        }
        is TransmissionType.MakeDirectory -> {
            "Created directory"
        }
        is TransmissionType.Error -> {
            type.message
        }
    }
}