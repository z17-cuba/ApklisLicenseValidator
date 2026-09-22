package cu.uci.android.apklis_license_validator.models

import com.google.gson.annotations.SerializedName

data class PaymentRequest(
    @SerializedName("device") val device: String,
    @SerializedName("webhook_url") val webhookUrl: String? = null
)