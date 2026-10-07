package app.web.oneonone.di

import app.web.oneonone.push.handlers.AlarmPushHandler
import app.web.oneonone.push.handlers.CallPushHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module @InstallIn(SingletonComponent::class)
object PushHandlersModule {
    @Provides fun alarm(): AlarmPushHandler = object : AlarmPushHandler {
        override fun onAlarmPush(data: Map<String, String>) { }
    }
    @Provides fun call(): CallPushHandler = object : CallPushHandler {
        override fun onCallPush(data: Map<String, String>) { }
        override fun onCallEndPush(data: Map<String, String>) { }
    }
}
