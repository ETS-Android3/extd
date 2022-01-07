package pjwstk.s18749.extd

import java.io.Serializable

data class Connection(
    val name: String,
    val ip: String,
    val originalIp: String,
    val port: Int,
    val secret: String,
    val password: String
) : Serializable, Comparable<Connection> {
    override fun compareTo(other: Connection): Int {
        return name.compareTo(other.name)
    }
}