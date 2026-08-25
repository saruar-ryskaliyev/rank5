package io.rank5.app.net

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

class GameClient(
    private val baseUrl: String,
    private val scope: CoroutineScope,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(json)
        }
    }

    private val outgoing = Channel<String>(Channel.BUFFERED)
    private var sessionJob: Job? = null
    @Volatile private var leavingRoom = false

    private val _events = MutableSharedFlow<ClientEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ClientEvent> = _events.asSharedFlow()

    var playerId: String? = null
        private set
    var reconnectToken: String? = null
        private set
    var roomCode: String? = null
        private set

    suspend fun createRoom(): String {
        val resp: CreateRoomResponse = http.post("$baseUrl/rooms").body()
        return resp.code
    }

    fun connect(code: String, nickname: String, authToken: String? = null, reconnect: Boolean = false) {
        leavingRoom = false
        roomCode = code.uppercase()
        sessionJob?.cancel()
        sessionJob = scope.launch {
            var attempt = 0
            while (isActive && !leavingRoom) {
                try {
                    connectOnce(
                        code = code.uppercase(),
                        nickname = nickname,
                        authToken = authToken,
                        doReconnect = reconnect || (playerId != null),
                    )
                    attempt = 0
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    _events.emit(ClientEvent.ConnectionError(e.message ?: "connection failed"))
                    attempt++
                    delay((1000L * attempt).coerceAtMost(8000L))
                }
            }
        }
    }

    private suspend fun connectOnce(
        code: String,
        nickname: String,
        authToken: String?,
        doReconnect: Boolean,
    ) {
        val wsBase = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        http.webSocket("$wsBase/ws?code=$code") {
            _events.emit(ClientEvent.Connected)
            val writer = launch { writeLoop(this@webSocket) }
            val reader = launch { readLoop(this@webSocket) }

            if (doReconnect && playerId != null && reconnectToken != null) {
                sendTyped(MsgType.RECONNECT, ReconnectPayload(playerId!!, reconnectToken!!))
            } else {
                sendTyped(
                    MsgType.JOIN_ROOM,
                    JoinRoomPayload(nickname = nickname, authToken = authToken),
                )
            }

            reader.join()
            writer.cancel()
        }
    }

    private suspend fun writeLoop(session: DefaultClientWebSocketSession) {
        for (msg in outgoing) {
            session.send(Frame.Text(msg))
        }
    }

    private suspend fun readLoop(session: DefaultClientWebSocketSession) {
        for (frame in session.incoming) {
            if (frame !is Frame.Text) continue
            val text = frame.readText()
            val env = json.decodeFromString<Envelope>(text)
            when (env.type) {
                MsgType.WELCOME -> {
                    val w = json.decodeFromJsonElement<WelcomePayload>(env.payload!!)
                    playerId = w.playerId
                    reconnectToken = w.reconnectToken
                    _events.emit(ClientEvent.Welcome(w))
                }
                MsgType.ROOM_STATE -> {
                    val s = json.decodeFromJsonElement<RoomStateView>(env.payload!!)
                    _events.emit(ClientEvent.RoomState(s))
                }
                MsgType.ERROR -> {
                    val e = json.decodeFromJsonElement<ErrorPayload>(env.payload!!)
                    _events.emit(ClientEvent.ServerError(e.message))
                }
            }
        }
    }

    suspend fun sendTyped(type: String, payload: Any?) {
        val element = when (payload) {
            null -> null
            is JoinRoomPayload -> json.encodeToJsonElement(payload)
            is ReconnectPayload -> json.encodeToJsonElement(payload)
            is AttachAccountPayload -> json.encodeToJsonElement(payload)
            is StartGamePayload -> json.encodeToJsonElement(payload)
            is RankingPayload -> json.encodeToJsonElement(payload)
            is SkipQuestionPayload -> json.encodeToJsonElement(payload)
            else -> null
        }
        val env = Envelope(type = type, payload = element)
        outgoing.send(json.encodeToString(env))
    }

    fun startGame(
        deckIds: List<String>,
        rounds: Int,
    ) {
        scope.launch {
            sendTyped(
                MsgType.START_GAME,
                StartGamePayload(deckIds = deckIds, rounds = rounds),
            )
        }
    }

    fun updateGameSettings(
        deckIds: List<String>,
        rounds: Int,
    ) {
        scope.launch {
            sendTyped(
                MsgType.UPDATE_GAME_SETTINGS,
                UpdateGameSettingsPayload(
                    deckIds = deckIds,
                    rounds = rounds,
                ),
            )
        }
    }

    fun attachAccount(authToken: String) {
        if (sessionJob?.isActive != true || playerId == null) return
        scope.launch {
            sendTyped(MsgType.ATTACH_ACCOUNT, AttachAccountPayload(authToken))
        }
    }

    fun submitRanking(roundIndex: Int, questionId: String, ranking: List<String>) {
        scope.launch {
            sendTyped(MsgType.SUBMIT_RANKING, RankingPayload(roundIndex, questionId, ranking))
        }
    }

    fun skipQuestion(roundIndex: Int, questionId: String) {
        scope.launch {
            sendTyped(MsgType.SKIP_QUESTION, SkipQuestionPayload(roundIndex, questionId))
        }
    }

    fun markReady() {
        scope.launch {
            sendTyped(MsgType.READY, null)
        }
    }

    fun nextRound() {
        scope.launch {
            sendTyped(MsgType.NEXT_ROUND, null)
        }
    }

    fun leaveRoom() {
        if (sessionJob?.isActive != true || playerId == null) {
            disconnect()
            return
        }
        val leavingSession = sessionJob
        leavingRoom = true
        scope.launch {
            sendTyped(MsgType.LEAVE_ROOM, null)
            // Give the active writer a short opportunity to flush the explicit
            // leave before closing. The server still handles a dropped socket
            // safely if delivery fails.
            delay(150)
            if (sessionJob === leavingSession) disconnect()
        }
    }

    fun disconnect() {
        leavingRoom = true
        sessionJob?.cancel()
        sessionJob = null
        // Clear identity so a later "Join room" sends JOIN_ROOM, not a stale RECONNECT.
        playerId = null
        reconnectToken = null
        roomCode = null
    }

    fun close() {
        disconnect()
        http.close()
    }
}

sealed class ClientEvent {
    data object Connected : ClientEvent()
    data class Welcome(val payload: WelcomePayload) : ClientEvent()
    data class RoomState(val state: RoomStateView) : ClientEvent()
    data class ServerError(val message: String) : ClientEvent()
    data class ConnectionError(val message: String) : ClientEvent()
}
