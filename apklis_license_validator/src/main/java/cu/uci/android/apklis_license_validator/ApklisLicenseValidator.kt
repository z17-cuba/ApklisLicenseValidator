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
                    "License UUID cannot be empty",
                    null
                )
            )
            return
        }

        Log.d(TAG, "Purchasing license with UUID: $licenseUuid")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: Map<String, Any>? = PurchaseAndVerify.purchaseLicense(
                    context,
                    licenseUuid
                )

                Log.d(TAG, "Purchase response: $response")

                withContext(Dispatchers.Main) {
                    if (response != null) {
                        callback.onSuccess(response)
                    } else {
                        callback.onError(
                            LicenseError(
                                "PURCHASE_FAILED",
                                "No response received from purchase operation",
                                null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error purchasing license", e)
                withContext(Dispatchers.Main) {
                    callback.onError(
                        LicenseError(
                            "PURCHASE_ERROR",
                            "Failed to purchase license: ${e.message}",
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
                    "Package ID cannot be empty",
                    null
                )
            )
            return
        }

        Log.d(TAG, "Verifying license for package: $packageId")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response: Map<String, Any>? = PurchaseAndVerify.verifyCurrentLicense(
                    context,
                    packageId
                )

                Log.d(TAG, "Verification response: $response")

                withContext(Dispatchers.Main) {
                    if (response != null) {
                        callback.onSuccess(response)
                    } else {
                        callback.onError(
                            LicenseError(
                                "VERIFICATION_FAILED",
                                "No response received from verification operation",
                                null
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying license", e)
                withContext(Dispatchers.Main) {
                    callback.onError(
                        LicenseError(
                            "VERIFY_ERROR",
                            "Failed to verify license: ${e.message}",
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
