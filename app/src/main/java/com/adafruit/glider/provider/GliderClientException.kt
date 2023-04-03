package com.adafruit.glider.provider

/**
 * Created by Antonio García (antonio@openroad.es)
 */

open class GliderClientException internal constructor(
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)


data class UnknownPeripheralGliderClientException internal constructor(
    val address: String
) : GliderClientException("Unknown peripheral address: $address")


