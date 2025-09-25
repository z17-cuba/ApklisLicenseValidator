package cu.uci.android.apklis_license_validator.models

sealed class PaymentResponse {
    data class Qr(val qrCode: QrCode) : PaymentResponse()
    data class DirectLicense(val license: VerifyLicenseResponse) : PaymentResponse()
}
