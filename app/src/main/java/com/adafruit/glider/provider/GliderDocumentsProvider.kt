package com.adafruit.glider.provider

import android.database.Cursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import com.adafruit.glider.AppContainer
import com.adafruit.glider.AppContainerImpl
import com.adafruit.glider.utils.LogUtils

class GliderDocumentsProvider : DocumentsProvider() {
    // Data - Private
    private val log by LogUtils()
    private lateinit var container: AppContainer

    private val DEFAULT_ROOT_PROJECTION: Array<String> = arrayOf(
        DocumentsContract.Root.COLUMN_ROOT_ID,
        DocumentsContract.Root.COLUMN_MIME_TYPES,
        DocumentsContract.Root.COLUMN_FLAGS,
        DocumentsContract.Root.COLUMN_ICON,
        DocumentsContract.Root.COLUMN_TITLE,
        DocumentsContract.Root.COLUMN_SUMMARY,
        DocumentsContract.Root.COLUMN_DOCUMENT_ID,
        DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
    )
    private val DEFAULT_DOCUMENT_PROJECTION: Array<String> = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_SIZE
    )

    override fun onCreate(): Boolean {
        log.info("onCreate")
        var success = false
        context?.let {
            container = AppContainerImpl(it)
            success = true
        }
        return success
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        log.info("queryRoots projection: $projection")
        TODO("Not yet implemented")
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        log.info("queryDocument documentId: $documentId, projection: $projection")
        TODO("Not yet implemented")
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        log.info("queryChildDocuments parentDocumentId: $parentDocumentId, projection: $projection, sortOrder: $sortOrder")
        TODO("Not yet implemented")
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        log.info("openDocument documentId: $documentId, mode: $mode, signal: $signal")
        TODO("Not yet implemented")
    }
}