package io.openroad.filetransfer.wifi.peripheral

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
private const val kSharedPreferencesName = "SavedSettingsWifiPeripherals"
private const val kSharedPreferences_settings = "settings"

class SavedSettingsWifiPeripherals(
    context: Context
) {
    data class Settings(var name: String, var hostName: String?, val password: String)

    // Data - Internal
    private val sharedPreferences =
        context.getSharedPreferences(kSharedPreferencesName, Context.MODE_PRIVATE)
    private val _peripheralsSettings = MutableStateFlow(getPeripheralsSettings())

    // Data - Public
    val peripheralsSettings = _peripheralsSettings.asStateFlow()

    // region Actions
    fun getPassword(hostName: String): String? {
        val existingPeripheral = _peripheralsSettings.value.firstOrNull { it.hostName == hostName }
        return existingPeripheral?.password
    }

    fun add(name: String, hostName: String, password: String) {
        // If already exist that address, remove it and add it to update the name
        val peripherals = _peripheralsSettings.value.toMutableList()
        val existingPeripheral = peripherals.firstOrNull { it.hostName == hostName }

        // Continue if not exist or the name has changed
        if (existingPeripheral == null || existingPeripheral.password != password) {

            // If the name has changed, remove it to add it with the new name
            if (existingPeripheral != null) {
                peripherals.remove(existingPeripheral)
            }

            peripherals.add(Settings(name, hostName, password))
            setPeripheralsSettings(peripherals)
        }
    }

    fun remove(hostName: String) {
        val peripherals = _peripheralsSettings.value.toMutableList()
        val existingPeripheral = peripherals.firstOrNull { it.hostName == hostName }
        if (existingPeripheral != null) {
            peripherals.remove(existingPeripheral)
        }
        setPeripheralsSettings(peripherals)
    }

    fun clear() {
        setPeripheralsSettings(emptyList())
    }
    // endregion

    // region SharedPreferences

    private fun getPeripheralsSettings(): List<Settings> {
        val jsonString = sharedPreferences.getString(kSharedPreferences_settings, null)
        return if (jsonString == null) {
            emptyList()
        } else {
            val type: Type = object : TypeToken<List<Settings?>?>() {}.type
            Gson().fromJson(jsonString, type)
        }
    }

    private fun setPeripheralsSettings(settings: List<Settings>) {
        with(sharedPreferences.edit()) {
            val jsonString = Gson().toJson(settings)
            putString(kSharedPreferences_settings, jsonString)
            apply()
        }
        _peripheralsSettings.update { getPeripheralsSettings() }
    }
    // endregion
}