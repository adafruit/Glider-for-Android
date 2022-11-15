package io.openroad.filetransfer.ble.utils

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

class BleOperationInProgressException internal constructor(
    message: String? = null,
) : BleException(message)


class BleConnectionInvalidStateException internal constructor(
    message: String? = null,
    cause: Throwable? = null,
) : BleException(message, cause)


class BleUnknownStateException internal constructor() : BleException()

data class BleConnectionException constructor(
    val status: Int,
) : BleException("Connection failed. Status: $status")

class BleDiscoveryException internal constructor() : BleException()
class BleTimeoutException internal constructor() : BleException()
class BleDisconnectedWhileWaitingForCommandException internal constructor() : BleException("Disconnected while waiting for operation")

data class BleStatusResultException internal constructor(
    val status: Int,
) : BleException("Status: $status")

