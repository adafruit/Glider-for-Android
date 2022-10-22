package io.openroad.filetransfer.ble.state

/**
 * Created by Antonio García (antonio@openroad.es)
 */
import io.openroad.filetransfer.ble.state.BleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

private const val kDefaultSubscribedTimeout = 5000L     //  keep the upstream flow active after the disappearance of the last collector. That avoids restarting the upstream flow in certain situations such as configuration changes

class BleStateRepository(
    bleStateDataSource: BleStateDataSource,
    externalScope: CoroutineScope
) {
    val bleState: StateFlow<BleState> = bleStateDataSource.bleStateFlow.stateIn(externalScope, WhileSubscribed(
        kDefaultSubscribedTimeout
    ),
        BleState.Unknown
    )
}