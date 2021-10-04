package pjwstk.s18749.extd

import android.app.Application
import android.content.Context

class AppContextProvider : Application() {

    init { INSTANCE = this }

    companion object {
        lateinit var INSTANCE: AppContextProvider
            private set

        val applicationContext: Context get() { return INSTANCE.applicationContext }
    }
}