package com.ykatchou.ylauncher.di

import com.ykatchou.ylauncher.data.running.RunningAppsSource
import com.ykatchou.ylauncher.data.running.UsageStatsRunningApps
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Picks which grade of [RunningAppsSource] the app runs on. Only the usage-stats floor exists
 * today; the privileged source that can also close apps binds here once it lands, and the choice
 * between them belongs in this one place so the rest of the app never branches on it.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RunningAppsModule {

    @Binds
    @Singleton
    abstract fun bindRunningAppsSource(impl: UsageStatsRunningApps): RunningAppsSource
}
