package com.adafruit.glider.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import com.adafruit.glider.AppContainer
import com.adafruit.glider.AppContainerImpl
import com.adafruit.glider.R
import com.adafruit.glider.provider.ProviderConfig.DEFAULT_DOCUMENT_PROJECTION
import com.adafruit.glider.provider.ProviderConfig.DEFAULT_ROOT_PROJECTION
import com.adafruit.glider.provider.ProviderConfig.ROOT_FOLDER_ID
import com.adafruit.glider.provider.ProviderConfig.ROOT_ID
import com.adafruit.glider.utils.LogUtils

class GliderDocumentsProvider : DocumentsProvider() {
    // Data - Private
    private val log by LogUtils()
    private lateinit var container: AppContainer

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

        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        cursor.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)

            // You can provide an optional summary, which helps distinguish roots with the same title. You can also use this field for displaying an user account name.
            add(DocumentsContract.Root.COLUMN_SUMMARY, "File Glider")

            // FLAG_SUPPORTS_CREATE means at least one directory under the root supports creating documents. FLAG_SUPPORTS_RECENTS means your application's most recently used documents will show up in the "Recents" category. FLAG_SUPPORTS_SEARCH allows users to search all documents the application shares.
            add(
                DocumentsContract.Root.COLUMN_FLAGS,
                0/*DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                        DocumentsContract.Root.FLAG_SUPPORTS_RECENTS or
                        DocumentsContract.Root.FLAG_SUPPORTS_SEARCH*/
            )

            // COLUMN_TITLE is the root title (e.g. Gallery, Drive).
            add(DocumentsContract.Root.COLUMN_TITLE, "Glider")

            // This document id cannot change after it's shared.
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_FOLDER_ID)

            // The child MIME types are used to filter the roots and only present to the user those roots that contain the desired type somewhere in their file hierarchy.
            //add(DocumentsContract.Root.COLUMN_MIME_TYPES, getChildMimeTypes(baseDir))
            //add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, baseDir.freeSpace)
            add(DocumentsContract.Root.COLUMN_ICON, R.drawable.glider_logo)
        }

        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        log.info("queryChildDocuments parentDocumentId: $parentDocumentId, projection: $projection, sortOrder: $sortOrder")

        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        if (parentDocumentId == ROOT_FOLDER_ID) {

        }
        else {

        }

        return cursor
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        log.info("queryDocument documentId: $documentId, projection: $projection")
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