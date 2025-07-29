package cu.uci.android.apklis_license_validator

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.random.Random

object WebSocketHolder {
    var client: WebSocketClient? = null
}

class WebSocketClient(
    private val listener: WebSocketEventListener? = null,
    private val context: Context? = null
) {
    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null
    private var pingJob: Job? = null
    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var deviceId: String? = null
    private var code: String? = null

    private val _connectionState = MutableStateFlow(ConnectionState())

    private val pendingMessages = mutableListOf<String>()

    // Reconnection parameters
    private var reconnectAttempts = 0
    private var isReconnecting = false
    private var shouldReconnect = true

    companion object {
        private const val PING_INTERVAL = 30_000L // 30 seconds
        private const val CONNECTION_TIMEOUT = 10_000L // 10 seconds
        private const val READ_TIMEOUT = 30_000L // 30 seconds
        private const val TAG = "WebSocketManager"
        private const val WS_URL = "wss://pubsub.mprc.cu"


        // Reconnection settings
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val INITIAL_RECONNECT_DELAY = 1000L // 1 second
        private const val MAX_RECONNECT_DELAY = 30000L // 30 seconds
        private const val BACKOFF_MULTIPLIER = 1.5
    }

    fun isReconnecting(): Boolean = isReconnecting

    fun init(code: String, deviceId: String) {
        this.deviceId = deviceId
        this.code = code
        this.shouldReconnect = true
        this.reconnectAttempts = 0
        initializeHttpClient()
    }

    private fun initializeHttpClient() {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
            .writeTimeout(READ_TIMEOUT, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true) // Enable automatic retry
            .build()
    }

    fun connectAndSubscribe() {
        if (_connectionState.value.isConnected) {
            Log.d(TAG, "Already connected, skipping connection attempt")
            return
        }

        if (isReconnecting) {
            Log.d(TAG, "Reconnection already in progress")
            return
        }

        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(WS_URL)
                    .build()

                webSocket = okHttpClient?.newWebSocket(request, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.i(TAG, "WebSocket connection established successfully")
                        _connectionState.value = ConnectionState(isConnected = true)
                        reconnectAttempts = 0 // Reset reconnect attempts on successful connection
                        isReconnecting = false
                        listener?.onConnected()

                        // Send CONNECT message
                        sendConnectMessage()

                        // Subscribe to the user channel after connection
                       subscribeToChannel(code ?: "", deviceId ?: "")

                        // Start ping-pong mechanism
                       startPingPong()

                        // Send any pending messages
                       sendPendingMessages()
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        handleIncomingMessage(text)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        handleIncomingMessage(bytes.utf8())
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.w(TAG, "WebSocket closing: code=$code, reason=$reason")
                        _connectionState.value = ConnectionState(isConnected = false)
                        listener?.onDisconnected(reason)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.i(TAG, "WebSocket closed: code=$code, reason=$reason")
                        cleanup()

                        // Schedule reconnection if needed
                        if (shouldReconnect && code != 1000) { // 1000 = normal closure
                            scheduleReconnect("Connection closed unexpectedly: $reason")
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WebSocket connection failed", t)

                        // Reset reconnecting flag on failure
                        isReconnecting = false

                        val errorMessage = when (t) {
                            is SocketTimeoutException -> "Connection timeout - server may be unavailable"
                            is SocketException -> when {
                                t.message?.contains("Software caused connection abort") == true ->
                                    "Network connection interrupted"
                                t.message?.contains("Connection reset") == true ->
                                    "Server connection reset"
                                else -> "Network socket error: ${t.message}"
                            }
                            else -> "Connection failed: ${t.message ?: t.javaClass.simpleName}"
                        }

                        _connectionState.value = ConnectionState(
                            isConnected = false,
                            error = errorMessage
                        )

                        listener?.onError(errorMessage)
                        cleanup()

                        // Schedule reconnection for recoverable errors
                        if (shouldReconnect && isRecoverableError(t)) {
                            scheduleReconnect(errorMessage)
                        }
                    }

                })

            } catch (e: Exception) {
                _connectionState.value = ConnectionState(
                    isConnected = false,
                    error = e.message
                )
                listener?.onError(e.message ?: "Failed to connect")

                if (shouldReconnect) {
                    scheduleReconnect("Setup failed: ${e.message}")
                }
            }
        }
    }


    private fun isRecoverableError(throwable: Throwable): Boolean {
        return when (throwable) {
            is SocketTimeoutException,
            is SocketException -> true
            else -> throwable.message?.let { message ->
                message.contains("Software caused connection abort") ||
                        message.contains("Connection reset") ||
                        message.contains("Network is unreachable") ||
                        message.contains("Connection refused")
            } ?: false
        }
    }

    private fun scheduleReconnect(reason: String) {
        if (!shouldReconnect) {
            Log.d(TAG, "Reconnection disabled, not scheduling")
            return
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Maximum reconnection attempts reached ($MAX_RECONNECT_ATTEMPTS)")
            isReconnecting = false // Reset flag when giving up
            listener?.onError("Failed to reconnect after $MAX_RECONNECT_ATTEMPTS attempts")
            return
        }


        // Cancel any existing reconnection job
        reconnectJob?.cancel()
        reconnectAttempts++
        isReconnecting = true

        // Calculate delay with exponential backoff
        val delay = minOf(
            INITIAL_RECONNECT_DELAY * BACKOFF_MULTIPLIER.pow(reconnectAttempts.toDouble()).toLong(),
            MAX_RECONNECT_DELAY
        )

        Log.w(TAG, "Scheduling reconnection attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS in ${delay}ms. Reason: $reason")

        reconnectJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                delay(delay)

                if (shouldReconnect && !_connectionState.value.isConnected) {
                    Log.d(TAG, "Executing reconnection attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS")
                    connectAndSubscribe()
                } else {
                    Log.d(TAG, "Skipping reconnection - conditions not met")
                    isReconnecting = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in reconnection job", e)
                isReconnecting = false
            }
        }
    }



    // Improve the reconnect method
    fun reconnect() {
        if (_connectionState.value.isConnected) {
            Log.d(TAG, "Already connected, skipping reconnection")
            return
        }

        if (isReconnecting) {
            Log.d(TAG, "Reconnection already in progress")
            return
        }

        Log.d(TAG, "Manual reconnection requested")
        shouldReconnect = true

        // Don't reset reconnectAttempts for manual reconnection
        // This allows the user to manually retry even after max attempts

        // Close existing connection if any
        webSocket?.close(1000, "Manual reconnect")
        cleanup()

        CoroutineScope(Dispatchers.IO).launch {
            delay(2000) // Longer delay for manual reconnections
            if (!_connectionState.value.isConnected) {
                connectAndSubscribe()
            }
        }
    }

    private fun sendConnectMessage() {
        // 1. Enviar mensaje CONNECT (requerido por NATS)
        val connectMsg = "CONNECT {\"verbose\":false,\"pedantic\":false,\"tls_required\":false}\r\n"
        webSocket?.send(connectMsg);
        Log.d(TAG, "CONNECT enviado");
    }

    private fun handleIncomingMessage(text: String) {
        try {

            if (!isJsonString(text)) {
                // Not JSON, handle as plain text
                Log.d(TAG, "Received plain text message: $text")
                handlePlainTextMessage(text)
            }

        } catch (e: Exception) {
            listener?.onError("Failed to parse message: ${e.message}")
        }
    }


    /**
     * Handle plain text messages (likely NATS protocol)
     */
    private fun handlePlainTextMessage(message: String) {
        // Process NATS protocol messages
        // Common NATS messages: INFO, MSG, +OK, -ERR, PING, PONG

        when {
            message.startsWith("INFO ") -> {
                // Server information
                Log.d(TAG, "NATS server info received")
            }
            message.startsWith("MSG ") -> {
                processNatsMessage(message)
            }

            message.startsWith("-ERR ") -> {
                // Error message
                Log.e(TAG, "Server returned error: ${message.substring(5)}")
                // Consider reconnecting on stale connection error
                if (message.contains("Stale Connection")) {
                    Log.w(TAG, "Connection is stale, attempting to reconnect...")
                    reconnect()
                }
            }
            message.startsWith("PONG") -> {
                // Response to our PING
                Log.d(TAG, "Received PONG from server")
            }
            message.startsWith("PING") -> {
                // Response to our PING
                Log.d(TAG, "Received PING from server")
                sendPong()
            }
            else -> {
                // Unknown message format
                Log.w(TAG, "Unrecognized message format: $message")
            }
        }
    }

    /**
     * Process NATS MSG format messages
     */
    private fun processNatsMessage(message: String) {
        // Message from a subscription
        // Format: MSG <subject> <sid> [reply-to] <bytes>\r\n<payload>
         try {
            // Split at the first \r\n to separate header from payload
            val parts = message.split("\r\n", limit = 2)
            if (parts.size < 2) {
                Log.w(TAG, "Invalid MSG format - missing payload")
            }

            val header = parts[0]
            val payload = parts[1]

            // Parse the header
            val headerParts = header.split(" ")
            if (headerParts.size < 4) {
                Log.w(TAG, "Invalid MSG header format: $header")
            }

            val subject = headerParts[1]
            val sid = headerParts[2]

            // Handle message payload - could be JSON or plain text
            Log.d(TAG, "Received message on channel $subject (sid: $sid): $payload")

            // Try to parse payload as JSON if it looks like JSON
            if (isJsonString(payload)) {
                try {
                    val json = JSONObject(payload)
                    val type = json.optString("type")


                    if (type == "payment-license") {
                        val messageObj = json.optJSONObject("message")
                        val licenseName = messageObj?.optString("name")

                        Log.d(TAG, "License name: $licenseName")

                        // Notify QrDialogManager about payment completion
                        QrDialogManager.notifyPaymentCompleted(licenseName ?: "")

                    } else {
                        Log.d(TAG, "Unhandled message type: $type")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse payload as JSON: $e")
                }
            } else{
                Log.d(TAG, "Message payload is plain text message: $payload")

            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MSG command: $e")
        }
    }


    /**
     * Check if a string is valid JSON
     */
    private fun isJsonString(str: String): Boolean {
        return try {
            // Simple check - you might want to use a proper JSON validator
            val trimmed = str.trim()
            (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                    (trimmed.startsWith("[") && trimmed.endsWith("]"))
        } catch (e: Exception) {
            false
        }
    }

   private fun subscribeToChannel(code: String, channel: String) {
       val subscriptionId = generateId()
       val channelName = "APKLIS_DEVICES_TEST.$code.$channel"
       val subMsg = "SUB $channelName $subscriptionId\r\n"

       if (_connectionState.value.isConnected) {
           webSocket?.send(subMsg)
           Log.d(TAG, "Subscription sent: ${subMsg.trim()}")
       } else {
           pendingMessages.add(subMsg)
       }
   }

    private fun sendPendingMessages() {
        pendingMessages.forEach { message ->
            webSocket?.send(message);
        }
        pendingMessages.clear()
    }

    private fun startPingPong() {
        pingJob = CoroutineScope(Dispatchers.IO).launch {
            while (_connectionState.value.isConnected) {
                delay(PING_INTERVAL)
                if (_connectionState.value.isConnected) {
                    sendPing()
                }
            }
        }
    }

    private fun sendPing() {
        val pingMsg = "PING"
        webSocket?.send(pingMsg)
        Log.d(TAG, "Sent PING message")
    }

    private fun sendPong() {
        val pongMsg = "PONG"
        webSocket?.send(pongMsg)
        Log.d(TAG, "Sent PONG response")
    }

    fun disconnect() {
        webSocket?.close(1000, "User requested disconnect")
        cleanup()
    }

    private fun cleanup() {
        pingJob?.cancel()
        connectionJob?.cancel()
        reconnectJob?.cancel()
        _connectionState.value = ConnectionState(isConnected = false)
        pendingMessages.clear()
        isReconnecting = false
    }

}


data class ConnectionState(
    val isConnected: Boolean = false,
    val error: String? = null,
    val lastPingTime: Long = 0L
)

private fun generateId(): String = Random.nextInt(10000, 99999).toString()

interface WebSocketEventListener {
    fun onConnected()
    fun onDisconnected(reason: String?)
    fun onError(error: String)
}