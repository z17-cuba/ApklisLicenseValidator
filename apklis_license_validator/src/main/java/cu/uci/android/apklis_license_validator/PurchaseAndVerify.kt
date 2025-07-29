package cu.uci.android.apklis_license_validator

import android.Manifest
import android.accounts.AccountManager
import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.annotation.RequiresPermission
import cu.uci.android.apklis_license_validator.api_helpers.ApiResult
import cu.uci.android.apklis_license_validator.api_helpers.ApiService
import cu.uci.android.apklis_license_validator.models.ApklisAccountData
import cu.uci.android.apklis_license_validator.models.LicenseRequest
import cu.uci.android.apklis_license_validator.models.PaymentRequest
import cu.uci.android.apklis_license_validator.models.QrCode
import cu.uci.android.apklis_license_validator.models.VerifyLicenseResponse
import cu.uci.android.apklis_license_validator.signature_helpers.SignatureVerificationService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PurchaseAndVerify {
    companion object {
        private const val TYPE = "cu.uci.android.apklis"
        private const val TAG = "PurchaseAndVerify"
        private const val SIGNATURE_HEADER_NAME = "signature"

        private val signatureVerificationService = SignatureVerificationService()

        @RequiresPermission(Manifest.permission.GET_ACCOUNTS)
        suspend fun purchaseLicense(context: Context, licenseUuid: String): Map<String, Any>? {
            val apklisAccountData : ApklisAccountData? = ApklisDataGetter.getApklisAccountData(context)

            try {
                WebSocketClient().apply {


                    val paymentResult = ApiService().payLicenseWithTF(
                        PaymentRequest(apklisAccountData?.deviceId ?: ""),
                        licenseUuid,
                        apklisAccountData?.accessToken ?: ""
                    )

                    return when (paymentResult) {
                        is ApiResult.Success -> {
                            val qrCode : QrCode = paymentResult.data

                            // Verify signature only on successful response
                            val isSignatureValid = verifySignatureIfPresent(
                                context,
                                qrCode.toJsonString(),
                                paymentResult.headers
                            )

                            if (!isSignatureValid) {
                                Log.w(TAG, "Signature verification failed for successful response")
                                return buildMap<String, Any> {
                                    put("error", "Invalid response signature")
                                    put("username", apklisAccountData?.username ?: "")
                                }
                            } else {
                                // Handle WebSocket connection and QR dialog
                                return handleWebSocketAndQrDialog(
                                    context,
                                    qrCode,
                                    apklisAccountData
                                )

                            }
                        }
                        is ApiResult.Error -> {
                            val errorMessage = "Error al efectuar el pago ${paymentResult.code}: ${paymentResult.message}"
                            Log.e(TAG, errorMessage)
                            buildMap<String, Any> {
                                put("error", paymentResult.message)
                                put("username", apklisAccountData?.username ?: "")
                                paymentResult.code?.let { put("status_code", it) }
                            }
                        }
                        is ApiResult.Exception -> {
                            val errorMessage = "Excepción al efectuar el pago: ${paymentResult.throwable.message}"
                            Log.e(TAG, errorMessage)
                            buildMap<String, Any> {
                                put("error", errorMessage)
                                put("username", apklisAccountData?.username ?: "")
                            }
                        }
                    }

                }
            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            return null
        }


        private suspend fun handleWebSocketAndQrDialog(
            context: Context,
            qrCode: QrCode,
            apklisAccountData : ApklisAccountData?
        ): Map<String, Any> = suspendCancellableCoroutine  { continuation ->

            // Flag to track if continuation has been resumed
            var isResumed = false

            val webSocketClient = WebSocketClient(object : WebSocketEventListener {
                override fun onConnected() {
                    Log.d(TAG, "WebSocket connected successfully")
                    // Now show the QR dialog since WebSocket is connected
                    if (!isResumed) {
                        Log.d(TAG, "WebSocket connected, showing QR dialog")
                        CoroutineScope(Dispatchers.Main).launch {
                            showQrDialogAfterConnection(context, qrCode, apklisAccountData?.username ?: "", continuation)
                        }
                    }
                }

                override fun onDisconnected(reason: String?) {
                    Log.d(TAG, "WebSocket disconnected: $reason")
                }

                override fun onError(error: String) {
                    Log.e(TAG, "WebSocket connection error: $error")
                    // Resume with error if WebSocket fails to connect
                    if (!isResumed) {
                        isResumed = true
                        continuation.resume(buildMap<String, Any> {
                            put("error", "WebSocket connection failed: $error")
                            put("username", apklisAccountData?.username ?: "")
                        })
                    }
                }
            })

            try {
                // Start WebSocket service if not already running
                WebSocketService.startService(context, apklisAccountData?.code ?: "", apklisAccountData?.deviceId ?: "")

                // Initialize and connect WebSocket
                webSocketClient.init(apklisAccountData?.code ?: "", apklisAccountData?.deviceId ?: "")

                WebSocketHolder.client = webSocketClient
                webSocketClient.connectAndSubscribe()


            } catch (e: Exception) {
                Log.e(TAG, "Error initializing WebSocket: ${e.message}")
                if (!isResumed) {
                    isResumed = true
                    continuation.resume(buildMap<String, Any> {
                        put("error", "Failed to initialize WebSocket: ${e.message}")
                        put("username", apklisAccountData?.username ?: "")
                    })
                }
            }
        }

        private suspend fun showQrDialogAfterConnection(
            context: Context,
            qrCode: QrCode,
            username: String,
            continuation: CancellableContinuation<Map<String, Any>>
        ) {

            val qrData = qrCode.toJsonString()
            val qrDialogManager = QrDialogManager(context)

            // Create payment callback to handle WebSocket messages
            val paymentCallback = object : PaymentResultCallback {
                override fun onPaymentCompleted(licenseName: String) {
                    Log.d(TAG, "Payment completed with license: $licenseName")
                    if (continuation.isActive) {
                        continuation.resume(buildMap {
                            put("success", true)
                            put("paid", true)
                            put("license", licenseName)
                            put("username", username)
                        })
                    }
                }

                override fun onPaymentFailed(error: String) {
                    Log.e(TAG, "Payment failed: $error")
                    if (continuation.isActive) {
                        continuation.resume(buildMap {
                            put("error", error)
                            put("paid", false)
                            put("username",username)
                        })
                    }
                }

                override fun onDialogClosed() {
                    Log.d(TAG, "Dialog was closed by user")
                    if (continuation.isActive) {
                        continuation.resume(buildMap {
                            put("success", false)
                            put("paid", false)
                            put("error", "Dialog closed by user")
                            put("username", username)
                        })
                    }
                }
            }


            // Set the active payment callback
            QrDialogManager.setActivePaymentCallback(paymentCallback)


            // Show the dialog
            qrDialogManager.showQrDialog(qrCode, qrData) { success ->
                if (!success) {
                    val errorMessage = "Failed to show dialog"
                    Log.e(TAG, errorMessage)
                    if (continuation.isActive) {
                        continuation.resume(buildMap {
                            put("error", errorMessage)
                            put("paid", false)
                            put("username", username)
                        })
                    }
                }
            }
        }

        @RequiresPermission(Manifest.permission.GET_ACCOUNTS)
       suspend fun verifyCurrentLicense(context: Context, packageId: String): Map<String, Any>? {
             val apklisAccountData : ApklisAccountData? = ApklisDataGetter.getApklisAccountData(context)

            try {

                    val verificationResult = ApiService().verifyCurrentLicense(
                        LicenseRequest( packageId,apklisAccountData?.deviceId ?: ""),
                        apklisAccountData?.accessToken ?: ""
                    )

                   return when (verificationResult) {
                        is ApiResult.Success -> {

                            val verifyLicenseResponse : VerifyLicenseResponse = verificationResult.data

                            // Verify signature only on successful response
                            val isSignatureValid = verifySignatureIfPresent(
                                context,
                                verifyLicenseResponse.toJsonString(),
                                verificationResult.headers
                            )

                            if (!isSignatureValid) {
                                Log.w(TAG, "Signature verification failed for successful response")
                                return buildMap<String, Any> {
                                    put("error", "Invalid response signature")
                                    put("username", apklisAccountData?.username ?: "")
                                }
                            } else {
                                Log.d(TAG, "Hay una licencia: $verificationResult");
                                val hasPaidLicense = verificationResult.data.license.isNotEmpty()
                                buildMap<String, Any> {
                                    put("license", verificationResult.data.license)
                                    put("paid", hasPaidLicense)
                                    put("username", apklisAccountData?.username ?: "")
                                }
                            }

                        }
                        is ApiResult.Error -> {
                            val errorMessage = "Fallo al efectuar la verificación ${verificationResult.code}: ${verificationResult.message}"
                            Log.e(TAG, errorMessage)
                            buildMap<String, Any> {
                                put("error", verificationResult.message)
                                put("username", apklisAccountData?.username ?: "")
                                verificationResult.code?.let { put("status_code", it) }
                            }
                        }
                        is ApiResult.Exception -> {
                            val errorMessage = "Fallo al efectuar la verificación: ${verificationResult.throwable.message}"
                            Log.e(TAG, errorMessage)
                            buildMap<String, Any> {
                                put("error", errorMessage)
                                put("username", apklisAccountData?.username ?: "")
                            }
                        }
                    }


            } catch (e: RemoteException) {
                e.printStackTrace()
            }
            return null
        }


        /**
         * Verifies the signature if present in the response headers
         */
        private  fun verifySignatureIfPresent(
            context: Context,
            responseString: String,
            headers: Map<String, String>?
        ): Boolean {
            val signatureValue = headers?.get(SIGNATURE_HEADER_NAME)

            if (signatureValue.isNullOrEmpty()) {
                Log.w(TAG, "No signature header found in response")
                return false
            }


            return signatureVerificationService.verifySignature(
                context,
                responseString.toByteArray(),
                signatureValue
            )
        }
    }
    }
