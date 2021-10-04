package pjwstk.s18749.extd

import java.io.Serializable

data class Server (
    var ip: String,
    val name: String,
    var available: Boolean
) : Serializable, Comparable<Server> {
    override fun compareTo(other: Server): Int {
        return (other.name).compareTo(this.name)
    }
}