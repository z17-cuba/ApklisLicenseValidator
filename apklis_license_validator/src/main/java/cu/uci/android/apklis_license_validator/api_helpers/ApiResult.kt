package cu.uci.android.apklis_license_validator.api_helpers

// API Response wrapper for error handling
sealed class ApiResult<out T> {
    data class Success<out T>(val data: T, val headers: Map<String, String>? = null) : ApiResult<T>()
    data class Error(val code: Int?, val message: String, val headers: Map<String, String>? = null) : ApiResult<Nothing>()
    data class Exception(val throwable: Throwable) : ApiResult<Nothing>()
}