package com.parentstracker.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.parentstracker.app.notify.Notifications

class ParentsTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        container = AppContainer(this)
        container.start()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = container.onForegroundChanged(true)
            override fun onStop(owner: LifecycleOwner) = container.onForegroundChanged(false)
        })
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ParentsTrackerApp).container
