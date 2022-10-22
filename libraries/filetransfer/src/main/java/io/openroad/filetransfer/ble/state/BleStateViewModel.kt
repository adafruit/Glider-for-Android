package io.openroad.filetransfer.ble.state

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class BleStateViewModel(
    bleStateRepository: BleStateRepository
) : ViewModel() {

    val bleBleState = bleStateRepository.bleState
}

// region Factory
class BleStateViewModelFactory(private val bleStateRepository: BleStateRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BleStateViewModel(bleStateRepository) as T
    }
}
// endregion