package cu.uci.android.apklis_license_validator

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ApklisLicenseValidator - A Kotlin library for license validation
 *
 * This library provides methods to purchase and verify licenses through the Apklis platform.
 * It can be used as a dependency in Android projects via JitPack.
 */
class ApklisLicenseValidator {

    companion object {
        private const val TAG = "ApklisLicenseValidator"

        /**
         * Static method to create and configure the library instance
         */
        @JvmStatic
        fun createInstance(): ApklisLicenseValidator {
            return ApklisLicenseValidator()
        }
    }

    /**
     * Purchase a license with the given UUID
     * @param context Android context
     * @param licenseUuid The UUID of the license to purchase
     * @param callback Callback to receive the result
     */
    fun purchaseLicense(
         context: Context,
        licenseUuid: String,
        callback: LicenseCallback
    ) {
        if (licenseUuid.isBlank()) {
            callback.onError(
                LicenseError(
                    "INVALID_ARGUMENT",
                    context.getString(R.string.error_empty_license_uuid),
                    null
                )
            )
            return
        }

        Log.d(TAG, context.getString(R.string.purchasing_license, licenseUuid))

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: Map<String, Any>? = PurchaseAndVerify.purchaseLicense(
                    context,
                    licenseUuid
                )

                Log.d(TAG, context.getString(R.string.purchase_response, response))

                withContext(Dispatchers.Main) {
                    if (response != null) {
                        callback.onSuccess(response)
                    } else {
                        callback.onError(
                            LicenseError(
                                "PURCHASE_FAILED",
                                context.getString(R.string.no_response_from_purchase),
                                null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, context.getString(R.string.error_purchasing_license,e))
                withContext(Dispatchers.Main) {
                    callback.onError(
                        LicenseError(
                            "PURCHASE_ERROR",
                            context.getString(R.string.error_purchasing_license,e.message),
                            e
                        )
                    )
                }
            }
        }
    }

    /**
     * Verify current license for the given package ID
     * @param context Android context
     * @param packageId The package ID to verify
     * @param callback Callback to receive the result
     */
    fun verifyCurrentLicense(
        context: Context,
         packageId: String,
         callback: LicenseCallback
    ) {
        if (packageId.isBlank()) {
            callback.onError(
                LicenseError(
                    "INVALID_ARGUMENT",
                    context.getString(R.string.error_empty_package_id),
                    null
                )
            )
            return
        }

        Log.d(TAG,context.getString(R.string.verifying_license,packageId))

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: Map<String, Any>? = PurchaseAndVerify.verifyCurrentLicense(
                    context,
                    packageId
                )

                Log.d(TAG, context.getString(R.string.verification_response,response))

                withContext(Dispatchers.Main) {
                    if (response != null) {
                        callback.onSuccess(response)
                    } else {
                        callback.onError(
                            LicenseError(
                                "VERIFICATION_FAILED",
                                context.getString(R.string.no_response_from_verification),
                                null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, context.getString(R.string.error_verifying_license, e))
                withContext(Dispatchers.Main) {
                    callback.onError(
                        LicenseError(
                            "VERIFY_ERROR",
                            context.getString(R.string.error_verifying_license, e.message),
                            e
                        )
                    )
                }
            }
        }
    }

/**
 * Callback interface for asynchronous license operations
 */
interface LicenseCallback {
    /**
     * Called when the operation completes successfully
     * @param response The response data from the operation
     */
    fun onSuccess(response: Map<String, Any>)

    /**
     * Called when the operation fails
     * @param error The error information
     */
    fun onError(error: LicenseError)
}

/**
 * Error class for license operations
 */
data class LicenseError(
    val code: String,
    val message: String,
    val exception: Exception?
)

/**
 * Utility class for static access to license operations
 */
object ApklisLicenseUtils {

    /**
     * Static method to purchase a license
     * @param context Android context
     * @param licenseUuid The UUID of the license to purchase
     * @param callback Callback to receive the result
     */
    @JvmStatic
    fun purchaseLicense(
        context: Context,
        licenseUuid: String,
        callback: LicenseCallback
    ) {
        ApklisLicenseValidator().purchaseLicense(context, licenseUuid, callback)
    }

    /**
     * Static method to verify current license
     * @param context Android context
     * @param packageId The package ID to verify
     * @param callback Callback to receive the result
     */
    @JvmStatic
    fun verifyCurrentLicense(
        context: Context,
        packageId: String,
        callback: LicenseCallback
    ) {
        ApklisLicenseValidator().verifyCurrentLicense(context, packageId, callback)
    }
}
}
