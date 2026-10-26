package dev.broadband.toolbox

import android.app.Application
import android.content.Context
import rikka.shizuku.ShizukuProvider

class BroadbandApplication : Application() {
    override fun attachBaseContext(base: Context) {
        ShizukuProvider.disableAutomaticSuiInitialization()
        super.attachBaseContext(base)
    }
}
