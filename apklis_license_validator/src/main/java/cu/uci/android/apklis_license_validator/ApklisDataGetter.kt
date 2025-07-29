package cu.uci.android.apklis_license_validator

import android.content.Context
import android.database.Cursor
import android.util.Log
import androidx.core.net.toUri
import cu.uci.android.apklis_license_validator.models.ApklisAccountData

class ApklisDataGetter {

    companion object {
        private const val TAG = "ApklisDataGetter"
        private const val AUTHORITY = "cu.uci.android.apklis.ApklisLicenseProvider"
        private val CONTENT_URI = "content://$AUTHORITY/account_data".toUri()

        // Column names
        private const val COLUMN_USERNAME = "username"
        private const val COLUMN_DEVICE_ID = "device_id"
        private const val COLUMN_ACCESS_TOKEN = "access_token"
        private const val COLUMN_CODE = "code"

        /**
         * Retrieves all account data from the Apklis app
         * @param context The application context
         * @return List of ApklisAccountData objects, or null if data cannot be retrieved
         */
        fun getApklisAccountData(context: Context): ApklisAccountData? {
            var cursor: Cursor? = null

            return try {
                Log.d(TAG, "Querying Apklis account data...")

                cursor = context.contentResolver.query(
                    CONTENT_URI,
                    null, // projection
                    null, // selection
                    null, // selectionArgs
                    null  // sortOrder
                )

                if (cursor == null) {
                    Log.e(TAG, "Cursor is null - provider might not be available")
                    return null
                }

                Log.d(TAG, "Cursor returned with ${cursor.count} rows")

                if (cursor.moveToFirst()) {
                    val username = cursor.getString(COLUMN_USERNAME)
                    val deviceId = cursor.getString(COLUMN_DEVICE_ID)
                    val accessToken = cursor.getString(COLUMN_ACCESS_TOKEN)
                    val code = cursor.getString(COLUMN_CODE)

                    val data = ApklisAccountData(username, deviceId, accessToken, code)
                    Log.d(TAG, "Retrieved: $data")

                    data
                } else {
                    Log.w(TAG, "No account data found")
                    null
                }

            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException - missing permission: ${e.message}")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving account data: ${e.message}", e)
                null
            } finally {
                cursor?.close()
            }
        }

        /**
         * Helper method to safely get string from cursor
         */
        private fun Cursor.getString(columnName: String): String? {
            val columnIndex = getColumnIndex(columnName)
            return if (columnIndex >= 0) getString(columnIndex) else null
        }

        /**
         * Checks if the Apklis app is installed and the provider is available
         * @param context The application context
         * @return true if provider is available, false otherwise
         */
        fun isApklisDataAvailable(context: Context): Boolean {
            return try {
                val cursor = context.contentResolver.query(
                    CONTENT_URI,
                    arrayOf(COLUMN_USERNAME), // minimal projection
                    null, null, null
                )

                cursor?.use { true } == true

            } catch (e: Exception) {
                Log.d(TAG, "Apklis data provider not available: ${e.message}")
                false
            }
        }
    }
}