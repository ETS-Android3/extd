package pjwstk.s18749.extd

import java.io.*

class ConnectionStore(
    private val store: File
) {
    fun save(connections: List<ConnectionListItem>?) {
        if (connections != null) {
            ObjectOutputStream(FileOutputStream(store)).use { it.writeObject(connections) }
        }
    }


    fun read(): List<ConnectionListItem>? {
        if (store.exists()) {
            try {
                ObjectInputStream(FileInputStream(store)).use {
                    return when (val connections = it.readObject()) {
                        is List<*> -> connections as List<ConnectionListItem>
                        else -> null
                    }
                }
            } catch (e: InvalidClassException) {
                throw RuntimeException("could not load saved connections ${e.message}")
            }
        }

        return null
    }
}