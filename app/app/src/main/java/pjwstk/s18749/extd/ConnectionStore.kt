package pjwstk.s18749.extd

import java.io.*

class ConnectionStore(
    private val store: File
) {
    fun save(connections: List<Connection>) {
        ObjectOutputStream(FileOutputStream(store)).use{ it.writeObject(connections) }
    }


    fun read(): List<Connection>? {
        var list: List<Connection>? = null

        if (store.exists()) {
            ObjectInputStream(FileInputStream(store)).use {
                list = when (val connections = it.readObject()) {
                    is List<*> -> connections as List<Connection>
                    else -> null
                }
            }
        }

        return list
    }
}