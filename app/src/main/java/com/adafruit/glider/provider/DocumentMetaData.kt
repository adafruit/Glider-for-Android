package com.adafruit.glider.provider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.database.MatrixCursor
import android.provider.DocumentsContract

data class DocumentMetaData(
    val id: String,
    val mimeType: String,
    val displayName: String,
    val lastModified: Long,
    val flags: Int,
    val size: Long
) {
    fun addToRow(row: MatrixCursor.RowBuilder) {
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, id)
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, 0)
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        row.add(DocumentsContract.Document.COLUMN_SIZE, size)
    }
}