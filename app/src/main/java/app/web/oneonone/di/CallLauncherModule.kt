package app.web.oneonone.di

import android.content.Context
import android.widget.Toast
import app.web.oneonone.call.CallKind
import app.web.oneonone.call.CallLauncher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module @InstallIn(SingletonComponent::class)
object CallLauncherModule {
    @Provides fun launcher(@ApplicationContext context: Context): CallLauncher = object : CallLauncher {
        override fun start(kind: CallKind) {
            Toast.makeText(context, "Calls coming soon", Toast.LENGTH_SHORT).show()
        }
    }
}
