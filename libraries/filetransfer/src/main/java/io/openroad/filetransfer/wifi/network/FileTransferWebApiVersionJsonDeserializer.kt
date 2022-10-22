package io.openroad.filetransfer.wifi.network

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.openroad.filetransfer.wifi.model.FileTransferWebApiVersion
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util.*

class FileTransferWebApiVersionJsonDeserializer : JsonDeserializer<FileTransferWebApiVersion> {
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): FileTransferWebApiVersion {

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

        json as JsonObject
        val apiVersion = json["web_api_version"].asInt
        val version = json["version"].asString
        val buildDate = dateFormatter.parse(json["build_date"].asString)
        val boardName = json["board_name"].asString
        val mcuName = json["mcu_name"].asString
        val boardId = json["board_id"].asString
        val creatorId = json["creator_id"].asInt
        val creationId = json["creation_id"].asInt
        val hostName = json["hostname"].asString
        val port = json["port"].asInt
        val ip = json["ip"].asString

        return FileTransferWebApiVersion(
            apiVersion = apiVersion,
            version = version,
            buildDate = buildDate,
            boardName = boardName,
            mcuName = mcuName,
            boardId = boardId,
            creatorId = creatorId,
            creationId = creationId,
            hostName = hostName,
            port = port,
            ip = ip,
        )
    }
}

/* Example:
{
    "web_api_version": 1,
    "version": "8.0.0-alpha.1-44-gd8447de0b",
    "build_date": "2022-07-12",
    "board_name": "Adafruit Feather ESP32-S2 TFT",
    "mcu_name": "ESP32S2",
    "board_id": "adafruit_feather_esp32s2_tft",
    "creator_id": 9114,
    "creation_id": 33040,
    "hostname": "cpy-f5a5d4",
    "port": 80,
    "ip": "192.168.5.172"
}
 */