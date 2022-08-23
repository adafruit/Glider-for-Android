package io.openroad.wifi.peripheral

import io.openroad.Peripheral

/**
 * Created by Antonio García (antonio@openroad.es)
 */

data class WifiPeripheral(
    override val name: String?,
    override val address: String,
    override val nameOrAddress: String = name ?: address,
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

    //override val type = Peripheral.Type.Wifi

    override fun disconnect(cause: Throwable?) {
        /* Nothing to do */
    }
}