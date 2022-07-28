package io.openroad.wifi.scanner

/**
 * Created by Antonio García (antonio@openroad.es)
 */

open class NsdException internal constructor(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

data class NsdScanException internal constructor(
    val errorCode: Int,
) : NsdException("NSD Error: $errorCode")
