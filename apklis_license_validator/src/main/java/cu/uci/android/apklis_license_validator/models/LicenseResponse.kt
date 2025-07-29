package cu.uci.android.apklis_license_validator.models

import com.google.gson.annotations.SerializedName

data class VerifyLicenseResponse(
    @SerializedName("expire_in") val expireIn: String,
    @SerializedName("license") val license: String
) {
    fun toJsonString(): String {
        return """{"license": "$license", "expire_in": "$expireIn"}"""
    }
}
