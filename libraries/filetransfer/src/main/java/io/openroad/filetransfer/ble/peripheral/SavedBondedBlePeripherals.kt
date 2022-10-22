package io.openroad.filetransfer.ble.peripheral

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.lang.reflect.Type

/**
 * Created by Antonio García (antonio@openroad.es)
 */

// Constants
private const val kSharedPreferencesName = "SavedBondedBlePeripherals"
private const val kSharedPreferences_data = "data"

class SavedBondedBlePeripherals(
    context: Context
) {
    data class Data(var name: String?, val address: String)

    // Data - Internal
    private val sharedPreferences =
        context.getSharedPreferences(kSharedPreferencesName, Context.MODE_PRIVATE)
    private val _peripheralsData = MutableStateFlow(getPeripheralsData())

    // Data - Public
    val peripheralsData = _peripheralsData.asStateFlow()

    // region Actions
    fun add(name: String?, address: String) {
        // If already exist that address, remove it and add it to update the name
        val peripherals = _peripheralsData.value.toMutableList()
        val existingPeripheral = peripherals.firstOrNull { it.address == address }

        // Continue if not exist or the name has changed
        if (existingPeripheral == null || existingPeripheral.name != name) {

            // If the name has changed, remove it to add it with the new name
            if (existingPeripheral != null) {
                peripherals.remove(existingPeripheral)
            }

            peripherals.add(Data(name, address))
            setBondedPeripherals(peripherals)
        }
    }

    fun remove(address: String) {
        val peripherals = _peripheralsData.value.toMutableList()
        val existingPeripheral = peripherals.firstOrNull { it.address == address }
        if (existingPeripheral != null) {
            peripherals.remove(existingPeripheral)
            setBondedPeripherals(peripherals)
        }
    }

    fun clear() {
        setBondedPeripherals(emptyList())
    }
    // endregion

    // region SharedPreferences

    private fun getPeripheralsData(): List<Data> {
        val jsonString = sharedPreferences.getString(kSharedPreferences_data, null)
        return if (jsonString == null) {
            emptyList()
        } else {
            val type: Type = object : TypeToken<List<Data?>?>() {}.type
            Gson().fromJson(jsonString, type)
        }
    }

    private fun setBondedPeripherals(peripherals: List<Data>) {
        with(sharedPreferences.edit()) {
            val jsonString = Gson().toJson(peripherals)
            putString(kSharedPreferences_data, jsonString)
            apply()
        }
        _peripheralsData.update { getPeripheralsData() }
    }
    // endregion

}