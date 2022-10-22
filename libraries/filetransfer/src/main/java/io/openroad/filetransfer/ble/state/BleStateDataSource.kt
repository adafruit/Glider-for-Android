package io.openroad.filetransfer.ble.state

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.adafruit.glider.utils.LogUtils
import io.openroad.filetransfer.ble.utils.BleManager
import io.openroad.filetransfer.BuildConfig
import io.openroad.filetransfer.ble.state.BleState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.callbackFlow

/*
    Get bluetooth status changes
*/
class BleStateDataSource(context: Context) {
    // Data - Internal
    private val log by LogUtils()

    val bleStateFlow = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                    val state =
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

                    if (BuildConfig.DEBUG) {
                        when (state) {
                            BluetoothAdapter.STATE_OFF -> log.info("Bluetooth off")
                            BluetoothAdapter.STATE_TURNING_OFF -> log.info("Turning Bluetooth off...")
                            BluetoothAdapter.STATE_ON -> log.info("Bluetooth on")
                            BluetoothAdapter.STATE_TURNING_ON -> log.info("Turning Bluetooth on...")
                        }
                    }

                    val bleState = BleState.from(state)
                    trySend(bleState)
                        .onFailure {
                            log.warning("bleStateFlow failure")
                        }
                }
            }
        }

        // Set initial value
        trySend(BleManager.getBleState(context))

        // Register receiver
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(receiver, filter)

        // Await close and unregister receiver
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }
}

