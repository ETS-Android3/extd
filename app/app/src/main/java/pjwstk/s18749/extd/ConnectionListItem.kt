package pjwstk.s18749.extd

import java.io.Serializable

data class ConnectionListItem(
    var isOpen: Boolean,
    val connection: Connection
) :Comparable<ConnectionListItem>, Serializable {
    override fun compareTo(other: ConnectionListItem): Int {
        val open = isOpen.compareTo(other.isOpen)

        if (open == 0) {
            return connection.compareTo(other.connection)
        }

        return open
    }
}