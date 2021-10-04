package pjwstk.s18749.extd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.ArrayList

// helper class from https://stackoverflow.com/a/57998099
abstract class NsdHelper(
    private val context: Context
) {
    var isDiscovering: Boolean = false
    // Declare DNS-SD related variables for service discovery
    val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as NsdManager?
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null
    private var resolveListenerBusy = AtomicBoolean(false)
    private var pendingNsdServices = ConcurrentLinkedQueue<NsdServiceInfo>()
    var resolvedNsdServices: MutableList<NsdServiceInfo> = Collections.synchronizedList(ArrayList<NsdServiceInfo>())

    companion object {

        // Type of services to look for
        const val NSD_SERVICE_TYPE: String = "_ssh._tcp."
        // Services' Names must start with this
        const val NSD_SERVICE_NAME: String = ""
    }

    // Initialize Listeners
    fun initializeNsd() {
        // Initialize only resolve listener
        initializeResolveListener()
    }

    // Instantiate DNS-SD discovery listener
    // used to discover available Sonata audio servers on the same network
    private fun initializeDiscoveryListener() {

        // Instantiate a new DiscoveryListener
        discoveryListener = object : NsdManager.DiscoveryListener {

            override fun onDiscoveryStarted(regType: String) {
                // Called as soon as service discovery begins.
                isDiscovering = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // A service was found! Do something with it

                if ( service.serviceType == NSD_SERVICE_TYPE &&
                    service.serviceName.startsWith(NSD_SERVICE_NAME) ) {
                    // Both service type and service name are the ones we want
                    // If the resolver is free, resolve the service to get all the details
                    if (resolveListenerBusy.compareAndSet(false, true)) {
                        nsdManager?.resolveService(service, resolveListener)
                    }
                    else {
                        // Resolver was busy. Add the service to the list of pending services
                        pendingNsdServices.add(service)
                    }
                }
                else {
                    // Not our service. Log message but do nothing else
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {

                // If the lost service was in the queue of pending services, remove it
                var iterator = pendingNsdServices.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().serviceName == service.serviceName)
                        iterator.remove()
                }

                // If the lost service was in the list of resolved services, remove it
                synchronized(resolvedNsdServices) {
                    iterator = resolvedNsdServices.iterator()
                    while (iterator.hasNext()) {
                        if (iterator.next().serviceName == service.serviceName)
                            iterator.remove()
                    }
                }

                // Do the rest of the processing for the lost service
                onNsdServiceLost(service)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                nsdManager?.stopServiceDiscovery(this)
            }
        }
    }

    // Instantiate DNS-SD resolve listener to get extra information about the service
    private fun initializeResolveListener() {
        resolveListener =  object : NsdManager.ResolveListener {

            override fun onServiceResolved(service: NsdServiceInfo) {

                // Register the newly resolved service into our list of resolved services
                resolvedNsdServices.add(service)

                // Process the newly resolved service
                onNsdServiceResolved(service)

                // Process the next service waiting to be resolved
                resolveNextInQueue()
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Called when the resolve fails. Use the error code to debug.

                // Process the next service waiting to be resolved
                resolveNextInQueue()
            }
        }
    }

    // Start discovering services on the network
    fun discoverServices() {
        // Cancel any existing discovery request
        stopDiscovery()

        initializeDiscoveryListener()

        // Start looking for available audio channels in the network
        nsdManager?.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    // Stop DNS-SD service discovery
    fun stopDiscovery() {
        if (discoveryListener != null) {
            try {
                nsdManager?.stopServiceDiscovery(discoveryListener)
            } finally {
            }
            discoveryListener = null
        }
    }

    // Resolve next NSD service pending resolution
    private fun resolveNextInQueue() {
        // Get the next NSD service waiting to be resolved from the queue
        val nextNsdService = pendingNsdServices.poll()
        if (nextNsdService != null) {
            // There was one. Send to be resolved.
            nsdManager?.resolveService(nextNsdService, resolveListener)
        }
        else {
            // There was no pending service. Release the flag
            resolveListenerBusy.set(false)
        }
    }

    // Function to be overriden with custom logic for new service resolved
    abstract fun onNsdServiceResolved(service: NsdServiceInfo)

    // Function to be overriden with custom logic for service lost
    abstract fun onNsdServiceLost(service: NsdServiceInfo)
}