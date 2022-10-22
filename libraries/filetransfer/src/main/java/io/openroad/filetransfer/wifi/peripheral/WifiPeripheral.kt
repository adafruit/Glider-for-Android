package io.openroad.filetransfer.wifi.peripheral

import io.openroad.filetransfer.Peripheral

/**
 * Created by Antonio García (antonio@openroad.es)
 */

data class WifiPeripheral(
    override val name: String?,
    override val address: String,
    val port: Int,
    override val createdMillis: Long = System.currentTimeMillis(),
) : Peripheral {

    fun baseUrl(): String {
        var baseUrl = "http://$address"
        if (port != 80) {
            baseUrl += ":$port"
        }

        return baseUrl
    }

    override val nameOrAddress: String = name ?: address
    //override val type = Peripheral.Type.Wifi

    override fun disconnect(cause: Throwable?) {
        /* Nothing to do */
    }
}