package app.web.oneonone.di

import app.web.oneonone.call.CallLauncher
import app.web.oneonone.call.CallManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module @InstallIn(SingletonComponent::class)
object CallLauncherModule {
    @Provides fun launcher(calls: CallManager): CallLauncher = calls
}
