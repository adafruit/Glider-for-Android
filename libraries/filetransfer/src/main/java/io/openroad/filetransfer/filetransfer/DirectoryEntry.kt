package io.openroad.filetransfer.filetransfer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.compose.runtime.Immutable
import java.util.*

@Immutable
data class DirectoryEntry(
    val name: String,
    val type: EntryType,
    val modificationDate: Date? = null
) {
    sealed class EntryType {
        data class File(val size: Int) : EntryType()
        object Directory : EntryType()
    }

    val isDirectory = type == EntryType.Directory
    val isHidden = name.startsWith(".")
}
