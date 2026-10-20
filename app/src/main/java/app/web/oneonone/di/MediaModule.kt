package app.web.oneonone.di

import app.web.oneonone.data.media.AttachmentApi
import app.web.oneonone.data.media.MediaGateway
import app.web.oneonone.data.media.MediaRepository
import app.web.oneonone.data.media.SpeechApi
import app.web.oneonone.data.media.SpeechGateway
import app.web.oneonone.data.media.SpeechRepository
import app.web.oneonone.data.media.DictationRecorder
import app.web.oneonone.data.media.NarrationAudio
import app.web.oneonone.data.media.AndroidNarrationAudio
import app.web.oneonone.data.media.DeviceSpeech
import app.web.oneonone.data.media.AndroidDeviceSpeech
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
    @Provides fun dictationRecorder(repository: MediaRepository): DictationRecorder = repository
    @Provides fun speech(repository: SpeechRepository): SpeechGateway = repository
    @Provides fun narrationAudio(audio: AndroidNarrationAudio): NarrationAudio = audio
    @Provides fun deviceSpeech(device: AndroidDeviceSpeech): DeviceSpeech = device
    // The speech service sleeps when idle; a first request includes its wake-up and model load
    // (backend timeouts: 90 s narration part, 120 s transcription; docs SPEECH-SERVICE.md).
    @Provides @Singleton fun speechApi(retrofit: Retrofit, client: OkHttpClient): SpeechApi = retrofit.newBuilder()
        .client(client.newBuilder().callTimeout(125, TimeUnit.SECONDS).readTimeout(95, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS).build()).build().create(SpeechApi::class.java)
}
