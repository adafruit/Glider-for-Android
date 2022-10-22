package io.openroad.filetransfer.ble.bond

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.adafruit.glider.utils.LogUtils
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.callbackFlow

/*
    deviceAddress: Set to null to receive all bond state changes, or set to a specific address to receive only changes for that device
 */
class BleBondStateDataSource(context: Context, deviceAddress: String?) {//, initialValue: BleBondState) {
    // Data - Internal
    private val log by LogUtils()

    val bleBondStateFlow = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                if (intent.action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                    val device = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    }
                    else {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    // Only continue if the device address matches the one we are interested in
                    if (deviceAddress == null || device?.address == deviceAddress) {

                        //val previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)
                        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)

                        val bleBondState = BleBondState.from(bondState)
                        trySend(bleBondState)
                            .onFailure {
                                log.warning("bleBondStateFlow failure")
                            }
                    }
                }
            }
        }

        // Set initial value
        //trySend(initialValue)
        //log.info("Start bond detector for: $deviceAddress")

        // Register receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(receiver, filter)

        // Await close and unregister receiver
        awaitClose {
            context.unregisterReceiver(receiver)
            //log.info("End bond detector for: $deviceAddress")
        }
    }
}