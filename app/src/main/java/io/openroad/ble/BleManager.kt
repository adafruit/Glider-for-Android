package io.openroad.ble

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.openroad.ble.state.BleState

class BleManager {
}

// region Ready Checks
fun isBleStateAndPermissionsReady(context: Context): Boolean {
    // Check state
    val state = getBleState(context)
    if (state != BleState.Enabled) return false

    // Check location
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val isLocationEnabled = isLocationEnabled(context)
        if (!isLocationEnabled) return false
    }

    // Check permissions
    val isBluetoothScanGranted =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    val isLocationGranted =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    return isBluetoothScanGranted && isLocationGranted
}

fun isLocationEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return LocationManagerCompat.isLocationEnabled(locationManager)
}
// endregion

// region Permissions

fun getNeededPermissions(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) //, Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

// endregion

// region Static BleState
fun getBleState(context: Context): BleState {
    val adapter = getBluetoothAdapter(context)
        ?: return BleState.BluetoothNotAvailable

    // Checks if Bluetooth is supported on the device.
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
        return BleState.BleNotAvailable
    }

    return BleState.from(adapter.state)
}
// endregion

// region Utils

private fun getBluetoothManager(context: Context): BluetoothManager? {
    return context.applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
}

fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
    return getBluetoothManager(context)?.adapter
}


@SuppressLint("MissingPermission")
fun cancelDiscovery(context: Context) {
    getBluetoothAdapter(context)?.cancelDiscovery()
}

fun refreshDeviceCache(gatt: BluetoothGatt): Boolean {
    var isRefreshed = false

    try {
        val localMethod = gatt.javaClass.getMethod("refresh")
        isRefreshed = (localMethod.invoke(gatt) as Boolean)
        //log.info("Gatt cache refresh successful: $isRefreshed")
    } catch (e: Exception) {
        //log.severe("An exception occurred while refreshing device: ${e.localizedMessage}")
    }

    return isRefreshed
}
// endregion