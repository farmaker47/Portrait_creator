package com.soloupis.sample.photos_with_depth

import android.app.Application
import com.soloupis.sample.photos_with_depth.di.depthAndStyleModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DepthAndStyleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            //androidContext(applicationContext)
            androidContext(this@DepthAndStyleApplication)
            modules(
                depthAndStyleModule
            )
        }

    }

}