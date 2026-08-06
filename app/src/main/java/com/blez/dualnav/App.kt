package com.blez.dualnav

import android.app.Application
import com.blez.dualnav.di.appModule
import com.blez.dualnav.di.companionModule
import com.blez.dualnav.di.connectionModule
import com.blez.dualnav.di.coreDataModule
import com.blez.dualnav.di.presentationModule
import com.blez.dualnav.di.updateModule
import com.blez.dualnav.di.useCaseModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@App)
            modules(
                appModule,
                coreDataModule,
                connectionModule,
                useCaseModule,
                companionModule,
                updateModule,
                presentationModule
            )
        }
    }
}
