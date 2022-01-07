package pjwstk.s18749.extd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.widget.Toast

class NsdService (
    private val nsdManager: NsdManager,
    private val context: Context
) : NsdManager.DiscoveryListener {

    override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
        TODO("Not yet implemented")
    }

    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
        Toast.makeText(context, "Could not stop discovery", Toast.LENGTH_LONG).show()
    }

    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
        Toast.makeText(context, "Could not start discovery", Toast.LENGTH_LONG).show()
    }

    override fun onDiscoveryStarted(serviceType: String?) {
        Toast.makeText(context, "Started discovery", Toast.LENGTH_LONG).show()
    }

    override fun onDiscoveryStopped(serviceType: String?) {
        Toast.makeText(context, "Stopped discovery", Toast.LENGTH_LONG).show()
    }

    override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
        TODO("Not yet implemented")
    }

    public fun discover() {
        nsdManager.discoverServices("_services._dns-sd._udp", NsdManager.PROTOCOL_DNS_SD, this)
    }

    public fun stopDiscovery() {
        nsdManager.stopServiceDiscovery(this)
    }
}