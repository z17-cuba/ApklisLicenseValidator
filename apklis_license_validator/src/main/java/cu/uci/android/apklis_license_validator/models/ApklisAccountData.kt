package cu.uci.android.apklis_license_validator.models

data class ApklisAccountData(
    val username: String?,
    val deviceId: String?,
    val accessToken: String?,
    val code: String?
) {
    override fun toString(): String {
        return "ApklisAccountData(" +
                "username='$username', " +
                "deviceId='${if (deviceId != null) "***" else "null"}', " +
                "accessToken='${if (accessToken != null) "***" else "null"}', " +
                "code='${if (code != null) "***" else "null"}')"
    }
}