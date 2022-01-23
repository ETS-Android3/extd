package pjwstk.s18749.extd

import java.io.*

class ConnectionStore(
    private val store: File
) {
    fun save(connections: List<Connection>?) {
        if (connections != null) {
            ObjectOutputStream(FileOutputStream(store)).use { it.writeObject(connections) }
        }
    }


    fun read(): List<Connection>? {
        if (store.exists()) {
            try {
                ObjectInputStream(FileInputStream(store)).use {
                    return when (val connections = it.readObject()) {
                        is List<*> -> connections as List<Connection>
                        else -> null
                    }
                }
            } catch (e: InvalidClassException) {
                throw RuntimeException("could not load saved connections ${e.message}")
            }  catch (e: ClassNotFoundException) {
                throw RuntimeException("could not load saved connections ${e.message}")
            }
        }

        return null
    }
}