package io.openroad.filetransfer.ble.utils

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level

object LogManager {
    // Config
    private const val maxEntries = 10000
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)

    // Structs
    data class Entry(
        val category: Category,
        val level: Level,
        val text: String,
        val millis: Long = System.currentTimeMillis()
    ) {

        enum class Category {
            Unknown,
            App,
            Bluetooth,
            FileTransferProtocol,
        }


        fun timeString(): String = formatter.format(Date(millis))

        /*
        companion object {
            fun debug(text: String): Entry {
                return Entry(text)
            }
        }*/
    }

    // Data - Private
    //private val log by LogUtils()
    private var _entries =
        MutableStateFlow<MutableList<Entry>>(mutableListOf())

    // Data - Public
    val entries = _entries.asStateFlow()

    // region Actions

    fun log(entry: Entry) {
        //log.info("log add: ${entry.text}")
        _entries.value.add(entry)

        // Limit entries count
        limitSizeIfNeeded()
    }

    fun clear() {
        _entries.update { mutableListOf() }
    }

    // endregion

    // region Utils
    private fun limitSizeIfNeeded() {
        val currentSize = _entries.value.size
        if (currentSize > maxEntries) {
            _entries.value.drop(currentSize - maxEntries)
        }
    }
    // endregion
}