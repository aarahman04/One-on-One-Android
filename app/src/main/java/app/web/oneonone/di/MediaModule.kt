package app.web.oneonone.di

import app.web.oneonone.data.media.AttachmentApi
import app.web.oneonone.data.media.MediaGateway
import app.web.oneonone.data.media.MediaRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object MediaModule {
    @Provides @Singleton fun api(retrofit: Retrofit, client: OkHttpClient): AttachmentApi = retrofit.newBuilder()
        .client(client.newBuilder().callTimeout(90, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS).build()).build().create(AttachmentApi::class.java)
    @Provides fun media(repository: MediaRepository): MediaGateway = repository
}
