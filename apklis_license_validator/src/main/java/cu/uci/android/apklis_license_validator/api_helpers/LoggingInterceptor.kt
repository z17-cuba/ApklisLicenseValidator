package cu.uci.android.apklis_license_validator.api_helpers

import okhttp3.Interceptor
import okhttp3.Response

class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        println("🔗 REQUEST: ${request.method} ${request.url}")
        println("📤 Headers: ${request.headers}")

        val startTime = System.currentTimeMillis()
        val response = chain.proceed(request)
        val endTime = System.currentTimeMillis()

        println("📥 RESPONSE: ${response.code} (${endTime - startTime}ms)")
        println("📋 Response Headers: ${response.headers}")

        return response
    }

}