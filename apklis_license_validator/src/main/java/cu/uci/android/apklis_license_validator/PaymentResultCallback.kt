package cu.uci.android.apklis_license_validator

interface PaymentResultCallback {
    fun onPaymentCompleted(licenseName: String)
    fun onPaymentFailed(error: String)
    fun onDialogClosed()
}