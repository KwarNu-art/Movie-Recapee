package com.movierecapee

import android.app.Application
import com.movierecapee.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MovieRecapeeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MovieRecapeeApp)
            modules(appModule)
        }
    }
}
