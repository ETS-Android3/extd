package pjwstk.s18749.extd

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*
import kotlin.collections.ArrayList

class Util {
    companion object {
        fun randomPass(len: Int): String {
            val str = "abcdefghijklmnopqrstuvwxyzABCD@$#*123456789"
            var password = ""

            for (i in 1..len) {
                password += str.random()
            }

            return password
        }

        fun sameNetwork(ip: String, subnet: String): Boolean {
            val split = subnet.split("/")
            if (split.size != 2) return false

            val ip = InetAddress.getByName(ip).address
            val subnet = InetAddress.getByName(split[0]).address

            val ipInt = ip[0].toInt() and 0xFF shl 24 or
                    (ip[1].toInt() and 0xFF shl 16) or
                    (ip[2].toInt() and 0xFF shl 8) or
                    (ip[3].toInt() and 0xFF shl 0)
            val subnetInt = subnet[0].toInt() and 0xFF shl 24 or
                    (subnet[1].toInt() and 0xFF shl 16) or
                    (subnet[2].toInt() and 0xFF shl 8) or
                    (subnet[3].toInt() and 0xFF shl 0)

            var bits: Int

            try {
                bits = Integer.parseInt(split[1])
            } catch (e: NumberFormatException) {
                return false
            }

            val mask = -1 shl (32 - bits)

            return (subnetInt and mask) == (ipInt and mask)
        }

        fun onlineNotCellular(connectivityManager: ConnectivityManager): Boolean {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

            if (capabilities != null) {
                when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                        return false
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                        return true
                    }
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                        return true
                    }
                }
            }

            return false
        }

        fun getIPAddresses(): List<String> {
            var list: ArrayList<String> = ArrayList()

            try {
                val interfaces: List<NetworkInterface> =
                    Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val mask = intf.interfaceAddresses[1].networkPrefixLength
                    val addrs: List<InetAddress> = Collections.list(intf.inetAddresses)

                    if (intf.isUp) {
                        for (addr in addrs) {
                            if (!addr.isLoopbackAddress) {
                                val sAddr: String = addr.hostAddress
                                val isIPv4 = sAddr.indexOf(':') < 0

                                if (isIPv4) list.add("$sAddr/$mask")
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {
            }

            return list
        }
    }
}