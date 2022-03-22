package io.openroad.ble

/**
 * Created by Antonio García (antonio@openroad.es)
 */


open class BleException internal constructor(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

data class BleScanException internal constructor(
    val errorCode: Int,
) : BleException("Scan Error: $errorCode")


class BleInvalidStateException internal constructor(
    message: String? = null,
    cause: Throwable? = null,
) : BleException(message, cause)


class BleConnectionInvalidStateException internal constructor(
    message: String? = null,
    cause: Throwable? = null,
) : BleException(message, cause)


class BleUnknownStateException internal constructor() : BleException()


data class BleConnectionException internal constructor(
    val status: Int,
) : BleException("Connection failed. Status: $status")

class BleDiscoveryException internal constructor() : BleException()


