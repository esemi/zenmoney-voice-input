package dev.esemi.zmvoice

import android.app.Application

class ZmVoiceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
