package pjwstk.s18749.extd

import java.io.Serializable
import java.util.*

data class Connection(
        val name: String,
        val ip: String,
        val port: Int,
        val daemonPort: Int,
        val secret: String,
        val password: String,
        val createdAt: Date
) : Serializable, Comparable<Connection> {
    val id: Long = System.currentTimeMillis()
    var lastConnected: Date? = null
    var isAvailable: Boolean = false
    var isFromSameNetwork: Boolean = false

    override fun compareTo(other: Connection): Int {
        return "$id-$name-$ip-$port-$secret-$password-$createdAt-$isAvailable-$isFromSameNetwork-$lastConnected"
                .compareTo("${other.id}-${other.name}-${other.ip}-${other.port}-${other.secret}-${other.password}-${other.createdAt}-${other.isAvailable}-${other.isFromSameNetwork}-${other.lastConnected}")
    }
}