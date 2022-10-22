package io.openroad.filetransfer.ble.utils

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import java.util.logging.LogRecord
import java.util.logging.StreamHandler

class LogHandler : StreamHandler() {

    override fun publish(record: LogRecord?) {
        super.publish(record)

        record?.let {
            val entry = LogManager.Entry(
                LogManager.Entry.Category.FileTransferProtocol,
                it.level,
                it.message,
                it.millis
            )
            LogManager.log(entry)
        }
    }
}