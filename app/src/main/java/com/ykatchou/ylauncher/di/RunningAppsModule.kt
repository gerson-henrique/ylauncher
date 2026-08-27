package com.ykatchou.ylauncher.di

import com.ykatchou.ylauncher.data.running.AdaptiveRunningApps
import com.ykatchou.ylauncher.data.running.RunningAppsSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the adaptive source, which picks between the privileged and usage-stats grades per call.
 * Keeping that choice behind one binding means the rest of the app never branches on which one
 * is live.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RunningAppsModule {

    @Binds
    @Singleton
    abstract fun bindRunningAppsSource(impl: AdaptiveRunningApps): RunningAppsSource
}
