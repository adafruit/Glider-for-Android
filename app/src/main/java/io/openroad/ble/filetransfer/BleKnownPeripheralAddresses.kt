package io.openroad.ble.filetransfer

import android.content.Context
import io.openroad.ble.applicationContext

/**
 * Created by Antonio García (antonio@openroad.es)
 */

object BleKnownPeripheralAddresses {
    // Constants
    const val kSharedPreferencesName = "BleKnownPeripheralAddresses"
    const val kSharedPreferences_knownAddresses = "knownAddresses"

    // Data - Internal
    private val sharedPreferences =
        applicationContext.getSharedPreferences(kSharedPreferencesName, Context.MODE_PRIVATE)

    //
    val knownAddresses: Set<String> =
        sharedPreferences.getStringSet(kSharedPreferences_knownAddresses, emptySet())!!

    // region Actions
    fun addPeripheralAddress(address: String) {
        val addresses = knownAddresses.toMutableSet()
        addresses.add(address)
        setKnownPeripherals(addresses)
    }

    fun clear() {
        setKnownPeripherals(emptySet())
    }
    // endregion

    private fun setKnownPeripherals(addresses: Set<String>) {
        with(sharedPreferences.edit()) {
            putStringSet(kSharedPreferences_knownAddresses, addresses)
            apply()
        }
    }

}