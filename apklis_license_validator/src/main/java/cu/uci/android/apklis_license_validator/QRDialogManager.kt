package cu.uci.android.apklis_license_validator

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import cu.uci.android.apklis_license_validator.models.QrCode
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.set
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// QR Dialog Activity
@Suppress("DEPRECATION")
class QrDialogActivity : Activity(), WebSocketEventListener {
    companion object {
        private const val TAG = "QrDialogActivity"
        private const val WEBSOCKET_CHECK_DELAY = 2000L // 2 seconds
        //  private const val TRANSFERMOVILPKGNAME  = "cu.etecsa.cubacel.tr.tm"
        //TODO borrar este packageName
        const val TRANSFERMOVILPKGNAME = "cu.etecsa.cubacel.tr.tmtest"
    }

    private lateinit var qr: QrCode
    private lateinit var qrData: String
    private var dialog: AlertDialog? = null
    private var webSocketClient: WebSocketClient? = null
    private var isWebSocketConnected = false
    private var checkConnectionJob: Job? = null
    private var hasReturnedFromExternalApp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() called")

        // Get QR from intent
        qr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("qr", QrCode::class.java)
        } else {
            intent.getParcelableExtra("qr")
        } ?: run {
            Log.e(TAG, "No QR received")
            finish()
            return
        }

        // Get QR data from intent
        qrData = intent.getStringExtra("qr_data") ?: run {
            Log.e(TAG, "No QR Data received")
            finish()
            return
        }

        // Get the existing WebSocket client
        webSocketClient = WebSocketHolder.client

        // Set up WebSocket client to listen for payment confirmations
        setupWebSocketForPayment()

        // Set dialog theme
        setTheme(android.R.style.Theme_DeviceDefault_Light_Dialog)

        // Create and show dialog
        showDialog()

    }

    private fun showDialog() {
        // Inflate custom dialog layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_qr_code, null)

        // Find views
        val amountTextView = dialogView.findViewById<TextView>(R.id.amount_text)
        amountTextView.text = "Monto: $${qr.importe} CUP"

        val qrImageView = dialogView.findViewById<ImageView>(R.id.qr_image_view)
        val openAppButton = dialogView.findViewById<Button>(R.id.open_app_button)
        val closeButton = dialogView.findViewById<Button>(R.id.close_button)

        // Generate and display QR code
        val qrWithLogo = QrCodeGenerator.generateQrCodeWithSvg(
            content = qrData,
            size = 512,
            svgResourceId = R.drawable.apklis_logo,
            context = this
        )
        qrImageView.setImageBitmap(qrWithLogo)

        // Create dialog
        dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Register dialog with QrDialogManager
        QrDialogManager.setActiveDialog(dialog) // Add this line

        // Set button listeners
        openAppButton.setOnClickListener {
            openTransfermovil(qrData)
        }

        closeButton.setOnClickListener {
            dialog?.dismiss()
            QrDialogManager.notifyDialogClosed()
            WebSocketService.stopService(this)
            finish()
        }

        // Finish activity when dialog is dismissed
        dialog?.setOnDismissListener {
            WebSocketService.stopService(this)
            QrDialogManager.notifyDialogClosed()
            finish()
        }

        dialog?.show()
    }


    private fun setupWebSocketForPayment() {
        // Connect to existing WebSocket or create new one if needed
        webSocketClient?.let { client ->
            // If WebSocket is not connected, try to reconnect
            if (!isWebSocketConnected) {
                client.reconnect()
            }
        }
    }

    private fun openTransfermovil(qrData: String) {

        try {
            hasReturnedFromExternalApp = true // Mark that we're leaving the app

            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, qrData)
                type = "text/plain"
                setPackage(TRANSFERMOVILPKGNAME)
            }

            startActivity(sendIntent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Transfermóvil app not installed", e)
            hasReturnedFromExternalApp = false
        } catch (e: Exception) {
            Log.e(TAG, "Error opening Transfermóvil app", e)
            hasReturnedFromExternalApp = false
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume() called - Activity is in foreground and interactive")

        // Only check connection if we have a WebSocket client and it's not reconnecting
        webSocketClient?.let { client ->
            if (!client.isReconnecting()) {
                checkWebSocketConnection()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() called - Activity is no longer in foreground")

        // Cancel any pending connection checks
        checkConnectionJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy() called - Activity is being destroyed")

        // Clean up resources
        cleanup()
    }

    private fun checkWebSocketConnectionAfterReturn() {
        Log.d(TAG, "Checking WebSocket connection after returning from external app")

        webSocketClient?.let { client ->
            if (!isWebSocketConnected) {
                Log.w(TAG, "WebSocket not connected after return, forcing reconnection")
                // Force a reconnection attempt
                client.reconnect()
            } else {
                Log.d(TAG, "WebSocket already connected")
            }
        } ?: run {
            Log.w(TAG, "WebSocket client is null, trying to get from service")
            webSocketClient = WebSocketHolder.client
            webSocketClient?.reconnect()
        }
    }

    private fun checkWebSocketConnection() {
        checkConnectionJob?.cancel()
        checkConnectionJob = CoroutineScope(Dispatchers.IO).launch {
            delay(WEBSOCKET_CHECK_DELAY)

            webSocketClient?.let { client ->
                if (!isWebSocketConnected && !client.isReconnecting()) {
                    Log.w(TAG, "WebSocket not connected, attempting reconnection")
                    client.reconnect()
                } else {
                    Log.d(TAG, "WebSocket connection is healthy or reconnection in progress")
                }
            }
        }
    }


    private fun cleanup() {
        dialog?.dismiss()
        dialog = null
        checkConnectionJob?.cancel()

    }


    // WebSocket event listeners
    override fun onConnected() {
        Log.d(TAG, "WebSocket connected")
        isWebSocketConnected = true

    }

    override fun onDisconnected(reason: String?) {
        Log.d(TAG, "WebSocket disconnected: $reason")
        isWebSocketConnected = false

    }

    override fun onError(error: String) {
        Log.e(TAG, "WebSocket error: $error")
        isWebSocketConnected = false

    }

}

class QrDialogManager(private val context: Context) {
    companion object {
        private const val TAG = "QrDialogManager"
        private var activePaymentCallback: PaymentResultCallback? = null
        private var activeDialog: AlertDialog? = null

        fun setActivePaymentCallback(callback: PaymentResultCallback?) {
            activePaymentCallback = callback
        }

        fun setActiveDialog(dialog: AlertDialog?) {
            activeDialog = dialog
        }

        fun notifyPaymentCompleted(licenseName: String) {
            Log.d(TAG, "Notifying payment completed: $licenseName")

            // Close the dialog directly without calling notifyDialogClosed
            activeDialog?.dismiss()
            activeDialog = null

            activePaymentCallback?.onPaymentCompleted(licenseName)
        }

        fun notifyDialogClosed() {
            Log.d(TAG, "Notifying dialog closed")
            activePaymentCallback?.onDialogClosed()
            activePaymentCallback = null
            activeDialog = null // Clear dialog reference
        }
    }

    fun showQrDialog(qr: QrCode,
                     data: String,
                     callback: (Boolean) -> Unit
    ) {
        Handler(Looper.getMainLooper()).post {
            try {
                val intent = Intent(context, QrDialogActivity::class.java).apply {
                    putExtra("qr", qr)
                    putExtra("qr_data", data)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                callback(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error launching QR dialog activity", e)
                callback(false)
            }
        }
    }

}

object QrCodeGenerator {
    private const val TAG = "QrCodeGenerator"

    private fun generateQrCode(content: String, size: Int = 512, logoBitmap: Bitmap? = null): Bitmap {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }

            // Add logo if provided
            if (logoBitmap != null) {
                addLogoToBitmap(bitmap, logoBitmap)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating QR code", e)
            createErrorBitmap(size)
        }
    }

    fun generateQrCodeWithSvg(content: String, size: Int = 512, svgResourceId: Int, context: Context): Bitmap {
        val logoBitmap = loadSvgAsBitmap(context, svgResourceId, size / 6) // Logo will be 1/6 of QR size
        return generateQrCode(content, size, logoBitmap)
    }

    private fun addLogoToBitmap(qrBitmap: Bitmap, logoBitmap: Bitmap): Bitmap {
        val canvas = Canvas(qrBitmap)

        // Calculate logo size (recommended to be around 1/5 to 1/6 of QR code size)
        val logoSize = minOf(qrBitmap.width, qrBitmap.height) / 6
        val scaledLogo = logoBitmap.scale(logoSize, logoSize)

        // Calculate position to center the logo
        val left = (qrBitmap.width - scaledLogo.width) / 2f
        val top = (qrBitmap.height - scaledLogo.height) / 2f

        // Optional: Add white background circle for better contrast
        val paint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        val radius = logoSize / 2f + 8 // Add some padding
        canvas.drawCircle(left + scaledLogo.width / 2f, top + scaledLogo.height / 2f, radius, paint)

        // Draw the logo
        canvas.drawBitmap(scaledLogo, left, top, null)

        return qrBitmap
    }

    private fun loadSvgAsBitmap(context: Context, svgResourceId: Int, size: Int): Bitmap {
        return try {
            val drawable = ContextCompat.getDrawable(context, svgResourceId)
            val bitmap = createBitmap(size, size)
            val canvas = Canvas(bitmap)
            drawable?.setBounds(0, 0, size, size)
            drawable?.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error loading SVG", e)
            createDefaultLogoBitmap(size)
        }
    }

    private fun createDefaultLogoBitmap(size: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLUE
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return bitmap
    }

    private fun createErrorBitmap(size: Int): Bitmap {
        val bitmap = createBitmap(size, size, Bitmap.Config.RGB_565)
        bitmap.eraseColor(Color.GRAY)
        return bitmap
    }
}