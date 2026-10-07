# Retrofit generic suspend responses, serialization metadata and Hilt entry points.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Protocol/JNI/engine boundaries: keep names and members while permitting optimization.
# Revisit only after authenticated minified builds exercise these SDK paths on-device.
-keep,allowoptimization class io.socket.** { *; }
-keep,allowoptimization class org.webrtc.** { *; }
-keep,allowoptimization class io.github.jan.supabase.** { *; }
-keep,allowoptimization class io.ktor.** { *; }
-keep,allowoptimization @kotlinx.serialization.Serializable class app.web.oneonone.** { *; }
-keep,allowoptimization class app.web.oneonone.**$$serializer { *; }
-keep,allowoptimization @dagger.hilt.EntryPoint interface app.web.oneonone.** { *; }
# Hilt, WorkManager, Room, Retrofit and kotlinx.serialization also supply consumer rules.
# No blanket -dontwarn: unresolved dependencies must fail release validation.
# Ktor 3.6's IntelliJ debugger detector catches Throwable and returns false on Android.
# These desktop-only JMX types are absent by design (verified in the pinned bytecode).
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
