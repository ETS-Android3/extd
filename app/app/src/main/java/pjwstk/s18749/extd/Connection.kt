package pjwstk.s18749.extd

import java.io.Serializable
import java.util.Date

data class Connection(
    val name: String,
    val ip: String,
    val originalIp: String,
    val port: Int,
    val daemonPort: Int,
    val secret: String,
    val password: String,
    val createdAt: Date
) : Serializable, Comparable<Connection> {
    val id: Long = System.currentTimeMillis()
    var lastConnected: Date? = null

    override fun compareTo(other: Connection): Int {
        return "$id-$name-$ip-$originalIp-$port-$secret-$password-${createdAt.toString()}".compareTo("${other.id}-${other.name}-${other.ip}-${other.originalIp}-${other.port}-${other.secret}-${other.password}-${other.createdAt.toString()}")
    }
}