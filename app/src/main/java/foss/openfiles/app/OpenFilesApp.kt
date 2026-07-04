package foss.openfiles.app

import android.app.Application
import foss.openfiles.app.data.Prefs

class OpenFilesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
