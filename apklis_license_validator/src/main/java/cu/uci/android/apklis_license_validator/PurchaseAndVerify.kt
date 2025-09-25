package cu.uci.android.apklis_license_validator

import android.Manifest
import android.content.Context
import android.os.RemoteException
import android.util.Log
import androidx.annotation.RequiresPermission
import cu.uci.android.apklis_license_validator.api_helpers.ApiResult
import cu.uci.android.apklis_license_validator.models.PaymentResponse
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
import java.util.Locale

class PurchaseAndVerify {
    companion object {
        private const val TAG = "PurchaseAndVerify"
        private const val SIGNATURE_HEADER_NAME = "signature"

        private val signatureVerificationService = SignatureVerificationService()


        @RequiresPermission(Manifest.permission.GET_ACCOUNTS)
        suspend fun purchaseLicense(context: Context, licenseUuid: String): Map<String, Any>? {
            val apklisAccountData : ApklisAccountData? = ApklisDataGetter.getApklisAccountData(context)

            try {
                WebSocketClient().apply {
                    val  deviceLanguage = Locale.getDefault().language

                    val paymentResult = ApiService().payLicenseWithTF(
                        PaymentRequest(apklisAccountData?.deviceId ?: ""),
                        licenseUuid,
                        apklisAccountData?.accessToken ?: "",
                        deviceLanguage,
                    )

                    return when (paymentResult) {
                        is ApiResult.Success -> {
                            val responseData = paymentResult.data
                            val responseBodyString = when (responseData) {
                                is PaymentResponse.Qr -> responseData.qrCode.toJsonString()
                                is PaymentResponse.DirectLicense -> responseData.license.toJsonString()
                            }

                            when (responseData) {
                                is PaymentResponse.Qr -> {
                                    // Verify signature only on successful response
                                    val isSignatureValid = verifySignatureIfPresent(
                                        context,
                                        responseBodyString,
                                        paymentResult.headers
                                    )

                                    if (!isSignatureValid) {
                                        Log.w(TAG, context.getString(R.string.log_signature_verification_failed))
                                        return buildMap {
                                            put("error", context.getString(R.string.error_invalid_response_signature))
                                            put("username", apklisAccountData?.username ?: "")
                                        }
                                    }

                                    // It's a QR code, proceed with the dialog flow
                                    return handleWebSocketAndQrDialog(
                                        context,
                                        responseData.qrCode,
                                        apklisAccountData
                                    )
                                }
                                is PaymentResponse.DirectLicense -> {
                                    // It's a direct license, return success immediately
                                    return buildMap {
                                        put("success", true)
                                        put("paid", true)
                                        put("license", responseData.license.license ?: "")
                                        put("username", apklisAccountData?.username ?: "")
                                    }
                                }
                            }
                        }
                        is ApiResult.Error -> {

                            val errorMessage =   context.getString(R.string.error_payment_failed_with_code, paymentResult.code.toString(), paymentResult.message)
                            Log.e(TAG, errorMessage)
                            buildMap<String, Any> {
                                put("error", paymentResult.message)
                                put("username", apklisAccountData?.username ?: "")
                                paymentResult.code?.let { put("status_code", it) }
                            }
                        }
                        is ApiResult.Exception -> {
                            val errorMessage =  context.getString(R.string.error_payment_exception, paymentResult.throwable.message)

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

                    Log.d(TAG, context.getString(R.string.websocket_connected))
                    // Now show the QR dialog since WebSocket is connected
                    if (!isResumed) {
                        Log.d(TAG, context.getString(R.string.websocket_connected_showing_qr_dialog))
                        CoroutineScope(Dispatchers.Main).launch {
                            showQrDialogAfterConnection(context, qrCode, apklisAccountData?.username ?: "", continuation)
                        }
                    }
                }

                override fun onDisconnected(reason: String?) {
                    Log.d(TAG,  context.getString(R.string.websocket_disconnected, reason))
                }

                override fun onError(error: String) {
                    Log.e(TAG,  context.getString(R.string.error_websocket_connection_failed, error))
                    // Resume with error if WebSocket fails to connect
                    if (!isResumed) {
                        isResumed = true
                         try {
                            continuation.resume(buildMap<String, Any> {
                                put("error", context.getString(R.string.error_websocket_connection_failed, error))
                                put("username", apklisAccountData?.username ?: "")
                            })
                        } catch (e: IllegalStateException) {
                            Log.e(TAG, context.getString(R.string.continuation_resumed_ignoring_websocket, error))
                        }
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
                Log.e(TAG, context.getString(R.string.error_websocket_init_failed, e.message))
                if (!isResumed) {
                    isResumed = true
                     try {
                        continuation.resume(buildMap<String, Any> {
                            put("error", context.getString(R.string.error_websocket_init_failed, e.message))
                            put("username", apklisAccountData?.username ?: "")
                        })
                    } catch (ex: IllegalStateException) {
                         Log.e(TAG, context.getString(R.string.continuation_resumed_ignoring_websocket, e.message))
                     }
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
                    Log.d(TAG, context.getString(R.string.payment_completed, licenseName))
                    if (continuation.isActive) {
                         try {
                            continuation.resume(buildMap {
                                put("success", true)
                                put("paid", true)
                                put("license", licenseName)
                                put("username", username)
                            })
                        } catch (e: IllegalStateException) {
                             Log.e(TAG, context.getString(R.string.continuation_resumed_ignoring_payment_completion,))
                        }
                    }
                }

                override fun onPaymentFailed(error: String) {
                    Log.e(TAG, context.getString(R.string.error_payment_failed,error))
                    if (continuation.isActive) {
                        try {
                            continuation.resume(buildMap {
                                put("error", error)
                                put("paid", false)
                                put("username", username)
                            })
                        } catch (e: IllegalStateException) {
                            Log.e(TAG, context.getString(R.string.continuation_resumed_ignoring_payment_failure,error))
                        }
                    }
                }

                override fun onDialogClosed() {
                    Log.d(TAG,context.getString(R.string.dialog_closed_by_user) )
                    if (continuation.isActive) {
                        try {
                            continuation.resume(buildMap {
                                put("success", false)
                                put("paid", false)
                                put("error", context.getString(R.string.dialog_closed_by_user) )
                                put("username", username)
                            })
                        } catch (e: IllegalStateException) {
                            Log.e(TAG, context.getString(R.string.continuation_resumed_ignoring_dialog_close,))

                        }
                    }
                }
            }


            // Set the active payment callback
            QrDialogManager.setActivePaymentCallback(paymentCallback)


            // Show the dialog
            qrDialogManager.showQrDialog(qrCode, qrData) { success ->
                if (!success) {
                    val errorMessage = context.getString(R.string.error_failed_to_show_dialog,)
                    Log.e(TAG, errorMessage)
                    if (continuation.isActive) {
                          try {
                            continuation.resume(buildMap {
                                put("error", errorMessage)
                                put("paid", false)
                                put("username", username)
                            })
                        } catch (e: IllegalStateException) {
                            Log.e(TAG, context.getString(R.string.continuation_resumed_ignoring_dialog_failure,) )
                        }
                    }
                }
            }
        }

        @RequiresPermission(Manifest.permission.GET_ACCOUNTS)
       suspend fun verifyCurrentLicense(context: Context, packageId: String): Map<String, Any>? {
             val apklisAccountData : ApklisAccountData? = ApklisDataGetter.getApklisAccountData(context)
            val  deviceLanguage = Locale.getDefault().language

            try {

                    val verificationResult = ApiService().verifyCurrentLicense(
                        LicenseRequest( packageId,apklisAccountData?.deviceId ?: ""),
                        apklisAccountData?.accessToken ?: "",
                        deviceLanguage
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
                                Log.w(TAG,context.getString(R.string.log_signature_verification_failed,))
                                return buildMap<String, Any> {
                                    put("error",context.getString(R.string.error_invalid_response_signature,))
                                    put("username", apklisAccountData?.username ?: "")
                                }
                            } else {
                                Log.d(TAG, context.getString(R.string.license_already_exists,verificationResult));
                                val hasPaidLicense = verificationResult.data.license.isNotEmpty()
                                buildMap<String, Any> {
                                    put("license", verificationResult.data.license)
                                    put("paid", hasPaidLicense)
                                    put("username", apklisAccountData?.username ?: "")
                                }
                            }

                        }
                        is ApiResult.Error -> {
                            val errorMessage =context.getString(R.string.error_verification_failed_with_code,verificationResult.code.toString(), verificationResult.message)
                            Log.e(TAG, errorMessage)
                            buildMap<String, Any> {
                                put("error", verificationResult.message)
                                put("username", apklisAccountData?.username ?: "")
                                verificationResult.code?.let { put("status_code", it) }
                            }
                        }
                        is ApiResult.Exception -> {
                            val errorMessage = context.getString(R.string.error_verification_exception,verificationResult.throwable.message)
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
                Log.e(TAG, context.getString(R.string.log_no_signature_header))
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
