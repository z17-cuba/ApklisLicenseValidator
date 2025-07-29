package cu.uci.android.apklis_license_validator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class WebSocketService : Service() {
    private val binder = WebSocketBinder()
    private var webSocketClient: WebSocketClient? = null

    companion object {
        private const val TAG = "WebSocketService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "websocket_service_channel"

        fun startService(context: Context, code: String, deviceId: String) {
            val intent = Intent(context, WebSocketService::class.java).apply {
                putExtra("code", code)
                putExtra("deviceId", deviceId)
            }

            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WebSocketService::class.java)
            context.stopService(intent)
        }
    }

    inner class WebSocketBinder : Binder() {
        fun getService(): WebSocketService = this@WebSocketService
        fun getWebSocketClient(): WebSocketClient? = webSocketClient
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WebSocket service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WebSocket service started")

        val code = intent?.getStringExtra("code")
        val deviceId = intent?.getStringExtra("deviceId")

        if (code != null && deviceId != null) {
            startForeground(NOTIFICATION_ID, createNotification())
            initializeWebSocket(code, deviceId)
        } else {
            Log.e(TAG, "Missing code or deviceId")
            stopSelf()
        }

        // Return START_STICKY to restart service if killed
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun initializeWebSocket(code: String, deviceId: String) {
        if (webSocketClient == null) {
            webSocketClient = WebSocketClient(
                listener = object : WebSocketEventListener {
                    override fun onConnected() {
                        Log.d(TAG, "Service WebSocket connected")
                        updateNotification("Connected")
                    }

                    override fun onDisconnected(reason: String?) {
                        Log.d(TAG, "Service WebSocket disconnected: $reason")
                        updateNotification("Disconnected")
                    }

                    override fun onError(error: String) {
                        Log.e(TAG, "Service WebSocket error: $error")
                        updateNotification("Error: $error")
                    }
                },
                context = this
            )

            WebSocketHolder.client = webSocketClient
        }

        webSocketClient?.init(code, deviceId)
        webSocketClient?.connectAndSubscribe()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WebSocket Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains WebSocket connection for license validation"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String = "Starting"): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("License Validator")
            .setContentText("WebSocket Status: $status")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WebSocket service destroyed")
        webSocketClient?.disconnect()
        webSocketClient = null
        WebSocketHolder.client = null
    }
}