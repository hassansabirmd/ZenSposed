package com.hassan.zensposed

import android.app.Application
import com.hassan.zensposed.data.db.AppDatabase
import com.hassan.zensposed.data.prefs.SecurePrefs
import com.hassan.zensposed.data.settings.SettingsRepository

class ZenSposedApp : Application() {

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val securePrefs: SecurePrefs by lazy { SecurePrefs(this) }
    val database: AppDatabase by lazy { AppDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: ZenSposedApp
            private set
    }
}
