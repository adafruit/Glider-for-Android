package io.openroad.filetransfer.wifi.network

import com.google.gson.*
import io.openroad.filetransfer.filetransfer.DirectoryEntry
import java.lang.reflect.Type
import java.sql.Timestamp
import java.util.*

class FileTransferWebDirectoryJsonDeserializer : JsonDeserializer<List<DirectoryEntry>> {

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): List<DirectoryEntry> {

        json as JsonArray
        val entries = mutableListOf<DirectoryEntry>()
        for (entryJson in json) {
            entryJson as JsonObject
            val name = entryJson["name"].asString
            val isDirectory = entryJson["directory"].asBoolean
            val fileSize = entryJson["file_size"].asInt
            val timestamp = entryJson["modified_ns"].asLong

            val entry = DirectoryEntry(
                name = name,
                type = if (isDirectory) DirectoryEntry.EntryType.Directory else DirectoryEntry.EntryType.File(
                    fileSize
                ),
                modificationDate = Date(Timestamp(timestamp).time)
            )

            entries.add(entry)
        }

        return entries.toList()
    }
}

/* Example:
[
    {
        "name": ".fseventsd",
        "directory": true,
        "modified_ns": 1480810702000000000,
        "file_size": 0
    },
    {
        "name": "code.py",
        "directory": false,
        "modified_ns": 1480810702000000000,
        "file_size": 22
    },

]
 */