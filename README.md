# ApklisLicenseValidator

A Kotlin library for license validation through the Apklis platform. This library provides methods to purchase and verify licenses in Android applications.

## Installation

Add this to your app's `build.gradle` file:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.z17-Cuba:ApklisLicenseValidatorLib:02e262fc1a'
}
```

## Usage

The library provides two main methods:
- `purchaseLicense()` - Purchase a license with a given UUID
- `verifyCurrentLicense()` - Verify the current license for a package
The library provides also structured error handling through the `LicenseError` class.

### 1. Instance-based Usage
Create an instance of `ApklisLicenseValidator` and call methods on it:

#### Purchase License
```kotlin
val validator = ApklisLicenseValidator()

validator.purchaseLicense(context, "your-license-uuid", object : LicenseCallback {
override fun onSuccess(response: Map<String, Any>) {
// License purchased successfully
Log.d("License", "Purchase successful: $response")

        // Handle the response data
        val success = response["success"] as? Boolean ?: false
        val paid = response["paid"] as? Boolean ?: false
        val license = response["license"] as? String ?: ""
        val username = response["username"] as? String ?: ""
        
        if (success && paid) {
            // Payment completed successfully
            Log.d("License", "Payment completed for license: $license")
            enablePremiumFeatures()
        } else if (response.containsKey("error")) {
            // Handle error cases
            val error = response["error"] as? String ?: "Unknown error"
            Log.e("License", "Purchase error: $error")
        }
    }
    
    override fun onError(error: LicenseError) {
        // Handle purchase error
        Log.e("License", "Purchase failed: ${error.message}")
        
        when (error.code) {
            "INVALID_ARGUMENT" -> {
                // Handle invalid license UUID
            }
            "PURCHASE_ERROR" -> {
                // Handle purchase failure
            }
            "PURCHASE_FAILED" -> {
                // Handle no response from server
            }
        }
    }
})
```

#### Verify Current License
```kotlin
val validator = ApklisLicenseValidator()

validator.verifyCurrentLicense(context, "com.example.myapp", object : LicenseCallback {
    override fun onSuccess(response: Map<String, Any>) {
        // License verification successful
        Log.d("License", "Verification successful: $response")
        
        // Handle the response data
        val license = response["license"] as? String ?: ""
        val paid = response["paid"] as? Boolean ?: false
        val username = response["username"] as? String ?: ""
        
        if (paid && license.isNotEmpty()) {
            // Valid license found
            Log.d("License", "Valid license found: $license for user: $username")
            enablePremiumFeatures()
        } else {
            // No valid license
            Log.d("License", "No valid license found")
            disablePremiumFeatures()
        }
    }
    
    override fun onError(error: LicenseError) {
        // Handle verification error
        Log.e("License", "Verification failed: ${error.message}")
        
        when (error.code) {
            "INVALID_ARGUMENT" -> {
                // Handle invalid package ID
            }
            "VERIFY_ERROR" -> {
                // Handle verification failure
            }
            "VERIFICATION_FAILED" -> {
                // Handle no response from server
            }
        }
    }
})
```

### 2. Static Utility Methods
Use the static utility methods from `ApklisLicenseUtils` for quick access:

#### Purchase License
```kotlin
ApklisLicenseUtils.purchaseLicense(context, "your-license-uuid", object : LicenseCallback {
    override fun onSuccess(response: Map<String, Any>) {
        // License purchased successfully
        Log.d("License", "Purchase successful: $response")
        
        // Process the response
        val success = response["success"] as? Boolean ?: false
        val paid = response["paid"] as? Boolean ?: false
        val license = response["license"] as? String ?: ""
        val username = response["username"] as? String ?: ""
        
        if (success && paid) {
            // Payment completed successfully
            Log.d("License", "Payment completed for license: $license")
            enablePremiumFeatures()
        } else if (response.containsKey("error")) {
            // Handle error within success response
            val error = response["error"] as? String ?: "Unknown error"
            showErrorDialog("Purchase Error", error)
        }
    }
    
    override fun onError(error: LicenseError) {
        // Handle purchase error
        Log.e("License", "Purchase failed: ${error.message}")
        
        // Show error message to user
        showErrorDialog("Purchase Failed", error.message)
    }
})
```

#### Verify Current License
```kotlin
ApklisLicenseUtils.verifyCurrentLicense(context, "com.example.myapp", object : LicenseCallback {
    override fun onSuccess(response: Map<String, Any>) {
        // License verification successful
        Log.d("License", "Verification successful: $response")
        
        // Process the response
        val license = response["license"] as? String ?: ""
        val paid = response["paid"] as? Boolean ?: false
        val username = response["username"] as? String ?: ""
        
        if (paid && license.isNotEmpty()) {
            // License is valid, enable features
            Log.d("License", "Valid license found: $license for user: $username")
            enablePremiumFeatures()
        } else {
            // No valid license found
            Log.d("License", "No valid license found")
            disablePremiumFeatures()
        }
    }
    
    override fun onError(error: LicenseError) {
        // Handle verification error
        Log.e("License", "Verification failed: ${error.message}")
        
        // Disable premium features on verification failure
        disablePremiumFeatures()
    }
})
```

## Response Format

### Purchase License Response

On successful purchase, the response contains:
```kotlin
// Successful payment completion
mapOf(
    "success" to true,
    "paid" to true,
    "license" to "license-name-string",
    "username" to "user-account-name"
)

// Payment dialog closed by user
mapOf(
    "success" to false,
    "paid" to false,
    "error" to "Dialog closed by user",
    "username" to "user-account-name"
)

// Error during payment process
mapOf(
    "error" to "error-message",
    "paid" to false,
    "username" to "user-account-name"
)
```

### Verify License Response
On successful verification, the response contains:

```kotlin
// Valid license found
mapOf(
    "license" to "license-name-string",
    "paid" to true,
    "username" to "user-account-name"
)

// No valid license
mapOf(
    "license" to "",
    "paid" to false,
    "username" to "user-account-name"
)

// Error during verification
mapOf(
    "error" to "error-message",
    "username" to "user-account-name",
    "status_code" to httpStatusCode  // Optional, only on API errors
)
```

## Error Handling
```kotlin
data class LicenseError(
    val code: String,       // Error code (e.g., "PURCHASE_ERROR", "VERIFY_ERROR")
    val message: String,    // Human-readable error message
    val exception: Exception? // Original exception if available
)
```

### Common Error Codes

| Code | Description |
|------|-------------|
| `INVALID_ARGUMENT` | Invalid or empty license UUID/package ID |
| `PURCHASE_ERROR` | General purchase operation failure |
| `PURCHASE_FAILED` | No response received from purchase operation |
| `VERIFY_ERROR` | General verification operation failure |
| `VERIFICATION_FAILED` | No response received from verification operation |


## Complete Example
Here's a complete example showing both methods in an Android Activity:

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Example 1: Instance-based usage
        val validator = ApklisLicenseValidator()

        findViewById<Button>(R.id.btnPurchase).setOnClickListener {
            purchaseLicenseExample(validator)
        }

        findViewById<Button>(R.id.btnVerify).setOnClickListener {
            verifyLicenseExample()
        }
    }

    private fun purchaseLicenseExample(validator: ApklisLicenseValidator) {
        validator.purchaseLicense(this, "sample-license-uuid", object : LicenseCallback {
            override fun onSuccess(response: Map<String, Any>) {
                runOnUiThread {
                    val success = response["success"] as? Boolean ?: false
                    val paid = response["paid"] as? Boolean ?: false
                    val license = response["license"] as? String ?: ""

                    if (success && paid) {
                        Toast.makeText(this@MainActivity, "License purchased: $license", Toast.LENGTH_SHORT).show()
                    } else {
                        val error = response["error"] as? String ?: "Unknown error"
                        Toast.makeText(this@MainActivity, "Purchase failed: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onError(error: LicenseError) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Purchase failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun verifyLicenseExample() {
        // Example 2: Static utility method
        ApklisLicenseUtils.verifyCurrentLicense(this, packageName, object : LicenseCallback {
            override fun onSuccess(response: Map<String, Any>) {
                runOnUiThread {
                    val license = response["license"] as? String ?: ""
                    val paid = response["paid"] as? Boolean ?: false

                    if (paid && license.isNotEmpty()) {
                        Toast.makeText(this@MainActivity, "Valid license: $license", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "No valid license found", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onError(error: LicenseError) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Verification failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
```

## Threading
- All callbacks are executed on the **Main Thread** for UI updates
- Network operations are performed on **Background Threads** automatically
- No need to handle threading manually
