package com.adafruit.glider.provider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentResolver.NOTIFY_UPDATE
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresPermission
import com.adafruit.glider.AppContainer
import com.adafruit.glider.AppContainerImpl
import com.adafruit.glider.BuildConfig
import com.adafruit.glider.R
import com.adafruit.glider.provider.ProviderConfig.DEFAULT_DOCUMENT_PROJECTION
import com.adafruit.glider.provider.ProviderConfig.DEFAULT_ROOT_PROJECTION
import com.adafruit.glider.provider.ProviderConfig.ROOT_FOLDER_ID
import com.adafruit.glider.provider.ProviderConfig.ROOT_ID
import io.openroad.filetransfer.ble.utils.LogUtils
import io.openroad.filetransfer.filetransfer.DirectoryEntry
import io.openroad.filetransfer.utils.upPath
import kotlinx.coroutines.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.coroutines.suspendCoroutine

class GliderDocumentsProvider : DocumentsProvider() {
    // Data - Private
    private val log by LogUtils()
    private lateinit var container: AppContainer
    private val mainScope = MainScope()

    private var discoveredPeripherals = DiscoveredPeripherals()
    private var discoverPeripheralsJob: Job? = null

    private val cache = DocumentCache()

    // region DocumentsProvider
    override fun onCreate(): Boolean {
        log.info("PROVIDER: onCreate")
        var success = false
        context?.let {
            container = AppContainerImpl(it)
            success = true
        }
        return success
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        log.info("PROVIDER: queryChildDocuments parentDocumentId: $parentDocumentId, projection: $projection, sortOrder: $sortOrder")

        val cursor: MatrixCursor = if (parentDocumentId == ROOT_FOLDER_ID) {
            queryRootDocuments(parentDocumentId, projection, sortOrder)
        } else {
            try {
                queryPeripheralDocuments(parentDocumentId, projection, sortOrder)
            } catch (e: SecurityException) {
                createErrorCursor(projection, "Bluetooth permissions needed")
            }
        }

        //log.info("queryChildDocuments cursor: ${if (cursor.isLoading()) "loading" else cursor.count}")
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        log.info("PROVIDER: queryDocument documentId: $documentId, projection: $projection")

        val cursor = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        /**
         * When the root folder is opened, we return a cursor containing the root. Mention that the mime type of the root is a
         * directory because there are typical more files and folders below the root folder. This indicates to SAF the for this
         * document, the queryChildDocuments should be queried.
         */
        if (documentId == ROOT_FOLDER_ID) {
            log.info("queryDocument root -> return folder")

            val row = cursor.newRow()
            with(row) {
                add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, ROOT_FOLDER_ID)
                add(
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.MIME_TYPE_DIR
                )
                add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "Glider Root")
                add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
                add(DocumentsContract.Document.COLUMN_FLAGS, 0)
                add(DocumentsContract.Document.COLUMN_SIZE, null)
            }

        } else {
            // non-root document is queried. Provide the correct display name and mime type (for openDocument).
            val parentDocumentId = upPath(documentId)
            log.info("\tparentDocumentId: $parentDocumentId")

            val notifyUri = DocumentsContract.buildChildDocumentsUri(
                BuildConfig.DOCUMENTS_AUTHORITY,
                parentDocumentId
            )
            val documents = cache.get(notifyUri)
            if (documents != null) {
                log.info("\tdocuments: ${documents.size}")
                val document = documents.firstOrNull {
                    it.id == documentId
                }

                if (document != null) {
                    log.info("document: ${document.id}, ${document.displayName}")
                }
            } else {
                log.info("\tdocument not in cache")
            }


            // Force update date to reload document and not use cached version
            val row = cursor.newRow()
            with(row) {
                add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, documentId)
                add(
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    "application/octet-stream"
                )
                add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, Date().time)
                add(DocumentsContract.Document.COLUMN_FLAGS, 0)
                add(DocumentsContract.Document.COLUMN_SIZE, null)
            }

            /*
            log.info(
                "\t${it.id}, ${it.displayName}, ${it.mimeType}, size: ${it.size}, date: ${
                    SimpleDateFormat(
                        "dd/MM/yyyy", Locale.ENGLISH
                    ).format(Date(it.lastModified))
                }"
            )
            it.addToRow(cursor.newRow())*/
        }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        log.info("PROVIDER: openDocument documentId: $documentId, mode: $mode, signal: $signal")
        val context = context!!

        val address = getDocumentAddress(documentId)
        val path = getDocumentPath(documentId)
        val isWrite: Boolean = mode.contains("w")
        log.info("\taddress: $address, path: $path, isWrite: $isWrite")

        var readByteArray: ByteArray? = null

        // Download file for reading operations
        if (!isWrite) {
            readByteArray = runBlocking {
                try {
                    downloadFile(address, path)
                } catch (e: SecurityException) {
                    log.warning("Error downloading file: $e")
                    throw e
                }
            }
            log.info("\tread bytes: ${readByteArray.size}")

            // Check if cancelled
            if (signal?.isCanceled == true) {
                throw CancellationException("\topenDocument cancelled")
            }
        }

        val file = File(context.cacheDir, documentId)
        file.parentFile?.mkdirs()
        file.deleteRecursively()       // Delete in case a directory with the same name exists

        // Byte array is not-null if we are reading the file
        readByteArray?.let {
            file.writeBytes(it)
            log.info("\tcached file to be read is ready: ${readByteArray.size} bytes")
        } ?: run {
            // Create if not exists
            file.createNewFile()
            // log.info("\tcached file to be written created at: ${file.absolutePath}")
        }

        // Check if cancelled
        if (signal?.isCanceled == true) {
            throw CancellationException("\topenDocument cancelled")
        }

        val accessMode: Int = ParcelFileDescriptor.parseMode(mode)
        return if (isWrite) {
            val handler = Handler(context.mainLooper)
            // Attach a close listener if the document is opened in write mode.
            try {
                ParcelFileDescriptor.open(file, accessMode, handler) {
                    // Update the file with the cloud server. The client is done writing.
                    log.info("\tA file with id $documentId has been closed")
                    val writeByteArray = file.readBytes()
                    log.info("\tSend to peripheral ${writeByteArray.size} bytes")

                    val exceptionHandler = CoroutineExceptionHandler { _, exception ->
                        log.warning("uploadFile coroutineExceptionHandler got $exception")
                    }
                    mainScope.launch(exceptionHandler) {
                        try {
                            uploadFile(address, path, writeByteArray)
                        } catch (e: SecurityException) {
                            log.warning("Error uploading file: $e")
                            throw e
                        }
                    }

                    log.info("\tuploaded bytes: ${writeByteArray.size}")

                }
            } catch (e: IOException) {
                throw FileNotFoundException("Failed to open document with id $documentId and mode $mode")
            }
        } else {
            ParcelFileDescriptor.open(file, accessMode)
        }
    }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private suspend fun uploadFile(address: String, path: String, data: ByteArray): Date? =
        suspendCoroutine { continuation ->

            val gliderClient = GliderClient.getInstance(address)
            gliderClient.writeFile(
                path = path,
                data = data,
                connectionManager = container.connectionManager,
                bondedBlePeripherals = container.bondedBlePeripherals,
                progress = { transmittedBytes, totalBytes ->
                    log.info("openDocument uploading $transmittedBytes / $totalBytes")
                },
                completion = { result ->
                    continuation.resumeWith(result)
                }
            )
        }

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private suspend fun downloadFile(address: String, path: String): ByteArray =
        suspendCoroutine { continuation ->

            val gliderClient = GliderClient.getInstance(address)
            gliderClient.readFile(
                path = path,
                connectionManager = container.connectionManager,
                bondedBlePeripherals = container.bondedBlePeripherals,
                progress = { transmittedBytes, totalBytes ->
                    log.info("openDocument downloading $transmittedBytes / $totalBytes")
                },
                completion = { result ->
                    continuation.resumeWith(result)
                }
            )
        }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        log.info("PROVIDER: queryRoots projection: $projection")

        val cursor = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        cursor.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)

            // You can provide an optional summary, which helps distinguish roots with the same title. You can also use this field for displaying an user account name.
            //add(DocumentsContract.Root.COLUMN_SUMMARY, "Discovered Peripherals")

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
    // endregion

    // region Utils
    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun queryPeripheralDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): MatrixCursor {
        if (parentDocumentId == null) {
            log.severe("queryPeripheralDocuments with null parentDocumentId")
            return MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        }

        val notifyUri = DocumentsContract.buildChildDocumentsUri(
            BuildConfig.DOCUMENTS_AUTHORITY,
            parentDocumentId
        )
        val documents = cache.get(notifyUri)

        if (documents == null) {
            // Display error if needed
            listFromPeripheralError?.let {
                // Expire error after some time
                if (Date().time - it.updatedTime.time > 5000) {
                    listFromPeripheralError = null
                }
                // Show error if the saved error is for the current directory
                else if (parentDocumentId == it.parentDocumentId) {
                    return createErrorCursor(projection, "Error: ${it.exception.message}")
                }
            }

            // Show loading and List directory
            val name = getDocumentName(parentDocumentId)
            return createLoadingCursor(projection, "Listing $name...").apply {
                setNotificationUri(context?.contentResolver, notifyUri)
            }.also {
                listFromPeripheral(parentDocumentId, notifyUri)
            }
        } else {
            // Documents are found in cache, return the documents by adding them to the cursor.
            log.info("List directory is cached")

            return MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION).also { cursor ->
                documents.forEach {

                    /*
                    log.info(
                        "\t${it.id}, ${it.displayName}, ${it.mimeType}, size: ${it.size}, date: ${
                            SimpleDateFormat(
                                "dd/MM/yyyy", Locale.ENGLISH
                            ).format(Date(it.lastModified))
                        }"
                    )*/
                    it.addToRow(cursor.newRow())
                }
            }
        }
    }

    private fun queryRootDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): MatrixCursor {

        val notifyUri = DocumentsContract.buildChildDocumentsUri(
            BuildConfig.DOCUMENTS_AUTHORITY,
            parentDocumentId
        )
        val loadingCursor = createLoadingCursor(projection, "Discovering peripherals...").apply {
            setNotificationUri(context?.contentResolver, notifyUri)
        }

        if (discoverPeripheralsJob != null) {
            log.info("queryRootDocuments. Skip, already running")
            return loadingCursor
        }

        // Check if it a recent scan is available or scan again
        val isScanningStale =
            System.currentTimeMillis() - discoveredPeripherals.lastUpdateMillis > 15000
        if (isScanningStale || discoveredPeripherals.isEmpty) {

            log.info("Root folder -> scan peripherals")
            return loadingCursor.also {
                discoverPeripherals(notifyUri)
            }
        } else {
            val documents = mutableListOf<DocumentMetaData>()

            // Add discovered peripherals
            discoveredPeripherals.peripherals.forEach { peripheral ->
                documents.add(
                    DocumentMetaData(
                        id = peripheral.address,
                        mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                        displayName = peripheral.nameOrAddress,
                        lastModified = discoveredPeripherals.lastUpdateMillis,
                        flags = 0,
                        size = 0,
                    )
                )
            }

            // Add bonded that but only if they are have not been already added because they were discovered
            val bondedNotAdvertisingPeripherals =
                getBondedPeripheralsNotDiscovered(discoveredPeripherals)

            bondedNotAdvertisingPeripherals.forEach { data ->
                documents.add(
                    DocumentMetaData(
                        id = data.address,
                        mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                        displayName = data.name ?: data.address,
                        lastModified = discoveredPeripherals.lastUpdateMillis,
                        flags = 0,
                        size = 0,
                    )
                )
            }

            log.info("Root folder -> show discovered peripherals: ${documents.size}")

            return MatrixCursor(
                projection ?: DEFAULT_DOCUMENT_PROJECTION
            ).also { aCursor ->
                documents.forEach {
                    it.addToRow(aCursor.newRow())
                }
            }
        }
    }

    private
    val listFromPeripheralJobs: ConcurrentMap<String, Job> = ConcurrentHashMap()

    private data class ListFromPeripheralError(
        val parentDocumentId: String,
        val exception: GliderClientException,
        val updatedTime: Date = Date()
    )

    private var listFromPeripheralError: ListFromPeripheralError? =
        null        // parentDocumentId and exception found. Used to show an error to the user

    @SuppressLint("InlinedApi")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    private fun listFromPeripheral(
        parentDocumentId: String,
        notifyUri: Uri
    ) {
        if (listFromPeripheralJobs[parentDocumentId] != null) {
            log.info("listFromPeripheral. Skip, already running")
            return
        }

        listFromPeripheralJobs[parentDocumentId] = mainScope.launch {
            log.info("listFromPeripheral: $parentDocumentId, notifyUri: $notifyUri")
            val documents = mutableListOf<DocumentMetaData>()

            val address = getDocumentAddress(parentDocumentId)
            var folderPath = getDocumentPath(parentDocumentId)
            if (!folderPath.endsWith("/")) folderPath += "/"
            log.info("\taddress: $address, folderPath: $folderPath")

            val gliderClient = GliderClient.getInstance(address)
            gliderClient.listDirectory(
                path = folderPath,
                connectionManager = container.connectionManager,
                bondedBlePeripherals = container.bondedBlePeripherals,
            ) { result ->

                result.fold(
                    onSuccess = { entries ->
                        entries?.let {
                            log.info("list entries: ${entries.size}")
                            val currentTime = System.currentTimeMillis()

                            entries.forEach { entry ->
                                val route = gliderRouteFromDocument(
                                    parentDocumentId,
                                    entry.name
                                )
                                val mimeType = getDirectoryEntryMimeType(entry)
                                val size =
                                    if (entry.isDirectory) 0 else (entry.type as DirectoryEntry.EntryType.File).size.toLong()

                                /*
                                log.info(
                                    "\t$route ${entry.name}, $mimeType, size: $size, date: ${
                                        entry.modificationDate?.let {
                                            SimpleDateFormat(
                                                "dd/MM/yyyy", Locale.ENGLISH
                                            ).format(it)
                                        }
                                    }."
                                )*/

                                documents.add(
                                    DocumentMetaData(
                                        id = route,
                                        mimeType = mimeType,
                                        displayName = entry.name,
                                        lastModified = entry.modificationDate?.time
                                            ?: currentTime,
                                        flags = 0,
                                        size = size,
                                    )
                                )
                            }

                            cache.add(notifyUri, documents)
                            notifyChange(notifyUri)

                        } ?: run {
                            log.info("listDirectory: nonexistent directory")
                            listFromPeripheralError = ListFromPeripheralError(
                                parentDocumentId,
                                GliderClientException("Non-existent directory", null)
                            )
                            notifyChange(notifyUri)
                        }
                    },
                    onFailure = { exception ->
                        log.warning("listDirectory $parentDocumentId error $exception")
                        listFromPeripheralError = ListFromPeripheralError(
                            parentDocumentId,
                            GliderClientException(exception.message, null)
                        )
                        notifyChange(notifyUri)
                    }
                )

                listFromPeripheralJobs.remove(parentDocumentId)
            }
        }
    }

    private fun getDirectoryEntryMimeType(entry: DirectoryEntry): String {
        return if (entry.isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            val extension = entry.name.substringAfterLast('.', "")
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                ?: "application/octet-stream"
        }
    }

    private fun discoverPeripherals(notifyUri: Uri) {
        if (discoverPeripheralsJob != null) {
            log.info("discoverPeripherals. Skip, already running")
            //context?.contentResolver?.notifyChange(notifyUri, null, false)
            return
        }

        log.info("discoverPeripherals")
        //discoverPeripheralsJob?.cancel()
        discoverPeripheralsJob = mainScope.launch {
            log.info("discoverPeripherals launch")
            startScan()
            delay(2000)
            log.info("discoverPeripherals delay finished")
            stopScan()

            // Skip if it is no longer active
            if (isActive) {
                // Update discovered peripherals
                discoveredPeripherals = createDiscoveredPeripherals(
                    container.connectionManager,
                    container.bondedBlePeripherals
                )

                log.info(
                    "peripherals discovered: ${
                        getBondedPeripheralsNotDiscovered(
                            discoveredPeripherals
                        ).size
                    }"
                )
                log.info("discoverPeripherals finished, notifyChange $notifyUri")
                notifyChange(notifyUri)
            } else {
                log.info("discoverPeripherals has been cancelled")
            }

            log.info("discoverPeripherals end")
            discoverPeripheralsJob = null
        }
    }

    private fun notifyChange(notifyUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context?.contentResolver?.notifyChange(
                notifyUri,
                null,
                NOTIFY_UPDATE
            )
        } else {
            context?.contentResolver?.notifyChange(notifyUri, null, false)
        }
    }

    private fun startScan() {
        //stopScan()
        container.bondedBlePeripherals.refresh()
        container.connectionManager.startScan()
    }

    private fun stopScan() {
        container.connectionManager.stopScan()
    }
    // endregion
}