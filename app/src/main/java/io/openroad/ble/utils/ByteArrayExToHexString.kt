package io.openroad.ble.utils

/**
 * Created by Antonio García (antonio@openroad.es)
 */

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }