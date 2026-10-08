package com.ykatchou.ylauncher

import android.app.Application
import com.ykatchou.ylauncher.util.CrashHandler
import com.ykatchou.ylauncher.data.ponte.Ponte
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YLauncherApp : Application() {

    @Inject lateinit var ponte: Ponte

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(this, Thread.getDefaultUncaughtExceptionHandler())
        )
        ponte.start()
    }
}
