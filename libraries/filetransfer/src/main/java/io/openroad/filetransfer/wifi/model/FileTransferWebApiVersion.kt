package io.openroad.filetransfer.wifi.model

import java.util.Date

data class FileTransferWebApiVersion(
    val apiVersion: Int,
    val version: String,
    val buildDate: Date?,
    val boardName: String,
    val mcuName: String,
    val boardId: String,
    val creatorId: Int,
    val creationId: Int,
    val hostName: String,
    val port: Int,
    val ip: String,
)
