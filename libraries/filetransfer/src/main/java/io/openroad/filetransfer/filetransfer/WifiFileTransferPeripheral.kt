package io.openroad.filetransfer.filetransfer

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.util.Base64
import com.adafruit.glider.utils.LogUtils
import io.openroad.filetransfer.PeripheralConnectCompletionHandler
import io.openroad.filetransfer.ble.utils.BleException
import io.openroad.filetransfer.ble.utils.LogHandler
import io.openroad.filetransfer.wifi.model.FileTransferWebApiVersion
import io.openroad.wifi.network.FileTransferNetworkServiceInterface
import io.openroad.filetransfer.wifi.network.FileTransferWebDirectoryJsonDeserializer
import io.openroad.filetransfer.wifi.peripheral.WifiPeripheral
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.util.*

class WifiFileTransferPeripheral(
    override val peripheral: WifiPeripheral,
    private val onGetPasswordForHostName: ((name: String, hostName: String) -> String?)?,
) : FileTransferPeripheral {

    // Data - Private
    companion object {
        private val log by LogUtils()
        const val defaultPassword = "passw0rd"

        init {
            log.addHandler(LogHandler())
        }
    }

    private val networkInterface =
        FileTransferNetworkServiceInterface(baseUrl = peripheral.baseUrl())

    // Params
    var password = defaultPassword

    // region Actions

    override fun connectAndSetup(
        externalScope: CoroutineScope,
        connectionTimeout: Int?,
        completion: PeripheralConnectCompletionHandler
    ) {
        // TODO: apply connectionTimeout

        getVersion(externalScope = externalScope) { version ->
            if (version != null) {
                val savedPassword =
                    onGetPasswordForHostName?.invoke(version.boardName, version.hostName)
                if (savedPassword != null) {
                    password = savedPassword
                    log.info("hostName: ${version.hostName} savedPassword: '$savedPassword'")
                } else {
                    log.info("hostName: ${version.hostName} using default password: '$password'")
                }
                completion(Result.success(Unit))
            } else {
                log.warning("connectAndSetup: Error retrieving /version.json")
                completion(Result.failure(BleException("Error retrieving /version.json")))
            }
        }
    }

    fun getVersion(
        externalScope: CoroutineScope,
        completion: (FileTransferWebApiVersion?) -> Unit,
    ) {
        // Retrieve hostname from /version.json to check if we already have a saved password
        externalScope.launch {
            runCatching {
                networkInterface.networkService.getVersion()
            }.onSuccess { version ->
                completion(version)
            }.onFailure {
                log.warning("getVersion: Error retrieving /version.json")
                completion(null)
            }
        }
    }

    override fun listDirectory(
        externalScope: CoroutineScope,
        path: String,
        completion: ((Result<List<DirectoryEntry>?>) -> Unit)?
    ) {
        log.info("List directory $path")

        externalScope.launch {
            runCatching {
                networkInterface.networkService.listDirectory(
                    authorization = getAuthHeader(),
                    path = path
                )
            }.onSuccess { jsonArray ->
                // Deserialized here because cant make it work with addConverterFactory
                val entries =
                    FileTransferWebDirectoryJsonDeserializer().deserialize(jsonArray, null, null)
                completion?.let { it(Result.success(entries)) }
            }.onFailure { exception ->
                completion?.let { it(Result.failure<List<DirectoryEntry>>(exception)) }
            }
        }
    }

    override fun makeDirectory(
        externalScope: CoroutineScope,
        path: String,
        completion: ((Result<Date?>) -> Unit)?
    ) {
        log.info("Make directory $path")

        externalScope.launch {
            runCatching {
                // Make sure it ends with '/'
                var directoryPath = path
                if (path.last() != '/') {
                    directoryPath += '/'
                }

                networkInterface.networkService.makeDirectory(
                    authorization = getAuthHeader(),
                    path = directoryPath
                )
            }.onSuccess { response ->
                val statusCode = response.code()
                if (statusCode == 204) {      // Already exists
                    completion?.let { it(Result.failure(Exception("Error $statusCode - Already exists"))) }
                } else if (statusCode >= 400) {
                    completion?.let { it(Result.failure(Exception(getErrorMessage(response)))) }
                } else {
                    completion?.let { it(Result.success(null)) }        // No date available on HTTP
                }
            }.onFailure { exception ->
                completion?.let { it(Result.failure(exception)) }
            }
        }
    }

    override fun readFile(
        externalScope: CoroutineScope,
        path: String,
        progress: FileTransferProgressHandler?,
        completion: ((Result<ByteArray>) -> Unit)?
    ) {
        log.info("Read file $path")

        externalScope.launch {
            runCatching {
                networkInterface.networkService.readFile(
                    authorization = getAuthHeader(),
                    path = path
                )
            }.onSuccess { responseBody ->
                val bytes = responseBody.bytes()
                completion?.let { it(Result.success(bytes)) }
            }.onFailure { exception ->
                completion?.let { it(Result.failure(exception)) }
            }
        }
    }

    override fun writeFile(
        externalScope: CoroutineScope,
        path: String,
        data: ByteArray,
        progress: FileTransferProgressHandler?,
        completion: ((Result<Date?>) -> Unit)?
    ) {
        log.info("Write file $path")

        //val urlEncodedPath = encodePath(path = path)

        externalScope.launch {
            runCatching {
                val contentType = "application/octet-stream".toMediaTypeOrNull()
                val body = data.toRequestBody(contentType)

                networkInterface.networkService.makeFile(
                    authorization = getAuthHeader(),
                    path = path,
                    body = body
                )
            }.onSuccess { response ->
                val statusCode = response.code()
                if (statusCode >= 400) {
                    completion?.let { it(Result.failure(Exception(getErrorMessage(response)))) }
                } else {
                    completion?.let { it(Result.success(null)) }        // No date available on HTTP
                }
            }.onFailure { exception ->
                completion?.let { it(Result.failure(exception)) }
            }
        }
    }

    override fun deleteFile(
        externalScope: CoroutineScope,
        path: String,
        completion: ((Result<Unit>) -> Unit)?
    ) {
        log.info("Delete $path")

        externalScope.launch {
            runCatching {
                networkInterface.networkService.delete(
                    authorization = getAuthHeader(),
                    path = path
                )
            }.onSuccess { response ->
                val statusCode = response.code()
                if (statusCode >= 400) {
                    completion?.let { it(Result.failure(Exception(getErrorMessage(response)))) }
                } else {
                    completion?.let { it(Result.success(Unit)) }        // No date available on HTTP
                }
            }.onFailure { exception ->
                completion?.let { it(Result.failure(exception)) }
            }
        }
    }

    override fun moveFile(
        externalScope: CoroutineScope,
        fromPath: String,
        toPath: String,
        completion: ((Result<Unit>) -> Unit)?
    ) {
        // TODO (REST API don't have a move command yet)
        completion?.let { it(Result.failure(Exception("Move command not implemented"))) }
    }
    // endregion


    // region Utils
    private fun getAuthHeader(): String {
        val userId = ""
        val authPayload = "$userId:$password"
        val data = authPayload.toByteArray()
        val base64 = Base64.encodeToString(data, Base64.NO_WRAP)
        val basicAuth = base64.trim()
        return "Basic $basicAuth"
    }

    private fun getErrorMessage(response: Response<ResponseBody>): String {
        val statusCode = response.code()
        var exceptionText = "Error $statusCode"
        val errorMessage =
            response.errorBody()?.string()       // Webservice sends error message in plain text
        errorMessage?.let {
            exceptionText += " - $it"
        }

        return exceptionText
    }

    /*
    private fun encodePath(path: String): String {
        return path.split('/').map {
            if (it != "/") {
                URLEncoder.encode(it, "UTF-8")
            } else {
                it
            }
        }.joinToString("/")
    }*/
    // endregion
}