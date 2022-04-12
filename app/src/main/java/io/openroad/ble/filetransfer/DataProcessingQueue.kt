package io.openroad.ble.filetransfer

import io.openroad.utils.LogUtils
import java.io.ByteArrayOutputStream
import java.lang.Integer.max
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore

/**
 * Created by Antonio García (antonio@openroad.es)
 */
class DataProcessingQueue {
    private var data = ByteArrayOutputStream()//ByteBuffer.allocate(1024)//mutableListOf<Byte>()
    private var dataSemaphore = Semaphore(1)
    private val log by LogUtils()

    fun processQueue(receivedData: ByteArray, processingHandler: (ByteArray) -> Int) {
        // Don't append more data until the delegate has finished processing it
        dataSemaphore.acquire()

        // Append received data
        data.write(receivedData)

        // Process chunks
        processQueuedChunks(processingHandler)

        // Ready to receive more data
        dataSemaphore.release()
    }

    fun reset() {
        data.reset()
        dataSemaphore.release()  // Force signal if it was waiting
    }

    // Important: this method changes "data", so it should be used only when the semaphore is blocking concurrent access
    private fun processQueuedChunks(processingHandler: ((ByteArray) -> Int)) {
        // Process chunk
        val byteArray = data.toByteArray()
        val processedDataCount = processingHandler.invoke(byteArray)

        if (processedDataCount > 0) {
            data.reset()
            val remainingDataSize = max(0, byteArray.size - processedDataCount)
            if (remainingDataSize > 0) {
                data.write(byteArray, processedDataCount, remainingDataSize)
            }
        } else {
            log.info("Queue size: ${byteArray.size} bytes. Waiting for more data to process packet...")
        }
    }
}