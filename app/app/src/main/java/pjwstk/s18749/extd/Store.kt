package pjwstk.s18749.extd

import android.content.Context
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class Store (
        private val fileName: String = "serverList.ser"
) {
    fun persist(context:Context, map : MutableList<Server>) {
        ObjectOutputStream(context.openFileOutput(fileName, Context.MODE_PRIVATE)).use {
            try {
                it.writeObject(map)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun load(context:Context) : ArrayList<Server> {
        val file = File(fileName)
        if (file.exists()) {
            ObjectInputStream(context.openFileInput(fileName)).use {
                try {
                    return it.readObject() as ArrayList<Server>
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return ArrayList()
    }
}