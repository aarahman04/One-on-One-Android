package app.web.oneonone

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import dagger.hilt.android.HiltAndroidApp
import app.web.oneonone.push.NotificationChannels
import app.web.oneonone.push.PushRegistration
import javax.inject.Inject

@HiltAndroidApp
class OneOnOneApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var pushRegistration: PushRegistration
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        pushRegistration.start()
    }
    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    override fun newImageLoader(context: Context) = ImageLoader.Builder(context).components {
        add(OkHttpNetworkFetcherFactory(callFactory = {
            // Media/map requests never use the authenticated API interceptor.
            OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "OneOnOneAndroid/${BuildConfig.VERSION_NAME} (+https://github.com/aarahman04/One-on-One-Android)").build())
            }.build()
        }, cacheStrategy = { CacheControlCacheStrategy() }))
    }.build()
}
