package app.web.oneonone

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import app.web.oneonone.push.NotificationChannels
import app.web.oneonone.push.PushRegistration
import javax.inject.Inject

@HiltAndroidApp
class OneOnOneApplication : Application() {
    @Inject lateinit var pushRegistration: PushRegistration
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.create(this)
        pushRegistration.start()
    }
}
