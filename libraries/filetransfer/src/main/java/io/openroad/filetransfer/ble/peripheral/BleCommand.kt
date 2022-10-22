package io.openroad.filetransfer.ble.peripheral

/**
 * Created by Antonio García (antonio@openroad.es)
 */

typealias CompletionHandler = (status: Int) -> Unit
typealias NotifyHandler = (status: Int) -> Unit
typealias DataReadHandler = (status: Int, value: ByteArray?) -> Unit
typealias CaptureReadCompletionHandler = (status: Int, value: ByteArray?) -> Unit
typealias TimeoutAction = (identifier: String) -> Unit

internal abstract class BleCommand @JvmOverloads constructor(
    val type: Int,
    val identifier: String?,
    private val completionHandler: CompletionHandler?,
    private val extra: Any? = null
) {
    companion object {
        // Command types
        const val BLECOMMANDTYPE_DISCOVERSERVICES = 1
        const val BLECOMMANDTYPE_SETNOTIFY = 2      // TODO: add support for indications
        const val BLECOMMANDTYPE_READCHARACTERISTIC = 3
        const val BLECOMMANDTYPE_WRITECHARACTERISTIC = 4
        const val BLECOMMANDTYPE_WRITECHARACTERISTICANDWAITNOTIFY = 5
        const val BLECOMMANDTYPE_READDESCRIPTOR = 6
        const val BLECOMMANDTYPE_REQUESTMTU = 7
    }

    // Data
    var isCancelled = false; private set

    // region Actions
    fun cancel() {
        isCancelled = true
    }

    fun completion(status: Int) {
        completionHandler?.let { it(status) }
    }

    // @return true if finished
    abstract fun execute()
}
