package com.dewijones92.uniapp

import android.app.Application
import com.dewijones92.uniapp.di.AppContainer
import com.dewijones92.uniapp.di.DefaultAppContainer

class UniAppApplication : Application() {
    val container: AppContainer by lazy { DefaultAppContainer(this) }
}
