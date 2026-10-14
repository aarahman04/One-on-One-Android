package app.web.oneonone

import android.app.Application
import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import dagger.hilt.android.HiltAndroidApp
import app.web.oneonone.alarm.AlarmCoordinator
import app.web.oneonone.call.CallManager
import app.web.oneonone.push.NotificationChannels
import app.web.oneonone.push.PushRegistration
import javax.inject.Inject

@HiltAndroidApp
class OneOnOneApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var pushRegistration: PushRegistration
    @Inject lateinit var alarms: AlarmCoordinator
    @Inject lateinit var calls: CallManager
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        pushRegistration.start()
        alarms.start()
        calls.listen()
    }
    @OptIn(coil3.annotation.ExperimentalCoilApi::class)
    override fun newImageLoader(context: Context) = ImageLoader.Builder(context).components {
        // GIF messages animate (coil-gif was installed but unregistered, so they showed one still frame).
        add(if (Build.VERSION.SDK_INT >= 28) AnimatedImageDecoder.Factory() else GifDecoder.Factory())
        add(OkHttpNetworkFetcherFactory(callFactory = {
            // Media/map requests never use the authenticated API interceptor.
            OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "OneOnOneAndroid/${BuildConfig.VERSION_NAME} (+https://github.com/aarahman04/One-on-One-Android)").build())
            }.build()
        }, cacheStrategy = { CacheControlCacheStrategy() }))
    }.build()
}
