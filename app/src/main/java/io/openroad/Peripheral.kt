package io.openroad

/**
 * Created by Antonio García (antonio@openroad.es)
 */

interface Peripheral {
    val name: String?
    val address: String
    val nameOrAddress: String

    val createdMillis: Long      // Time when it was created (usually the time when it was discovered)

    fun disconnect(cause: Throwable? = null)
}
