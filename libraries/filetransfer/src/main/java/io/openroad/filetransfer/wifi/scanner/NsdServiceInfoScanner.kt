package io.openroad.filetransfer.wifi.scanner

/**
 * Created by Antonio García (antonio@openroad.es)
 */

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.ResolveListener
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.adafruit.glider.utils.LogUtils
import io.openroad.filetransfer.ble.utils.LogHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/*
    NSD scanner
    @param serviceType: string containing the protocol and transport layer for this service.
 */
class NsdServiceInfoScanner(
    context: Context,
    private val serviceType: String,
) {
    companion object {
        private val log by LogUtils()

        init {
            log.addHandler(LogHandler())
        }
    }

    // Data - Private
    private var nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // To avoid "error 3" when resolving multiple services concurrently, used a fix based on https://stackoverflow.com/questions/616484/how-to-use-concurrentlinkedqueue
    private var isResolving = AtomicBoolean(false)
    private var pendingDiscoveriesToResolve = ConcurrentLinkedQueue<NsdServiceInfo>()

    // Data structures
    data class NsdScanResult(
        val info: NsdServiceInfo,
        val isLost: Boolean = false,            // if true the service has been lost
    )

    // Data
    var isScanning = false; private set


    // region Flow
    val nsdServiceInfoFlow: Flow<NsdScanResult> = callbackFlow {
        nsdManager?.let { nsdManager ->

            val discoveryListener = object : NsdManager.DiscoveryListener {
                // Called as soon as service discovery begins.
                override fun onDiscoveryStarted(regType: String) {
                    log.info("Network service discovery started for $regType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    // Check that is a valid serviceType
                    if (serviceInfo.serviceType != serviceType) {
                        log.warning("Unknown network service type found: ${serviceInfo.serviceType}")
                        return
                    }

                    log.info("Network service discovery success: $serviceInfo. Resolving...")

                    // Resolve if not already resolving
                    if (isResolving.compareAndSet(false, true)) {
                        nsdManager.resolveService(serviceInfo, resolveListener)
                    }
                    else {
                        // Add to pending resolves
                        pendingDiscoveriesToResolve.add(serviceInfo)
                    }
                }

                private var resolveListener: ResolveListener = object : ResolveListener {
                    override fun onResolveFailed(
                        serviceInfo: NsdServiceInfo?,
                        errorCode: Int
                    ) {
                        log.severe("Resolve failed: $errorCode")

                        // Process the next service waiting to be resolved
                        resolveNextInQueue()
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                        if (serviceInfo != null) {
                            log.info("Network service resolve success: $serviceInfo")

                            trySend(NsdScanResult(serviceInfo))
                                .onFailure {
                                    log.warning("nsdServiceInfoFlow failure for $serviceInfo")
                                }

                            // Process the next service waiting to be resolved
                            resolveNextInQueue()
                        }
                    }
                }

                private fun resolveNextInQueue() {
                    val serviceInfo = pendingDiscoveriesToResolve.poll()
                    if (serviceInfo != null) {
                        nsdManager.resolveService(serviceInfo, resolveListener)
                    }
                    else {
                        isResolving.set(false)
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    // When the network service is no longer available.
                    log.warning("Network service lost: $serviceInfo")

                    // If it was pending to be resolved, remove it from the queue
                    pendingDiscoveriesToResolve.removeIf { pendingServiceInfo ->
                        pendingServiceInfo.serviceName == serviceInfo.serviceName
                    }

                    // Send it
                    trySend(NsdScanResult(serviceInfo, isLost = true))
                        .onFailure {
                            log.warning("nsdServiceInfoFlow failure for $serviceInfo")
                        }
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    log.warning("Discovery stopped for: $serviceType")
                    isScanning = false
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    log.warning("Start discovery failed: Error code: $errorCode")
                    nsdManager.stopServiceDiscovery(this)
                    isScanning = false
                    cancel("Resolve failed", NsdScanException(errorCode))
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    log.warning("Stop discovery failed: Error code: $errorCode")
                    nsdManager.stopServiceDiscovery(this)
                }
            }

            // Multicast permission due to some bugs with NsdManager: https://stackoverflow.com/questions/53615125/nsdmanager-discovery-does-not-work-on-android-9
            val multicastLock = wifiManager.createMulticastLock("multicastLock")
            multicastLock.setReferenceCounted(true)
            multicastLock.acquire()

            // Start discovery
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            isScanning = true

            awaitClose {
                nsdManager.stopServiceDiscovery(discoveryListener)
                isScanning = false

                multicastLock.release()
                //log.info("nsdServiceInfoFlow finished")
            }
        } ?: run {
            cancel("nsdServiceInfoFlow cannot start", NsdException("NSD service cannot start"))
        }
    }.flowOn(Dispatchers.Main.immediate)        // Call startScan on MainThread

    // endregion

}