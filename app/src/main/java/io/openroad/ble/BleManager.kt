package io.openroad.ble

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import io.openroad.ble.filetransfer.BleFileTransferPeripheral
import io.openroad.ble.peripheral.BlePeripheral
import io.openroad.ble.state.BleState
import io.openroad.utils.LogUtils

object BleManager {
    // Data - Private
    private val log by LogUtils()

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
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // endregion

    // region Static BleState
    fun getBleState(context: Context = applicationContext): BleState {
        val adapter = getBluetoothAdapter(context)
            ?: return BleState.BluetoothNotAvailable

        // Checks if Bluetooth is supported on the device.
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return BleState.BleNotAvailable
        }

        return BleState.from(adapter.state)
    }

    fun confirmBleState(context: Context = applicationContext, requiredState: BleState): Boolean {
        return getBleState(context) == requiredState
    }

    fun confirmBleStateOrThrow(context: Context = applicationContext, requiredState: BleState) {
        if (!confirmBleState(context, requiredState)) {
            throw BleInvalidStateException()
        }
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
    fun getConnectedPeripherals(context: Context): List<BluetoothDevice>? {
        return getBluetoothManager(context)?.getConnectedDevices(BluetoothProfile.GATT)
    }

    @SuppressLint("MissingPermission")
    fun getPairedPeripherals(context: Context): Set<BluetoothDevice>? {
        return getBluetoothAdapter(context)?.bondedDevices
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

    // region Reconnect

    fun reconnectToPeripherals(
        context: Context,
        addresses: Set<String>,
        connectionTimeout: Int? = null,
        completion: (List<BleFileTransferPeripheral>) -> Unit
    ) {
        val connectedAndSetupPeripherals: MutableList<BleFileTransferPeripheral> = mutableListOf()

        val adapter = getBluetoothAdapter(context)
        if (adapter == null) {
            log.warning("reconnectToPeripheral called when adapter is null")
            completion(connectedAndSetupPeripherals)
            return
        }

        // Reconnect to a known identifier
        val awaitingConnection: MutableSet<String> = addresses.toMutableSet()
        for (address in addresses) {
            try {
                val device = adapter.getRemoteDevice(address)
                val blePeripheral = BlePeripheral(device)
                val bleFileTransferPeripheral = BleFileTransferPeripheral(blePeripheral)
                log.info("Try to connect to known peripheral: ${blePeripheral.nameOrAddress}")
                bleFileTransferPeripheral.connectAndSetup(connectionTimeout = connectionTimeout) { isConnected ->
                    // Return with the fist connected peripheral or with null if all peripherals fail
                    if (isConnected) {
                        connectedAndSetupPeripherals.add(bleFileTransferPeripheral)
                    }
                    awaitingConnection.remove(address)

                    // Call completion when all awaiting peripherals have finished reconnection
                    if (awaitingConnection.isEmpty()) {
                        completion(connectedAndSetupPeripherals)
                    }
                }

            } catch (e: IllegalArgumentException) {
                log.warning("Invalid address: $address")
            }
        }

        // Reconnect even if no identifier was saved if we are already connected to a device with the expected services
        // TODO

    }

    // endregion

    // region Bonding
    fun removeAllPairedPeripheralInfo(context: Context) {
        try {
            val bondedDevices = getPairedPeripherals(context)
            log.info("Bound devices: $bondedDevices")

            bondedDevices?.forEach { device ->
                try {
                    val method = device.javaClass.getMethod("removeBond")
                    val result = method.invoke(device) as Boolean
                    if (result) {
                        log.info("Successfully removed bond")
                    }
                } catch (e: Exception) {
                    log.info("ERROR: could not remove bond: $e")
                }

            }
        } catch (ignored: SecurityException) {
        }
    }

    // endregion
}
