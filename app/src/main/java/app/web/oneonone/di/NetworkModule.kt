package app.web.oneonone.di

import app.web.oneonone.BuildConfig
import app.web.oneonone.data.api.AccountApi
import app.web.oneonone.data.api.BearerInterceptor
import app.web.oneonone.data.auth.AuthRepository
import app.web.oneonone.data.auth.AccountSession
import app.web.oneonone.data.DevicePreferences
import app.web.oneonone.data.DeviceStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides fun session(auth: AuthRepository): AccountSession = auth
    @Provides fun deviceStore(preferences: DevicePreferences): DeviceStore = preferences
    @Provides @Singleton fun json(): Json = Json { ignoreUnknownKeys = true }
    @Provides @Singleton fun client(auth: AuthRepository): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BearerInterceptor(auth))
        .followRedirects(false).followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build()
    @Provides @Singleton fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        // Secret-free CI is installable; auth reports missing configuration before any request.
        .baseUrl(BuildConfig.API_URL.takeIf { it.startsWith("https://") }?.trimEnd('/')?.plus('/')
            ?: "https://localhost/")
        .client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    @Provides @Singleton fun accountApi(retrofit: Retrofit): AccountApi = retrofit.create(AccountApi::class.java)
}
