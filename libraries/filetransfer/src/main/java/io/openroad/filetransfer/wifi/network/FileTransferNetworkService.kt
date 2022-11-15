package io.openroad.wifi.network

import android.util.Log
import com.adafruit.glider.utils.LogUtils
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import io.openroad.filetransfer.BuildConfig
import io.openroad.filetransfer.ble.utils.LogHandler
import io.openroad.filetransfer.wifi.model.FileTransferWebApiVersion
import io.openroad.filetransfer.wifi.network.FileTransferWebApiVersionJsonDeserializer
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Converter
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import retrofit2.http.Headers
import java.lang.reflect.Type
import java.util.logging.Level

interface FileTransferNetworkService {
    @GET("/cp/version.json")
    @Headers("Accept: application/json")
    suspend fun getVersion(): FileTransferWebApiVersion

    @GET("/fs{path}")
    @Headers("Accept: application/json")
    suspend fun listDirectory(
        @Header("Authorization") authorization: String,
        @Path("path", encoded = true) path: String     // encoded true to maintain slashes
    ): JsonArray//MutableList<DirectoryEntry>

    @PUT("/fs{path}")
    suspend fun makeDirectory(
        @Header("Authorization") authorization: String,
        @Path("path", encoded = true) path: String     // encoded true to maintain slashes
    ): Response<ResponseBody>

    @GET("/fs{path}")
    suspend fun readFile(
        @Header("Authorization") authorization: String,
        @Path("path", encoded = true) path: String     // encoded true to maintain slashes
    ): ResponseBody

    @PUT("/fs{path}")
    suspend fun makeFile(
        @Header("Authorization") authorization: String,
        @Path("path", encoded = true) path: String,     // encoded true to maintain slashes
        @Body body: RequestBody
    ): Response<ResponseBody>

    @DELETE("/fs{path}")
    suspend fun delete(
        @Header("Authorization") authorization: String,
        @Path("path", encoded = true) path: String,     // encoded true to maintain slashes
    ): Response<ResponseBody>
}

class FileTransferNetworkServiceInterface(
    baseUrl: String,
) {
    // Data - Private
    companion object {
        private val log by LogUtils()

        init {
            log.level = Level.FINE
            log.addHandler(LogHandler())
        }
    }

    var networkService: FileTransferNetworkService

    init {
        val versionConverterFactory =
            createGsonConverter(
                FileTransferWebApiVersion::class.java,
                FileTransferWebApiVersionJsonDeserializer()
            )!!

        /*
        val directoryEntryListType = object : TypeToken<MutableList<DirectoryEntry>>() {}.type
        val listDirectoryConverterFactory =
            createGsonConverter(
                directoryEntryListType,//FileTransferWebDirectory::class.java,
                FileTransferWebDirectoryJsonDeserializer()
            )!!
        */

        // Custom log manager for HttpLoggingInterceptor to capture the results and show them in LogManager
        class LogManagerLogger: HttpLoggingInterceptor.Logger {
            override fun log(message: String) {
                log.fine(message)
            }
        }

        val loggingInterceptor = HttpLoggingInterceptor(LogManagerLogger()).apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }

        if (!BuildConfig.DEBUG) {
            loggingInterceptor.redactHeader("Authorization")
        }

        val okHttpClient = OkHttpClient.Builder()
            //.addInterceptor(HeaderInterceptor())
            .addInterceptor(loggingInterceptor)
            .build()

        networkService = Retrofit.Builder()
            .baseUrl(baseUrl)
            //.addConverterFactory(ScalarsConverterFactory.create())      // To receive String response. Should be before the gson converters
            .addConverterFactory(versionConverterFactory)
            //.addConverterFactory(listDirectoryConverterFactory)
            .client(okHttpClient)
            .build()
            .create(FileTransferNetworkService::class.java)
    }

    /*
    class HeaderInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = chain.run {
            proceed(
                request()
                    .newBuilder()
                    .addHeader("Accept", "application/json")
                    .build()
            )
        }
    }*/

    private fun createGsonConverter(type: Type, typeAdapter: Any): Converter.Factory? {
        val gson = GsonBuilder()
            //.setLenient()
            .registerTypeAdapter(type, typeAdapter)
            .create()
        return GsonConverterFactory.create(gson)
    }
}