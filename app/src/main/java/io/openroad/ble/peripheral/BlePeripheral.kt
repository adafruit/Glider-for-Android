package io.openroad.ble.peripheral

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanResult
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.annotation.IntRange
import androidx.annotation.MainThread
import com.adafruit.glider.BuildConfig
import io.openroad.ble.*
import io.openroad.ble.bond.BleBondState
import io.openroad.ble.bond.BleBondStateDataSource
import io.openroad.ble.state.BleState
import io.openroad.ble.utils.BleKnownPeripheralAddresses
import io.openroad.ble.utils.toHexString
import io.openroad.utils.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.*

// Constants
private const val kDefaultMtuSize = 20
internal val kClientCharacteristicConfigUUID =
    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

// Configuration
// -    Sets a identifier for each command and verifies that the command processed is the one expected
private val kDebugCommands = BuildConfig.DEBUG && true

// -    Debug timeouts from commands
private val kProfileTimeouts = BuildConfig.DEBUG && true

//
open class BlePeripheral(
    scanResult: ScanResult,
    internal val externalScope: CoroutineScope = MainScope()
) {
    companion object {
        // Global Parameter that affects all rssi measurements. 1 means don't use a running average. The closer to 0 the more resistant the value it is to change
        // const val rssiRunningAverageFactor = 1.0

        // Note: Value should be different that errors defined in BluetoothGatt.GATT_*
        const val kPeripheralReadTimeoutError = -1
    }

    // Data - Private
    private val log by LogUtils()
    private var _runningRssi: Int? = null       // Backing variable for rssi
    var mtuSize: Int = kDefaultMtuSize; private set

    // Data - Private - Bonding
    var bleBondDataSource: BleBondStateDataSource? = null
    var bondStateJob: Job? = null

    // Data - Private - Cached values to speed up processing
    private var cachedNameNeedsUpdate = true
    private var cachedName: String? = null      // Cached name
    private var cachedAddress: String? = null   // Cached address

    // Data - State
    sealed class ConnectionState {
        object Start : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Disconnecting(val cause: Throwable? = null) : ConnectionState()
        data class Disconnected(val cause: Throwable? = null) : ConnectionState()

        companion object {
            fun from(bluetoothProfileState: Int): ConnectionState {
                return when (bluetoothProfileState) {
                    BluetoothProfile.STATE_DISCONNECTED -> Disconnected()
                    BluetoothProfile.STATE_CONNECTING -> Connecting
                    BluetoothProfile.STATE_CONNECTED -> Connected
                    BluetoothProfile.STATE_DISCONNECTING -> Disconnecting()
                    else -> {
                        // Unknown state returned by Android. Set is as disconnected
                        Disconnected(cause = BleUnknownStateException())
                    }
                }
            }
        }
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Start)
    val connectionState = _connectionState.asStateFlow()

    fun confirmConnectionState(requiredConnectionState: ConnectionState) =
        connectionState.value == requiredConnectionState

    fun confirmConnectionStateOrThrow(requiredConnectionState: ConnectionState) {
        if (!confirmConnectionState(requiredConnectionState)) {
            throw BleInvalidStateException()
        }
    }

    // Data - Bonding information
    private val _bondingState = MutableStateFlow(BleBondState.Unknown)
    val bondingState = _bondingState.asStateFlow()

    // Data - Advertising
    val createdMillis =
        System.currentTimeMillis()          // Time when it was created (usually the time when the advertising was discovered)
    var lastUpdateMillis = System.currentTimeMillis(); private set

    private val _rssi: MutableStateFlow<Int> = MutableStateFlow(scanResult.rssi)
    val rssi = _rssi.asStateFlow()

    val currentRssi = rssi.value

    // Data - Connection
    private val bluetoothDevice: BluetoothDevice = scanResult.device
    private var connectionHandlerThread: HandlerThread? = null

/*
    var rssi: Int?                          // Latest rssi read (initial value from scanResult, but can be updated when remote reading rssi)
        get() {
            return _runningRssi
        }
        private set(newValue) {
            // based on https://en.wikipedia.org/wiki/Exponential_smoothing
            val oldRunningRssi = _runningRssi
            _runningRssi = if (newValue == null || oldRunningRssi == null) {
                newValue
            } else {
                (rssiRunningAverageFactor * newValue + (1 - rssiRunningAverageFactor) * oldRunningRssi).toInt()
            }
        }*/

    // Data - ScanRecord Data
    private val _scanResult: MutableStateFlow<ScanResult> = MutableStateFlow(scanResult)
    val scanResult = _scanResult.asStateFlow()

    // TODO: encapsulate advertising data in separate class
    val scanRecord = scanResult.scanRecord

    val address: String
        get() {
            if (cachedAddress == null) {
                cachedAddress = scanResult.value.device.address
            }
            return cachedAddress!!
        }

    val name: String?
        get() {
            if (cachedNameNeedsUpdate) {
                cachedName = scanRecord?.deviceName ?: remoteName
                cachedNameNeedsUpdate = false
            }
            return cachedName
        }

    val nameOrAddress = name ?: address

    val remoteName: String?
        get() {
            var result: String? = null
            try {
                result = scanResult.value.device.name
            } catch (e: SecurityException) {
                log.severe("Security exception accessing remote name: $e")
            }

            return result
        }

    // region Scanning
    fun updateScanResult(scanResult: ScanResult) {
        lastUpdateMillis = System.currentTimeMillis()
        _scanResult.update { scanResult }
        _rssi.update { scanResult.rssi }     // Update rssi from scanResult
        cachedNameNeedsUpdate = true
    }

    // endregion

    // Data - Private - Connection
    private var bluetoothGatt: BluetoothGatt? = null
    private val commandQueue = CommandQueue()

    // Data - Private - Reconnection
    private var reconnectionAttempts = 0

    // Data - Private - Reconnection
    private val notifyHandlers = HashMap<String, NotifyHandler>()
    private val captureReadHandlers = mutableListOf<CaptureReadHandler>()


    // region Lifecycle
    init {
    }

    // endregion

    // region Connection


    // TODO: convert connection and gattCallback to use Kotlin flows
    @SuppressLint("MissingPermission")
    @MainThread
    fun connect() {
        // Confirm that ble state is enabled
        if (!confirmBleState(requiredState = BleState.Enabled)) {
            _connectionState.update { ConnectionState.Disconnected(BleInvalidStateException()) }
            return
        }

        // Confirm that is disconnected before starting connection (because it can already be connected)
        if (!confirmConnectionState(requiredConnectionState = ConnectionState.Start)) {
            _connectionState.update {
                ConnectionState.Disconnected(BleConnectionInvalidStateException())
            }
            return
        }

        // Setup connection handler
        val thread = HandlerThread("BlePeripheral_${nameOrAddress}").apply { start() }
        val handler = Handler(thread.looper)
        connectionHandlerThread = thread

        val context = applicationContext

        // Start bonding information listener
        val currentBondState = BleBondState.from(scanResult.value.device.bondState)
        bondStateJob = externalScope.launch {
            bleBondDataSource =
                BleBondStateDataSource(applicationContext, address, currentBondState)
            bleBondDataSource?.bleBondStateFlow?.collect { bondState ->

                _bondingState.update { bondState }
                when (bondState) {
                    BleBondState.Bonding -> log.info("$nameOrAddress bonding")
                    BleBondState.Bonded -> {
                        log.info("$nameOrAddress bonded")

                        // Save bonded information
                        BleKnownPeripheralAddresses.addPeripheralAddress(address)
                    }
                    BleBondState.NotBonded -> log.info("$nameOrAddress not bonded")
                    else -> {
                        log.warning("$nameOrAddress unknown bondState: $bondState")
                    }
                }
            }
        }

        // Start connection attempts
        reconnectionAttempts = 0
        commandQueue.clear()
        _connectionState.update { ConnectionState.Connecting }
        cancelDiscovery(context)    // Note: always cancel discovery before connecting

        bluetoothGatt = bluetoothDevice.connectGatt(
            context,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE,
            BluetoothDevice.PHY_LE_1M_MASK,
            handler
        )

        if (bluetoothGatt == null) {
            log.severe("connectGatt Error. Returns null")
            cancelConnectionHandlerThread()
            throw BleException("connectGatt returns null")
        }

        // Wait for connection status in gattCallback
    }

    private fun cancelConnectionHandlerThread() {
        connectionHandlerThread?.quit()
        connectionHandlerThread = null
    }

    @SuppressLint("MissingPermission")
    @MainThread
    fun disconnect(cause: Throwable? = null) {
        if (bluetoothGatt != null) {
            val oldState = _connectionState.getAndUpdate { ConnectionState.Disconnecting(cause) }
            bluetoothGatt?.disconnect()
            val wasConnecting = oldState == ConnectionState.Connecting
            if (wasConnecting) {        // Force a disconnect status change because onDisconnect is not generated by Android
                connectionFinished()
            }
        } else {
            log.warning("Disconnect called with null bluetoothGatt")
        }
    }

    private fun connectionFinished(cause: Throwable? = null) {
        _connectionState.update { previousState ->
            // Set the state to disconnected, but if no cause is supplied and there was a cause when disconnecting, use that one
            val disconnectingCause = (previousState as? ConnectionState.Disconnecting)?.cause
            if (cause == null && disconnectingCause != null) {
                ConnectionState.Disconnected(disconnectingCause)
            } else {
                ConnectionState.Disconnected(cause)
            }
        }

        bondStateJob?.cancel()
        bondStateJob = null
        bleBondDataSource = null

        cancelConnectionHandlerThread()
        closeBluetoothGatt()
    }
    // endregion

    // region Phy
    @SuppressLint("MissingPermission")
    fun setPreferredPhy(txPhy: Int, rxPhy: Int, phyOptions: Int) {
        bluetoothGatt?.setPreferredPhy(txPhy, rxPhy, phyOptions)
    }

    @SuppressLint("MissingPermission")
    fun readPhy() {
        bluetoothGatt?.readPhy()
    }
    // endregion

    // region Discovery
    fun discoverServices(completion: CompletionHandler?) {
        val command = object : BleCommand(BLECOMMANDTYPE_DISCOVERSERVICES, null, completion) {
            @SuppressLint("MissingPermission")
            override fun execute() {
                val isDiscoveryInProgress =
                    bluetoothGatt != null && bluetoothGatt!!.discoverServices()
                if (!isDiscoveryInProgress) {
                    log.severe("discoverServices failed")
                    finishExecutingCommand(BluetoothGatt.GATT_FAILURE)
                }
            }
        }
        commandQueue.add(command)
    }

    fun isDiscoveringServices(): Boolean {
        return commandQueue.containsCommandType(BleCommand.BLECOMMANDTYPE_DISCOVERSERVICES)
    }

    // endregion

    // region Services
    fun getServices(): MutableList<BluetoothGattService?>? {
        // This function requires that service discovery has been completed for the given device or returns null
        return bluetoothGatt?.services
    }

    fun getService(uuid: UUID): BluetoothGattService? {
        // This function requires that service discovery has been completed for the given device.
        // If multiple instance of the service exist, it returns the first one
        return bluetoothGatt?.getService(uuid)
    }
    // endregion

    // region Characteristics

    fun getCharacteristic(
        characteristicUUID: UUID,
        serviceUUID: UUID
    ): BluetoothGattCharacteristic? {
        // This function requires that service discovery has been completed for the given device.
        val service = getService(serviceUUID)
        return service?.getCharacteristic(characteristicUUID)
    }

    fun getCharacteristic(
        characteristicUUID: UUID,
        service: BluetoothGattService
    ): BluetoothGattCharacteristic? {
        // This function requires that service discovery has been completed for the given device.
        return service.getCharacteristic(characteristicUUID)
    }

    fun characteristicEnableNotify(
        characteristic: BluetoothGattCharacteristic,
        notifyHandler: NotifyHandler,
        completionHandler: CompletionHandler
    ) {
        val identifier = getCharacteristicIdentifier(characteristic)
        val command = object : BleCommand(
            BLECOMMANDTYPE_SETNOTIFY,
            if (kDebugCommands) identifier else null,
            completionHandler
        ) {
            @SuppressLint("MissingPermission")
            override fun execute() {
                val descriptor = characteristic.getDescriptor(kClientCharacteristicConfigUUID)
                if (bluetoothGatt != null && descriptor != null && characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    notifyHandlers.put(identifier, notifyHandler)
                    bluetoothGatt!!.setCharacteristicNotification(characteristic, true)
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bluetoothGatt!!.writeDescriptor(descriptor)
                } else {
                    log.info("enable notify: client config descriptor not found for characteristic: " + characteristic.uuid.toString())
                    finishExecutingCommand(BluetoothGatt.GATT_FAILURE)
                }
            }
        }
        commandQueue.add(command)
    }

    fun characteristicDisableNotify(
        characteristic: BluetoothGattCharacteristic,
        completionHandler: CompletionHandler
    ) {
        val identifier = getCharacteristicIdentifier(characteristic)
        val command = object : BleCommand(
            BLECOMMANDTYPE_SETNOTIFY,
            if (kDebugCommands) identifier else null,
            completionHandler
        ) {
            @SuppressLint("MissingPermission")
            override fun execute() {
                val descriptor = characteristic.getDescriptor(kClientCharacteristicConfigUUID)
                if (bluetoothGatt != null && descriptor != null && characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                    notifyHandlers.remove(identifier)
                    descriptor.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    bluetoothGatt!!.writeDescriptor(descriptor)
                } else {
                    log.info("disable notify: client config descriptor not found for characteristic: " + characteristic.uuid.toString())
                    finishExecutingCommand(BluetoothGatt.GATT_FAILURE)
                }
            }
        }
        commandQueue.add(command)
    }

    fun characteristicUpdateNotify(
        characteristic: BluetoothGattCharacteristic,
        notifyHandler: NotifyHandler
    ) {
        val identifier = getCharacteristicIdentifier(characteristic)
        val previousNotifyHandler = notifyHandlers.put(identifier, notifyHandler)
        if (previousNotifyHandler == null) {
            log.info("trying to update inexistent notifyHandler for characteristic: " + characteristic.uuid.toString())
        }
    }

    fun readCharacteristic(
        service: BluetoothGattService,
        characteristicUUID: UUID,
        dataReadHandler: DataReadHandler
    ) {
        val characteristic = service.getCharacteristic(characteristicUUID)
        if (characteristic != null) {
            readCharacteristic(characteristic, dataReadHandler)
        } else {
            dataReadHandler(BluetoothGatt.GATT_FAILURE, null)
        }
    }

    fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        dataReadHandler: DataReadHandler
    ) {
        readCharacteristic(characteristic) { status: Int ->
            var data: ByteArray? = null
            if (status == BluetoothGatt.GATT_SUCCESS) {
                data = characteristic.value
            }
            dataReadHandler(status, data)
        }
    }

    fun readCharacteristic(
        service: BluetoothGattService,
        characteristicUUID: UUID?,
        completionHandler: CompletionHandler
    ) {
        val characteristic = service.getCharacteristic(characteristicUUID)
        if (characteristic != null) {
            readCharacteristic(characteristic, completionHandler)
        } else {
            completionHandler(BluetoothGatt.GATT_FAILURE)
        }
    }

    fun readCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        completionHandler: CompletionHandler
    ) {
        val identifier =
            if (kDebugCommands) getCharacteristicIdentifier(
                characteristic.service.uuid,
                characteristic.uuid
            ) else null

        val command =
            object : BleCommand(BLECOMMANDTYPE_READCHARACTERISTIC, identifier, completionHandler) {
                @SuppressLint("MissingPermission")
                override fun execute() {
                    if (bluetoothGatt != null) {
                        // Read Characteristic
                        if (!kDebugCommands || characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0) {
                            bluetoothGatt!!.readCharacteristic(characteristic)
                        } else {
                            log.info("read: characteristic not readable: " + characteristic.uuid.toString())
                            finishExecutingCommand(BluetoothGatt.GATT_READ_NOT_PERMITTED)
                        }
                    } else {
                        log.info("read: characteristic not found: " + characteristic.uuid.toString())
                        finishExecutingCommand(BluetoothGatt.GATT_READ_NOT_PERMITTED)
                    }
                }
            }
        commandQueue.add(command)
    }

    private fun getCharacteristicProperties(
        service: BluetoothGattService,
        characteristicUUID: UUID
    ): Int {
        val characteristic = service.getCharacteristic(characteristicUUID)
        return characteristic?.properties ?: 0
    }

    fun isCharacteristicReadable(service: BluetoothGattService, characteristicUUID: UUID): Boolean {
        val properties: Int = getCharacteristicProperties(service, characteristicUUID)
        return properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
    }

    fun isCharacteristicNotifiable(
        service: BluetoothGattService,
        characteristicUUID: UUID
    ): Boolean {
        val properties: Int = getCharacteristicProperties(service, characteristicUUID)
        return properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
    }

    fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        writeType: Int,
        data: ByteArray,
        completionHandler: CompletionHandler
    ) {
        val identifier =
            if (kDebugCommands) getCharacteristicIdentifier(
                characteristic.service.uuid,
                characteristic.uuid
            ) else null

        val command =
            object : BleCommand(BLECOMMANDTYPE_WRITECHARACTERISTIC, identifier, completionHandler) {
                @SuppressLint("MissingPermission")
                override fun execute() {
                    if (bluetoothGatt != null) {
                        // Write value
                        characteristic.writeType = writeType
                        characteristic.value = data
                        val success = bluetoothGatt!!.writeCharacteristic(characteristic)
                        if (success) {
                            // Simulate response if needed
                            // Android: no need to simulate response: https://stackoverflow.com/questions/43741849/oncharacteristicwrite-and-onnotificationsent-are-being-called-too-fast-how-to/43744888
                        } else {
                            log.warning("writeCharacteristic could not be initiated")
                            finishExecutingCommand(BluetoothGatt.GATT_FAILURE)
                        }
                    } else {
                        log.warning("bluetoothGatt is null")
                        finishExecutingCommand(BluetoothGatt.GATT_FAILURE)
                    }
                }
            }
        commandQueue.add(command)
    }

    // endregion

    // region Descriptors

    private fun getDescriptorPermissions(
        service: BluetoothGattService,
        characteristicUUIDString: String,
        descriptorUUIDString: String
    ): Int {
        val characteristicUuid = UUID.fromString(characteristicUUIDString)
        val characteristic = service.getCharacteristic(characteristicUuid)
        var permissions = 0
        if (characteristic != null) {
            val descriptor = characteristic.getDescriptor(UUID.fromString(descriptorUUIDString))
            if (descriptor != null) {
                permissions = descriptor.permissions
            }
        }
        return permissions
    }

    fun isDescriptorReadable(
        service: BluetoothGattService,
        characteristicUUIDString: String,
        descriptorUUIDString: String
    ): Boolean {
        val permissions =
            getDescriptorPermissions(service, characteristicUUIDString, descriptorUUIDString)
        return permissions and BluetoothGattCharacteristic.PERMISSION_READ != 0
    }

    fun isCharacteristicNotifyingForCachedClientConfigDescriptor(characteristic: BluetoothGattCharacteristic): Boolean {
        // Note: client characteristic descriptor should have been read previously
        val descriptor = characteristic.getDescriptor(kClientCharacteristicConfigUUID)
        return if (descriptor != null) {
            val configValue = descriptor.value
            Arrays.equals(configValue, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            false
        }
    }

    fun readDescriptor(
        service: BluetoothGattService,
        characteristicUUID: UUID,
        descriptorUUID: UUID,
        completionHandler: CompletionHandler
    ) {
        val characteristic = service.getCharacteristic(characteristicUUID)
        if (characteristic != null) {
            readDescriptor(characteristic, descriptorUUID, completionHandler)
        } else {
            log.info("read: characteristic not found: $characteristicUUID")
            finishExecutingCommand(BluetoothGatt.GATT_READ_NOT_PERMITTED)
        }
    }

    fun readDescriptor(
        characteristic: BluetoothGattCharacteristic,
        descriptorUUID: UUID,
        completionHandler: CompletionHandler
    ) {
        val identifier = if (kDebugCommands) getDescriptorIdentifier(
            characteristic.service.uuid,
            characteristic.uuid,
            descriptorUUID
        ) else null

        val command =
            object : BleCommand(BLECOMMANDTYPE_READDESCRIPTOR, identifier, completionHandler) {
                @SuppressLint("MissingPermission")
                override fun execute() {
                    // Read Descriptor
                    val descriptor = characteristic.getDescriptor(descriptorUUID)
                    if (bluetoothGatt != null && descriptor != null) {
                        bluetoothGatt!!.readDescriptor(descriptor)
                    } else {
                        log.info("read: descriptor not found: $descriptorUUID")
                        finishExecutingCommand(BluetoothGatt.GATT_READ_NOT_PERMITTED)
                    }
                }
            }
        commandQueue.add(command)
    }

    open fun requestMtu(
        @IntRange(from = 23, to = 517) mtuSize: Int,
        completionHandler: CompletionHandler
    ) {
        val identifier: String? = null
        val command: BleCommand =
            object : BleCommand(BLECOMMANDTYPE_REQUESTMTU, identifier, completionHandler) {
                @SuppressLint("MissingPermission")
                override fun execute() {
                    // Request mtu size change
                    log.info("Request mtu change to $mtuSize")
                    bluetoothGatt?.requestMtu(mtuSize)

                }
            }
        commandQueue.add(command)
    }
    // endregion

    // region BluetoothGattCallback
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)

            log.info("onConnectionStateChange: $newState")
            Handler(Looper.getMainLooper()).post {          // Not needed ??
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            log.info("Connection state changed: CONNECTED")
                            _connectionState.update { ConnectionState.Connected }
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            log.info("Connection state changed: DISCONNECTED")
                            connectionFinished()
                        }
                        else -> {
                            log.info("Connection state changed to: $newState (status: $status)")
                        }
                    }
                } else {      // Error
                    log.info("Connection status error $status")
                    if (status == 133 && newState == BluetoothProfile.STATE_DISCONNECTED && reconnectionAttempts < 2) {     // Error. Try to connect again https://medium.com/@martijn.van.welie/making-android-ble-work-part-2-47a3cdaade07
                        reconnectionAttempts++
                        log.info("Connection error. Trying to reconnect... (Attempt $reconnectionAttempts)")

                        if (reconnectionAttempts == 2) {        // On the second reconnection attempt, clear the device cache. https://github.com/android/connectivity-samples/issues/18
                            refreshDeviceCache(bluetoothGatt ?: gatt)
                        }

                        (bluetoothGatt ?: gatt).close()

                        // Setup connection handler
                        connectionHandlerThread?.quit()
                        val thread =
                            HandlerThread("BlePeripheral_${nameOrAddress}").apply { start() }
                        val handler = Handler(thread.looper)
                        connectionHandlerThread = thread

                        // Connect
                        bluetoothGatt =
                            scanResult.device.connectGatt(
                                applicationContext,
                                reconnectionAttempts == 2,      // Autoconnect true looks like can solve the problem but it will never timeout
                                this,
                                BluetoothDevice.TRANSPORT_LE,
                                BluetoothDevice.PHY_LE_1M_MASK,
                                handler
                            )
                    } else {
                        connectionFinished(BleConnectionException(status))
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            finishExecutingCommand(status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)

            log.info("onCharacteristicRead: ${characteristic.uuid.toString()}")
            if (kDebugCommands) {
                val identifier = getCharacteristicIdentifier(characteristic)
                val command = commandQueue.first()
                if (command != null && command.type == BleCommand.BLECOMMANDTYPE_READCHARACTERISTIC && identifier == command.identifier) {
                    finishExecutingCommand(status)
                } else {
                    log.info("Warning: onCharacteristicRead with no matching command")
                }
            } else {
                finishExecutingCommand(status)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            // TODO
            finishExecutingCommand(status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            //log.info("onCharacteristicChanged. numCaptureReadHandlers: " + captureReadHandlers.size)
            val identifier: String = getCharacteristicIdentifier(characteristic)
            val status =
                BluetoothGatt.GATT_SUCCESS // On Android, there is no error reported for this callback, so we assume it is SUCCESS

            // Check if waiting to capture this read
            var isNotifyOmitted = false
            var hasCaptureHandler = false

            // Remove capture handler
            val captureHandlerIndex: Int = getCaptureHandlerIndex(identifier)
            if (captureHandlerIndex >= 0) {
                hasCaptureHandler = true
                val captureReadHandler: CaptureReadHandler =
                    captureReadHandlers.removeAt(captureHandlerIndex)

                // Cancel timeout handler
                if (captureReadHandler.timeoutTimer != null) {
                    if (kProfileTimeouts) {
                        log.info("Cancel timeout: " + captureReadHandler.identifier + ". elapsed millis:" + (System.currentTimeMillis() - captureReadHandler.timeoutStartingMillis))
                    }
                    captureReadHandler.timeoutTimer?.cancel()
                    captureReadHandler.timeoutTimer = null
                }

                // Send result
                val value = characteristic.value
                log.info("onCharacteristicChanged: send result to captureReadHandler:" + value.toHexString())
                captureReadHandler.result(status, value)
                isNotifyOmitted = captureReadHandler.isNotifyOmitted
            }

            // Notify
            if (!isNotifyOmitted) {
                val notifyHandler = notifyHandlers[identifier]
                log.info("onCharacteristicChanged. notify: ${if (notifyHandler == null) "no" else "yes"}")
                notifyHandler?.let { it(status) }
            }

            if (hasCaptureHandler) {
                finishExecutingCommand(status)
            }
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)

            if (kDebugCommands) {
                val identifier: String = getDescriptorIdentifier(
                    descriptor.characteristic.service.uuid,
                    descriptor.characteristic.uuid,
                    descriptor.uuid
                )
                val command = commandQueue.first()
                if (command != null && command.type == BleCommand.BLECOMMANDTYPE_READDESCRIPTOR && identifier == command.identifier) {
                    finishExecutingCommand(status)
                } else {
                    log.info("Warning: onDescriptorRead with no matching command")
                }
            } else {
                finishExecutingCommand(status)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)

            val command = commandQueue.first()
            if (command != null && command.type == BleCommand.BLECOMMANDTYPE_SETNOTIFY) {
                if (kDebugCommands) {
                    val identifier: String = getCharacteristicIdentifier(descriptor.characteristic)
                    if (identifier == command.identifier) {
                        //Log.d(TAG, "Set Notify descriptor write: " + status);
                        finishExecutingCommand(status)
                    } else {
                        log.info("Warning: onDescriptorWrite for BLECOMMANDTYPE_SETNOTIFY with no matching command")
                    }
                } else {
                    finishExecutingCommand(status)
                }
            } else {
                log.info("Warning: onDescriptorWrite with no matching command")
            }
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
            super.onReliableWriteCompleted(gatt, status)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)

            if (status == BluetoothGatt.GATT_SUCCESS) {
                this@BlePeripheral._runningRssi = rssi
                //localBroadcastUpdate(kBlePeripheral_OnRssiUpdated, address)
            } else {
                log.info("onReadRemoteRssi error: $status")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mtuSize = mtu
                log.info("Mtu changed: $mtu")
            } else {
                log.info("Error changing mtu to: $mtu status: $status")
            }

            // Check that the MTU changed callback was called in response to a command
            val command = commandQueue.first()
            if (command != null && command.type == BleCommand.BLECOMMANDTYPE_REQUESTMTU) {
                finishExecutingCommand(status)
            }
        }

        override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status)
            log.info("onPhyUpdate -> tx: $txPhy rx: $rxPhy status: $status")
        }

        override fun onPhyRead(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            super.onPhyRead(gatt, txPhy, rxPhy, status)
            log.info("onPhyRead -> tx: $txPhy rx: $rxPhy status: $status")
        }
    }

    // region Utils
    @SuppressLint("MissingPermission")
    @MainThread
    private fun closeBluetoothGatt() {
        bluetoothGatt?.close()
        commandQueue.clear()
        bluetoothGatt = null
    }

    private fun finishExecutingCommand(status: Int) {
        val command = commandQueue.first()
        if (command != null && !command.isCancelled) {
            command.completion(status)
        }
        commandQueue.executeNext()
    }

    // endregion

    // region Command Utils

    private fun getCharacteristicIdentifier(characteristic: BluetoothGattCharacteristic): String {
        return getCharacteristicIdentifier(characteristic.service.uuid, characteristic.uuid)
    }

    private fun getCharacteristicIdentifier(serviceUUID: UUID, characteristicUUID: UUID): String {
        return serviceUUID.toString() + characteristicUUID.toString()
    }

    private fun getDescriptorIdentifier(
        serviceUUID: UUID,
        characteristicUUID: UUID,
        descriptorUUID: UUID
    ): String {
        return serviceUUID.toString() + characteristicUUID.toString() + descriptorUUID.toString()
    }
    // endregion

    // region CaptureReadHandler

    internal class CaptureReadHandler @JvmOverloads constructor(
        val identifier: String,
        val result: CaptureReadCompletionHandler,
        timeout: Int,
        timeoutAction: TimeoutAction?,
        val isNotifyOmitted: Boolean = false
    ) {
        // Constants

        // Data
        private val log by LogUtils()
        var timeoutTimer: Timer? = null
        var timeoutStartingMillis: Long = 0 // only used for debug (kProfileTimeouts)
        private var timeoutAction: TimeoutAction? = null

        init {
            // Setup timeout if not zero
            if (timeout > 0 && timeoutAction != null) {
                this.timeoutAction = timeoutAction
                timeoutTimer = Timer()
                if (kProfileTimeouts) {
                    timeoutStartingMillis = System.currentTimeMillis()
                    log.info("Start timeout: $identifier. millis: $timeoutStartingMillis")
                }
                timeoutTimer?.schedule(object : TimerTask() {
                    override fun run() {
                        if (kProfileTimeouts) {
                            log.info("Fire timeout: " + identifier + ". elapsed millis:" + (System.currentTimeMillis() - timeoutStartingMillis))
                        }

                        result(kPeripheralReadTimeoutError, null)
                        timeoutAction(identifier)
                    }
                }, timeout.toLong())
            }
        }
    }

    private fun getCaptureHandlerIndex(identifier: String): Int {
        return captureReadHandlers.indexOfFirst { it.identifier == identifier }       // returns -1 if not found
    }

    /*
    private val mTimeoutRemoveCaptureHandler =
        object : CaptureReadCompletionHandler.TimeoutAction {        // Default behaviour for a capture handler timeout
            override fun execute(identifier: String) {
                // Remove capture handler
                val captureHandlerIndex = getCaptureHandlerIndex(identifier)
                if (captureHandlerIndex >= 0) {
                    mCaptureReadHandlers.removeAt(captureHandlerIndex)
                }
                finishExecutingCommand(kPeripheralReadTimeoutError)
            }
        }

    internal class BleCommandCaptureReadParameters(
        var readIdentifier: String,
        var completionHandler: CaptureReadCompletionHandler?,
        var timeout: Int
    )
*/
    // endregion
}