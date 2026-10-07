package app.web.oneonone.data.realtime

import app.web.oneonone.BuildConfig
import app.web.oneonone.data.auth.AccountSession
import io.socket.client.AckWithTimeout
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class ConnectionState { Connected, Connecting, Offline }

/** Frozen A2 seam. This is the ONLY class that creates a Socket.IO connection.
 * Message transport and Claude's call signaling share it. Each attempt reads fresh auth.
 * start/stop are owned by MessageService; feature consumers only subscribe/emit.
 */
@Singleton
class RealtimeSocket @Inject constructor(private val auth: AccountSession, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(ConnectionState.Offline)
    val state: StateFlow<ConnectionState> = mutableState.asStateFlow()
    private val authData = ConcurrentHashMap<String, String>()
    @Volatile private var connectError: String? = null
    private val socketDelegate = lazy {
        val options = IO.Options().apply {
            auth = authData
            reconnection = false // Our loop awaits fresh async auth before every attempt.
            forceNew = true
            timeout = 15_000
        }
        IO.socket(BuildConfig.API_URL, options).also { socket ->
            socket.on(Socket.EVENT_CONNECT) { mutableState.value = ConnectionState.Connected }
            socket.on(Socket.EVENT_DISCONNECT) { mutableState.value = ConnectionState.Offline }
            socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
                connectError = (args.firstOrNull() as? JSONObject)?.optString("message") ?: args.firstOrNull()?.toString()
                mutableState.value = ConnectionState.Offline
            }
        }
    }
    private val socket: Socket by socketDelegate
    private var connectionJob: Job? = null
    private val lifecycle = Mutex()

    suspend fun start() = lifecycle.withLock {
        if (connectionJob?.isActive == true) return@withLock
        connectionJob = scope.launch {
            var refreshed = false
            while (isActive) {
                try {
                    val token = auth.accessToken() ?: break
                    authData["token"] = token
                    connectError = null
                    mutableState.value = ConnectionState.Connecting
                    socket.connect()
                    val connected = withTimeout(15_000) { state.first { it != ConnectionState.Connecting } }
                    if (connected == ConnectionState.Connected) {
                        refreshed = false
                        state.first { it != ConnectionState.Connected }
                    } else if (connectError?.contains("invalid or expired token") == true) {
                        if (refreshed) { auth.invalidate(token); break }
                        auth.refreshToken(token) ?: break
                        refreshed = true
                    }
                } catch (error: CancellationException) {
                    if (error !is TimeoutCancellationException) throw error
                } catch (_: Exception) {
                    // Retry network/auth availability; do not log tokens or payloads.
                }
                socket.disconnect()
                mutableState.value = ConnectionState.Offline
                delay(2_000)
            }
            mutableState.value = ConnectionState.Offline
        }
    }

    suspend fun stop() = lifecycle.withLock {
        connectionJob?.cancelAndJoin()
        connectionJob = null
        if (socketDelegate.isInitialized()) socket.disconnect()
        authData.clear()
        mutableState.value = ConnectionState.Offline
    }

    fun events(name: String): Flow<JSONObject> = callbackFlow {
        val listener = Emitter.Listener { args ->
            // connection:ended has no arguments in the authoritative contract.
            trySend(args.firstOrNull() as? JSONObject ?: JSONObject())
        }
        socket.on(name, listener)
        awaitClose { socket.off(name, listener) }
    // ponytail: buffer server-limited events until Room consumes them; add overflow resync if sustained traffic grows.
    }.buffer(Channel.UNLIMITED)

    suspend fun emitWithAck(name: String, payload: JSONObject, timeoutMs: Long = 10_000): JSONObject {
        require(timeoutMs > 0)
        if (!socket.connected()) throw IOException("Waiting for connection.")
        val result = CompletableDeferred<JSONObject>()
        val ack = object : AckWithTimeout(timeoutMs) {
            override fun onSuccess(vararg args: Any?) {
                val response = args.firstOrNull() as? JSONObject
                if (response != null) result.complete(response)
                else result.completeExceptionally(IOException("Invalid server acknowledgement."))
            }
            override fun onTimeout() {
                result.completeExceptionally(IOException("Send timed out. Retry with the same ID."))
            }
        }
        // AckWithTimeout removes its callback/buffer entry on timeout or disconnect.
        socket.emit(name, payload, ack)
        return try { result.await() } finally { result.cancel() }
    }

    fun emit(name: String, payload: JSONObject) {
        if (socket.connected()) socket.emit(name, payload)
    }
}
