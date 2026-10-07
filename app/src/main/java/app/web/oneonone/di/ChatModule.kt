package app.web.oneonone.di

import android.content.Context
import androidx.room.Room
import app.web.oneonone.data.chat.ChatDatabase
import app.web.oneonone.data.chat.MessageStore
import app.web.oneonone.data.chat.RoomMessageStore
import app.web.oneonone.data.transport.ChatApi
import app.web.oneonone.data.transport.InternetTransport
import app.web.oneonone.data.transport.Transport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import retrofit2.Retrofit
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object ChatModule {
    @Provides @Singleton fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Provides @Singleton fun database(@ApplicationContext context: Context): ChatDatabase =
        Room.databaseBuilder(context, ChatDatabase::class.java, "chat.db").build()
    @Provides fun store(store: RoomMessageStore): MessageStore = store
    @Provides fun transport(transport: InternetTransport): Transport = transport
    @Provides @Singleton fun api(retrofit: Retrofit): ChatApi = retrofit.create(ChatApi::class.java)
}
